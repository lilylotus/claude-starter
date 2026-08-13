package cn.nihility.rbac.sync.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppSyncOrgScopeResolver} 的单元测试，覆盖"未配置范围视为全部数据"、
 * {@code include_children} 展开子孙、以及用户多任职场景下任一命中即为 true
 * （app-sync-org-scope-and-app-change-log change design.md Decision 2/3）。
 */
@ExtendWith(MockitoExtension.class)
class AppSyncOrgScopeResolverTest {

    /** 被测组件的应用同步组织范围数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncOrgScopeMapper appSyncOrgScopeMapper;

    /** 被测组件的组织子孙展开工具依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgDescendantExpander orgDescendantExpander;

    /** 被测组件的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测组件实例。 */
    private AppSyncOrgScopeResolver resolver;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        resolver = new AppSyncOrgScopeResolver(appSyncOrgScopeMapper, orgDescendantExpander, userPositionMapper);
    }

    /**
     * 应用某数据域未配置任何组织范围行时，应解析为不受限制（空 Optional），即"全部数据"。
     */
    @Test
    void resolveAllowedOrgIds_shouldReturnEmpty_whenNoScopeConfigured() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        Optional<Set<Long>> result = resolver.resolveAllowedOrgIds(1L, SyncDomain.ORG);

        assertThat(result).isEmpty();
        verify(orgDescendantExpander, never()).expandWithDescendants(any());
    }

    /**
     * {@code include_children = 1} 的配置行应展开为该组织及其全部子孙组织 id。
     */
    @Test
    void resolveAllowedOrgIds_shouldExpandDescendants_whenIncludeChildrenTrue() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgDescendantExpander.expandWithDescendants(Set.of(10L))).thenReturn(Set.of(10L, 11L, 12L));

        Optional<Set<Long>> result = resolver.resolveAllowedOrgIds(1L, SyncDomain.ORG);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    /**
     * 受限时，命中允许集合内的组织 id 应返回 true。
     */
    @Test
    void isOrgIdWithinScope_shouldReturnTrue_whenOrgIdInAllowedSet() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));

        assertThat(resolver.isOrgIdWithinScope(1L, SyncDomain.ORG, 10L)).isTrue();
    }

    /**
     * 受限时，不在允许集合内的组织 id 应返回 false。
     */
    @Test
    void isOrgIdWithinScope_shouldReturnFalse_whenOrgIdOutOfAllowedSet() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));

        assertThat(resolver.isOrgIdWithinScope(1L, SyncDomain.ORG, 20L)).isFalse();
    }

    /**
     * 不受限制时，{@code isOrgIdWithinScope} 对任意组织 id 恒返回 true。
     */
    @Test
    void isOrgIdWithinScope_shouldReturnTrue_whenNotRestricted() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        assertThat(resolver.isOrgIdWithinScope(1L, SyncDomain.POSITION, 999L)).isTrue();
    }

    /**
     * 不受限制时，{@code isUserWithinScope} 恒返回 true，不查询任职记录。
     */
    @Test
    void isUserWithinScope_shouldReturnTrue_whenNotRestricted() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        assertThat(resolver.isUserWithinScope(1L, 100L)).isTrue();
        verify(userPositionMapper, never()).selectList(any());
    }

    /**
     * 受限时，用户存在多条任职记录，只要任一条 {@code orgId} 落在允许集合内即视为命中。
     */
    @Test
    void isUserWithinScope_shouldReturnTrue_whenAnyPositionOrgIdMatches() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        UserPositionEntity outOfScope = UserPositionEntity.builder().userId(100L).orgId(20L).build();
        UserPositionEntity inScope = UserPositionEntity.builder().userId(100L).orgId(10L).build();
        when(userPositionMapper.selectList(any())).thenReturn(List.of(outOfScope, inScope));

        assertThat(resolver.isUserWithinScope(1L, 100L)).isTrue();
    }

    /**
     * 受限时，用户全部任职记录的 {@code orgId} 均不在允许集合内应返回 false。
     */
    @Test
    void isUserWithinScope_shouldReturnFalse_whenNoPositionMatches() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        UserPositionEntity outOfScope = UserPositionEntity.builder().userId(100L).orgId(20L).build();
        when(userPositionMapper.selectList(any())).thenReturn(List.of(outOfScope));

        assertThat(resolver.isUserWithinScope(1L, 100L)).isFalse();
    }
}
