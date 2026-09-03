package cn.nihility.rbac.workflow.designer.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.designer.dto.ApprovalNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ConditionNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.EndNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.StartNodeDsl;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.List;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.junit.jupiter.api.Test;

/**
 * {@link WorkflowModelCompilerImpl} 单元测试（workflow-approval-engine change tasks.md
 * 9.5）：覆盖"单人串行两级""两级含一个会签节点""含条件分支"三种典型 DSL 的编译结果，以及
 * "孤立节点""条件分支缺默认边"两种结构校验失败场景。
 */
class WorkflowModelCompilerImplTest {

    /** 待测编译器实例。 */
    private final WorkflowModelCompilerImpl compiler = new WorkflowModelCompilerImpl();

    /**
     * 单人串行两级：等价于 Flyway 预置的 MASTER_DATA_APPROVAL 默认流程（部门负责人 ->
     * 安全管理员，均为 SINGLE 模式）。
     */
    @Test
    void compile_shouldBuildTwoSequentialSingleApprovalNodes() {
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("MASTER_DATA_APPROVAL")
                .processName("主数据变更审批流程")
                .nodes(List.of(
                        startNode("start"),
                        approvalNode("deptLeaderApprove", "部门负责人审批", AssigneeType.ORG_LEADER, "DEPT_LEADER",
                                ApprovalMode.SINGLE, null),
                        approvalNode("securityAdminApprove", "安全管理员审批", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("deptLeaderApprove").build(),
                        EdgeDsl.builder().from("deptLeaderApprove").to("securityAdminApprove").build(),
                        EdgeDsl.builder().from("securityAdminApprove").to("end").build()))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        BpmnModel bpmnModel = compiled.bpmnModel();
        Process process = bpmnModel.getMainProcess();
        assertThat(process.getId()).isEqualTo("MASTER_DATA_APPROVAL");
        assertThat(elementsOf(process, StartEvent.class)).hasSize(1);
        assertThat(elementsOf(process, EndEvent.class)).hasSize(1);
        assertThat(elementsOf(process, UserTask.class)).hasSize(2);
        assertThat(elementsOf(process, ExclusiveGateway.class)).isEmpty();
        assertThat(elementsOf(process, SequenceFlow.class)).hasSize(3);

        for (UserTask userTask : elementsOf(process, UserTask.class)) {
            assertThat(userTask.getTaskListeners()).hasSize(1);
            assertThat(userTask.getTaskListeners().get(0).getEvent()).isEqualTo("create");
            assertThat(userTask.getTaskListeners().get(0).getImplementation())
                    .isEqualTo("cn.nihility.rbac.workflow.engine.flowable.WorkflowAssigneeTaskListener");
            assertThat(userTask.hasMultiInstanceLoopCharacteristics()).isFalse();
        }

        assertThat(compiled.assigneeRules()).hasSize(2);
        NodeAssigneeRuleDraft deptRule = ruleOf(compiled, "deptLeaderApprove");
        assertThat(deptRule.assigneeType()).isEqualTo(AssigneeType.ORG_LEADER);
        assertThat(deptRule.nodeOrder()).isEqualTo(1);
        NodeAssigneeRuleDraft securityRule = ruleOf(compiled, "securityAdminApprove");
        assertThat(securityRule.assigneeType()).isEqualTo(AssigneeType.ROLE);
        assertThat(securityRule.nodeOrder()).isEqualTo(2);
    }

    /**
     * 两级审批，其中第二级是会签节点（OR 模式）：应附加多实例特性与专属监听器。
     */
    @Test
    void compile_shouldAttachMultiInstanceCharacteristicsForNonSingleApprovalMode() {
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("MULTI_LEVEL_WITH_COUNTERSIGN")
                .processName("含会签的两级审批")
                .nodes(List.of(
                        startNode("start"),
                        approvalNode("deptLeaderApprove", "部门负责人审批", AssigneeType.ORG_LEADER, "DEPT_LEADER",
                                ApprovalMode.SINGLE, null),
                        approvalNode("securityAdminApprove", "安全管理员会签", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.OR, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("deptLeaderApprove").build(),
                        EdgeDsl.builder().from("deptLeaderApprove").to("securityAdminApprove").build(),
                        EdgeDsl.builder().from("securityAdminApprove").to("end").build()))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        Process process = compiled.bpmnModel().getMainProcess();
        UserTask countersignTask = (UserTask) process.getFlowElement("securityAdminApprove");
        assertThat(countersignTask.hasMultiInstanceLoopCharacteristics()).isTrue();
        assertThat(countersignTask.getLoopCharacteristics().getInputDataItem())
                .isEqualTo("approvers_securityAdminApprove");
        assertThat(countersignTask.getLoopCharacteristics().getElementVariable()).isEqualTo("approver");
        assertThat(countersignTask.getLoopCharacteristics().getCompletionCondition())
                .isEqualTo("${miVeto == true || (nrOfCompletedInstances >= 1)}");
        assertThat(countersignTask.getExecutionListeners()).hasSize(1);
        assertThat(countersignTask.getExecutionListeners().get(0).getImplementation())
                .isEqualTo("cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceExecutionListener");
        assertThat(countersignTask.getTaskListeners()).hasSize(2);
        assertThat(countersignTask.getTaskListeners().get(0).getImplementation())
                .isEqualTo("cn.nihility.rbac.workflow.engine.flowable.WorkflowMultiInstanceTaskListener");

        UserTask deptTask = (UserTask) process.getFlowElement("deptLeaderApprove");
        assertThat(deptTask.hasMultiInstanceLoopCharacteristics()).isFalse();
    }

    /**
     * 含条件分支且携带默认兜底边：应生成排他网关，携带条件的边设置条件表达式，未携带条件的
     * 边被标记为网关的默认流。
     */
    @Test
    void compile_shouldBuildExclusiveGatewayWithConditionAndDefaultFlow() {
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("CONDITION_BRANCH_PROCESS")
                .processName("含条件分支的审批流程")
                .nodes(List.of(
                        startNode("start"),
                        conditionNode("amountGateway"),
                        approvalNode("highAmountApprove", "大额审批", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("lowAmountApprove", "常规审批", AssigneeType.ORG_LEADER, "DEPT_LEADER",
                                ApprovalMode.SINGLE, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("amountGateway").build(),
                        EdgeDsl.builder().from("amountGateway").to("highAmountApprove")
                                .condition(EdgeConditionDsl.builder().field("amount").operator("GT").value(1000).build())
                                .build(),
                        EdgeDsl.builder().from("amountGateway").to("lowAmountApprove").build(),
                        EdgeDsl.builder().from("highAmountApprove").to("end").build(),
                        EdgeDsl.builder().from("lowAmountApprove").to("end").build()))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        Process process = compiled.bpmnModel().getMainProcess();
        assertThat(elementsOf(process, ExclusiveGateway.class)).hasSize(1);
        ExclusiveGateway gateway = elementsOf(process, ExclusiveGateway.class).get(0);
        assertThat(gateway.getId()).isEqualTo("amountGateway");
        assertThat(gateway.getDefaultFlow()).isNotBlank();

        SequenceFlow defaultFlow = (SequenceFlow) process.getFlowElement(gateway.getDefaultFlow());
        assertThat(defaultFlow.getTargetRef()).isEqualTo("lowAmountApprove");
        assertThat(defaultFlow.getConditionExpression()).isNull();

        SequenceFlow conditionalFlow = elementsOf(process, SequenceFlow.class).stream()
                .filter(flow -> "highAmountApprove".equals(flow.getTargetRef()))
                .findFirst()
                .orElseThrow();
        assertThat(conditionalFlow.getConditionExpression()).isEqualTo("${amount > 1000}");
    }

    /**
     * 存在孤立审批节点（没有任何连线指向、也不是开始节点）时应拒绝编译。
     */
    @Test
    void compile_shouldRejectIsolatedNode() {
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("ISOLATED_NODE_PROCESS")
                .processName("含孤立节点的流程")
                .nodes(List.of(
                        startNode("start"),
                        approvalNode("mainApprove", "主审批", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("isolatedApprove", "孤立节点", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("mainApprove").build(),
                        EdgeDsl.builder().from("mainApprove").to("end").build()))
                .build();

        assertThatThrownBy(() -> compiler.compile(dsl))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("isolatedApprove");
    }

    /**
     * 条件节点的多条出边都携带条件、缺少兜底默认分支时应拒绝编译。
     */
    @Test
    void compile_shouldRejectConditionNodeWithoutDefaultBranch() {
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("MISSING_DEFAULT_BRANCH_PROCESS")
                .processName("条件分支缺默认边的流程")
                .nodes(List.of(
                        startNode("start"),
                        conditionNode("gateway"),
                        approvalNode("branchA", "分支 A", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("branchB", "分支 B", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("branchA")
                                .condition(EdgeConditionDsl.builder().field("amount").operator("GT").value(1000).build())
                                .build(),
                        EdgeDsl.builder().from("gateway").to("branchB")
                                .condition(EdgeConditionDsl.builder().field("amount").operator("LTE").value(1000).build())
                                .build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build()))
                .build();

        assertThatThrownBy(() -> compiler.compile(dsl))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("缺少默认分支");
    }

    private StartNodeDsl startNode(String id) {
        StartNodeDsl node = new StartNodeDsl();
        node.setId(id);
        node.setType("START");
        return node;
    }

    private EndNodeDsl endNode(String id) {
        EndNodeDsl node = new EndNodeDsl();
        node.setId(id);
        node.setType("END");
        return node;
    }

    private ConditionNodeDsl conditionNode(String id) {
        ConditionNodeDsl node = new ConditionNodeDsl();
        node.setId(id);
        node.setType("CONDITION");
        return node;
    }

    private ApprovalNodeDsl approvalNode(
            String id,
            String name,
            AssigneeType assigneeType,
            String assigneeValue,
            ApprovalMode approvalMode,
            Integer approvalPercent) {
        ApprovalNodeDsl node = new ApprovalNodeDsl();
        node.setId(id);
        node.setType("APPROVAL");
        node.setName(name);
        node.setAssigneeType(assigneeType);
        node.setAssigneeValue(assigneeValue);
        node.setApprovalMode(approvalMode);
        node.setApprovalPercent(approvalPercent);
        node.setEmptyAssigneeStrategy(EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        node.setAllowSelfApproval(false);
        node.setAllowTransfer(true);
        node.setAllowDelegate(true);
        node.setAllowAddSign(false);
        node.setAllowReturn(false);
        return node;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> elementsOf(Process process, Class<T> type) {
        return process.getFlowElements().stream()
                .filter(type::isInstance)
                .map(element -> (T) element)
                .toList();
    }

    private NodeAssigneeRuleDraft ruleOf(CompiledProcess compiled, String nodeId) {
        return compiled.assigneeRules().stream()
                .filter(rule -> rule.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow();
    }
}
