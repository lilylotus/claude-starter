package cn.nihility.rbac.workflow.dslv2.engine;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.CandidateType;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceExecutionListener;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskCandidateEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskCandidateMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
        idempotencyService.executeOnce(requestKey, ApprovalAction.REASSIGN, operatorId, taskId, () -> {
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
                    .remark("重分配候选人：" + newUserIds + (remark == null ? "" : "；" + remark))
                    .createBy(operatorText)
                    .createTime(now)
                    .updateBy(operatorText)
                    .updateTime(now)
                    .build());
        }
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
    }
}
