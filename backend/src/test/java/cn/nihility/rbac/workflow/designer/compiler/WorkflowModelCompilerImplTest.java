package cn.nihility.rbac.workflow.designer.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.formfield.dto.FormFieldRenderItemVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.workflow.constant.ApprovalMode;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.designer.dto.ApprovalNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ConditionNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeConditionDsl;
import cn.nihility.rbac.workflow.designer.dto.EdgeDsl;
import cn.nihility.rbac.workflow.designer.dto.EndNodeDsl;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelDsl;
import cn.nihility.rbac.workflow.designer.dto.RouteFieldCode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link WorkflowModelCompilerImpl} 单元测试（workflow-approval-engine change tasks.md
 * 9.5；workflow-condition-payload-fields change tasks.md 5.1；
 * workflow-condition-auto-default-branch change tasks.md 2.2）：覆盖"单人串行两级""两级含
 * 一个会签节点""含条件分支"三种典型 DSL 的编译结果，条件分支引用真实表单字段的 null 安全
 * 表达式生成（含数字/日期比较值转换）、路由字段清单的收集去重，"孤立节点"结构校验失败场景，
 * 以及条件节点缺少默认分支时编译期自动补全共享结束节点/兜底边、使用者已手动配置默认分支时
 * 不触发自动补全三类场景。
 */
class WorkflowModelCompilerImplTest {

    /** 表单字段定义服务的桩，条件分支引用的字段元数据从这里查询。 */
    private FormFieldDefinitionService formFieldDefinitionService;

    /** 待测编译器实例。 */
    private WorkflowModelCompilerImpl compiler;

    /** 构造被测编译器与桩。 */
    @BeforeEach
    void setUp() {
        formFieldDefinitionService = mock(FormFieldDefinitionService.class);
        ProcessModelDslValidator validator = new ProcessModelDslValidator(formFieldDefinitionService);
        compiler = new WorkflowModelCompilerImpl(validator, formFieldDefinitionService);
    }

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
        assertThat(compiled.routeFieldCodes()).isEmpty();

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
     * 含条件分支（数字字段）且携带默认兜底边：应生成排他网关，携带条件的边设置 null 安全的
     * 数字比较表达式，未携带条件的边被标记为网关的默认流，路由字段清单去重收集
     * （workflow-condition-payload-fields change design.md Decision 1/2/3/5）。
     */
    @Test
    void compile_shouldBuildExclusiveGatewayWithNullSafeNumberConditionAndDefaultFlow() {
        stubField("ORG", "amount", 2);
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
                                .condition(condition("ORG", "amount", "GT", 1000))
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
        assertThat(conditionalFlow.getConditionExpression())
                .isEqualTo("${(ORG_amount != null) && (ORG_amount > 1000)}");

        assertThat(compiled.routeFieldCodes()).containsExactly(new RouteFieldCode("ORG", "amount"));
    }

    /**
     * 日期字段条件应把比较值编译时转换为 epoch day 数字字面量嵌入表达式，两边同为数值比较
     * （workflow-condition-payload-fields change design.md Decision 3）。
     */
    @Test
    void compile_shouldConvertDateConditionValueToEpochDayLiteral() {
        stubField("ORG", "hireDate", 4);
        ProcessModelDsl dsl = conditionBranchDsl(condition("ORG", "hireDate", "GT", "2026-01-01"));

        CompiledProcess compiled = compiler.compile(dsl);

        SequenceFlow conditionalFlow = conditionalFlowOf(compiled);
        long expectedEpochDay = java.time.LocalDate.of(2026, 1, 1).toEpochDay();
        assertThat(conditionalFlow.getConditionExpression())
                .isEqualTo("${(ORG_hireDate != null) && (ORG_hireDate > " + expectedEpochDay + ")}");
    }

    /**
     * 文本框/字典下拉字段条件的比较值应按字符串字面量加单引号嵌入表达式，沿用既有
     * {@code formatValue} 字符串分支逻辑。
     */
    @Test
    void compile_shouldQuoteStringConditionValueForTextField() {
        stubField("ORG", "riskLevel", 3);
        ProcessModelDsl dsl = conditionBranchDsl(condition("ORG", "riskLevel", "EQ", "HIGH"));

        CompiledProcess compiled = compiler.compile(dsl);

        SequenceFlow conditionalFlow = conditionalFlowOf(compiled);
        assertThat(conditionalFlow.getConditionExpression())
                .isEqualTo("${(ORG_riskLevel != null) && (ORG_riskLevel == 'HIGH')}");
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
     * 条件节点的多条出边都携带条件、缺少兜底默认分支时，编译期应自动补入一个共享的结束节点
     * 与一条无条件兜底边，生成的排他网关应带有 {@code defaultFlow}，指向的连线不携带条件表达式
     * （workflow-condition-auto-default-branch change design.md Decision 1/2）。
     */
    @Test
    void compile_shouldAutoAugmentDefaultBranch_whenConditionNodeMissingDefaultEdge() {
        stubField("ORG", "amount", 2);
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
                .edges(new java.util.ArrayList<>(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("branchA")
                                .condition(condition("ORG", "amount", "GT", 1000))
                                .build(),
                        EdgeDsl.builder().from("gateway").to("branchB")
                                .condition(condition("ORG", "amount", "LTE", 1000))
                                .build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build())))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        Process process = compiled.bpmnModel().getMainProcess();
        assertThat(elementsOf(process, ExclusiveGateway.class)).hasSize(1);
        ExclusiveGateway gateway = elementsOf(process, ExclusiveGateway.class).get(0);
        assertThat(gateway.getDefaultFlow()).isNotBlank();

        SequenceFlow defaultFlow = (SequenceFlow) process.getFlowElement(gateway.getDefaultFlow());
        assertThat(defaultFlow.getConditionExpression()).isNull();
        assertThat(defaultFlow.getSourceRef()).isEqualTo("gateway");

        // 自动补全的结束节点也应体现在原地修改后的 dsl 上，供 publishV1() 落库快照。
        assertThat(dsl.getNodes()).anyMatch(node -> node instanceof EndNodeDsl && node.getId().equals(defaultFlow.getTargetRef()));
        assertThat(dsl.getEdges()).anyMatch(edge -> "gateway".equals(edge.getFrom())
                && edge.getTo().equals(defaultFlow.getTargetRef()) && edge.getCondition() == null);
        compiler.compile(dsl);
        assertThat(dsl.getNodes()).hasSize(6);
        assertThat(dsl.getEdges()).hasSize(6);
    }

    /** 用户节点占用自动结束节点的候选标识时，应递增后缀并保持用户节点不变。 */
    @Test
    void compile_shouldAvoidExistingAutoEndIds() {
        stubField("ORG", "amount", 2);
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("AUTO_END_ID_COLLISION")
                .processName("自动节点标识冲突")
                .nodes(List.of(startNode("start"), conditionNode("gateway"),
                        endNode("__auto_approved_end__"), endNode("__auto_approved_end___2")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("__auto_approved_end__")
                                .condition(condition("ORG", "amount", "GT", 1000)).build(),
                        EdgeDsl.builder().from("gateway").to("__auto_approved_end___2")
                                .condition(condition("ORG", "amount", "LT", 0)).build()))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        Process process = compiled.bpmnModel().getMainProcess();
        ExclusiveGateway gateway = (ExclusiveGateway) process.getFlowElement("gateway");
        SequenceFlow defaultFlow = (SequenceFlow) process.getFlowElement(gateway.getDefaultFlow());
        assertThat(defaultFlow.getTargetRef()).isNotIn("__auto_approved_end__", "__auto_approved_end___2");
        assertThat(dsl.getNodes()).hasSize(5).extracting("id").doesNotHaveDuplicates();
        assertThat(elementsOf(process, EndEvent.class)).hasSize(3);
    }

    /**
     * 多个条件节点都缺少默认分支时，应共享同一个自动生成的结束节点，不重复创建
     * （workflow-condition-auto-default-branch change design.md Decision 2）。
     */
    @Test
    void compile_shouldShareSingleAutoEndNode_whenMultipleConditionNodesMissingDefault() {
        stubField("ORG", "amount", 2);
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("MULTI_MISSING_DEFAULT_BRANCH_PROCESS")
                .processName("多条件分支均缺默认边的流程")
                .nodes(new java.util.ArrayList<>(List.of(
                        startNode("start"),
                        conditionNode("gatewayA"),
                        conditionNode("gatewayB"),
                        approvalNode("branchA", "分支 A", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("branchB", "分支 B", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        endNode("end"))))
                .edges(new java.util.ArrayList<>(List.of(
                        EdgeDsl.builder().from("start").to("gatewayA").build(),
                        EdgeDsl.builder().from("gatewayA").to("branchA")
                                .condition(condition("ORG", "amount", "GT", 1000))
                                .build(),
                        EdgeDsl.builder().from("gatewayA").to("gatewayB")
                                .condition(condition("ORG", "amount", "LTE", 1000))
                                .build(),
                        EdgeDsl.builder().from("gatewayB").to("branchB")
                                .condition(condition("ORG", "amount", "EQ", 500))
                                .build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build())))
                .build();

        CompiledProcess compiled = compiler.compile(dsl);

        long autoEndNodeCount = dsl.getNodes().stream()
                .filter(node -> node instanceof EndNodeDsl)
                .filter(node -> !"end".equals(node.getId()))
                .count();
        assertThat(autoEndNodeCount).isEqualTo(1);

        Process process = compiled.bpmnModel().getMainProcess();
        List<ExclusiveGateway> gateways = elementsOf(process, ExclusiveGateway.class);
        assertThat(gateways).hasSize(2);
        String targetA = ((SequenceFlow) process.getFlowElement(
                gateways.stream().filter(g -> "gatewayA".equals(g.getId())).findFirst().orElseThrow().getDefaultFlow()))
                .getTargetRef();
        String targetB = ((SequenceFlow) process.getFlowElement(
                gateways.stream().filter(g -> "gatewayB".equals(g.getId())).findFirst().orElseThrow().getDefaultFlow()))
                .getTargetRef();
        assertThat(targetA).isEqualTo(targetB);
    }

    /**
     * 使用者已手动配置默认分支（无论指向哪里）时，不应触发自动补全，编译后 {@code dsl} 的节点/
     * 边数量应保持不变（workflow-condition-auto-default-branch change design.md
     * "使用者手动配置的默认分支优先生效"）。
     */
    @Test
    void compile_shouldNotAugment_whenConditionNodeAlreadyHasManualDefaultBranch() {
        stubField("ORG", "amount", 2);
        ProcessModelDsl dsl = ProcessModelDsl.builder()
                .processCode("MANUAL_DEFAULT_BRANCH_PROCESS")
                .processName("已手动配置默认分支的流程")
                .nodes(new java.util.ArrayList<>(List.of(
                        startNode("start"),
                        conditionNode("gateway"),
                        approvalNode("branchA", "分支 A", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("branchB", "分支 B（人工默认分支）", AssigneeType.ORG_LEADER, "DEPT_LEADER",
                                ApprovalMode.SINGLE, null),
                        endNode("end"))))
                .edges(new java.util.ArrayList<>(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("branchA")
                                .condition(condition("ORG", "amount", "GT", 1000))
                                .build(),
                        EdgeDsl.builder().from("gateway").to("branchB").build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build())))
                .build();
        int originalNodeCount = dsl.getNodes().size();
        int originalEdgeCount = dsl.getEdges().size();

        CompiledProcess compiled = compiler.compile(dsl);

        assertThat(dsl.getNodes()).hasSize(originalNodeCount);
        assertThat(dsl.getEdges()).hasSize(originalEdgeCount);

        Process process = compiled.bpmnModel().getMainProcess();
        ExclusiveGateway gateway = elementsOf(process, ExclusiveGateway.class).get(0);
        SequenceFlow defaultFlow = (SequenceFlow) process.getFlowElement(gateway.getDefaultFlow());
        assertThat(defaultFlow.getTargetRef()).isEqualTo("branchB");
    }

    /**
     * 构造一个只含"网关 -> 分支节点（携带条件）/默认分支节点"的最小含条件分支 DSL，供仅关注
     * 条件表达式编译结果的测试用例复用。
     */
    private ProcessModelDsl conditionBranchDsl(EdgeConditionDsl condition) {
        return ProcessModelDsl.builder()
                .processCode("CONDITION_VALUE_PROCESS")
                .processName("条件比较值编译测试流程")
                .nodes(List.of(
                        startNode("start"),
                        conditionNode("gateway"),
                        approvalNode("branchA", "分支 A", AssigneeType.ROLE, "SECURITY_ADMIN",
                                ApprovalMode.SINGLE, null),
                        approvalNode("branchB", "分支 B", AssigneeType.ORG_LEADER, "DEPT_LEADER",
                                ApprovalMode.SINGLE, null),
                        endNode("end")))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("branchA").condition(condition).build(),
                        EdgeDsl.builder().from("gateway").to("branchB").build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build()))
                .build();
    }

    /** 从编译产物中取出唯一携带条件表达式的连线。 */
    private SequenceFlow conditionalFlowOf(CompiledProcess compiled) {
        Process process = compiled.bpmnModel().getMainProcess();
        return elementsOf(process, SequenceFlow.class).stream()
                .filter(flow -> flow.getConditionExpression() != null)
                .findFirst()
                .orElseThrow();
    }

    /** 桩定 {@link FormFieldDefinitionService#buildRenderSchema} 返回携带指定字段的渲染元数据。 */
    private void stubField(String bizType, String fieldCode, int controlType) {
        when(formFieldDefinitionService.buildRenderSchema(eq(bizType))).thenReturn(List.of(
                FormFieldRenderItemVO.builder().fieldCode(fieldCode).controlType(controlType).build()));
    }

    /** 构造条件 DSL。 */
    private EdgeConditionDsl condition(String fieldBizType, String field, String operator, Object value) {
        return EdgeConditionDsl.builder().fieldBizType(fieldBizType).field(field).operator(operator).value(value).build();
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
