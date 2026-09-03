package cn.nihility.rbac.workflow.designer.compiler;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.designer.dto.ApprovalNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ConditionNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.EndNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.StartNodeDsl;
import cn.nihility.rbac.workflow.engine.flowable.MultiInstanceCompletionEvaluator;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.FlowableListener;
import org.flowable.bpmn.model.ImplementationType;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.validation.ProcessValidator;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ValidationError;
import org.springframework.stereotype.Component;

/**
 * {@link WorkflowModelCompiler} 默认实现：先执行 {@link ProcessModelDslValidator} 结构校验，
 * 再用 Flowable {@code org.flowable.bpmn.model.*} 对象模型 API 逐节点构建 BPMN，最后过
 * Flowable 自带的 {@link ProcessValidator} 做二次校验（workflow-approval-engine change
 * design.md Decision 10）。
 * <p>
 * 生成的用户任务节点与既有 {@code cn.nihility.rbac.workflow.engine.flowable} 包下手写的
 * {@code WorkflowAssigneeTaskListener}/{@code WorkflowMultiInstanceExecutionListener}/
 * {@code WorkflowMultiInstanceTaskListener} 三个监听器的真实实现假设保持一致：单人/候选组
 * 节点（{@code approvalMode=SINGLE}）挂 {@code taskListener event="create"} 指向
 * {@code WorkflowAssigneeTaskListener}；会签节点（{@code AND}/{@code OR}/{@code PERCENT}）
 * 挂 {@code executionListener event="start"} 指向 {@code WorkflowMultiInstanceExecutionListener}
 * 准备候选人集合流程变量，变量命名约定为 {@code approvers_<nodeId>}（与
 * {@code WorkflowMultiInstanceExecutionListener.collectionVariableName} 的真实实现完全一致，
 * 因该方法为包内可见无法跨包直接复用，此处按相同约定复刻），并挂
 * {@code taskListener event="create"/"complete"} 指向 {@code WorkflowMultiInstanceTaskListener}；
 * 多实例元素变量名固定为 {@code approver}，与 {@code FlowableWorkflowService.doAddSign} 加签时
 * 使用的变量名保持一致；{@code completionCondition} 复用
 * {@code MultiInstanceCompletionEvaluator.buildCompletionCondition}，与 Java 端会签完成判定
 * 共享同一套语义，避免两处实现逐渐漂移。
 */
@Component
public class WorkflowModelCompilerImpl implements WorkflowModelCompiler {

    /** 单人/候选组节点挂载的任务监听器类名。 */
    private static final String ASSIGNEE_TASK_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.engine.flowable.WorkflowAssigneeTaskListener";

    /** 会签节点挂载的执行监听器类名。 */
    private static final String MULTI_INSTANCE_EXECUTION_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceExecutionListener";

    /** 会签节点单个实例任务监听器类名。 */
    private static final String MULTI_INSTANCE_TASK_LISTENER_CLASS =
            "cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceTaskListener";

    /** 会签多实例元素变量名，须与 {@code FlowableWorkflowService.doAddSign} 加签时使用的
     *  变量名一致。 */
    private static final String MULTI_INSTANCE_ELEMENT_VARIABLE = "approver";

    /** 条件比较符到 UEL 运算符符号的映射，只允许白名单内的比较符拼装表达式。 */
    private static final Map<String, String> OPERATOR_SYMBOLS = Map.of(
            "EQ", "==",
            "NE", "!=",
            "GT", ">",
            "GTE", ">=",
            "LT", "<",
            "LTE", "<=");

    /** Flowable 自带的流程结构二次校验器，无状态，实例可复用。 */
    private final ProcessValidator processValidator = new ProcessValidatorFactory().createDefaultProcessValidator();

    /**
     * {@inheritDoc}
     */
    @Override
    public CompiledProcess compile(ProcessModelDsl dsl) {
        ProcessModelDslValidator.validate(dsl);

        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId(dsl.getProcessCode());
        process.setName(dsl.getProcessName());
        process.setExecutable(true);
        bpmnModel.addProcess(process);

        Map<String, Integer> approvalNodeOrders = computeApprovalNodeOrders(dsl);
        Map<String, ExclusiveGateway> gatewayById = new HashMap<>();
        List<NodeAssigneeRuleDraft> assigneeRules = new ArrayList<>();

        for (ProcessNodeDsl node : dsl.getNodes()) {
            if (node instanceof StartNodeDsl) {
                StartEvent startEvent = new StartEvent();
                startEvent.setId(node.getId());
                startEvent.setName(node.getName());
                process.addFlowElement(startEvent);
            } else if (node instanceof EndNodeDsl) {
                EndEvent endEvent = new EndEvent();
                endEvent.setId(node.getId());
                endEvent.setName(node.getName());
                process.addFlowElement(endEvent);
            } else if (node instanceof ConditionNodeDsl) {
                ExclusiveGateway gateway = new ExclusiveGateway();
                gateway.setId(node.getId());
                gateway.setName(node.getName());
                process.addFlowElement(gateway);
                gatewayById.put(node.getId(), gateway);
            } else if (node instanceof ApprovalNodeDsl approval) {
                process.addFlowElement(buildApprovalUserTask(approval));
                assigneeRules.add(toRuleDraft(approval, approvalNodeOrders.getOrDefault(approval.getId(), 0)));
            }
        }

        int flowIndex = 0;
        for (EdgeDsl edge : dsl.getEdges()) {
            flowIndex++;
            SequenceFlow flow = new SequenceFlow(edge.getFrom(), edge.getTo());
            flow.setId("flow_" + flowIndex + "_" + edge.getFrom() + "_" + edge.getTo());
            if (edge.getCondition() != null) {
                flow.setConditionExpression(buildConditionExpression(edge.getCondition()));
            }
            process.addFlowElement(flow);
            linkSequenceFlow(process, flow);

            ExclusiveGateway sourceGateway = gatewayById.get(edge.getFrom());
            if (sourceGateway != null && edge.getCondition() == null) {
                sourceGateway.setDefaultFlow(flow.getId());
            }
        }

        List<String> engineErrors = processValidator.validate(bpmnModel).stream()
                .filter(error -> !error.isWarning())
                .map(WorkflowModelCompilerImpl::describe)
                .toList();
        if (!engineErrors.isEmpty()) {
            throw new WorkflowModelValidationException(engineErrors);
        }

        return new CompiledProcess(bpmnModel, assigneeRules);
    }

    /**
     * 构建审批节点对应的 {@link UserTask}：单人/候选组节点挂创建事件任务监听器；会签节点挂
     * 执行监听器（准备候选人集合变量）与创建/完成两个任务监听器，并附加
     * {@link MultiInstanceLoopCharacteristics}。
     */
    private UserTask buildApprovalUserTask(ApprovalNodeDsl approval) {
        UserTask userTask = new UserTask();
        userTask.setId(approval.getId());
        userTask.setName(approval.getName());

        ApprovalMode mode = approval.getApprovalMode() == null ? ApprovalMode.SINGLE : approval.getApprovalMode();
        if (mode == ApprovalMode.SINGLE) {
            userTask.setTaskListeners(List.of(buildListener("create", ASSIGNEE_TASK_LISTENER_CLASS)));
            return userTask;
        }

        userTask.setExecutionListeners(List.of(buildListener("start", MULTI_INSTANCE_EXECUTION_LISTENER_CLASS)));
        userTask.setTaskListeners(List.of(
                buildListener("create", MULTI_INSTANCE_TASK_LISTENER_CLASS),
                buildListener("complete", MULTI_INSTANCE_TASK_LISTENER_CLASS)));

        MultiInstanceLoopCharacteristics loopCharacteristics = new MultiInstanceLoopCharacteristics();
        loopCharacteristics.setSequential(false);
        // 对应 BPMN XML 的 flowable:collection 属性：该属性由 Flowable 的
        // MultiInstanceParser 解析为 inputDataItem 字段（一个流程变量名的直接引用），
        // 而不是 collectionString 字段——后者仅用于配合 flowable:class/
        // flowable:delegateExpression 的 CollectionHandler 场景，误用会触发 Flowable
        // ProcessValidator 的 flowable-multi-instance-missing-collection-parser 报错。
        loopCharacteristics.setInputDataItem(collectionVariableName(approval.getId()));
        loopCharacteristics.setElementVariable(MULTI_INSTANCE_ELEMENT_VARIABLE);
        loopCharacteristics.setCompletionCondition(
                MultiInstanceCompletionEvaluator.buildCompletionCondition(mode, approval.getApprovalPercent()));
        userTask.setLoopCharacteristics(loopCharacteristics);
        return userTask;
    }

    /**
     * 建立 {@link SequenceFlow} 与源/目标 {@link FlowNode} 之间的双向引用（补全
     * {@code incomingFlows}/{@code outgoingFlows}）。直接用对象模型 API 构建 BPMN 时不会
     * 像 XML 解析那样自动回填这两个便捷列表，但 Flowable 自带的 {@link ProcessValidator}
     * （如"排他网关必须有出边"校验）依赖这两个列表判断，遗漏会导致误报。
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
     * 会签节点候选人集合流程变量名，须与
     * {@code WorkflowMultiInstanceExecutionListener.collectionVariableName} 的真实实现
     * （{@code "approvers_" + nodeId}）保持完全一致。
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
     * 把 {@link ApprovalNodeDsl} 映射为 {@link NodeAssigneeRuleDraft}。
     */
    private NodeAssigneeRuleDraft toRuleDraft(ApprovalNodeDsl approval, int order) {
        return new NodeAssigneeRuleDraft(
                approval.getId(),
                approval.getName(),
                order,
                approval.getAssigneeType(),
                approval.getAssigneeValue(),
                approval.getApprovalMode() == null ? ApprovalMode.SINGLE : approval.getApprovalMode(),
                approval.getApprovalPercent(),
                approval.getEmptyAssigneeStrategy(),
                Boolean.TRUE.equals(approval.getAllowSelfApproval()),
                Boolean.TRUE.equals(approval.getAllowTransfer()),
                Boolean.TRUE.equals(approval.getAllowDelegate()),
                Boolean.TRUE.equals(approval.getAllowAddSign()),
                Boolean.TRUE.equals(approval.getAllowReturn()));
    }

    /**
     * 按 {@code EQ}/{@code NE}/{@code GT}/{@code GTE}/{@code LT}/{@code LTE} 白名单比较符 +
     * 字段 + 比较值拼装 UEL 条件表达式，不接受任何自由表达式字符串
     * （workflow-approval-engine change design.md Decision 10）。
     */
    private String buildConditionExpression(EdgeConditionDsl condition) {
        String symbol = OPERATOR_SYMBOLS.get(condition.getOperator());
        return "${" + condition.getField() + " " + symbol + " " + formatValue(condition.getValue()) + "}";
    }

    /**
     * 格式化比较值字面量：字符串加单引号，数字/布尔值原样输出。
     */
    private String formatValue(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        return "'" + String.valueOf(value).replace("'", "\\'") + "'";
    }

    /**
     * 按 BFS 从开始节点计算每个审批节点的顺序（第几个被访问到即第几级），供
     * {@code tab_wf_node_assignee_rule.node_order} 展示"第几级审批"使用。
     */
    private Map<String, Integer> computeApprovalNodeOrders(ProcessModelDsl dsl) {
        Map<String, ProcessNodeDsl> nodeById = new LinkedHashMap<>();
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (ProcessNodeDsl node : dsl.getNodes()) {
            nodeById.put(node.getId(), node);
            outgoing.put(node.getId(), new ArrayList<>());
        }
        for (EdgeDsl edge : dsl.getEdges()) {
            outgoing.computeIfAbsent(edge.getFrom(), key -> new ArrayList<>()).add(edge.getTo());
        }

        String startId = dsl.getNodes().stream()
                .filter(StartNodeDsl.class::isInstance)
                .map(ProcessNodeDsl::getId)
                .findFirst()
                .orElse(null);
        Map<String, Integer> order = new LinkedHashMap<>();
        if (startId == null) {
            return order;
        }

        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        visited.add(startId);
        int counter = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (nodeById.get(current) instanceof ApprovalNodeDsl) {
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
