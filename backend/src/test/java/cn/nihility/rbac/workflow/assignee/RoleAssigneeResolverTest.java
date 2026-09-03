package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.workflow.assignee.support.AdminRoleLookupService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link RoleAssigneeResolver} 单元测试（spec.md "按角色规则解析出候选审批人" Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class RoleAssigneeResolverTest {

    @Mock
    private AdminRoleLookupService adminRoleLookupService;

    /** 应委托 {@link AdminRoleLookupService} 按角色编码解析候选人。 */
    @Test
    void resolve_shouldDelegateToAdminRoleLookupService() {
        RoleAssigneeResolver resolver = new RoleAssigneeResolver(adminRoleLookupService);
        when(adminRoleLookupService.findUserIdsByRoleCode("SECURITY_ADMIN")).thenReturn(Set.of(1L, 2L));

        Set<Long> result = resolver.resolve(new AssigneeResolveContext(1L, "node1", "SECURITY_ADMIN", 100L, 10L));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }
}
