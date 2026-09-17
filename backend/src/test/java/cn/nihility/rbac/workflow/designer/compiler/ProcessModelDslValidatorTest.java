package cn.nihility.rbac.workflow.designer.compiler;

import static org.assertj.core.api.Assertions.assertThatCode;
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
import cn.nihility.rbac.workflow.designer.dto.StartNodeDsl;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ProcessModelDslValidator} 单元测试（workflow-condition-payload-fields change
 * tasks.md 5.2）：覆盖条件字段须真实存在、拒绝多选字典字段、拒绝文本/字典下拉字段配置
 * 非 EQ/NE 比较符、数字/日期字段允许全部比较符四类场景。条件节点缺少默认分支不再是本校验器
 * 的拒绝发布项——编译期由 {@code WorkflowModelCompilerImpl} 自动补全
 * （workflow-condition-auto-default-branch change design.md Decision 1），相关用例见
 * {@link WorkflowModelCompilerImplTest}。
 */
class ProcessModelDslValidatorTest {

    /** 表单字段定义服务的桩。 */
    private FormFieldDefinitionService formFieldDefinitionService;

    /** 待测校验器实例。 */
    private ProcessModelDslValidator validator;

    /** 构造被测校验器与桩。 */
    @BeforeEach
    void setUp() {
        formFieldDefinitionService = mock(FormFieldDefinitionService.class);
        validator = new ProcessModelDslValidator(formFieldDefinitionService);
    }

    /** 条件字段真实存在且控件类型允许该比较符时应校验通过。 */
    @Test
    void validate_shouldPass_whenConditionFieldExistsAndOperatorAllowed() {
        stubField("ORG", "riskLevel", 3);

        assertThatCode(() -> validator.validate(dslWithCondition(condition("ORG", "riskLevel", "EQ", "HIGH"))))
                .doesNotThrowAnyException();
    }

    /** 引用不存在的字段（未出现在对应业务类型启用字段定义中）时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenFieldNotFoundInBizType() {
        stubField("ORG", "riskLevel", 3);

        assertThatThrownBy(() -> validator.validate(dslWithCondition(condition("ORG", "notExists", "EQ", "HIGH"))))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("不存在于该业务类型的启用表单字段定义中");
    }

    /** 引用多选字典类型字段作为条件字段时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenFieldIsMultiDict() {
        stubField("ORG", "tags", 5);

        assertThatThrownBy(() -> validator.validate(dslWithCondition(condition("ORG", "tags", "EQ", "A"))))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("多选字典类型，不能作为条件字段");
    }

    /** 文本框字段配置非 EQ/NE 比较符时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenTextFieldUsesNonEqualOperator() {
        stubField("ORG", "remark", 1);

        assertThatThrownBy(() -> validator.validate(dslWithCondition(condition("ORG", "remark", "GT", "A"))))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("比较符仅允许 EQ/NE");
    }

    /** 字典下拉字段配置非 EQ/NE 比较符时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenDictFieldUsesNonEqualOperator() {
        stubField("ORG", "riskLevel", 3);

        assertThatThrownBy(() -> validator.validate(dslWithCondition(condition("ORG", "riskLevel", "LTE", "HIGH"))))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("比较符仅允许 EQ/NE");
    }

    /** 数字框字段允许全部六种比较符。 */
    @Test
    void validate_shouldPass_whenNumberFieldUsesAnyAllowedOperator() {
        stubField("ORG", "amount", 2);

        assertThatCode(() -> validator.validate(dslWithCondition(condition("ORG", "amount", "GTE", 100))))
                .doesNotThrowAnyException();
    }

    /** 日期字段允许全部六种比较符。 */
    @Test
    void validate_shouldPass_whenDateFieldUsesAnyAllowedOperator() {
        stubField("ORG", "hireDate", 4);

        assertThatCode(() -> validator.validate(dslWithCondition(condition("ORG", "hireDate", "LT", "2026-01-01"))))
                .doesNotThrowAnyException();
    }

    /** 条件字段所属业务对象类型不在 ORG/USER/POSITION/APP 白名单内时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenFieldBizTypeNotAllowed() {
        assertThatThrownBy(() -> validator.validate(dslWithCondition(condition("UNKNOWN", "amount", "EQ", 1))))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("fieldBizType 不合法");
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

    /**
     * 构造一个只含"网关 -> 分支节点（携带条件）/默认分支节点"的最小含条件分支 DSL。
     */
    private ProcessModelDsl dslWithCondition(EdgeConditionDsl condition) {
        StartNodeDsl start = new StartNodeDsl();
        start.setId("start");
        start.setType("START");

        ConditionNodeDsl gateway = new ConditionNodeDsl();
        gateway.setId("gateway");
        gateway.setType("CONDITION");

        ApprovalNodeDsl branchA = approvalNode("branchA", AssigneeType.ROLE, "SECURITY_ADMIN");
        ApprovalNodeDsl branchB = approvalNode("branchB", AssigneeType.ORG_LEADER, "DEPT_LEADER");

        EndNodeDsl end = new EndNodeDsl();
        end.setId("end");
        end.setType("END");

        return ProcessModelDsl.builder()
                .processCode("CONDITION_VALIDATOR_TEST")
                .processName("条件校验测试流程")
                .nodes(List.of(start, gateway, branchA, branchB, end))
                .edges(List.of(
                        EdgeDsl.builder().from("start").to("gateway").build(),
                        EdgeDsl.builder().from("gateway").to("branchA").condition(condition).build(),
                        EdgeDsl.builder().from("gateway").to("branchB").build(),
                        EdgeDsl.builder().from("branchA").to("end").build(),
                        EdgeDsl.builder().from("branchB").to("end").build()))
                .build();
    }

    /** 构造一个字段完整的审批节点。 */
    private ApprovalNodeDsl approvalNode(String id, AssigneeType assigneeType, String assigneeValue) {
        ApprovalNodeDsl node = new ApprovalNodeDsl();
        node.setId(id);
        node.setType("APPROVAL");
        node.setName(id);
        node.setAssigneeType(assigneeType);
        node.setAssigneeValue(assigneeValue);
        node.setApprovalMode(ApprovalMode.SINGLE);
        node.setEmptyAssigneeStrategy(EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        node.setAllowSelfApproval(false);
        node.setAllowTransfer(true);
        node.setAllowDelegate(true);
        node.setAllowAddSign(false);
        node.setAllowReturn(false);
        return node;
    }
}
