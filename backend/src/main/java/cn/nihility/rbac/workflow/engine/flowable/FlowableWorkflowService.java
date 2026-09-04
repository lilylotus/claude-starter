package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
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

    /** Flowable 流程仓库服务，本类未直接使用，随其余引擎 Service 一并声明，保留以备扩展。 */
    @SuppressWarnings("unused")
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
        taskService.complete(task.getFlowableTaskId(), Map.of("approved", approved));

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
            closeOpenTasks(instance.getId());
            if (StringUtils.hasText(instance.getFlowableInstanceId())) {
                runtimeService.deleteProcessInstance(instance.getFlowableInstanceId(),
                        isReject ? "审批人拒绝，终止流程" : "会签反对票达到终止阈值，终止流程");
            }
            return;
        }

        if (StringUtils.hasText(nodeRun.getExecutionId())) {
            runtimeService.setVariableLocal(nodeRun.getExecutionId(), "voteAgreeCount", newAgreeCount);
        }
        taskService.complete(task.getFlowableTaskId(), Map.of());
        finalizeInstanceIfEnded(task.getProcessInstanceId());
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
     */
    private void doReturnTask(ReturnTaskCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }

        NodeAssigneeRuleEntity targetRule = requireRule(instance.getProcessDefinitionId(), command.targetNodeId());
        if (!Boolean.TRUE.equals(targetRule.getAllowReturn())) {
            throw new BusinessException("目标节点不允许退回");
        }

        runtimeService.createChangeActivityStateBuilder()
                .processInstanceId(instance.getFlowableInstanceId())
                .moveActivityIdTo(task.getNodeId(), command.targetNodeId())
                .changeState();

        task.setStatus(TaskStatus.RETURNED);
        task.setFinishedTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        approvalTaskMapper.updateById(task);

        recordAction(task, command.operatorId(), ApprovalAction.RETURN, command.remark());
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

        closeOpenTasks(instance.getId());

        recordAction(instance, null, command.operatorId(), ApprovalAction.WITHDRAW, command.remark());
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
     * 第8节，tasks.md 6.2）。
     */
    private void doDelegate(DelegateCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        NodeAssigneeRuleEntity rule = requireRule(instance.getProcessDefinitionId(), task.getNodeId());
        if (!Boolean.TRUE.equals(rule.getAllowDelegate())) {
            throw new BusinessException("该节点不允许委派");
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
     * 固定加锁顺序：先锁流程实例行，再锁任务行（design.md 第8节，tasks.md 6.2）。
     */
    private void doAddSign(AddSignCommand command) {
        Long processInstanceId = requireTask(command.taskId()).getProcessInstanceId();
        ProcessInstanceEntity instance = requireInstanceForUpdate(processInstanceId);
        ApprovalTaskEntity task = requireTaskForUpdate(command.taskId());
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
        for (Long addUserId : command.addUserIds()) {
            runtimeService.addMultiInstanceExecution(task.getNodeId(), instance.getFlowableInstanceId(),
                    Map.of("approver", addUserId.toString()));
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
    public List<ApprovalTaskVO> findDoneTasks(Long userId, TaskQuery query) {
        return workflowTaskService.findDoneTasks(userId, query);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProcessInstanceDetailVO getProcessDetail(Long processInstanceId) {
        return workflowTaskService.getProcessDetail(processInstanceId);
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
     * 任务完成后检查所属流程实例是否已结束，结束则回写终态。
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
    }

    /**
     * 关闭流程实例撤回后仍处于开放状态的自有任务记录，避免继续出现在"我的待办"中。
     */
    private void closeOpenTasks(Long processInstanceId) {
        List<ApprovalTaskEntity> openTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        LocalDateTime now = LocalDateTime.now();
        for (ApprovalTaskEntity task : openTasks) {
            task.setStatus(TaskStatus.COMPLETED);
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
