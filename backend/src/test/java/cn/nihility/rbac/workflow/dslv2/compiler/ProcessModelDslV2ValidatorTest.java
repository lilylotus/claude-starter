package cn.nihility.rbac.workflow.dslv2.compiler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.exception.WorkflowModelValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ProcessModelDslV2Validator} 单元测试，聚焦 production-approval-lifecycle change
 * tasks.md 5.3 新增的两项校验：{@code ORG_LEADER} 固定目标组织（{@code orgSource=FIXED_ORG}）
 * 与 {@code PREVIOUS_APPROVER} 并行汇合后多来源必须显式指定 {@code sourceNodeId}。
 */
class ProcessModelDslV2ValidatorTest {

    /** 构造一个 start -> approval -> end 的最小合法骨架，approval 节点的 assignee 由调用方定制。 */
    private ProcessModelDslV2 buildDsl(AssigneeConfigDsl assignee) {
        StartNodeDslV2 start = new StartNodeDslV2();
        start.setId("start");
        start.setType("START");

        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId("leader");
        approval.setType("APPROVAL");
        approval.setName("负责人审批");
        approval.setAssignee(assignee);
        approval.setEmptyPolicy(EmptyPolicy.BLOCK);

        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId("end");
        end.setType("END");
        end.setOutcome("APPROVED");

        List<ProcessNodeDslV2> nodes = List.of(start, approval, end);
        List<EdgeDslV2> edges = List.of(
                EdgeDslV2.builder().id("e1").source("start").target("leader").build(),
                EdgeDslV2.builder().id("e2").source("leader").target("end").build());

        return ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("TEST_PROCESS")
                .processName("测试流程")
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    private AssigneeConfigDsl orgLeaderAssignee(String orgSource, Long orgId) {
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.ORG_LEADER);
        assignee.setValue("DEPT_LEADER");
        assignee.setOrgSource(orgSource);
        assignee.setOrgId(orgId);
        return assignee;
    }

    /** orgSource=FIXED_ORG 但未配置 orgId 时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenFixedOrgSourceMissingOrgId() {
        ProcessModelDslV2 dsl = buildDsl(orgLeaderAssignee("FIXED_ORG", null));

        assertThatThrownBy(() -> ProcessModelDslV2Validator.validate(dsl, orgId -> true))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("必须配置 assignee.orgId");
    }

    /** orgSource=FIXED_ORG 且 orgId 指向不存在/未启用的组织时应拒绝发布。 */
    @Test
    void validate_shouldReject_whenFixedOrgSourceTargetOrgNotExistsOrDisabled() {
        ProcessModelDslV2 dsl = buildDsl(orgLeaderAssignee("FIXED_ORG", 999L));

        assertThatThrownBy(() -> ProcessModelDslV2Validator.validate(dsl, orgId -> false))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("对应组织不存在或未启用");
    }

    /** orgSource=FIXED_ORG 且目标组织真实存在启用时应通过校验，不抛异常。 */
    @Test
    void validate_shouldPass_whenFixedOrgSourceTargetOrgExistsAndEnabled() {
        ProcessModelDslV2 dsl = buildDsl(orgLeaderAssignee("FIXED_ORG", 999L));

        assertThat(catchException(() -> ProcessModelDslV2Validator.validate(dsl, orgId -> orgId.equals(999L))))
                .isNull();
    }

    /** orgSource 未配置（默认 APPLICANT_SNAPSHOT 行为）时不触发 FIXED_ORG 相关校验。 */
    @Test
    void validate_shouldPass_whenOrgSourceNotConfigured() {
        ProcessModelDslV2 dsl = buildDsl(orgLeaderAssignee(null, null));

        assertThat(catchException(() -> ProcessModelDslV2Validator.validate(dsl))).isNull();
    }

    /** PREVIOUS_APPROVER 来源在存在多个直接入边（并行汇合后）且未指定 sourceNodeId 时应拒绝。 */
    @Test
    void validate_shouldReject_whenPreviousApproverAmbiguousWithoutSourceNodeId() {
        StartNodeDslV2 start = new StartNodeDslV2();
        start.setId("start");
        start.setType("START");

        ApprovalNodeDslV2 branchA = new ApprovalNodeDslV2();
        branchA.setId("a");
        branchA.setType("APPROVAL");
        branchA.setName("分支A");
        branchA.setAssignee(userAssignee("100"));
        branchA.setEmptyPolicy(EmptyPolicy.BLOCK);

        ApprovalNodeDslV2 branchB = new ApprovalNodeDslV2();
        branchB.setId("b");
        branchB.setType("APPROVAL");
        branchB.setName("分支B");
        branchB.setAssignee(userAssignee("200"));
        branchB.setEmptyPolicy(EmptyPolicy.BLOCK);

        AssigneeConfigDsl previousApprover = new AssigneeConfigDsl();
        previousApprover.setType(AssigneeTypeV2.PREVIOUS_APPROVER);

        ApprovalNodeDslV2 merged = new ApprovalNodeDslV2();
        merged.setId("merged");
        merged.setType("APPROVAL");
        merged.setName("汇合后节点");
        merged.setAssignee(previousApprover);
        merged.setEmptyPolicy(EmptyPolicy.BLOCK);

        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId("end");
        end.setType("END");
        end.setOutcome("APPROVED");

        List<ProcessNodeDslV2> nodes = List.of(start, branchA, branchB, merged, end);
        List<EdgeDslV2> edges = List.of(
                EdgeDslV2.builder().id("e1").source("start").target("a").build(),
                EdgeDslV2.builder().id("e2").source("start").target("b").build(),
                EdgeDslV2.builder().id("e3").source("a").target("merged").build(),
                EdgeDslV2.builder().id("e4").source("b").target("merged").build(),
                EdgeDslV2.builder().id("e5").source("merged").target("end").build());
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("TEST_PROCESS_2")
                .processName("测试流程2")
                .nodes(nodes)
                .edges(edges)
                .build();

        assertThatThrownBy(() -> ProcessModelDslV2Validator.validate(dsl))
                .isInstanceOf(WorkflowModelValidationException.class)
                .hasMessageContaining("必须显式指定 assignee.sourceNodeId");
    }

    private AssigneeConfigDsl userAssignee(String value) {
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue(value);
        return assignee;
    }

    /** 捕获 lambda 执行过程中抛出的异常，未抛出时返回 {@code null}。 */
    private Throwable catchException(org.junit.jupiter.api.function.Executable executable) {
        try {
            executable.execute();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }
}
