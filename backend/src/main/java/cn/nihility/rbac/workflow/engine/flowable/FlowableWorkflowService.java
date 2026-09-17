package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.engine.ParallelSerialDomainResolver;
import cn.nihility.rbac.workflow.dslv2.engine.VoteThresholdCalculator;
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
import cn.nihility.rbac.workflow.dto.DisagreeCommand;
import cn.nihility.rbac.workflow.dto.ProcessInstanceDetailVO;
import cn.nihility.rbac.workflow.dto.RejectCommand;
import cn.nihility.rbac.workflow.dto.ReturnTaskCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.TaskQuery;
import cn.nihility.rbac.workflow.dto.TerminateCommand;
import cn.nihility.rbac.workflow.dto.TransferCommand;
import cn.nihility.rbac.workflow.dto.WithdrawCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import cn.nihility.rbac.workflow.entity.NodeRunEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.assignee.support.TaskAuthorizationService;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.NodeRunMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.policy.WithdrawPolicy;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.DelegationState;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * {@link WorkflowService} 的 Flowable 适配实现，业务代码之外的唯一一处直接依赖 Flowable
 * {@code RuntimeService}/{@code TaskService}/{@code RepositoryService}/{@code HistoryService}
 * 的地方（workflow-approval-engine change design.md Decision 1/2）。待办/已办/流程详情查询
 * 委托给 {@link WorkflowTaskService}。
 */
@Service
@RequiredArgsConstructor
public class FlowableWorkflowService implements WorkflowService {

    /** Flowable 运行时服务。 */
    private final RuntimeService runtimeService;

    /** Flowable 用户任务服务。 */
    private final TaskService taskService;

    /** Flowable 历史服务。 */
    private final HistoryService historyService;

    /** Flowable 流程仓库服务，用于 {@link #doAddSign} 读取已部署 BPMN 模型判断目标节点是否为
     *  串行多实例（tasks.md 6.7）。 */
    private final RepositoryService repositoryService;

    /** 流程定义数据访问接口。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 节点审批人规则数据访问接口。 */
    private final NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** 流程实例数据访问接口。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 审批任务数据访问接口。 */
    private final ApprovalTaskMapper approvalTaskMapper;

    /** 节点轮次数据访问接口，DSL v2 会签计票专用（production-approval-lifecycle change
     *  tasks.md 6.3）。 */
    private final NodeRunMapper nodeRunMapper;

    /** 审批轨迹数据访问接口。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /** 操作幂等服务。 */
    private final IdempotencyService idempotencyService;

    /** 撤回策略。 */
    private final WithdrawPolicy withdrawPolicy;

    /** 任务处理越权校验服务。 */
    private final TaskAuthorizationService taskAuthorizationService;

    /** 待办/已办/流程详情查询服务。 */
    private final WorkflowTaskService workflowTaskService;

    /** 用户数据访问接口，用于任务处理前校验操作人当前是否仍处于启用状态
     *  （production-approval-lifecycle change tasks.md 5.4"停用身份检测"）。 */
    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public WorkflowInstanceResult start(StartProcessCommand command) {
        if (command.definitionId() == null) {
            throw new BusinessException("启动流程必须显式指定流程定义 id");
        }
        ProcessDefinitionEntity processDefinition = processDefinitionMapper.selectById(command.definitionId());
        if (processDefinition == null || !ProcessModelStatus.PUBLISHED.equals(processDefinition.getStatus())) {
            throw new BusinessException("流程定义 " + command.definitionId() + " 不存在或已下线，无法发起");
        }

        LocalDateTime now = LocalDateTime.now();
        String applicantText = command.applicantId() == null ? null : command.applicantId().toString();
        ProcessInstanceEntity instance = ProcessInstanceEntity.builder()
                .processDefinitionId(processDefinition.getId())
                .bindingId(command.bindingId())
                .bindingRevision(command.bindingRevision())
                .businessType(command.businessType())
                .businessId(command.businessId())
                .title(command.title())
                .applicantId(command.applicantId())
                .applicantOrgId(command.applicantOrgId())
                .status(ProcessInstanceStatus.RUNNING)
                .startedTime(now)
                .createBy(applicantText)
                .createTime(now)
                .updateBy(applicantText)
                .updateTime(now)
                .build();
        processInstanceMapper.insert(instance);

        Map<String, Object> variables = command.variables() == null ? new HashMap<>() : new HashMap<>(command.variables());
        ProcessInstance flowableInstance = runtimeService.startProcessInstanceById(
                processDefinition.getFlowableDefinitionId(),
                String.valueOf(instance.getId()),
                variables);

        instance.setFlowableInstanceId(flowableInstance.getId());
        instance.setUpdateTime(LocalDateTime.now());
        processInstanceMapper.updateById(instance);

        approvalRecordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(instance.getId())
                .operatorId(command.applicantId())
                .action(ApprovalAction.SUBMIT)
                .createBy(applicantText)
                .createTime(now)
                .updateBy(applicantText)
                .updateTime(now)
                .build());

        // 命中路径若不经过任何审批节点，Flowable 会在上面 startProcessInstanceById 这一次同步
        // 调用内就把流程直接跑到结束事件；此处补一次幂等收尾，避免实例状态永远停留在 RUNNING
        // （fix-approval-zero-task-process-completion change design.md Decision 1）。
        finalizeInstanceIfEnded(instance.getId());

        ProcessInstanceEntity refreshed = processInstanceMapper.selectById(instance.getId());
        return new WorkflowInstanceResult(
                refreshed.getId(),
                refreshed.getFlowableInstanceId(),
                refreshed.getCurrentNodeId(),
                refreshed.getCurrentNodeName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void approve(ApproveCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.APPROVE, command.operatorId(),
                command.taskId(), command, () -> {
                    completeTask(command.taskId(), command.operatorId(), command.remark(), ApprovalAction.APPROVE);
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void reject(RejectCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.REJECT, command.operatorId(),
                command.taskId(), command, () -> {
                    completeTask(command.taskId(), command.operatorId(), command.remark(), ApprovalAction.REJECT);
                    return null;
                });
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code DISAGREE} 只在 {@code rejectPolicy=THRESHOLD} 的会签节点上有意义（design.md 第7节
     * "DISAGREE 是 THRESHOLD 反对票"），只计入反对票数、不立即终止流程；用在其余节点上
     * （单人/候选组节点、{@code rejectPolicy=VETO} 的会签节点）会在 {@link #completeTask} 内
     * 被拒绝（production-approval-lifecycle change tasks.md 6.3）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void disagree(DisagreeCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.DISAGREE, command.operatorId(),
                command.taskId(), command, () -> {
                    completeTask(command.taskId(), command.operatorId(), command.remark(), ApprovalAction.DISAGREE);
                    return null;
                });
    }

    /**
     * 通过/驳回/反对共用的任务完成逻辑：越权校验、候选组自动认领、Flowable 完成任务/委派归还、
     * 落库审批任务与轨迹、流程实例状态收尾。固定加锁顺序（业务活动锁在更上层的
     * {@code ApprovalRequestServiceImpl} 发起阶段已处理，此处只涉及实例行→任务行两层，
     * design.md 第8节）：先对流程实例行加 {@code SELECT ... FOR UPDATE}，再对任务行加锁，
     * 与 {@link #doReturnTask}/{@link #doTransfer}/{@link #doDelegate}/{@link #doAddSign}
     * 保持一致（production-approval-lifecycle change tasks.md 6.2）。
     * <p>
     * {@code action} 为 {@link ApprovalAction#APPROVE}/{@link ApprovalAction#REJECT}/
     * {@link ApprovalAction#DISAGREE} 之一。单人/候选组节点与 v1 遗留的会签节点
     * （{@code tab_wf_node_assignee_rule.reject_policy} 为空）保持原有"传一个 {@code approved}
     * 布尔变量给 {@code taskService.complete}"行为不变；DSL v2 会签节点
     * （{@code reject_policy} 非空）委托 {@link #completeV2VoteTask} 走 N/A/R/U 计票判定
     * （production-approval-lifecycle change design.md 第7节，tasks.md 6.3）。
     */
    private void completeTask(Long taskId, Long operatorId, String remark, String action) {
        Long processInstanceId = requireTask(taskId).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(taskId);
        requireTaskStillOpen(task);
        if (!taskAuthorizationService.isAuthorized(task, operatorId)) {
            throw new BusinessException("无权限处理该审批任务");
        }
        requireOperatorEnabled(operatorId);
        autoClaimIfNeeded(task, operatorId);

        Task flowableTask = taskService.createTaskQuery().taskId(task.getFlowableTaskId()).singleResult();
        if (flowableTask == null) {
            throw new BusinessException("审批任务不存在或已处理");
        }

        if (flowableTask.getDelegationState() == DelegationState.PENDING) {
            // 受托人处理委派任务：归还原处理人，不驱动流程往下走、不计票，流程最终决策由原
            // 处理人后续再次调用 approve/reject/disagree 时才真正生效（tasks.md 6.3"取消/
            // 委派归还不计票"）。
            taskService.resolveTask(task.getFlowableTaskId());
            task.setAssigneeId(parseUserId(flowableTask.getOwner()));
            task.setUpdateTime(LocalDateTime.now());
            approvalTaskMapper.updateById(task);
            recordAction(task, operatorId, action, remark);
            return;
        }

        NodeAssigneeRuleEntity rule = requireRule(instance.getProcessDefinitionId(), task.getNodeId());
        boolean isV2VoteNode = isMultiInstanceMode(rule.getApprovalMode()) && StringUtils.hasText(rule.getRejectPolicy());
        if (isV2VoteNode) {
            completeV2VoteTask(instance, task, rule, operatorId, remark, action);
            return;
        }

        if (ApprovalAction.DISAGREE.equals(action)) {
            throw new BusinessException("该节点不支持反对票操作，请使用通过/拒绝");
        }
        boolean approved = ApprovalAction.APPROVE.equals(action);
        runEngineCommand(() -> taskService.complete(task.getFlowableTaskId(), Map.of("approved", approved)));

        task.setStatus(TaskStatus.COMPLETED);
        task.setAssigneeId(operatorId);
        task.setFinishedTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);

        recordAction(task, operatorId, approved ? ApprovalAction.APPROVE : ApprovalAction.REJECT, remark);
        finalizeInstanceIfEnded(task.getProcessInstanceId());
    }

    /**
     * DSL v2 会签节点计票判定（design.md 第7节，tasks.md 6.3）：读取 {@code tab_wf_node_run}
     * 当前轮次行（{@code SELECT ... FOR UPDATE}，锁顺序位于实例行→任务行之后，与
     * design.md 第8节"业务活动锁→实例行→任务行→节点轮次"一致），按整数公式重算通过阈值 K，
     * 分三种结果处理：
     * <ol>
     *   <li>{@code REJECT}（不论 {@code rejectPolicy}）或 {@code THRESHOLD} 策略下反对票已使
     *       "同意票+未决票"低于 K（{@code N-R<K}，等价于 design.md 公式 {@code A+U<K}）：直接
     *       终止整个流程实例（{@code runtimeService.deleteProcessInstance}），不调用
     *       {@code taskService.complete}——与 {@link #doWithdraw} 同一模式，避免在 Flowable
     *       监听器内部再次调用终止 API 造成引擎命令重入；</li>
     *   <li>同意票已达到 K：更新计票、把 {@code voteAgreeCount} 写回 miBody 执行作用域局部
     *       变量后调用 {@code taskService.complete}，触发编译期固化的完成条件表达式
     *       {@code voteAgreeCount >= voteThreshold} 结束本轮 MI 等待；</li>
     *   <li>票数不足以决出结果：更新计票、同步 {@code voteAgreeCount} 后正常
     *       {@code taskService.complete} 本个体任务，继续等待其余候选人。</li>
     * </ol>
     */
    private void completeV2VoteTask(
            ProcessInstanceEntity instance,
            ApprovalTaskEntity task,
            NodeAssigneeRuleEntity rule,
            Long operatorId,
            String remark,
            String action) {
        boolean isDisagree = ApprovalAction.DISAGREE.equals(action);
        boolean isReject = ApprovalAction.REJECT.equals(action);
        RejectPolicy rejectPolicy = RejectPolicy.valueOf(rule.getRejectPolicy());
        if (isDisagree && rejectPolicy != RejectPolicy.THRESHOLD) {
            throw new BusinessException("该节点为一票否决（VETO）策略，不支持反对票操作，请使用拒绝");
        }
        if (task.getNodeRunId() == null) {
            throw new BusinessException("会签任务缺少节点轮次信息，无法计票");
        }
        NodeRunEntity nodeRun = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getId, task.getNodeRunId())
                .last("FOR UPDATE"));
        if (nodeRun == null) {
            throw new BusinessException("节点轮次记录不存在");
        }

        int totalCount = nodeRun.getTotalCount();
        boolean isApprove = ApprovalAction.APPROVE.equals(action);
        int newAgreeCount = nodeRun.getAgreeCount() + (isApprove ? 1 : 0);
        int newRejectCount = nodeRun.getRejectCount() + (isApprove ? 0 : 1);
        int threshold = VoteThresholdCalculator.threshold(
                ApprovalMode.valueOf(rule.getApprovalMode()), rule.getApprovalPercent(), totalCount);

        boolean thresholdFail = !isReject && rejectPolicy == RejectPolicy.THRESHOLD
                && (totalCount - newRejectCount) < threshold;
        boolean terminate = isReject || thresholdFail;
        boolean pass = !terminate && newAgreeCount >= threshold;

        LocalDateTime now = LocalDateTime.now();
        nodeRun.setAgreeCount(newAgreeCount);
        nodeRun.setRejectCount(newRejectCount);
        nodeRun.setRunStatus(terminate ? "REJECTED" : (pass ? "COMPLETED" : "RUNNING"));
        nodeRun.setRevision(nodeRun.getRevision() == null ? 1L : nodeRun.getRevision() + 1);
        nodeRun.setUpdateBy(operatorId == null ? null : operatorId.toString());
        nodeRun.setUpdateTime(now);
        nodeRunMapper.updateById(nodeRun);

        task.setStatus(TaskStatus.COMPLETED);
        task.setAssigneeId(operatorId);
        task.setFinishedTime(now);
        task.setUpdateTime(now);
        approvalTaskMapper.updateById(task);

        String recordedAction = isApprove ? ApprovalAction.APPROVE : (isReject ? ApprovalAction.REJECT : ApprovalAction.DISAGREE);
        recordAction(task, operatorId, recordedAction, remark);

        if (terminate) {
            instance.setStatus(ProcessInstanceStatus.REJECTED);
            instance.setCurrentNodeId(null);
            instance.setCurrentNodeName(null);
            instance.setFinishedTime(now);
            instance.setUpdateTime(now);
            processInstanceMapper.updateById(instance);
            String terminateReason = isReject ? "审批人拒绝，终止流程" : "会签反对票达到终止阈值，终止流程";
            closeOpenTasks(instance.getId(), terminateReason);
            if (StringUtils.hasText(instance.getFlowableInstanceId())) {
                runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(), terminateReason);
            }
            return;
        }

        runEngineCommand(() -> {
            if (StringUtils.hasText(nodeRun.getExecutionId())) {
                runtimeService.setVariableLocal(nodeRun.getExecutionId(), "voteAgreeCount", newAgreeCount);
            }
            taskService.complete(task.getFlowableTaskId(), Map.of());
        });
        finalizeInstanceIfEnded(task.getProcessInstanceId());
    }

    /**
     * 执行会修改 Flowable 引擎运行时数据的命令（{@code taskService.complete}/
     * {@code runtimeService.addMultiInstanceExecution} 等），并把引擎自身基于版本号的乐观锁冲突
     * （{@link FlowableOptimisticLockingException}）转换为清晰的业务异常（tasks.md 6.7 真实并发
     * 集成测试核实确认的缺口）。
     * <p>
     * 背景：本类通过对 {@code tab_wf_process_instance}/{@code tab_wf_approval_task}/
     * {@code tab_wf_node_run} 依次加 {@code SELECT ... FOR UPDATE} 保证"业务表"层面互斥写入，
     * 但 MySQL 默认 {@code REPEATABLE READ} 隔离级别下，一个事务的一致性读快照在其第一条普通
     * （非加锁）查询时就已固定；两个真实并发事务里"较晚拿到业务行锁"的那一个，其快照仍停留在
     * "较早提交的事务"提交之前——Flowable 引擎自身对 {@code ACT_RU_EXECUTION} 等表的读取正是这类
     * 普通查询，不受我们自己的行锁保护。这会导致后拿到锁的事务基于过期的执行版本号发起引擎写入，
     * 被 Flowable 自身的乐观锁检测为"被另一个事务并发更新"而抛出未经包装的
     * {@link FlowableOptimisticLockingException}——{@code WorkflowV2AddSignConcurrencyIntegrationTest}
     * （加签与最后一票并发提交）真实复现确认。我们自己对 {@code tab_wf_node_run} 的
     * {@code SELECT ... FOR UPDATE} 读取不受此问题影响（加锁读取总是读最新已提交版本，绕开快照，
     * 与 {@code IdempotencyServiceImpl} 处理同类问题的方式一致）——因此"轮次已结束"这类基于我们
     * 自己表的校验本就能正确拦截多数场景，本方法只是为"两个事务都通过了我们自己的校验、只在
     * Flowable 引擎自身的乐观锁上相撞"这一剩余场景兜底，让并发下的"后手"操作得到确定性的清晰
     * 拒绝（可安全重试），而不是原始引擎异常/500。
     */
    private void runEngineCommand(Runnable command) {
        try {
            command.run();
        } catch (FlowableOptimisticLockingException ex) {
            throw new BusinessException("该任务所在节点刚被并发处理，请刷新后重试");
        }
    }

    /**
     * 判断规则的审批模式是否为会签（多实例），即非 {@code SINGLE}。
     */
    private boolean isMultiInstanceMode(String approvalMode) {
        return approvalMode != null && ApprovalMode.valueOf(approvalMode) != ApprovalMode.SINGLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void returnTask(ReturnTaskCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.RETURN, command.operatorId(),
                command.taskId(), command, () -> {
                    doReturnTask(command);
                    return null;
                });
    }

    /**
     * 退回历史节点的实际处理逻辑。固定加锁顺序：先锁流程实例行，再锁任务行（design.md 第8节，
     * tasks.md 6.2——此前实现是先取任务行再取实例行，与本类其余动作方法顺序相反，本轮统一）。
     * 退回前置校验见 {@link #validateReturnTarget}（tasks.md 6.6——此前实现只校验了目标节点
     * {@code allowReturn}，未校验目标节点是否真的是该实例走过的历史节点、是否与当前节点同一
     * 串行域）。退回后取消当前节点被撤销的其余开放任务见 {@link #cancelSiblingOpenTasksOnReturn}。
     */
    private void doReturnTask(ReturnTaskCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        requireTaskStillOpen(task);
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }

        NodeAssigneeRuleEntity targetRule = requireRule(instance.getProcessDefinitionId(), command.targetNodeId());
        if (!Boolean.TRUE.equals(targetRule.getAllowReturn())) {
            throw new BusinessException("目标节点不允许退回");
        }
        validateReturnTarget(instance, task, command.targetNodeId());

        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(instance.getFlowableInstanceId())
                .moveActivityIdTo(task.getNodeId(), command.targetNodeId())
                .changeState();

        LocalDateTime now = LocalDateTime.now();
        task.setStatus(TaskStatus.RETURNED);
        task.setFinishedTime(now);
        task.setUpdateTime(now);
        approvalTaskMapper.updateById(task);

        cancelSiblingOpenTasksOnReturn(task, command.operatorId());

        recordAction(task, command.operatorId(), ApprovalAction.RETURN, command.remark());
    }

    /**
     * 退回前置校验（production-approval-lifecycle change design.md 第7节"只退到同一串行域中
     * 实际完成且配置可退的节点；拒绝跨并行块/跨MI边界/未经过节点"，tasks.md 6.6）：
     * <ol>
     *   <li>不能退回到当前所在节点本身；</li>
     *   <li>目标节点必须是当前流程实例真实审批轨迹（{@code tab_wf_approval_record}）里已经
     *       完成过的历史节点（存在 {@code APPROVE}/{@code REJECT}/{@code DISAGREE} 动作记录），
     *       伪造一个从未真正走过的节点 id 直接拒绝；</li>
     *   <li>若流程定义为 DSL v2（{@code schemaVersion=2}）且发布时刻的 DSL 快照
     *       ({@code modelJsonSnapshot}) 可解析，目标节点须与当前节点处于同一"串行域"
     *       （{@link ParallelSerialDomainResolver}）——不允许跨并行块，也不允许跨同一并行块内
     *       的不同分支（各分支并发执行，是彼此独立的串行域）；v1 遗留定义或缺少可用快照时不存在
     *       并行块概念，跳过本项。</li>
     * </ol>
     */
    private void validateReturnTarget(ProcessInstanceEntity instance, ApprovalTaskEntity task, String targetNodeId) {
        if (targetNodeId.equals(task.getNodeId())) {
            throw new BusinessException("不能退回到当前所在节点");
        }
        boolean everCompleted = approvalRecordMapper.selectCount(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, instance.getId())
                .eq(ApprovalRecordEntity::getNodeId, targetNodeId)
                .in(ApprovalRecordEntity::getAction,
                        ApprovalAction.APPROVE, ApprovalAction.REJECT, ApprovalAction.DISAGREE)) > 0;
        if (!everCompleted) {
            throw new BusinessException("目标节点不是当前流程实例已完成的历史节点，不允许退回");
        }

        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(instance.getProcessDefinitionId());
        if (definition == null || !Integer.valueOf(2).equals(definition.getSchemaVersion())
                || !StringUtils.hasText(definition.getModelJsonSnapshot())) {
            // v1 遗留定义（无并行块语言概念）或缺少可用 DSL v2 快照：跳过同串行域校验，
            // 历史轨迹 + allowReturn 已经是这类定义下能做的全部校验。
            return;
        }
        ProcessModelDslV2 dsl;
        try {
            dsl = JacksonUtils.toObj(definition.getModelJsonSnapshot(), ProcessModelDslV2.class);
        } catch (RuntimeException ex) {
            // 快照异常无法解析：不因基础设施问题阻断合法退回，仅跳过本项细粒度校验。
            return;
        }
        if (!ParallelSerialDomainResolver.sameSerialDomain(dsl, task.getNodeId(), targetNodeId)) {
            throw new BusinessException("退回目标节点与当前节点不在同一并行块作用域内，不允许跨并行块退回");
        }
    }

    /**
     * 退回后取消同一节点仍处于开放状态的其余任务（tasks.md 6.6）：{@code moveActivityIdTo}
     * 会一并取消源活动 id 上的其余并发执行（如会签节点其余候选人尚未处理的票），但引擎侧取消
     * 不会自动回写业务投影表 {@code tab_wf_approval_task}，这些行会变成永远查不到对应 Flowable
     * 任务的僵尸记录（与 {@link #finalizeInstanceIfEnded} 文档描述的同类问题）。若被退回的任务
     * 属于某个会签轮次（{@code nodeRunId} 非空）且该轮次仍在 {@code RUNNING}，一并标记为
     * {@code CANCELLED}；下次重新进入该节点时由
     * {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceExecutionListener}
     * 既有的"按 {@code (instanceId, nodeId)} 现有最大 {@code round_no} 递增"逻辑自动开启新一轮，
     * 本方法无需也不重复处理轮次递增。
     */
    private void cancelSiblingOpenTasksOnReturn(ApprovalTaskEntity returnedTask, Long operatorId) {
        List<ApprovalTaskEntity> siblings = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, returnedTask.getProcessInstanceId())
                .eq(ApprovalTaskEntity::getNodeId, returnedTask.getNodeId())
                .ne(ApprovalTaskEntity::getId, returnedTask.getId())
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalTaskEntity sibling : siblings) {
            sibling.setStatus(TaskStatus.CANCELLED);
            sibling.setCancelReason("同节点被退回，取消其余未处理任务");
            sibling.setFinishedTime(now);
            sibling.setUpdateTime(now);
            approvalTaskMapper.updateById(sibling);
        }

        if (returnedTask.getNodeRunId() == null) {
            return;
        }
        NodeRunEntity nodeRun = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getId, returnedTask.getNodeRunId())
                .last("FOR UPDATE"));
        if (nodeRun != null && "RUNNING".equals(nodeRun.getRunStatus())) {
            nodeRun.setRunStatus("CANCELLED");
            nodeRun.setUpdateBy(operatorId == null ? null : operatorId.toString());
            nodeRun.setUpdateTime(now);
            nodeRunMapper.updateById(nodeRun);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void withdraw(WithdrawCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.WITHDRAW, command.operatorId(),
                null, command, () -> {
                    doWithdraw(command);
                    return null;
                });
    }

    /**
     * 撤回流程实例的实际处理逻辑：只涉及实例行这一层锁（撤回本身不针对具体任务，任务批量
     * 关闭见 {@link #closeOpenTasks}），与其余动作方法"先实例后任务"的固定顺序不冲突。
     */
    private void doWithdraw(WithdrawCommand command) {
        ProcessInstanceEntity instance = requireInstanceForUpdate(command.processInstanceId());
        if (!Objects.equals(instance.getApplicantId(), command.operatorId())) {
            throw new BusinessException("只能撤回本人发起的流程");
        }
        if (!withdrawPolicy.canWithdraw(instance.getId(), command.operatorId())) {
            throw new BusinessException("流程已存在审批记录，不能撤回");
        }

        boolean stillRunning = StringUtils.hasText(instance.getFlowableInstanceId())
                && runtimeService.createProcessInstanceQuery()
                        .processInstanceId(instance.getFlowableInstanceId())
                        .count() > 0;
        if (stillRunning) {
            runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(), "申请人撤回审批申请");
        }

        LocalDateTime now = LocalDateTime.now();
        instance.setStatus(ProcessInstanceStatus.WITHDRAWN);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setFinishedTime(now);
        instance.setUpdateTime(now);
        processInstanceMapper.updateById(instance);

        closeOpenTasks(instance.getId(), "申请人撤回审批申请");

        recordAction(instance, null, command.operatorId(), ApprovalAction.WITHDRAW, command.remark());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void terminate(TerminateCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.TERMINATE, command.operatorId(),
                null, command, () -> {
                    doTerminate(command);
                    return null;
                });
    }

    /**
     * 运维强制终止流程实例的实际处理逻辑（production-approval-lifecycle change design.md
     * 第7节"terminate：独立运维权限和必填原因，结束流程并取消任务，不执行主数据变更"，
     * tasks.md 6.8）。权限本身由独立权限点 {@code WorkflowDesign:instance:terminate} 在
     * {@code IdentityAuthFilter} 固定权限映射表层面控制，不要求操作人是流程发起人/参与者，
     * 与 {@link #doWithdraw} 的"只能本人撤回"限制不同；只涉及实例行这一层锁，与
     * {@link #doWithdraw} 同一模式。
     * <p>
     * "不触发任何业务执行事件"核实结论（tasks.md 6.8）：本类（通用 Workflow 引擎层）自身不
     * 包含任何"审批通过后落地主数据变更"的钩子——真正的业务写操作（如新增/编辑组织、启停用户
     * 等）落在 {@code cn.nihility.rbac.approval.service.impl.ApprovalRequestServiceImpl
     * #finalizeApproval}，且只在业务层自己的
     * {@code POST /api/approval-requests/{id}/approve} 接口调用链路内、流程状态在同一事务中
     * 变为 {@code APPROVED} 时才会同步调用（非异步/非轮询）。运维终止走的是完全独立的调用链
     * （{@code WorkflowTaskController} → 本类 {@code terminate}），从不经过
     * {@code ApprovalRequestServiceImpl}，因此天然不会触发 {@code finalizeApproval}/
     * {@code executeWrite}——本方法只更新流程实例状态、取消开放任务、写一条审计轨迹。
     * <p>
     * 范围说明：本次改动不同步业务层 {@code tab_approval_request.status}（该表仍会停留在
     * {@code PENDING}）——这与既有的通用引擎层 {@code doWithdraw}（同样只更新
     * {@code tab_wf_process_instance}/{@code tab_wf_approval_task}，不回写
     * {@code tab_approval_request}；业务层撤回走的是独立的
     * {@code ApprovalRequestServiceImpl#cancel}/{@code POST /api/approval-requests/{id}/cancel}
     * 接口）是同一既有分层模式，非本轮引入的新缺口，不在 tasks.md 6.8 范围内一并修复。
     */
    private void doTerminate(TerminateCommand command) {
        ProcessInstanceEntity instance = requireInstanceForUpdate(command.processInstanceId());
        if (!ProcessInstanceStatus.RUNNING.equals(instance.getStatus())) {
            throw new BusinessException("流程实例已结束，不能重复终止");
        }

        boolean stillRunning = StringUtils.hasText(instance.getFlowableInstanceId())
                && runtimeService.createProcessInstanceQuery()
                        .processInstanceId(instance.getFlowableInstanceId())
                        .count() > 0;
        if (stillRunning) {
            runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(), command.reason());
        }

        // 用 LambdaUpdateWrapper 显式 .set(field, null) 而不是给 entity 赋 null 后调用
        // updateById——MyBatis-Plus 全局默认 update-strategy=NOT_NULL（本项目未覆盖该配置，
        // 见 application.yml），entity 字段为 null 时该列会被静默跳过、不会真正写入数据库，
        // 与既有的 ApprovalRequestServiceImpl 清空 currentNodeName 时使用同一写法（tasks.md
        // 6.8 编写本方法测试时真实复现确认：不这样写会导致终止后 current_node_id 仍停留在
        // 终止前的旧值）。
        LocalDateTime now = LocalDateTime.now();
        processInstanceMapper.update(null, new LambdaUpdateWrapper<ProcessInstanceEntity>()
                .eq(ProcessInstanceEntity::getId, instance.getId())
                .set(ProcessInstanceEntity::getStatus, ProcessInstanceStatus.TERMINATED)
                .set(ProcessInstanceEntity::getCurrentNodeId, null)
                .set(ProcessInstanceEntity::getCurrentNodeName, null)
                .set(ProcessInstanceEntity::getFinishedTime, now)
                .set(ProcessInstanceEntity::getUpdateTime, now));
        instance.setStatus(ProcessInstanceStatus.TERMINATED);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setFinishedTime(now);
        instance.setUpdateTime(now);

        closeOpenTasks(instance.getId(), command.reason());

        recordAction(instance, null, command.operatorId(), ApprovalAction.TERMINATE, command.reason());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void transfer(TransferCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.TRANSFER, command.operatorId(),
                command.taskId(), command, () -> {
                    doTransfer(command);
                    return null;
                });
    }

    /**
     * 转办的实际处理逻辑。固定加锁顺序：先锁流程实例行，再锁任务行（design.md 第8节，
     * tasks.md 6.2）。
     */
    private void doTransfer(TransferCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        requireTaskStillOpen(task);
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        NodeAssigneeRuleEntity rule = requireRule(instance.getProcessDefinitionId(), task.getNodeId());
        if (!Boolean.TRUE.equals(rule.getAllowTransfer())) {
            throw new BusinessException("该节点不允许转办");
        }

        taskService.setAssignee(task.getFlowableTaskId(), command.targetUserId().toString());
        task.setAssigneeId(command.targetUserId());
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);

        recordTransferOrDelegate(task, command.operatorId(), command.targetUserId(),
                ApprovalAction.TRANSFER, "转办给用户" + command.targetUserId(), command.remark());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void delegate(DelegateCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.DELEGATE, command.operatorId(),
                command.taskId(), command, () -> {
                    doDelegate(command);
                    return null;
                });
    }

    /**
     * 委派的实际处理逻辑：使用 Flowable 原生委派语义，受托人处理完成后归还原处理人
     * （见 {@link #completeTask}）。固定加锁顺序：先锁流程实例行，再锁任务行（design.md
     * 第8节，tasks.md 6.2）。禁止链式委派（design.md 第7节"禁止未归还直接批准、链式委派"，
     * tasks.md 6.5——此前实现未校验，受托人能在归还前对同一任务再次发起委派，本轮补齐）：
     * 任务已处于 {@link DelegationState#PENDING}（即已被委派且原受托人尚未 resolve 归还）时
     * 直接拒绝；由于 {@link #completeTask} 处于 PENDING 状态时始终把 approve/reject 降级为
     * resolve（当前受托人不能真正完成任务），能通过 {@code isAuthorized}（此时 assignee 为
     * 受托人）走到本方法的操作人必然就是受托人本人，因此这一状态检查天然只拦住"受托人再次
     * 委派"，不影响原处理人首次发起委派。
     */
    private void doDelegate(DelegateCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        requireTaskStillOpen(task);
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        NodeAssigneeRuleEntity rule = requireRule(instance.getProcessDefinitionId(), task.getNodeId());
        if (!Boolean.TRUE.equals(rule.getAllowDelegate())) {
            throw new BusinessException("该节点不允许委派");
        }
        Task flowableTask = taskService.createTaskQuery().taskId(task.getFlowableTaskId()).singleResult();
        if (flowableTask == null) {
            throw new BusinessException("审批任务不存在或已处理");
        }
        if (flowableTask.getDelegationState() == DelegationState.PENDING) {
            throw new BusinessException("该任务已处于委派中，受托人只能归还处理意见，不允许链式委派");
        }

        taskService.delegateTask(task.getFlowableTaskId(), command.targetUserId().toString());
        task.setAssigneeId(command.targetUserId());
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);

        recordTransferOrDelegate(task, command.operatorId(), command.targetUserId(),
                ApprovalAction.DELEGATE, "委派给用户" + command.targetUserId(), command.remark());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void addSign(AddSignCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.ADD_SIGN, command.operatorId(),
                command.taskId(), command, () -> {
                    doAddSign(command);
                    return null;
                });
    }

    /**
     * 加签的实际处理逻辑：会签节点动态增加候选审批人，使用 Flowable
     * {@code addMultiInstanceExecution} 原生 API，不手工修改内部计数变量。约定多实例节点的
     * {@code flowable:elementVariable} 统一命名为 {@code approver}（design.md Decision 4/7）。
     * 固定加锁顺序：先锁流程实例行，再锁任务行，最后锁节点轮次行（design.md 第8节"业务活动锁
     * → 实例行 → 任务行 → 节点轮次"，tasks.md 6.2/6.7）。
     * <p>
     * 加签前置校验见 {@link #validateAddSignTarget}（tasks.md 6.7——此前实现只校验了
     * {@code allowAddSign} 开关，未校验节点是否真的是会签节点、是否为串行多实例、目标轮次是否
     * 仍在进行中，也未做候选人去重）。
     * <p>
     * N/K 同步（tasks.md 6.7 核实确认的真实缺口）：DSL v2 会签轮次的总票数 N（
     * {@code tab_wf_node_run.totalCount}）与阈值 K（miBody 执行作用域局部变量
     * {@code voteThreshold}）此前只在
     * {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceExecutionListener#openRound}
     * 轮次开启时计算一次，{@code addMultiInstanceExecution} 只会让 Flowable 内部
     * {@code nrOfInstances} 增加、新候选人子任务能通过 miBody 局部变量正确关联到当前轮次，但完全
     * 不会同步 N/K，导致加签后新候选人未投票时可能仅凭旧候选人票数按旧阈值提前通过——本方法在
     * 对 {@code tab_wf_node_run} 加锁、校验通过、真正调用引擎 API 增加候选人后，按新的候选人总数
     * 重新计算并回写 {@code totalCount} 与 {@code voteThreshold}，与 {@link #completeV2VoteTask}
     * 共用同一把节点轮次行锁，保证与并发的最后一票不会互相踩踏（详见
     * {@link #validateAddSignTarget} 锁顺序说明）。v1 遗留会签节点或本轮未使用
     * {@code tab_wf_node_run} 机制的任务（{@code nodeRunId} 为空）跳过本项同步，维持既有行为
     * 不变（与 {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2ReassignmentService
     * #syncNodeRunAfterReassignment} 同一模式）。
     */
    private void doAddSign(AddSignCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        // 不调用 requireTaskStillOpen：加签允许用已完成轮次里的历史任务作为"锚点"定位节点/
        // 节点轮次（validateAddSignTarget 已针对"流程实例已结束"/"该轮次已结束"给出更精确的
        // 专门校验与提示文案），锚点任务本身当前是否仍为 PENDING/CLAIMED 不是加签的前置条件，
        // 与 completeTask/doReturnTask/doTransfer/doDelegate 语义不同，不能套用同一守卫
        // （tasks.md 6.8 真实回归测试 WorkflowV2AddSignIntegrationTest 确认过度拦截会破坏
        // 该合法场景）。
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        NodeAssigneeRuleEntity rule = requireRule(instance.getProcessDefinitionId(), task.getNodeId());
        if (!Boolean.TRUE.equals(rule.getAllowAddSign())) {
            throw new BusinessException("该节点不允许加签");
        }
        if (command.addUserIds() == null || command.addUserIds().isEmpty()) {
            throw new BusinessException("加签用户不能为空");
        }
        NodeRunEntity nodeRun = validateAddSignTarget(instance, task, rule, command.addUserIds());
        Integer newThreshold = nodeRun == null ? null : VoteThresholdCalculator.threshold(
                ApprovalMode.valueOf(rule.getApprovalMode()), rule.getApprovalPercent(),
                nodeRun.getTotalCount() + command.addUserIds().size());

        runEngineCommand(() -> {
            for (Long addUserId : command.addUserIds()) {
                runtimeService.addMultiInstanceExecution(task.getNodeId(), instance.getFlowableInstanceId(),
                        Map.of("approver", addUserId.toString()));
            }
            if (nodeRun != null && StringUtils.hasText(nodeRun.getExecutionId())) {
                runtimeService.setVariableLocal(nodeRun.getExecutionId(), "voteThreshold", newThreshold);
            }
        });

        if (nodeRun != null) {
            nodeRun.setTotalCount(nodeRun.getTotalCount() + command.addUserIds().size());
            nodeRun.setRevision(nodeRun.getRevision() == null ? 1L : nodeRun.getRevision() + 1);
            nodeRun.setUpdateBy(command.operatorId() == null ? null : command.operatorId().toString());
            nodeRun.setUpdateTime(LocalDateTime.now());
            nodeRunMapper.updateById(nodeRun);
        }

        approvalRecordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(instance.getId())
                .taskId(task.getId())
                .nodeId(task.getNodeId())
                .nodeName(task.getNodeName())
                .operatorId(command.operatorId())
                .action(ApprovalAction.ADD_SIGN)
                .remark("加签用户：" + command.addUserIds()
                        + (StringUtils.hasText(command.remark()) ? "；" + command.remark() : ""))
                .createBy(command.operatorId() == null ? null : command.operatorId().toString())
                .createTime(LocalDateTime.now())
                .updateBy(command.operatorId() == null ? null : command.operatorId().toString())
                .updateTime(LocalDateTime.now())
                .build());
    }

    /**
     * 加签前置校验（tasks.md 6.7）：
     * <ol>
     *   <li>流程实例须仍在 {@code RUNNING}，已终止/已撤回的实例不允许加签；</li>
     *   <li>节点审批模式须为会签（非 {@link ApprovalMode#SINGLE}）——单人节点/候选组任务池
     *       调用 {@code addMultiInstanceExecution} 会让 Flowable 因找不到多实例根执行而抛出
     *       未经包装的 {@code FlowableException}，本项在业务层提前拦截给出清晰提示；</li>
     *   <li>目标节点须是并行多实例（读取已部署 BPMN 模型的
     *       {@code MultiInstanceLoopCharacteristics#isSequential()}）——串行多实例逐个创建任务，
     *       {@code addMultiInstanceExecution} 只会新增一个未被引擎"继续"的子执行（不会新建任务、
     *       不会被串行轮转到），永远等不到该候选人投票却已计入总票数，DSL v2
     *       {@code ActionsConfigDsl#getAddSign()} 的字段注释本就写明"仅对仍活跃的并行会签节点
     *       有意义"；</li>
     *   <li>加签用户列表内部不能有重复；</li>
     *   <li>DSL v2 会签任务（{@code nodeRunId} 非空）须额外对 {@code tab_wf_node_run} 当前轮次行
     *       加 {@code SELECT ... FOR UPDATE}（与 {@link #completeV2VoteTask} 共用同一把锁，加锁
     *       顺序固定在实例行→任务行之后、调用 {@code addMultiInstanceExecution} 引擎命令之前——
     *       若先调用引擎命令再加锁，可能对一个"最后一票"事务已经并发终止/完成的轮次继续新增多实例
     *       子执行，产生引擎侧的孤儿执行；先加锁可保证两个事务里后到达者读到的
     *       {@code runStatus} 一定是先到达者提交后的最新结果，从而确定性地要么正常加签、要么
     *       因轮次已结束而清晰拒绝）：
     *       <ul>
     *         <li>轮次须仍为 {@code RUNNING}，已经决出结果（{@code COMPLETED}/{@code REJECTED}/
     *             {@code CANCELLED}）的轮次不允许加签；</li>
     *         <li>加签用户不能与本轮已有候选人（不论其任务当前状态，含已投票/待处理）重复。</li>
     *       </ul>
     *   </li>
     * </ol>
     * v1 遗留会签节点（{@code nodeRunId} 为空）没有 {@code tab_wf_node_run} 机制，第 5 项去重/
     * 轮次状态校验天然跳过，返回 {@code null}；调用方据此判断是否需要同步 N/K。
     *
     * @return DSL v2 会签任务对应的、已加锁的节点轮次行；v1 遗留任务返回 {@code null}
     */
    private NodeRunEntity validateAddSignTarget(
            ProcessInstanceEntity instance,
            ApprovalTaskEntity task,
            NodeAssigneeRuleEntity rule,
            List<Long> addUserIds) {
        if (!ProcessInstanceStatus.RUNNING.equals(instance.getStatus())) {
            throw new BusinessException("流程实例已结束，不能加签");
        }
        if (!isMultiInstanceMode(rule.getApprovalMode())) {
            throw new BusinessException("该节点不是会签节点，不支持加签");
        }
        if (new HashSet<>(addUserIds).size() != addUserIds.size()) {
            throw new BusinessException("加签用户列表存在重复");
        }

        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(instance.getProcessDefinitionId());
        if (definition != null && StringUtils.hasText(definition.getFlowableDefinitionId())) {
            BpmnModel bpmnModel = repositoryService.getBpmnModel(definition.getFlowableDefinitionId());
            FlowElement flowElement = bpmnModel == null ? null : bpmnModel.getFlowElement(task.getNodeId());
            if (flowElement instanceof Activity activity
                    && activity.getLoopCharacteristics() != null
                    && activity.getLoopCharacteristics().isSequential()) {
                throw new BusinessException("串行会签节点不支持加签");
            }
        }

        if (task.getNodeRunId() == null) {
            return null;
        }
        NodeRunEntity nodeRun = nodeRunMapper.selectOne(new LambdaQueryWrapper<NodeRunEntity>()
                .eq(NodeRunEntity::getId, task.getNodeRunId())
                .last("FOR UPDATE"));
        if (nodeRun == null) {
            throw new BusinessException("节点轮次记录不存在");
        }
        if (!"RUNNING".equals(nodeRun.getRunStatus())) {
            throw new BusinessException("该轮次已结束，不能加签");
        }

        List<ApprovalTaskEntity> roundTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getNodeRunId, nodeRun.getId()));
        Set<Long> existingAssigneeIds = roundTasks.stream()
                .map(ApprovalTaskEntity::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (Long addUserId : addUserIds) {
            if (existingAssigneeIds.contains(addUserId)) {
                throw new BusinessException("用户 " + addUserId + " 已是本轮候选人，不能重复加签");
            }
        }
        return nodeRun;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalTaskVO> findTodoTasks(Long userId, TaskQuery query) {
        return workflowTaskService.findTodoTasks(userId, query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query) {
        return workflowTaskService.findDoneTasks(userId, query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessInstanceDetailVO getProcessDetail(Long processInstanceId, Long viewerId) {
        return workflowTaskService.getProcessDetail(processInstanceId, viewerId);
    }

    /**
     * 候选组任务未认领时自动先认领。
     */
    private void autoClaimIfNeeded(ApprovalTaskEntity task, Long operatorId) {
        if (task.getAssigneeId() != null) {
            return;
        }
        taskService.claim(task.getFlowableTaskId(), operatorId.toString());
        task.setAssigneeId(operatorId);
        task.setStatus(TaskStatus.CLAIMED);
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);
    }

    /**
     * 任务完成后检查所属流程实例是否已结束，结束则回写终态。流程被判定为 REJECTED 意味着某个
     * 分支命中了 {@code outcome=REJECTED} 的 END 节点（编译期附加了根流程范围
     * {@code TerminateEventDefinition}，见 3.4），Flowable 已经在引擎侧取消了同一根作用域内
     * 其余并行分支的执行与用户任务；但引擎侧取消不会自动回写我们自己的
     * {@code tab_wf_approval_task} 投影表，这些行会在"待办"里变成永远查不到对应 Flowable
     * 任务的僵尸记录。因此终态为 REJECTED 时必须同步回收（tasks.md 6.4：任一并行分支触发全流程
     * 终止拒绝时，其余开放分支/任务须被取消且业务投影记录 CANCELLED，不能误记为已同意）；
     * APPROVED 时正常情况下不应还有开放任务（并行汇合要求全部分支正常完成），此处调用是幂等的
     * 兜底，不会误伤已完成任务。
     */
    private void finalizeInstanceIfEnded(Long processInstanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null || !StringUtils.hasText(instance.getFlowableInstanceId())) {
            return;
        }
        long remaining = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .count();
        if (remaining > 0) {
            return;
        }
        HistoricVariableInstance approvedVariable = historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(instance.getFlowableInstanceId())
                .variableName("approved")
                .singleResult();
        boolean approved = approvedVariable == null || !Boolean.FALSE.equals(approvedVariable.getValue());
        instance.setStatus(approved ? ProcessInstanceStatus.APPROVED : ProcessInstanceStatus.REJECTED);
        instance.setCurrentNodeId(null);
        instance.setCurrentNodeName(null);
        instance.setFinishedTime(LocalDateTime.now());
        instance.setUpdateTime(LocalDateTime.now());
        processInstanceMapper.updateById(instance);

        if (!approved) {
            closeOpenTasks(instance.getId(), "并行分支终止拒绝，取消其余开放任务");
        }
    }

    /**
     * 关闭流程实例撤回/终止后仍处于开放状态的自有任务记录，避免继续出现在"我的待办"中。
     * 状态记为 {@link TaskStatus#CANCELLED}（而非曾经错误使用的 {@code COMPLETED}——把"引擎
     * 因撤回/终止取消的任务"误记为"已完成"会让业务投影看起来像已经同意，design.md 第7节明确
     * 要求"业务投影记录 CANCELLED+原因，不能作为已同意"，tasks.md 6.4 核实并修复此缺陷），并写入
     * {@code cancel_reason} 供审计与前端展示。覆盖同节点仍在等待的会签候选任务（如 VETO 一票
     * 否决时其余未决候选人）与并行块中未走完的其余分支任务。
     */
    private void closeOpenTasks(Long processInstanceId, String reason) {
        List<ApprovalTaskEntity> openTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalTaskEntity task : openTasks) {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCancelReason(reason);
            task.setFinishedTime(now);
            task.setUpdateTime(now);
            approvalTaskMapper.updateById(task);
        }
    }

    /**
     * 记录审批/驳回轨迹。
     */
    private void recordAction(ApprovalTaskEntity task, Long operatorId, String action, String remark) {
        recordAction(requireInstance(task.getProcessInstanceId()), task.getId(), task.getNodeId(), task.getNodeName(),
                operatorId, action, remark, null, null);
    }

    /**
     * 记录不关联具体任务的审批轨迹（如撤回）。
     */
    private void recordAction(
            ProcessInstanceEntity instance,
            Long taskId,
            Long operatorId,
            String action,
            String remark) {
        recordAction(instance, taskId, null, null, operatorId, action, remark, null, null);
    }

    /**
     * 记录转办/委派轨迹：{@code operatorId} 记为发起转办/委派的原处理人（{@code fromUserId}），
     * {@code targetUserId} 记为新处理人（{@code toUserId}），两者均结构化落库，不再仅拼入
     * {@code remark} 文本。
     */
    private void recordTransferOrDelegate(
            ApprovalTaskEntity task,
            Long operatorId,
            Long targetUserId,
            String action,
            String defaultRemark,
            String userRemark) {
        String remark = StringUtils.hasText(userRemark) ? defaultRemark + "；" + userRemark : defaultRemark;
        recordAction(requireInstance(task.getProcessInstanceId()), task.getId(), task.getNodeId(), task.getNodeName(),
                operatorId, action, remark, operatorId, targetUserId);
    }

    /**
     * 落库审批轨迹的最终实现。
     */
    private void recordAction(
            ProcessInstanceEntity instance,
            Long taskId,
            String nodeId,
            String nodeName,
            Long operatorId,
            String action,
            String remark,
            Long fromUserId,
            Long toUserId) {
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? "system" : operatorId.toString();
        approvalRecordMapper.insert(ApprovalRecordEntity.builder()
                .processInstanceId(instance == null ? null : instance.getId())
                .taskId(taskId)
                .nodeId(nodeId)
                .nodeName(nodeName)
                .operatorId(operatorId)
                .action(action)
                .remark(remark)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build());
    }

    /**
     * 查询审批任务，不存在时抛出业务异常。不加锁，仅用于在正式加锁前先定位任务所属流程实例
     * id（决定加锁顺序第一步该锁哪一行实例），或其余不需要行锁的只读场景。
     * <p>
     * 保持不加锁（tasks.md 6.8 真实并发测试排查过程中的一次已放弃尝试：曾改成
     * {@code SELECT ... FOR UPDATE} 试图规避下面"任务是否仍然有效"检查引用的
     * {@code REPEATABLE READ} 快照过早固定问题，但这会让本方法在正式的"实例行→任务行"顺序
     * 之前就先锁住任务行；而 {@link #closeOpenTasks}（被 {@link #doWithdraw}/
     * {@link #doTerminate} 在已持有实例行锁之后调用，用于取消同一任务）是"先实例行、后任务行"
     * 的顺序，两者方向相反，真实触发了 MySQL 死锁（{@code DeadlockLoserDataAccessException}）。
     * 已改为下面"锁到任务行后立即基于本类自己的表校验任务是否仍处于可处理状态"这一更安全的
     * 方案，因此本方法维持原有的不加锁语义，不再改动）。
     */
    private ApprovalTaskEntity requireTask(Long taskId) {
        ApprovalTaskEntity task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        return task;
    }

    /**
     * 对任务行加 {@code SELECT ... FOR UPDATE} 行锁并查询，不存在时抛出业务异常。必须已处于
     * 事务上下文中，且按固定顺序在 {@link #requireInstanceForUpdate} 之后调用
     * （production-approval-lifecycle change design.md 第8节，tasks.md 6.2"固定锁顺序：
     * 业务活动锁 → 实例行 → 任务行 → 节点轮次"）。
     */
    private ApprovalTaskEntity requireTaskForUpdate(Long taskId) {
        ApprovalTaskEntity task = approvalTaskMapper.selectOne(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getId, taskId)
                .last("FOR UPDATE"));
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        return task;
    }

    /**
     * 校验任务当前是否仍处于可处理状态（{@link TaskStatus#PENDING}/{@link TaskStatus#CLAIMED}），
     * 不满足则清晰拒绝。必须紧跟在 {@link #requireTaskForUpdate} 之后调用，用本类自己已加锁、
     * 保证读到最新已提交数据的 {@code tab_wf_approval_task} 行判定，而不是依赖后续对 Flowable
     * 引擎表（如 {@code taskService.createTaskQuery()}）的普通查询——tasks.md 6.8 真实并发测试
     * 排查确认的缺口：MySQL InnoDB {@code REPEATABLE READ} 下，一个事务的一致性读快照在其
     * *第一次*普通（非加锁）查询时就固定；本类各动作方法进入本方法前已有的
     * {@link #requireTask} 是整个事务的第一条语句且是普通查询，会在事务真正抢到
     * {@link #requireInstanceForUpdate} 的实例行锁之前就提前固定快照（这一点无法简单改成加锁
     * 读——若改成加锁读，会与 {@link #closeOpenTasks}（被 {@link #doWithdraw}/
     * {@link #doTerminate} 在已持有实例行锁后才调用、"先实例后任务"的顺序）方向相反，真实触发
     * 死锁，已在实现过程中放弃该方案，见 {@link #requireTask} 类注释）。当本事务在实例行锁上
     * 排队、等到并发的 {@link #doWithdraw}/{@link #doTerminate} 已提交任务取消后才被唤醒继续
     * 执行时，若之后才第一次触达 Flowable 引擎表的普通查询，读到的仍是这个过早固定的陈旧快照，
     * 会误判为"任务仍然存在"，进而调用 {@code taskService.complete}/{@code delegateTask} 等
     * 引擎写操作试图写入已被对方事务真实删除的 {@code ACT_RU_EXECUTION}/{@code ACT_RU_TASK} 行，
     * 抛出未经处理的 {@link java.sql.SQLIntegrityConstraintViolationException}（真实复现见
     * {@code WithdrawFirstApprovalRaceConcurrencyIntegrationTest}）。本方法依赖的
     * {@code task.getStatus()} 来自 {@link #requireTaskForUpdate} 的加锁读取，加锁读取总是读
     * 最新已提交版本、不受快照影响，能正确看到并发方已提交的取消结果，在触达 Flowable 引擎表
     * 之前就先行清晰拒绝，从根本上避免走到那条会踩中陈旧快照的代码路径。
     */
    private void requireTaskStillOpen(ApprovalTaskEntity task) {
        if (!TaskStatus.PENDING.equals(task.getStatus()) && !TaskStatus.CLAIMED.equals(task.getStatus())) {
            throw new BusinessException("审批任务不存在或已处理");
        }
    }

    /**
     * 校验操作人当前是否仍处于启用状态：已停用/已删除身份不得处理审批任务，明确拒绝而不是
     * 静默放行（production-approval-lifecycle change tasks.md 5.4"停用身份检测"，与既有
     * {@code taskAuthorizationService.isAuthorized}——判断"是否有权处理该任务"——是两个独立
     * 维度的校验，一个判定"资格"，一个判定"身份仍然有效"）。{@code tab_user} 查不到该
     * operatorId 时不拒绝（用户体系之外的历史/测试调用方，不在本次加固范围内误伤）——只对
     * "身份真实存在但已被停用/删除"这一具体风险场景拦截。
     */
    private void requireOperatorEnabled(Long operatorId) {
        UserEntity user = userMapper.selectById(operatorId);
        if (user != null && !Objects.equals(user.getStatus(), UserStatus.ENABLED)) {
            throw new BusinessException("当前操作人身份已停用，无法处理审批任务");
        }
    }

    /**
     * 查询流程实例，不存在时抛出业务异常。不加锁，用于审计轨迹落库等只读场景（对应行早已在
     * 本方法调用点所在的同一事务内被 {@link #requireInstanceForUpdate} 锁过）。
     */
    private ProcessInstanceEntity requireInstance(Long processInstanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(processInstanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        return instance;
    }

    /**
     * 对流程实例行加 {@code SELECT ... FOR UPDATE} 行锁并查询，不存在时抛出业务异常。必须已
     * 处于事务上下文中；按固定顺序，本方法须在同一动作方法内任何任务行加锁之前调用
     * （production-approval-lifecycle change design.md 第8节，tasks.md 6.2"固定锁顺序：
     * 业务活动锁 → 实例行 → 任务行 → 节点轮次"——业务活动锁在更上层的
     * {@code ApprovalRequestServiceImpl} 发起阶段处理，节点轮次 {@code tab_wf_node_run}
     * 表当前尚未被任何动作方法使用，留待 6.3 计票逻辑落地后再纳入本顺序）。
     */
    private ProcessInstanceEntity requireInstanceForUpdate(Long processInstanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectOne(
                new LambdaQueryWrapper<ProcessInstanceEntity>()
                        .eq(ProcessInstanceEntity::getId, processInstanceId)
                        .last("FOR UPDATE"));
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        return instance;
    }

    /**
     * 查询节点审批人规则，不存在时抛出业务异常。
     */
    private NodeAssigneeRuleEntity requireRule(Long processDefinitionId, String nodeId) {
        NodeAssigneeRuleEntity rule = nodeAssigneeRuleMapper.selectOne(new LambdaQueryWrapper<NodeAssigneeRuleEntity>()
                .eq(NodeAssigneeRuleEntity::getProcessDefinitionId, processDefinitionId)
                .eq(NodeAssigneeRuleEntity::getNodeId, nodeId)
                .last("LIMIT 1"));
        if (rule == null) {
            throw new BusinessException("节点 " + nodeId + " 未配置审批人规则");
        }
        return rule;
    }

    /**
     * 解析用户 id 文本，失败返回 {@code null}。
     */
    private Long parseUserId(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
