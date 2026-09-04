package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrgLeaderAssigneeResolver} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class OrgLeaderAssigneeResolverTest {

    @Mock
    private AdminRoleLookupService adminRoleLookupService;

    /** 应按发起人所属组织与配置角色编码解析。 */
    @Test
    void resolve_shouldUseApplicantOrgAndConfiguredRoleCode() {
        OrgLeaderAssigneeResolver resolver = new OrgLeaderAssigneeResolver(adminRoleLookupService);
        when(adminRoleLookupService.findOrgLeaderUserIds(10L, "DEPT_LEADER")).thenReturn(Set.of(5L));

        Set<Long> result = resolver.resolve(new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, 10L, null, null));

        assertThat(result).containsExactly(5L);
    }

    /** 未配置 assigneeValue 时回退默认角色编码。 */
    @Test
    void resolve_shouldFallbackToDefaultRoleCodeWhenValueBlank() {
        OrgLeaderAssigneeResolver resolver = new OrgLeaderAssigneeResolver(adminRoleLookupService);
        when(adminRoleLookupService.findOrgLeaderUserIds(10L, WorkflowConstants.DEFAULT_ORG_LEADER_ROLE_CODE))
                .thenReturn(Set.of(6L));

        Set<Long> result = resolver.resolve(new AssigneeResolveContext(1L, "node1", null, 100L, 10L, null, null));

        assertThat(result).containsExactly(6L);
    }

    /** 发起人组织上下文缺失时返回空集合。 */
    @Test
    void resolve_shouldReturnEmptyWhenApplicantOrgMissing() {
        OrgLeaderAssigneeResolver resolver = new OrgLeaderAssigneeResolver(adminRoleLookupService);

        Set<Long> result = resolver.resolve(new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, null, null, null));

        assertThat(result).isEmpty();
    }

    /** orgSource=FIXED_ORG 时应使用节点规则配置的固定目标组织，而不是发起人所属组织。 */
    @Test
    void resolve_shouldUseFixedTargetOrgWhenOrgSourceIsFixedOrg() {
        OrgLeaderAssigneeResolver resolver = new OrgLeaderAssigneeResolver(adminRoleLookupService);
        when(adminRoleLookupService.findOrgLeaderUserIds(999L, "DEPT_LEADER")).thenReturn(Set.of(9L));

        Set<Long> result = resolver.resolve(
                new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, 10L, "FIXED_ORG", 999L));

        assertThat(result).containsExactly(9L);
    }
}
