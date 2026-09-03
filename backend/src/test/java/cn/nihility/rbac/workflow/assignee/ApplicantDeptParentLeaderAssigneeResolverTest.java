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
 * {@link ApplicantDeptParentLeaderAssigneeResolver} 单元测试（spec.md "发起人部门无负责人时
 * 向上级部门查找" Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class ApplicantDeptParentLeaderAssigneeResolverTest {

    @Mock
    private AdminRoleLookupService adminRoleLookupService;

    /** 应委托辅助组件沿组织路径向上查找。 */
    @Test
    void resolve_shouldDelegateToParentOrgLookup() {
        ApplicantDeptParentLeaderAssigneeResolver resolver =
                new ApplicantDeptParentLeaderAssigneeResolver(adminRoleLookupService);
        when(adminRoleLookupService.findParentOrgLeaderUserIds(10L, "DEPT_LEADER")).thenReturn(Set.of(9L));

        Set<Long> result = resolver.resolve(new AssigneeResolveContext(1L, "node1", "DEPT_LEADER", 100L, 10L));

        assertThat(result).containsExactly(9L);
    }
}
