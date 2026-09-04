package cn.nihility.rbac.workflow.assignee.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.assignee.AssigneeResolveContext;
import cn.nihility.rbac.workflow.assignee.AssigneeResolverRegistry;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import cn.nihility.rbac.workflow.constant.EmptyAssigneeStrategy;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NodeAssigneeResolutionService} 单元测试：验证自审过滤与三种空审批人策略兜底
 * （workflow-approval-engine change spec.md "空审批人与自审场景的处理策略" Requirement）。
 */
@ExtendWith(MockitoExtension.class)
class NodeAssigneeResolutionServiceTest {

    @Mock
    private AssigneeResolverRegistry resolverRegistry;

    @Mock
    private AdminRoleLookupService adminRoleLookupService;

    private NodeAssigneeResolutionService service;

    /** 解析结果非空且不涉及自审时直接返回。 */
    @Test
    void resolve_shouldReturnDirectWhenNonEmptyAndNotSelfApproval() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.ROLE, false, EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "SECURITY_ADMIN", 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.ROLE), any())).thenReturn(Set.of(200L));

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.DIRECT);
        assertThat(result.userIds()).containsExactly(200L);
    }

    /** 唯一候选人是发起人本人且不允许自审时，视为空审批人。 */
    @Test
    void resolve_shouldTreatSelfApprovalAsEmptyWhenNotAllowed() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.APPLICANT_DEPT_LEADER, false,
                EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.APPLICANT_DEPT_LEADER), any())).thenReturn(Set.of(100L));
        when(adminRoleLookupService.findUserIdsByRoleCode(WorkflowConstants.WORKFLOW_ADMIN_ROLE_CODE))
                .thenReturn(Set.of(1L));

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.WORKFLOW_ADMIN);
        assertThat(result.userIds()).containsExactly(1L);
    }

    /** 唯一候选人是发起人本人但允许自审时，保留候选人不替换。 */
    @Test
    void resolve_shouldKeepSelfApprovalWhenAllowed() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.APPLICANT_DEPT_LEADER, true,
                EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.APPLICANT_DEPT_LEADER), any())).thenReturn(Set.of(100L));

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.DIRECT);
        assertThat(result.userIds()).containsExactly(100L);
    }

    /** 解析为空、策略为 AUTO_SKIP 时应返回 AUTO_SKIP 结果。 */
    @Test
    void resolve_shouldAutoSkipWhenEmptyAndStrategyIsAutoSkip() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.POSITION, false, EmptyAssigneeStrategy.AUTO_SKIP);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", null, 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.POSITION), any())).thenReturn(Set.of());

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.AUTO_SKIP);
        assertThat(result.hasAssignees()).isFalse();
    }

    /** 候选人集合包含多人且其中含发起人本人、不允许自审时，应仅剔除发起人本人，保留其余
     *  候选人（此前实现仅在"唯一候选人恰为发起人"时才排除，多候选人场景未生效，
     *  production-approval-lifecycle change tasks.md 5.4 修复）。 */
    @Test
    void resolve_shouldExcludeApplicantOnlyFromMultiCandidateSetWhenNotAllowed() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.ROLE, false, EmptyAssigneeStrategy.TO_WORKFLOW_ADMIN);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "SECURITY_ADMIN", 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.ROLE), any())).thenReturn(Set.of(100L, 200L, 300L));

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.DIRECT);
        assertThat(result.userIds()).containsExactlyInAnyOrder(200L, 300L);
    }

    /** 解析为空、策略为 REJECT 时应返回 REJECT 结果。 */
    @Test
    void resolve_shouldRejectWhenEmptyAndStrategyIsReject() {
        service = new NodeAssigneeResolutionService(resolverRegistry, adminRoleLookupService);
        NodeAssigneeRuleEntity rule = buildRule(AssigneeType.POSITION, false, EmptyAssigneeStrategy.REJECT);
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", null, 100L, 10L, null, null);
        when(resolverRegistry.resolve(eq(AssigneeType.POSITION), any())).thenReturn(Set.of());

        ResolvedAssignees result = service.resolve(rule, context);

        assertThat(result.kind()).isEqualTo(ResolvedAssignees.Kind.REJECT);
    }

    /** 构造节点审批人规则。 */
    private NodeAssigneeRuleEntity buildRule(
            AssigneeType type,
            boolean allowSelfApproval,
            EmptyAssigneeStrategy strategy) {
        return NodeAssigneeRuleEntity.builder()
                .id(1L)
                .processDefinitionId(1L)
                .nodeId("node1")
                .nodeName("节点1")
                .assigneeType(type.name())
                .allowSelfApproval(allowSelfApproval)
                .emptyAssigneeStrategy(strategy.name())
                .build();
    }
}
