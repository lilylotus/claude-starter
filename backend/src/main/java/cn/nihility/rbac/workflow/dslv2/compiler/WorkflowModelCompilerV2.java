package cn.nihility.rbac.workflow.dslv2.compiler;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.designer.compiler.NodeAssigneeRuleDraft;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.RejectPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.SelfPolicy;
import cn.nihility.rbac.workflow.dslv2.constant.VoteExecution;
import cn.nihility.rbac.workflow.dslv2.constant.VoteMode;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AutoNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.CcNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelJoinNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelSplitNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.engine.AutoServiceTaskDelegate;
import cn.nihility.rbac.workflow.dslv2.engine.CcServiceTaskDelegate;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.TerminateEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.validation.ProcessValidator;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ValidationError;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DSL v2 → BPMN 编译器，{@link cn.nihility.rbac.workflow.designer.compiler.WorkflowModelCompiler}
 * v1 实现的姊妹实现，彼此独立（production-approval-lifecycle change design.md Decision 3）。
 * 单人/候选组节点复用 v1 的 {@code WorkflowAssigneeTaskListener}；会签节点使用 v2 专用的
 * {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceExecutionListener}/
 * {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceTaskListener}（新建，
 * 不复用/不改动 v1 的 {@code WorkflowMultiInstanceExecutionListener}/
 * {@code WorkflowMultiInstanceTaskListener}，v1 存量流程行为不受影响），实现
 * design.md 第7节 {@code rejectPolicy=VETO/THRESHOLD} 区分与 {@code tab_wf_node_run} 计票
 * （production-approval-lifecycle change tasks.md 6.3）。DSL v2 审批节点编译产物统一落地为
 * 与 v1 完全同构的 {@link NodeAssigneeRuleDraft}，任务创建持久化、幂等、越权校验均直接复用
 * 既有运行时基础设施，本编译器只新增 v1 不支持的部分：并行分叉/汇合网关、抄送/自动任务服务
 * 任务、条件 AST 编译、会签串行执行方式、明确 outcome 的结束事件、会签 N/A/R/U 计票。
 */
@Component
@RequiredArgsConstructor
public class WorkflowModelCompilerV2 {

    /** 组织数据访问接口，用于校验 {@code assignee.orgSource=FIXED_ORG} 场景下 {@code orgId}
     *  是否真实存在且启用（production-approval-lifecycle change tasks.md 5.3）。 */
    private final OrgMapper orgMapper;

    /** 单人/候选组节点挂载的任务监听器类名，复用 v1。 */
    private static final String ASSIGNEE_TASK_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.engine.flowable.WorkflowAssigneeTaskListener";

    /** 会签节点挂载的执行监听器类名，v2 专用，不复用 v1（tasks.md 6.3）。 */
    private static final String MULTI_INSTANCE_EXECUTION_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceExecutionListener";

    /** 会签节点单个实例任务监听器类名，v2 专用，只负责持久化任务行，不复用 v1（tasks.md 6.3）。 */
    private static final String MULTI_INSTANCE_TASK_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceTaskListener";

    /** 会签完成条件表达式：{@code voteAgreeCount}/{@code voteThreshold} 是
     *  {@link cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2MultiInstanceExecutionListener}
     *  在会签轮次开始时按整数公式计算并写入 miBody 执行作用域的局部变量（K 值计算与实际计票均
     *  在 Java 端完成，此处只需判断是否已达到通过阈值，不在 UEL 表达式里做除法/百分比运算，
     *  避免浮点误差）；反对票导致的节点终止（VETO 一票否决 / THRESHOLD 阈值不足）由
     *  {@code FlowableWorkflowService.completeTask} 在决定计票结果后直接调用
     *  {@code runtimeService.deleteProcessInstance} 终止整个流程实例，不依赖也不通过本完成
     *  条件表达式表达"拒绝"语义（design.md 第7节，tasks.md 6.3）。 */
    private static final String MULTI_INSTANCE_COMPLETION_CONDITION = "${voteAgreeCount >= voteThreshold}";

    /** 会签多实例元素变量名，须与 v1、{@code FlowableWorkflowService.doAddSign}、
     *  {@code WorkflowV2ReassignmentService} 保持完全一致。 */
    private static final String MULTI_INSTANCE_ELEMENT_VARIABLE = "approver";

    /** Flowable 自带的流程结构二次校验器，无状态，实例可复用。 */
    private final ProcessValidator processValidator = new ProcessValidatorFactory().createDefaultProcessValidator();

    /**
     * 编译 DSL v2 为 BPMN。
     *
     * @param dsl 待编译的流程模型 DSL v2
     * @return 编译产物
     */
    public CompiledProcessV2 compile(ProcessModelDslV2 dsl) {
        ProcessModelDslV2Validator.validate(dsl, this::orgExistsAndEnabled);

        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId(dsl.getProcessCode());
        process.setName(dsl.getProcessName());
        process.setExecutable(true);
        bpmnModel.addProcess(process);

        Map<String, Integer> approvalNodeOrders = computeApprovalNodeOrders(dsl);
        Map<String, ExclusiveGateway> exclusiveGatewayById = new HashMap<>();
        List<NodeAssigneeRuleDraft> assigneeRules = new ArrayList<>();
        Map<String, String> nodeMapping = new LinkedHashMap<>();

        for (ProcessNodeDslV2 node : dsl.getNodes()) {
            nodeMapping.put(node.getId(), node.getId());
            if (node instanceof StartNodeDslV2) {
                StartEvent startEvent = new StartEvent();
                startEvent.setId(node.getId());
                startEvent.setName(node.getName());
                process.addFlowElement(startEvent);
            } else if (node instanceof EndNodeDslV2 end) {
                process.addFlowElement(buildEndEvent(end));
            } else if (node instanceof ConditionNodeDslV2) {
                ExclusiveGateway gateway = new ExclusiveGateway();
                gateway.setId(node.getId());
                gateway.setName(node.getName());
                process.addFlowElement(gateway);
                exclusiveGatewayById.put(node.getId(), gateway);
            } else if (node instanceof ParallelSplitNodeDslV2 || node instanceof ParallelJoinNodeDslV2) {
                ParallelGateway gateway = new ParallelGateway();
                gateway.setId(node.getId());
                gateway.setName(node.getName());
                process.addFlowElement(gateway);
            } else if (node instanceof CcNodeDslV2 cc) {
                process.addFlowElement(buildCcServiceTask(cc));
            } else if (node instanceof AutoNodeDslV2 auto) {
                process.addFlowElement(buildAutoServiceTask(auto));
            } else if (node instanceof ApprovalNodeDslV2 approval) {
                process.addFlowElement(buildApprovalUserTask(approval));
                assigneeRules.add(toRuleDraft(approval, approvalNodeOrders.getOrDefault(approval.getId(), 0)));
            }
        }

        int flowIndex = 0;
        for (EdgeDslV2 edge : dsl.getEdges()) {
            flowIndex++;
            SequenceFlow flow = new SequenceFlow(edge.getSource(), edge.getTarget());
            flow.setId(edge.getId() != null ? edge.getId() : "flow_" + flowIndex + "_" + edge.getSource() + "_" + edge.getTarget());
            if (edge.getCondition() != null) {
                flow.setConditionExpression(ConditionAstCompiler.compile(edge.getCondition()));
            }
            process.addFlowElement(flow);
            linkSequenceFlow(process, flow);

            ExclusiveGateway sourceGateway = exclusiveGatewayById.get(edge.getSource());
            if (sourceGateway != null && edge.getCondition() == null) {
                sourceGateway.setDefaultFlow(flow.getId());
            }
        }

        List<String> engineErrors = processValidator.validate(bpmnModel).stream()
                .filter(error -> !error.isWarning())
                .map(WorkflowModelCompilerV2::describe)
                .toList();
        if (!engineErrors.isEmpty()) {
            throw new WorkflowModelValidationException(engineErrors);
        }

        return new CompiledProcessV2(bpmnModel, assigneeRules, nodeMapping);
    }

    /**
     * 构建结束事件：{@code APPROVED} 为普通结束事件（等待其余分支正常完成才算流程完成）；
     * {@code REJECTED} 附加根流程范围的 {@link TerminateEventDefinition}，立即取消其余全部
     * 开放分支（design.md Decision 3）。
     */
    private EndEvent buildEndEvent(EndNodeDslV2 end) {
        EndEvent endEvent = new EndEvent();
        endEvent.setId(end.getId());
        endEvent.setName(end.getName());
        if ("REJECTED".equals(end.getOutcome())) {
            TerminateEventDefinition terminate = new TerminateEventDefinition();
            terminate.setTerminateAll(true);
            endEvent.addEventDefinition(terminate);
        }
        // 用 "start"（到达该结束节点、其自身行为执行前）而非 "end"：Terminate 结束事件的
        // 终止行为可能会抢在 "end" 事件监听器触发之前就已经取消当前作用域，导致监听器
        // 根本没有机会执行；"start" 在节点刚被到达、行为尚未执行时就已确定会触发。
        FlowableListener outcomeListener = buildListener("start",
                "cn.nihility.rbac.workflow.dslv2.engine.WorkflowV2EndOutcomeListener");
        outcomeListener.setFieldExtensions(List.of(fieldExtension("outcome", end.getOutcome())));
        endEvent.setExecutionListeners(List.of(outcomeListener));
        return endEvent;
    }

    /**
     * 构建抄送节点对应的 {@link ServiceTask}，接收人来源类型/取值通过字段注入传给
     * {@link CcServiceTaskDelegate}。
     */
    private ServiceTask buildCcServiceTask(CcNodeDslV2 cc) {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(cc.getId());
        serviceTask.setName(cc.getName());
        serviceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
        serviceTask.setImplementation(CcServiceTaskDelegate.class.getName());
        serviceTask.setFieldExtensions(List.of(
                fieldExtension("recipientType", cc.getRecipientType()),
                fieldExtension("recipientValue", cc.getRecipientValue())));
        return serviceTask;
    }

    /**
     * 构建自动任务节点对应的 {@link ServiceTask}。{@code actionCode}/{@code params} 本轮不
     * 注入具体执行逻辑（{@code AutoActionRegistry} 为空，发布前校验已拒绝任何引用），仅保证
     * 结构完整。
     */
    private ServiceTask buildAutoServiceTask(AutoNodeDslV2 auto) {
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId(auto.getId());
        serviceTask.setName(auto.getName());
        serviceTask.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
        serviceTask.setImplementation(AutoServiceTaskDelegate.class.getName());
        serviceTask.setFieldExtensions(List.of(fieldExtension("actionCode", auto.getActionCode())));
        return serviceTask;
    }

    /**
     * 构建字段注入的静态字符串值。
     */
    private FieldExtension fieldExtension(String name, String value) {
        FieldExtension extension = new FieldExtension();
        extension.setFieldName(name);
        extension.setStringValue(value == null ? "" : value);
        return extension;
    }

    /**
     * 构建审批节点对应的 {@link UserTask}：无 {@code vote} 配置按单人/候选组编译，复用 v1
     * {@code WorkflowAssigneeTaskListener}；有 {@code vote} 配置按会签编译，挂 v2 专用的
     * {@code WorkflowV2MultiInstanceExecutionListener}/{@code WorkflowV2MultiInstanceTaskListener}
     * （只负责候选人集合准备/任务行持久化，不在 {@code complete} 事件挂任何监听器——计票与
     * 完成/终止判定统一由 {@code FlowableWorkflowService.completeTask} 在调用
     * {@code taskService.complete} 前后以 Java 代码完成，见 tasks.md 6.3），
     * {@code vote.execution=SEQUENTIAL} 时设置串行多实例（v1 恒为并行，是 v1 不具备的新能力）。
     */
    private UserTask buildApprovalUserTask(ApprovalNodeDslV2 approval) {
        UserTask userTask = new UserTask();
        userTask.setId(approval.getId());
        userTask.setName(approval.getName());

        VoteMode voteMode = approval.getVote() == null ? null : approval.getVote().getMode();
        if (voteMode == null) {
            userTask.setTaskListeners(List.of(buildListener("create", ASSIGNEE_TASK_LISTENER_CLASS)));
            return userTask;
        }

        userTask.setAssignee("${" + MULTI_INSTANCE_ELEMENT_VARIABLE + "}");
        userTask.setExecutionListeners(List.of(buildListener("start", MULTI_INSTANCE_EXECUTION_LISTENER_CLASS)));
        userTask.setTaskListeners(List.of(buildListener("create", MULTI_INSTANCE_TASK_LISTENER_CLASS)));

        MultiInstanceLoopCharacteristics loopCharacteristics = new MultiInstanceLoopCharacteristics();
        loopCharacteristics.setSequential(approval.getVote().getExecution() == VoteExecution.SEQUENTIAL);
        loopCharacteristics.setInputDataItem(collectionVariableName(approval.getId()));
        loopCharacteristics.setElementVariable(MULTI_INSTANCE_ELEMENT_VARIABLE);
        loopCharacteristics.setCompletionCondition(MULTI_INSTANCE_COMPLETION_CONDITION);
        userTask.setLoopCharacteristics(loopCharacteristics);
        return userTask;
    }

    /**
     * {@link VoteMode} → v1 {@link ApprovalMode} 映射：{@code approvalMode}/{@code approvalPercent}
     * 落库到 {@code tab_wf_node_assignee_rule} 后，由
     * {@code WorkflowV2MultiInstanceExecutionListener} 在会签轮次开始时按整数公式换算通过阈值
     * K（{@code ALL=N/ANY=1/PERCENT=ceil(N×percent/100)}），{@code rejectPolicy}
     * （{@code VETO}/{@code THRESHOLD}）单独落库、单独在
     * {@code FlowableWorkflowService.completeTask} 计票时读取，两者互不影响
     * （production-approval-lifecycle change design.md 第7节，tasks.md 6.3）。
     */
    private ApprovalMode mapVoteMode(VoteMode voteMode) {
        return switch (voteMode) {
            case ALL -> ApprovalMode.AND;
            case ANY -> ApprovalMode.OR;
            case PERCENT -> ApprovalMode.PERCENT;
        };
    }

    /**
     * 建立 {@link SequenceFlow} 与源/目标 {@link FlowNode} 之间的双向引用，与 v1 编译器同一
     * 原因（直接用对象模型 API 构建 BPMN 不会自动回填 Flowable {@link ProcessValidator}
     * 依赖的 incoming/outgoing 便捷列表）。
     */
    private void linkSequenceFlow(Process process, SequenceFlow flow) {
        FlowElement source = process.getFlowElement(flow.getSourceRef());
        FlowElement target = process.getFlowElement(flow.getTargetRef());
        if (source instanceof FlowNode sourceNode) {
            sourceNode.getOutgoingFlows().add(flow);
        }
        if (target instanceof FlowNode targetNode) {
            targetNode.getIncomingFlows().add(flow);
        }
        flow.setSourceFlowElement(source);
        flow.setTargetFlowElement(target);
    }

    /**
     * 会签节点候选人集合流程变量名，须与 v1 {@code WorkflowMultiInstanceExecutionListener}
     * 完全一致。
     */
    private String collectionVariableName(String nodeId) {
        return "approvers_" + nodeId;
    }

    /**
     * 构建一个 {@code class} 实现类型的 Flowable 监听器。
     */
    private FlowableListener buildListener(String event, String implementationClass) {
        FlowableListener listener = new FlowableListener();
        listener.setEvent(event);
        listener.setImplementationType(ImplementationType.IMPLEMENTATION_TYPE_CLASS);
        listener.setImplementation(implementationClass);
        return listener;
    }

    /**
     * 判断组织 id 是否在 {@code tab_org} 中真实存在且状态启用，供
     * {@code assignee.orgSource=FIXED_ORG} 场景的发布校验使用。
     */
    private boolean orgExistsAndEnabled(Long orgId) {
        OrgEntity org = orgMapper.selectById(orgId);
        return org != null && Objects.equals(org.getStatus(), OrgStatus.ENABLED);
    }

    /**
     * 把 {@link ApprovalNodeDslV2} 映射为与 v1 完全同构的 {@link NodeAssigneeRuleDraft}，
     * 复用既有的 {@code tab_wf_node_assignee_rule} 持久化路径与运行时解析基础设施。
     */
    private NodeAssigneeRuleDraft toRuleDraft(ApprovalNodeDslV2 approval, int order) {
        AssigneeType assigneeType = approval.getAssignee() == null || approval.getAssignee().getType() == null
                ? null
                : AssigneeType.valueOf(approval.getAssignee().getType().name());
        String assigneeValue = approval.getAssignee() == null ? null : approval.getAssignee().getValue();
        ApprovalMode mode = approval.getVote() == null || approval.getVote().getMode() == null
                ? ApprovalMode.SINGLE
                : mapVoteMode(approval.getVote().getMode());
        Integer percent = approval.getVote() == null ? null : approval.getVote().getPercent();
        EmptyAssigneeStrategy emptyStrategy = mapEmptyPolicy(approval.getEmptyPolicy());
        boolean allowSelfApproval = approval.getSelfPolicy() == SelfPolicy.ALLOW;
        boolean allowTransfer = approval.getActions() != null && Boolean.TRUE.equals(approval.getActions().getTransfer());
        boolean allowDelegate = approval.getActions() != null && Boolean.TRUE.equals(approval.getActions().getDelegate());
        boolean allowAddSign = approval.getActions() != null && Boolean.TRUE.equals(approval.getActions().getAddSign());
        boolean allowReturn = approval.getActions() != null && Boolean.TRUE.equals(approval.getActions().getReturnAllowed());
        String fieldPermissionsJson = approval.getFieldPermissions() == null || approval.getFieldPermissions().isEmpty()
                ? null
                : JacksonUtils.toJson(approval.getFieldPermissions());
        String assigneeOrgSource = approval.getAssignee() == null ? null : approval.getAssignee().getOrgSource();
        Long targetOrgId = approval.getAssignee() == null ? null : approval.getAssignee().getOrgId();
        // 会签节点反对票策略默认 VETO（design.md"v1 AND/OR/PERCENT 映射为默认一票否决的 v2
        // 规则"），未配置 vote 的单人/候选组节点恒为 null——FlowableWorkflowService 据此区分
        // 一个会签任务是否属于本轮新计票路径（tasks.md 6.3）。
        String rejectPolicy = approval.getVote() == null
                ? null
                : (approval.getVote().getRejectPolicy() == null ? RejectPolicy.VETO : approval.getVote().getRejectPolicy()).name();

        return new NodeAssigneeRuleDraft(
                approval.getId(),
                approval.getName(),
                order,
                assigneeType,
                assigneeValue,
                mode,
                percent,
                emptyStrategy,
                approval.getFallbackRoleCode(),
                allowSelfApproval,
                allowTransfer,
                allowDelegate,
                allowAddSign,
                allowReturn,
                fieldPermissionsJson,
                assigneeOrgSource,
                targetOrgId,
                rejectPolicy);
    }

    /**
     * {@link EmptyPolicy} → v1 共享的 {@link EmptyAssigneeStrategy} 映射，{@code BLOCK}/
     * {@code FALLBACK_ROLE} 为 v2 专用取值，v1 编译器从不产生。
     */
    private EmptyAssigneeStrategy mapEmptyPolicy(EmptyPolicy emptyPolicy) {
        if (emptyPolicy == null) {
            return EmptyAssigneeStrategy.BLOCK;
        }
        return switch (emptyPolicy) {
            case BLOCK -> EmptyAssigneeStrategy.BLOCK;
            case FALLBACK_ROLE -> EmptyAssigneeStrategy.FALLBACK_ROLE;
        };
    }

    /**
     * 按 BFS 从开始节点计算每个审批节点的顺序，供 {@code tab_wf_node_assignee_rule.node_order}
     * 展示"第几级审批"使用；与 v1 同一算法，改用 v2 的节点/边类型。
     */
    private Map<String, Integer> computeApprovalNodeOrders(ProcessModelDslV2 dsl) {
        Map<String, ProcessNodeDslV2> nodeById = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (ProcessNodeDslV2 node : dsl.getNodes()) {
            nodeById.put(node.getId(), node);
            outgoing.put(node.getId(), new ArrayList<>());
        }
        for (EdgeDslV2 edge : dsl.getEdges()) {
            outgoing.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
        }

        String startId = dsl.getNodes().stream()
                .filter(StartNodeDslV2.class::isInstance)
                .map(ProcessNodeDslV2::getId)
                .findFirst()
                .orElse(null);
        Map<String, Integer> order = new LinkedHashMap<>();
        if (startId == null) {
            return order;
        }

        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        int counter = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (nodeById.get(current) instanceof ApprovalNodeDslV2) {
                counter++;
                order.put(current, counter);
            }
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

    /**
     * 把 Flowable {@link ValidationError} 转换为便于展示的定位信息文本。
     */
    private static String describe(ValidationError error) {
        String location = error.getActivityId() != null ? "[" + error.getActivityId() + "] " : "";
        return location + error.getProblem() + "：" + error.getDefaultDescription();
    }
}
