package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link OrgScopeServiceImpl} 的单元测试，覆盖"未配置管辖范围/无管理员身份视为不受限制"、
 * {@code include_children} 展开、多条配置取并集等分支逻辑
 * （org-scope-data-permission change design.md Decision 1/2/3）。
 */
@ExtendWith(MockitoExtension.class)
class OrgScopeServiceImplTest {

    /** 被测服务的管理员组织管辖范围数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AdminOrgScopeMapper adminOrgScopeMapper;

    /** 被测服务的组织子孙展开工具依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgDescendantExpander orgDescendantExpander;

    /** 被测服务实例。 */
    private OrgScopeServiceImpl orgScopeService;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        orgScopeService = new OrgScopeServiceImpl(adminOrgScopeMapper, orgDescendantExpander);
    }

    /**
     * 用户没有配置任何管辖组织范围时，应解析为不受限制（空 Optional）。
     */
    @Test
    void resolveAllowedOrgIds_shouldReturnEmpty_whenNoScopeConfigured() {
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of());

        Optional<Set<Long>> result = orgScopeService.resolveAllowedOrgIds(1L);

        assertThat(result).isEmpty();
        verify(orgDescendantExpander, never()).expandWithDescendants(any());
    }

    /**
     * 用户没有启用状态的管理员身份时（对应 Mapper 返回空集合），应解析为不受限制，
     * 与"有管理员身份但未配置范围"统一处理为同一种结果，不额外区分。
     */
    @Test
    void resolveAllowedOrgIds_shouldReturnEmpty_whenUserHasNoAdminIdentity() {
        when(adminOrgScopeMapper.selectOrgScopesByUserId(2L)).thenReturn(null);

        Optional<Set<Long>> result = orgScopeService.resolveAllowedOrgIds(2L);

        assertThat(result).isEmpty();
    }

    /**
     * {@code include_children = 1} 的配置行应展开为该组织及其全部子孙组织 id。
     */
    @Test
    void resolveAllowedOrgIds_shouldExpandDescendants_whenIncludeChildrenTrue() {
        AdminOrgScopeEntity scope = AdminOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of(scope));
        when(orgDescendantExpander.expandWithDescendants(Set.of(10L))).thenReturn(Set.of(10L, 11L, 12L));

        Optional<Set<Long>> result = orgScopeService.resolveAllowedOrgIds(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    /**
     * {@code include_children = 0} 的配置行不应展开，直接把该组织 id 本身加入允许集合。
     */
    @Test
    void resolveAllowedOrgIds_shouldNotExpand_whenIncludeChildrenFalse() {
        AdminOrgScopeEntity scope = AdminOrgScopeEntity.builder().orgId(20L).includeChildren(false).build();
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of(scope));

        Optional<Set<Long>> result = orgScopeService.resolveAllowedOrgIds(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(20L);
        verify(orgDescendantExpander, never()).expandWithDescendants(any());
    }

    /**
     * 多条管辖范围配置应取并集：既有需要展开子孙的行，也有不展开的行。
     */
    @Test
    void resolveAllowedOrgIds_shouldUnionMultipleScopeRows() {
        AdminOrgScopeEntity recursiveScope = AdminOrgScopeEntity.builder().orgId(10L).includeChildren(true).build();
        AdminOrgScopeEntity directScope = AdminOrgScopeEntity.builder().orgId(30L).includeChildren(false).build();
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of(recursiveScope, directScope));
        when(orgDescendantExpander.expandWithDescendants(Set.of(10L))).thenReturn(Set.of(10L, 11L));

        Optional<Set<Long>> result = orgScopeService.resolveAllowedOrgIds(1L);

        assertThat(result).isPresent();
        assertThat(result.get()).containsExactlyInAnyOrder(10L, 11L, 30L);
    }

    /**
     * 不受限制（未配置管辖范围）时，{@code isOrgIdAllowed} 对任意组织 id 恒返回
     * {@code true}（org-scope-write-guard change design.md Decision 1）。
     */
    @Test
    void isOrgIdAllowed_shouldReturnTrue_whenNotRestricted() {
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of());

        assertThat(orgScopeService.isOrgIdAllowed(1L, 999L)).isTrue();
    }

    /**
     * 受限时，落在允许集合内的组织 id 应通过校验。
     */
    @Test
    void isOrgIdAllowed_shouldReturnTrue_whenOrgIdInAllowedSet() {
        AdminOrgScopeEntity scope = AdminOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of(scope));

        assertThat(orgScopeService.isOrgIdAllowed(1L, 10L)).isTrue();
    }

    /**
     * 受限时，不在允许集合内的组织 id 应未通过校验。
     */
    @Test
    void isOrgIdAllowed_shouldReturnFalse_whenOrgIdOutOfAllowedSet() {
        AdminOrgScopeEntity scope = AdminOrgScopeEntity.builder().orgId(10L).includeChildren(false).build();
        when(adminOrgScopeMapper.selectOrgScopesByUserId(1L)).thenReturn(List.of(scope));

        assertThat(orgScopeService.isOrgIdAllowed(1L, 20L)).isFalse();
    }
}
