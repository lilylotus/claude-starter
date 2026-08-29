package cn.nihility.rbac.sync.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
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

    /** 被测组件的组织数据访问依赖，使用 Mockito 打桩，供 {@link #resolveScopePrefixes} 相关用例使用。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测组件实例。 */
    private AppSyncOrgScopeResolver resolver;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        resolver = new AppSyncOrgScopeResolver(appSyncOrgScopeMapper, orgDescendantExpander, userPositionMapper,
                orgMapper);
    }

    /**
     * 应用某数据域未配置任何组织范围行时，应解析为不受限制（空 Optional），即"全部数据"。
     */
    @Test
    void resolveAllowedOrgIds_shouldReturnEmpty_whenNoScopeConfigured() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        Optional<Set<Long>> result = resolver.resolveAllowedOrgIds(1L, SyncDomain.ORG);

        assertThat(result).isEmpty();
        verify(orgDescendantExpander, never()).expandWithDescendantsIncludingDeleted(any());
    }

    /**
     * {@code include_children = 1} 的配置行应展开为该组织及其全部子孙组织 id，且必须调用
     * {@code expandWithDescendantsIncludingDeleted}（不排除已删除组织），而不是
     * {@code expandWithDescendants}（fix-app-sync-pull-deleted-org-scope change design.md
     * Decision 2）。
     */
    @Test
    void resolveAllowedOrgIds_shouldExpandDescendants_whenIncludeChildrenTrue() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(10L)))
                .thenReturn(Set.of(10L, 11L, 12L));

        Optional<Set<Long>> result = resolver.resolveAllowedOrgIds(1L, SyncDomain.ORG);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 11L, 12L);
        verify(orgDescendantExpander, never()).expandWithDescendants(any());
    }

    /**
     * 范围根组织被删除后，{@code allowedOrgIds} 仍应包含其删除前展开出的全部子孙组织 id
     * （包括已被逻辑删除的子孙），验证 {@code resolveAllowedOrgIds} 不再因为组织被删除而
     * 把它排除出应用同步范围（fix-app-sync-pull-deleted-org-scope change tasks.md 2.2）。
     */
    @Test
    void resolveAllowedOrgIds_shouldRetainDeletedRootAndDescendants_afterRootOrgDeleted() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(10L)))
                .thenReturn(Set.of(10L, 11L, 12L));

        Optional<Set<Long>> result = resolver.resolveAllowedOrgIds(1L, SyncDomain.ORG);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    /**
     * 范围内某个非根组织被删除后，{@code allowedOrgIds} 仍应包含该已删除组织，
     * {@code isOrgIdWithinScope} 对其应仍返回 {@code true}（fix-app-sync-pull-deleted-org-scope
     * change tasks.md 2.2）。
     */
    @Test
    void isOrgIdWithinScope_shouldReturnTrue_forDeletedNonRootOrgWithinExpandedScope() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgDescendantExpander.expandWithDescendantsIncludingDeleted(Set.of(10L)))
                .thenReturn(Set.of(10L, 11L, 12L));

        assertThat(resolver.isOrgIdWithinScope(1L, SyncDomain.ORG, 12L)).isTrue();
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

    /**
     * 应用某数据域未配置任何组织范围行时，{@link AppSyncOrgScopeResolver#resolveScopePrefixes}
     * 应解析为空列表（调用方据此判断不受限制），不触发 {@link OrgMapper} 查询
     * （app-sync-changelog-pull change design.md Decision 4）。
     */
    @Test
    void resolveScopePrefixes_shouldReturnEmptyList_whenNoScopeConfigured() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        List<ScopePrefix> result = resolver.resolveScopePrefixes(1L, SyncDomain.ORG);

        assertThat(result).isEmpty();
        verify(orgMapper, never()).selectByIds(any());
    }

    /**
     * 含子孙的配置行应正确解析出对应组织当前的 {@code orgPath} 与
     * {@code includeChildren=true}；不展开子孙 id（与 {@link #resolveAllowedOrgIds_shouldExpandDescendants_whenIncludeChildrenTrue}
     * 的展开行为不同，本方法只保留原始前缀语义）。
     */
    @Test
    void resolveScopePrefixes_shouldResolveCurrentOrgPath_whenIncludeChildrenTrue() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgMapper.selectByIds(Set.of(10L)))
                .thenReturn(List.of(OrgEntity.builder().id(10L).orgPath("1/10").build()));

        List<ScopePrefix> result = resolver.resolveScopePrefixes(1L, SyncDomain.ORG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgPath()).isEqualTo("1/10");
        assertThat(result.get(0).isIncludeChildren()).isTrue();
    }

    /**
     * 不含子孙（{@code includeChildren=false}）的配置行应正确解析，标记为不含子孙。
     */
    @Test
    void resolveScopePrefixes_shouldResolveCurrentOrgPath_whenIncludeChildrenFalse() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(20L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgMapper.selectByIds(Set.of(20L)))
                .thenReturn(List.of(OrgEntity.builder().id(20L).orgPath("1/20").build()));

        List<ScopePrefix> result = resolver.resolveScopePrefixes(1L, SyncDomain.POSITION);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgPath()).isEqualTo("1/20");
        assertThat(result.get(0).isIncludeChildren()).isFalse();
    }

    /**
     * 配置行关联的组织已被物理删除、查不到 {@code orgPath} 时应静默跳过该行，不抛异常
     * （防御性写法，正常情况下组织只做逻辑删除）。
     */
    @Test
    void resolveScopePrefixes_shouldSkipRow_whenOrgPathNotFound() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(999L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgMapper.selectByIds(Set.of(999L))).thenReturn(List.of());

        List<ScopePrefix> result = resolver.resolveScopePrefixes(1L, SyncDomain.ORG);

        assertThat(result).isEmpty();
    }

    /**
     * 查到的 {@code orgPath} 含 SQL {@code LIKE} 通配字符（{@code %}/{@code _}）时应拒绝，
     * 抛出 {@link BusinessException}，防止相邻编码前缀越权命中（app-sync-changelog-pull
     * change design.md Decision 4，边界安全校验）。
     */
    @Test
    void resolveScopePrefixes_shouldReject_whenOrgPathContainsLikeWildcard() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(30L).includeChildren(true).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        when(orgMapper.selectByIds(Set.of(30L)))
                .thenReturn(List.of(OrgEntity.builder().id(30L).orgPath("1/30%").build()));

        assertThatThrownBy(() -> resolver.resolveScopePrefixes(1L, SyncDomain.ORG))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 候选用户集合为空时，{@link AppSyncOrgScopeResolver#filterUsersWithinScope} 应直接返回
     * 空集合，不触发查询。
     */
    @Test
    void filterUsersWithinScope_shouldReturnEmpty_whenCandidatesEmpty() {
        Set<Long> result = resolver.filterUsersWithinScope(1L, Set.of());

        assertThat(result).isEmpty();
        verify(userPositionMapper, never()).selectList(any());
    }

    /**
     * {@code USER} 数据域不受限制时，{@code filterUsersWithinScope} 应原样返回候选集合，
     * 不触发任职记录查询。
     */
    @Test
    void filterUsersWithinScope_shouldReturnAllCandidates_whenNotRestricted() {
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of());

        Set<Long> result = resolver.filterUsersWithinScope(1L, Set.of(100L, 200L));

        assertThat(result).containsExactlyInAnyOrder(100L, 200L);
        verify(userPositionMapper, never()).selectList(any());
    }

    /**
     * 受限时，应一次 {@code IN} 查询批量校验候选用户，只返回任一任职落在允许范围内的用户 id。
     */
    @Test
    void filterUsersWithinScope_shouldReturnOnlyMatchedUsers_whenRestricted() {
        AppSyncOrgScopeEntity scope = AppSyncOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(appSyncOrgScopeMapper.selectList(any())).thenReturn(List.of(scope));
        UserPositionEntity matched = UserPositionEntity.builder().userId(100L).orgId(10L).build();
        UserPositionEntity unmatched = UserPositionEntity.builder().userId(200L).orgId(20L).build();
        when(userPositionMapper.selectList(any())).thenReturn(List.of(matched, unmatched));

        Set<Long> result = resolver.filterUsersWithinScope(1L, Set.of(100L, 200L));

        assertThat(result).containsExactly(100L);
    }
}
