package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.ProcessModelStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.AddSignCommand;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.ApprovalTaskVO;
import cn.nihility.rbac.workflow.dto.DelegateCommand;
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
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.assignee.support.TaskAuthorizationService;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.NodeAssigneeRuleMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import cn.nihility.rbac.workflow.policy.WithdrawPolicy;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import cn.nihility.rbac.workflow.service.WorkflowTaskService;
import cn.nihility.rbac.common.exception.BusinessException;
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

    /** 流程模型数据访问接口。 */
    private final ProcessModelMapper processModelMapper;

    /** 流程定义数据访问接口。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /** 节点审批人规则数据访问接口。 */
    private final NodeAssigneeRuleMapper nodeAssigneeRuleMapper;

    /** 流程实例数据访问接口。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 审批任务数据访问接口。 */
    private final ApprovalTaskMapper approvalTaskMapper;

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

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public WorkflowInstanceResult start(StartProcessCommand command) {
        ProcessModelEntity processModel = processModelMapper.selectOne(new LambdaQueryWrapper<ProcessModelEntity>()
                .eq(ProcessModelEntity::getProcessCode, command.processCode()));
        if (processModel == null || !ProcessModelStatus.PUBLISHED.equals(processModel.getStatus())
                || processModel.getCurrentDefinitionId() == null) {
            throw new BusinessException("流程 " + command.processCode() + " 未发布，无法发起");
        }
        ProcessDefinitionEntity processDefinition = processDefinitionMapper.selectById(processModel.getCurrentDefinitionId());
        if (processDefinition == null || !ProcessModelStatus.PUBLISHED.equals(processDefinition.getStatus())) {
            throw new BusinessException("流程 " + command.processCode() + " 当前版本已下线，无法发起");
        }

        LocalDateTime now = LocalDateTime.now();
        String applicantText = command.applicantId() == null ? null : command.applicantId().toString();
        ProcessInstanceEntity instance = ProcessInstanceEntity.builder()
                .processDefinitionId(processDefinition.getId())
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
                command.taskId(), () -> {
                    completeTask(command.taskId(), command.operatorId(), command.remark(), true);
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
                command.taskId(), () -> {
                    completeTask(command.taskId(), command.operatorId(), command.remark(), false);
                    return null;
                });
    }

    /**
     * 通过/驳回共用的任务完成逻辑：越权校验、候选组自动认领、Flowable 完成任务/委派归还、
     * 落库审批任务与轨迹、流程实例状态收尾。
     */
    private void completeTask(Long taskId, Long operatorId, String remark, boolean approved) {
        ApprovalTaskEntity task = requireTask(taskId);
        if (!taskAuthorizationService.isAuthorized(task, operatorId)) {
            throw new BusinessException("无权限处理该审批任务");
        }
        autoClaimIfNeeded(task, operatorId);

        Task flowableTask = taskService.createTaskQuery().taskId(task.getFlowableTaskId()).singleResult();
        if (flowableTask == null) {
            throw new BusinessException("审批任务不存在或已处理");
        }

        if (flowableTask.getDelegationState() == DelegationState.PENDING) {
            // 受托人处理委派任务：归还原处理人，不驱动流程往下走，流程变量 approved 由原处理人
            // 后续再次调用 approve/reject 时最终写入。
            taskService.resolveTask(task.getFlowableTaskId());
            task.setAssigneeId(parseUserId(flowableTask.getOwner()));
            task.setUpdateTime(LocalDateTime.now());
            approvalTaskMapper.updateById(task);
            recordAction(task, operatorId, approved ? ApprovalAction.APPROVE : ApprovalAction.REJECT, remark);
            return;
        }

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
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void returnTask(ReturnTaskCommand command) {
        idempotencyService.executeOnce(command.idempotencyKey(), ApprovalAction.RETURN, command.operatorId(),
                command.taskId(), () -> {
                    doReturnTask(command);
                    return null;
                });
    }

    /**
     * 退回历史节点的实际处理逻辑。
     */
    private void doReturnTask(ReturnTaskCommand command) {
        ApprovalTaskEntity task = requireTask(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }

        ProcessInstanceEntity instance = requireInstance(task.getProcessInstanceId());
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
                null, () -> {
                    doWithdraw(command);
                    return null;
                });
    }

    /**
     * 撤回流程实例的实际处理逻辑。
     */
    private void doWithdraw(WithdrawCommand command) {
        ProcessInstanceEntity instance = requireInstance(command.processInstanceId());
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
                command.taskId(), () -> {
                    doTransfer(command);
                    return null;
                });
    }

    /**
     * 转办的实际处理逻辑。
     */
    private void doTransfer(TransferCommand command) {
        ApprovalTaskEntity task = requireTask(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        ProcessInstanceEntity instance = requireInstance(task.getProcessInstanceId());
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
                command.taskId(), () -> {
                    doDelegate(command);
                    return null;
                });
    }

    /**
     * 委派的实际处理逻辑：使用 Flowable 原生委派语义，受托人处理完成后归还原处理人
     * （见 {@link #completeTask}）。
     */
    private void doDelegate(DelegateCommand command) {
        ApprovalTaskEntity task = requireTask(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        ProcessInstanceEntity instance = requireInstance(task.getProcessInstanceId());
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
                command.taskId(), () -> {
                    doAddSign(command);
                    return null;
                });
    }

    /**
     * 加签的实际处理逻辑：会签节点动态增加候选审批人，使用 Flowable
     * {@code addMultiInstanceExecution} 原生 API，不手工修改内部计数变量。约定多实例节点的
     * {@code flowable:elementVariable} 统一命名为 {@code approver}（design.md Decision 4/7）。
     */
    private void doAddSign(AddSignCommand command) {
        ApprovalTaskEntity task = requireTask(command.taskId());
        if (!taskAuthorizationService.isAuthorized(task, command.operatorId())) {
            throw new BusinessException("无权限处理该审批任务");
        }
        ProcessInstanceEntity instance = requireInstance(task.getProcessInstanceId());
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
     * 查询审批任务，不存在时抛出业务异常。
     */
    private ApprovalTaskEntity requireTask(Long taskId) {
        ApprovalTaskEntity task = approvalTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("审批任务不存在");
        }
        return task;
    }

    /**
     * 查询流程实例，不存在时抛出业务异常。
     */
    private ProcessInstanceEntity requireInstance(Long processInstanceId) {
        ProcessInstanceEntity instance = processInstanceMapper.selectById(processInstanceId);
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
