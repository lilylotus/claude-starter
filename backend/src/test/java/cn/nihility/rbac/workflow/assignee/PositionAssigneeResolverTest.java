package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PositionAssigneeResolver} 单元测试：按岗位类型编码委托
 * {@link PositionService#findActiveUserIdsByPositionType(String)} 查询当前启用任职用户
 * （production-approval-lifecycle change tasks.md 5.3）。
 */
@ExtendWith(MockitoExtension.class)
class PositionAssigneeResolverTest {

    @Mock
    private PositionService positionService;

    /** 应按 assigneeValue 指定的岗位类型编码委托 PositionService 查询。 */
    @Test
    void resolve_shouldDelegateToPositionServiceByAssigneeValue() {
        PositionAssigneeResolver resolver = new PositionAssigneeResolver(positionService);
        when(positionService.findActiveUserIdsByPositionType("primary")).thenReturn(Set.of(7L, 8L));
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "primary", 100L, 10L, null, null);

        assertThat(resolver.resolve(context)).containsExactlyInAnyOrder(7L, 8L);
    }

    /** 无匹配任职时返回空集合，不抛出异常。 */
    @Test
    void resolve_shouldReturnEmptyWhenNoMatchingPosition() {
        PositionAssigneeResolver resolver = new PositionAssigneeResolver(positionService);
        when(positionService.findActiveUserIdsByPositionType("temporary")).thenReturn(Set.of());
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", "temporary", 100L, 10L, null, null);

        assertThat(resolver.resolve(context)).isEmpty();
    }

    /** 支持的类型应为 POSITION。 */
    @Test
    void supportedType_shouldBePosition() {
        PositionAssigneeResolver resolver = new PositionAssigneeResolver(positionService);
        assertThat(resolver.supportedType()).isEqualTo(AssigneeType.POSITION);
    }
}
