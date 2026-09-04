package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceExecutionListener;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 运维重分配服务：为 DSL v2 空审批人 {@code BLOCK}/{@code FALLBACK_ROLE}（兜底仍为空）策略
 * 触发后停在待分配状态的任务补充真实候选人（production-approval-lifecycle change design.md
 * Decision 5"运维重分配必须留下原候选人、原因、执行者与新候选人"）。区分两种停留形态：
 * 单人/候选组节点（{@link cn.nihility.rbac.workflow.engine.flowable.WorkflowAssigneeTaskListener}
 * 产生，任务已创建但 {@code assignee_id}/{@code candidate_type} 均为空）直接补写处理人/候选人；
 * 会签节点（{@link WorkflowMultiInstanceExecutionListener} 产生，任务的
 * {@code assignee_id} 恰为哨兵值 {@code 0}）先用
 * {@code runtimeService.addMultiInstanceExecution} 为每个真实候选人新增多实例分支，再删除
 * 哨兵分支，使会签 N/K 计算按补充后的真实候选人数量进行，不遗留哨兵占位票。恢复操作通过
 * {@link IdempotencyService} 保证同一幂等键重复调用不会重复新增/删除分支。
 */
@Service
@RequiredArgsConstructor
public class WorkflowV2ReassignmentService {

    /** 审批任务数据访问接口。 */
    private final ApprovalTaskMapper approvalTaskMapper;

    /** 审批任务候选人明细数据访问接口。 */
    private final ApprovalTaskCandidateMapper approvalTaskCandidateMapper;

    /** 节点轮次数据访问接口，用于会签哨兵分支被真实候选人替换后同步总票数 N 与通过阈值 K
     *  （production-approval-lifecycle change tasks.md 6.3）。 */
    private final NodeRunMapper nodeRunMapper;

    /** 节点审批人规则数据访问接口，重算通过阈值 K 需要读取 {@code approvalMode}/
     *  {@code approvalPercent}。 */
    private final NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** 流程实例数据访问接口。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 审批轨迹数据访问接口。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /** 操作幂等服务。 */
    private final IdempotencyService idempotencyService;

    /** Flowable 运行时服务。 */
    private final RuntimeService runtimeService;

    /** Flowable 任务服务。 */
    private final TaskService taskService;

    /**
     * 为一个停在待分配状态的任务补充真实候选人。
     *
     * @param taskId     停在待分配状态的 {@code tab_wf_approval_task.id}
     * @param newUserIds 真实候选人用户 id 列表，不能为空
     * @param operatorId 执行重分配的运维操作人 id
     * @param requestKey 幂等键，可为空
     * @param remark     重分配原因说明
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void reassign(Long taskId, List<Long> newUserIds, Long operatorId, String requestKey, String remark) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("newUserIds", newUserIds);
        payload.put("remark", remark);
        idempotencyService.executeOnce(requestKey, ApprovalAction.REASSIGN, operatorId, taskId, payload, () -> {
            doReassign(taskId, newUserIds, operatorId, remark);
            return null;
        });
    }

    /**
     * 实际重分配逻辑，幂等保护由 {@link #reassign} 统一处理。
     */
    private void doReassign(Long taskId, List<Long> newUserIds, Long operatorId, String remark) {
        if (newUserIds == null || newUserIds.isEmpty()) {
            throw new BusinessException("重分配的候选人不能为空");
        }
        ApprovalTaskEntity task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        if (!TaskStatus.PENDING.equals(task.getStatus())) {
            throw new BusinessException("任务已处理，无需重分配");
        }

        Long sentinelUserId = Long.valueOf(WorkflowMultiInstanceExecutionListener.EMPTY_SENTINEL_USER_ID);
        boolean isMultiInstanceSentinel = sentinelUserId.equals(task.getAssigneeId());
        String originalCandidatesDescription = describeOriginalCandidates(task, isMultiInstanceSentinel);
        if (isMultiInstanceSentinel) {
            reassignMultiInstance(task, newUserIds);
        } else {
            reassignSingle(task, newUserIds);
        }

        ProcessInstanceEntity instance = task.getProcessInstanceId() == null
                ? null
                : processInstanceMapper.selectById(task.getProcessInstanceId());
        if (instance != null) {
            // MyBatis-Plus updateById() 默认按 NOT_NULL 策略生成 SQL，字段置 null 后调用
            // updateById 不会真的把该列清成 NULL；显式用 LambdaUpdateWrapper.set(..., null)
            // 才能清空 exception_code。
            processInstanceMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ProcessInstanceEntity>()
                    .eq(ProcessInstanceEntity::getId, instance.getId())
                    .set(ProcessInstanceEntity::getExceptionCode, null)
                    .set(ProcessInstanceEntity::getUpdateTime, LocalDateTime.now()));

            LocalDateTime now = LocalDateTime.now();
            String operatorText = operatorId == null ? "system" : operatorId.toString();
            approvalRecordMapper.insert(ApprovalRecordEntity.builder()
                    .processInstanceId(instance.getId())
                    .taskId(task.getId())
                    .nodeId(task.getNodeId())
                    .nodeName(task.getNodeName())
                    .operatorId(operatorId)
                    .action(ApprovalAction.REASSIGN)
                    .remark("原候选人：" + originalCandidatesDescription + "；执行人：" + operatorText
                            + "；新候选人：" + newUserIds + (remark == null ? "" : "；原因：" + remark))
                    .createBy(operatorText)
                    .createTime(now)
                    .updateBy(operatorText)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 描述重分配前的原候选人状态，供审计轨迹记录"原候选人、原因、执行者、新候选人"完整对比
     * （production-approval-lifecycle change tasks.md 5.4"重分配审计"）。停在待分配状态的任务
     * 本就没有真实候选人（单人/候选组节点空审批人时不写候选人行，会签节点用哨兵占位），
     * 因此原候选人描述恒为"空审批人待分配"/"会签哨兵占位"两种固定文案之一，而不是查询一份
     * 空列表。
     */
    private String describeOriginalCandidates(ApprovalTaskEntity task, boolean isMultiInstanceSentinel) {
        if (isMultiInstanceSentinel) {
            return "会签节点空审批人哨兵占位（无真实候选人）";
        }
        return "单人/候选组节点空审批人待分配（无真实候选人）";
    }

    /**
     * 单人/候选组节点：直接为既有 Flowable 任务补写处理人/候选人。
     */
    private void reassignSingle(ApprovalTaskEntity task, List<Long> newUserIds) {
        if (newUserIds.size() == 1) {
            taskService.setAssignee(task.getFlowableTaskId(), newUserIds.get(0).toString());
            task.setAssigneeId(newUserIds.get(0));
        } else {
            for (Long userId : newUserIds) {
                taskService.addCandidateUser(task.getFlowableTaskId(), userId.toString());
            }
            task.setCandidateType(CandidateType.USER);
            LocalDateTime now = LocalDateTime.now();
            for (Long userId : newUserIds) {
                approvalTaskCandidateMapper.insert(ApprovalTaskCandidateEntity.builder()
                        .taskId(task.getId())
                        .candidateType(CandidateType.USER)
                        .candidateValue(userId.toString())
                        .createTime(now)
                        .updateTime(now)
                        .build());
            }
        }
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);
    }

    /**
     * 会签节点：为每个真实候选人新增多实例分支，再删除哨兵分支。
     */
    private void reassignMultiInstance(ApprovalTaskEntity task, List<Long> newUserIds) {
        Task sentinelFlowableTask = taskService.createTaskQuery()
                .taskId(task.getFlowableTaskId())
                .singleResult();
        if (sentinelFlowableTask == null) {
            throw new BusinessException("哨兵任务在 Flowable 引擎中已不存在，可能已被处理，无法重分配");
        }
        String processInstanceId = sentinelFlowableTask.getProcessInstanceId();
        for (Long userId : newUserIds) {
            runtimeService.addMultiInstanceExecution(task.getNodeId(), processInstanceId,
                    Map.of("approver", userId.toString()));
        }
        runtimeService.deleteMultiInstanceExecution(sentinelFlowableTask.getExecutionId(), false);

        task.setStatus(TaskStatus.CANCELLED);
        task.setCancelReason("空候选人哨兵分支已由重分配替换为真实候选人");
        task.setFinishedTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);

        syncNodeRunAfterReassignment(task, newUserIds.size());
    }

    /**
     * 哨兵分支替换为真实候选人后，同步 {@code tab_wf_node_run} 的总票数 N（由哨兵的 1 改为
     * 真实候选人数量）与重算的通过阈值 K，并把新阈值写回 miBody 执行作用域局部变量
     * {@code voteThreshold}，供后续投票的完成条件表达式正确判定（production-approval-lifecycle
     * change tasks.md 6.3）。v1 遗留会签节点或本轮未使用 {@code tab_wf_node_run} 机制的历史
     * 任务（{@code nodeRunId} 为空）静默跳过，不影响其既有行为。
     */
    private void syncNodeRunAfterReassignment(ApprovalTaskEntity task, int newTotalCount) {
        if (task.getNodeRunId() == null) {
            return;
        }
        NodeRunEntity nodeRun = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getId, task.getNodeRunId())
                .last("FOR UPDATE"));
        if (nodeRun == null || task.getProcessInstanceId() == null) {
            return;
        }
        ProcessInstanceEntity instance = processInstanceMapper.selectById(task.getProcessInstanceId());
        if (instance == null) {
            return;
        }
        NodeAssigneeRuleEntity rule = nodeAssigneeRuleMapper.selectOne(new LambdaQueryWrapper<NodeAssigneeRuleEntity>()
                .eq(NodeAssigneeRuleEntity::getProcessDefinitionId, instance.getProcessDefinitionId())
                .eq(NodeAssigneeRuleEntity::getNodeId, task.getNodeId())
                .last("LIMIT 1"));
        if (rule == null) {
            return;
        }
        int threshold = VoteThresholdCalculator.threshold(
                ApprovalMode.valueOf(rule.getApprovalMode()), rule.getApprovalPercent(), newTotalCount);
        nodeRun.setTotalCount(newTotalCount);
        nodeRun.setRevision(nodeRun.getRevision() == null ? 1L : nodeRun.getRevision() + 1);
        nodeRun.setUpdateTime(LocalDateTime.now());
        nodeRunMapper.updateById(nodeRun);
        if (StringUtils.hasText(nodeRun.getExecutionId())) {
            runtimeService.setVariableLocal(nodeRun.getExecutionId(), "voteThreshold", threshold);
        }
    }
}
