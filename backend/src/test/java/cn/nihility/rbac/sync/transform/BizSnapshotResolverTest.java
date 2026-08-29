package cn.nihility.rbac.sync.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link BizSnapshotResolver} 的单元测试，覆盖五个数据域各自分发到正确的 Mapper，
 * 以及业务表查不到对应行时返回 {@code null}（fix-app-sync-pull-live-data change
 * tasks.md 2.3）。
 */
@ExtendWith(MockitoExtension.class)
class BizSnapshotResolverTest {

    @Mock
    private OrgMapper orgMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPositionMapper userPositionMapper;

    @Mock
    private AppMapper appMapper;

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private DictItemMapper dictItemMapper;

    private BizSnapshotResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BizSnapshotResolver(orgMapper, userMapper, userPositionMapper, appMapper, roleMapper,
                dictItemMapper);
    }

    /**
     * {@code dataType=ORG} 时应查询 {@link OrgMapper}，并把实体转换为字段快照 Map。
     */
    @Test
    void resolve_shouldDispatchToOrgMapperWhenOrg() {
        when(orgMapper.selectById(1L)).thenReturn(OrgEntity.builder().id(1L).code("ORG001").build());

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.ORG, 1L);

        assertThat(snapshot).containsEntry("code", "ORG001");
        verify(orgMapper).selectById(1L);
    }

    /**
     * {@code dataType=USER} 时应查询 {@link UserMapper}，并把实体转换为字段快照 Map。
     */
    @Test
    void resolve_shouldDispatchToUserMapperWhenUser() {
        when(userMapper.selectById(2L)).thenReturn(UserEntity.builder().id(2L).name("zhangsan").build());

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.USER, 2L);

        assertThat(snapshot).containsEntry("name", "zhangsan");
        verify(userMapper).selectById(2L);
    }

    /**
     * {@code dataType=POSITION} 时应查询 {@link UserPositionMapper}，并把实体转换为字段快照 Map。
     */
    @Test
    void resolve_shouldDispatchToUserPositionMapperWhenPosition() {
        when(userPositionMapper.selectById(3L))
                .thenReturn(UserPositionEntity.builder().id(3L).positionType("MAIN").build());

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.POSITION, 3L);

        assertThat(snapshot).containsEntry("positionType", "MAIN");
        verify(userPositionMapper).selectById(3L);
    }

    /**
     * {@code dataType=APP} 时应查询 {@link AppMapper}，并把实体转换为字段快照 Map。
     */
    @Test
    void resolve_shouldDispatchToAppMapperWhenApp() {
        when(appMapper.selectById(4L)).thenReturn(AppEntity.builder().id(4L).code("APP001").build());

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.APP, 4L);

        assertThat(snapshot).containsEntry("code", "APP001");
        verify(appMapper).selectById(4L);
    }

    /**
     * {@code dataType=ROLE} 时应查询 {@link RoleMapper}，并把实体转换为字段快照 Map。
     */
    @Test
    void resolve_shouldDispatchToRoleMapperWhenRole() {
        when(roleMapper.selectById(5L)).thenReturn(RoleEntity.builder().id(5L).code("ROLE001").build());

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.ROLE, 5L);

        assertThat(snapshot).containsEntry("code", "ROLE001");
        verify(roleMapper).selectById(5L);
    }

    /**
     * 业务表查不到对应行时应返回 {@code null}（防御性兜底分支）。
     */
    @Test
    void resolve_shouldReturnNullWhenRowNotFound() {
        when(orgMapper.selectById(99L)).thenReturn(null);

        Map<String, Object> snapshot = resolver.resolve(SyncDomain.ORG, 99L);

        assertThat(snapshot).isNull();
    }

    /**
     * {@code resolveBizCode} 应复用 {@code resolve} 的现查逻辑，取业务表的 {@code code} 属性。
     */
    @Test
    void resolveBizCode_shouldReturnCodeFromResolvedSnapshot() {
        when(orgMapper.selectById(1L)).thenReturn(OrgEntity.builder().id(1L).code("ORG001").build());

        String bizCode = resolver.resolveBizCode(SyncDomain.ORG, 1L);

        assertThat(bizCode).isEqualTo("ORG001");
    }

    /**
     * POSITION 数据域没有业务编码字段，应恒返回 {@code null}，且不应发起任何业务表查询。
     */
    @Test
    void resolveBizCode_shouldReturnNullForPositionDomain() {
        String bizCode = resolver.resolveBizCode(SyncDomain.POSITION, 3L);

        assertThat(bizCode).isNull();
        verify(userPositionMapper, org.mockito.Mockito.never()).selectById(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 业务表查不到对应行时，{@code resolveBizCode} 应返回 {@code null}。
     */
    @Test
    void resolveBizCode_shouldReturnNullWhenRowNotFound() {
        when(orgMapper.selectById(99L)).thenReturn(null);

        String bizCode = resolver.resolveBizCode(SyncDomain.ORG, 99L);

        assertThat(bizCode).isNull();
    }
}
