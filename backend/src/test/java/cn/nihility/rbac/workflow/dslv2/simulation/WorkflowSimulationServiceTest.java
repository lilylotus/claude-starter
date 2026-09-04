package cn.nihility.rbac.workflow.dslv2.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionLogic;
import cn.nihility.rbac.workflow.dslv2.constant.ConditionOperator;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionAstDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionItemDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ConditionNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelJoinNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ParallelSplitNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessModelMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link WorkflowSimulationService} 单元测试（production-approval-lifecycle change design.md
 * 第 4 节"试运行分两层"第一层，tasks.md 4.2）：条件分支决策、审批人解析、并行分叉两条分支
 * 均展开、空审批人显式标注、拒绝非 v2 草稿五个核心场景，全部脱离真实 Flowable 引擎与数据库。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowSimulationServiceTest {

    @Mock
    private ProcessModelMapper processModelMapper;

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private AssigneeResolverRegistry assigneeResolverRegistry;

    private WorkflowSimulationService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowSimulationService(processModelMapper, processDefinitionMapper, assigneeResolverRegistry);
    }

    /** 条件命中分支应被展开，未命中分支记入未覆盖分支列表，且不展开其下游。 */
    @Test
    void simulate_shouldFollowMatchedConditionBranch_andRecordUncoveredBranch() {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("SIM_COND")
                .processName("条件预演测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("highRisk", "REJECTED"),
                        endNode("normal", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "cond", null, null),
                        edge("e2", "cond", "highRisk", conditionEq("riskLevel", "HIGH"), 1),
                        edge("e3", "cond", "normal", null, 2)))
                .build();
        stubDraft(1L, dsl);

        SimulationResultVO result = service.simulate(1L, request(null, Map.of("riskLevel", "HIGH"), null, null));

        assertThat(result.getMode()).isEqualTo("QUICK_PREVIEW");
        assertThat(result.getHitPath()).containsExactly("start", "cond", "highRisk");
        assertThat(result.getUncoveredBranches()).hasSize(1);
        assertThat(result.getUncoveredBranches().get(0).getTargetNodeId()).isEqualTo("normal");
    }

    /** 未命中任何条件分支时应回退到无条件的默认分支。 */
    @Test
    void simulate_shouldFallBackToDefaultBranch_whenNoConditionMatches() {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("SIM_COND_DEFAULT")
                .processName("默认分支预演测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(new ConditionNodeDslV2(), "cond"),
                        endNode("highRisk", "REJECTED"),
                        endNode("normal", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "cond", null, null),
                        edge("e2", "cond", "highRisk", conditionEq("riskLevel", "HIGH"), 1),
                        edge("e3", "cond", "normal", null, 2)))
                .build();
        stubDraft(1L, dsl);

        SimulationResultVO result = service.simulate(1L, request(null, Map.of("riskLevel", "LOW"), null, null));

        assertThat(result.getHitPath()).containsExactly("start", "cond", "normal");
        assertThat(result.getUncoveredBranches()).hasSize(1);
        assertThat(result.getUncoveredBranches().get(0).getTargetNodeId()).isEqualTo("highRisk");
    }

    /** 审批节点应通过 {@link AssigneeResolverRegistry} 解析候选人，非空时不标注为空审批人。 */
    @Test
    void simulate_shouldResolveApprovalCandidates_viaRegistry() {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("SIM_APPROVAL")
                .processName("审批人解析预演测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"), approvalNode("approve", AssigneeTypeV2.ROLE,
                        "SECURITY_ADMIN"), endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "approve", null, null), edge("e2", "approve", "end", null, null)))
                .build();
        stubDraft(1L, dsl);
        when(assigneeResolverRegistry.resolve(org.mockito.ArgumentMatchers.eq(AssigneeType.ROLE), any()))
                .thenReturn(Set.of(501L, 502L));

        SimulationResultVO result = service.simulate(1L, request(null, Map.of(), 999L, 100L));

        assertThat(result.getApprovalResolutions()).hasSize(1);
        ApprovalNodeSimulationVO approval = result.getApprovalResolutions().get(0);
        assertThat(approval.getNodeId()).isEqualTo("approve");
        assertThat(approval.getCandidateUserIds()).containsExactlyInAnyOrder(501L, 502L);
        assertThat(approval.isEmptyAssignee()).isFalse();
        assertThat(approval.getResolveBasis()).contains("ROLE").contains("2 人");
    }

    /** 解析为空且空审批人策略为 {@code BLOCK} 时应显式标注为空审批人节点。 */
    @Test
    void simulate_shouldMarkEmptyAssignee_whenResolverReturnsEmpty() {
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("SIM_EMPTY")
                .processName("空审批人预演测试")
                .nodes(List.of(node(new StartNodeDslV2(), "start"),
                        approvalNode("approve", AssigneeTypeV2.ROLE, "NO_SUCH_ROLE"),
                        endNode("end", "APPROVED")))
                .edges(List.of(edge("e1", "start", "approve", null, null), edge("e2", "approve", "end", null, null)))
                .build();
        stubDraft(1L, dsl);
        when(assigneeResolverRegistry.resolve(org.mockito.ArgumentMatchers.eq(AssigneeType.ROLE), any()))
                .thenReturn(Set.of());

        SimulationResultVO result = service.simulate(1L, request(null, Map.of(), null, null));

        ApprovalNodeSimulationVO approval = result.getApprovalResolutions().get(0);
        assertThat(approval.isEmptyAssignee()).isTrue();
        assertThat(approval.getCandidateUserIds()).isEmpty();
        assertThat(approval.getResolveBasis()).contains("BLOCK");
    }

    /** 并行分叉节点的两条分支均应展开，汇合节点只在首次到达时展开一次下游。 */
    @Test
    void simulate_shouldExpandBothParallelBranches() {
        ParallelSplitNodeDslV2 split = new ParallelSplitNodeDslV2();
        split.setJoinNodeId("join");
        ParallelJoinNodeDslV2 join = new ParallelJoinNodeDslV2();
        join.setSplitNodeId("split");
        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode("SIM_PARALLEL")
                .processName("并行分叉预演测试")
                .nodes(List.of(
                        node(new StartNodeDslV2(), "start"),
                        node(split, "split"),
                        approvalNode("branchA", AssigneeTypeV2.USER, "601"),
                        approvalNode("branchB", AssigneeTypeV2.USER, "602"),
                        node(join, "join"),
                        endNode("end", "APPROVED")))
                .edges(List.of(
                        edge("e1", "start", "split", null, null),
                        edge("e2", "split", "branchA", null, null),
                        edge("e3", "split", "branchB", null, null),
                        edge("e4", "branchA", "join", null, null),
                        edge("e5", "branchB", "join", null, null),
                        edge("e6", "join", "end", null, null)))
                .build();
        stubDraft(1L, dsl);
        when(assigneeResolverRegistry.resolve(org.mockito.ArgumentMatchers.eq(AssigneeType.USER), any()))
                .thenReturn(Set.of(601L));

        SimulationResultVO result = service.simulate(1L, request(null, Map.of(), null, null));

        assertThat(result.getHitPath()).contains("split", "branchA", "branchB", "join", "end");
        // join 只应出现一次：两条分支都指向 join，但访问一次后不重复展开下游
        assertThat(result.getHitPath().stream().filter("join"::equals).count()).isEqualTo(1);
        assertThat(result.getApprovalResolutions()).hasSize(2);
    }

    /** 草稿非 {@code schemaVersion=2} 时应拒绝预演。 */
    @Test
    void simulate_shouldReject_whenDraftIsNotSchemaV2() {
        ProcessModelEntity model = ProcessModelEntity.builder().id(1L).modelJson("{\"schemaVersion\":1}").build();
        when(processModelMapper.selectById(1L)).thenReturn(model);

        assertThatThrownBy(() -> service.simulate(1L, request(null, Map.of(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("schemaVersion=2");
    }

    private void stubDraft(Long modelId, ProcessModelDslV2 dsl) {
        ProcessModelEntity model = ProcessModelEntity.builder().id(modelId).modelJson(JacksonUtils.toJson(dsl)).build();
        when(processModelMapper.selectById(modelId)).thenReturn(model);
    }

    private SimulationRequest request(Long definitionId, Map<String, Object> formValues, Long applicantId, Long applicantOrgId) {
        SimulationRequest request = new SimulationRequest();
        request.setDefinitionId(definitionId);
        request.setFormValues(formValues);
        request.setApplicantId(applicantId);
        request.setApplicantOrgId(applicantOrgId);
        return request;
    }

    private ProcessNodeDslV2 node(ProcessNodeDslV2 node, String id) {
        node.setId(id);
        node.setType(typeOf(node));
        return node;
    }

    private String typeOf(ProcessNodeDslV2 node) {
        if (node instanceof StartNodeDslV2) {
            return "START";
        }
        if (node instanceof ConditionNodeDslV2) {
            return "CONDITION";
        }
        if (node instanceof ParallelSplitNodeDslV2) {
            return "PARALLEL_SPLIT";
        }
        if (node instanceof ParallelJoinNodeDslV2) {
            return "PARALLEL_JOIN";
        }
        return node.getClass().getSimpleName();
    }

    private ApprovalNodeDslV2 approvalNode(String id, AssigneeTypeV2 type, String value) {
        ApprovalNodeDslV2 approval = new ApprovalNodeDslV2();
        approval.setId(id);
        approval.setType("APPROVAL");
        approval.setName(id);
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(type);
        assignee.setValue(value);
        approval.setAssignee(assignee);
        approval.setEmptyPolicy(EmptyPolicy.BLOCK);
        return approval;
    }

    private EndNodeDslV2 endNode(String id, String outcome) {
        EndNodeDslV2 end = new EndNodeDslV2();
        end.setId(id);
        end.setType("END");
        end.setOutcome(outcome);
        return end;
    }

    private EdgeDslV2 edge(String id, String source, String target, ConditionAstDsl condition, Integer priority) {
        return EdgeDslV2.builder().id(id).source(source).target(target).condition(condition).priority(priority).build();
    }

    private ConditionAstDsl conditionEq(String field, Object value) {
        return ConditionAstDsl.builder().logic(ConditionLogic.AND)
                .items(List.of(ConditionItemDsl.builder().field(field).op(ConditionOperator.EQ).value(value).build()))
                .build();
    }
}
