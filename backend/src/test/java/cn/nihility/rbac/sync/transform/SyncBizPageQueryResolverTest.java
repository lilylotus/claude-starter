package cn.nihility.rbac.sync.transform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.dict.entity.DictItemEntity;
import cn.nihility.rbac.dict.entity.DictTypeEntity;
import cn.nihility.rbac.dict.mapper.DictItemMapper;
import cn.nihility.rbac.dict.mapper.DictTypeMapper;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SyncBizPageQueryResolver} 的单元测试，覆盖六个数据域各自分发到正确的 Mapper、
 * 分页参数（offset 由 page/pageSize 换算）原样透传、查询结果正确转换为
 * {@link SyncBizPageRow}（app-sync-drop-changelog change tasks.md 4.1/8.4/10.8），以及
 * POSITION 的 {@code userCode}/{@code orgCode}/DICT 的 {@code dictTypeCode} 批量回填
 * （不逐行单独查询）。
 */
@ExtendWith(MockitoExtension.class)
class SyncBizPageQueryResolverTest {

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

    @Mock
    private DictTypeMapper dictTypeMapper;

    private SyncBizPageQueryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SyncBizPageQueryResolver(orgMapper, userMapper, userPositionMapper, appMapper, roleMapper,
                dictItemMapper, dictTypeMapper);
    }

    /**
     * {@code dataType=ORG} 时应查询 {@link OrgMapper}，offset 按 (page-1)*pageSize 换算，
     * 结果转换为携带 {@code code} 的查询结果行。
     */
    @Test
    void query_shouldDispatchToOrgMapperWhenOrg() {
        LocalDateTime updateTime = LocalDateTime.now();
        when(orgMapper.selectSyncPullPage(eq(20), eq(10), any(), any(), any(), any(), any())).thenReturn(
                List.of(OrgEntity.builder().id(1L).code("ORG001").updateTime(updateTime).status(2000).version(4L)
                        .build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.ORG, SyncBizPageQuery.builder().page(3).pageSize(10)
                .build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(1L);
        assertThat(rows.get(0).getCode()).isEqualTo("ORG001");
        assertThat(rows.get(0).getUpdateTime()).isEqualTo(updateTime);
        assertThat(rows.get(0).getStatus()).isEqualTo(2000);
        assertThat(rows.get(0).getVersion()).isEqualTo(4L);
        assertThat(rows.get(0).getData()).containsEntry("code", "ORG001");
    }

    /**
     * {@code dataType=USER} 时应查询 {@link UserMapper}，并携带 {@code mobile} 过滤参数。
     */
    @Test
    void query_shouldDispatchToUserMapperWhenUser() {
        when(userMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any(), eq("13800000000"), any()))
                .thenReturn(List.of(UserEntity.builder().id(2L).code("USER002").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.USER,
                SyncBizPageQuery.builder().page(1).pageSize(10).mobile("13800000000").build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCode()).isEqualTo("USER002");
        verify(userMapper).selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any(), eq("13800000000"),
                any());
    }

    /**
     * {@code dataType=POSITION} 时应查询 {@link UserPositionMapper}，结果行的 {@code code}
     * 恒为 {@code null}（任职数据域没有业务编码字段）；本例记录未关联 {@code userId}，不应
     * 触发 {@link UserMapper#selectByIds} 调用（但 {@code orgId} 是必填字段，仍会触发
     * {@link OrgMapper#selectByIds} 回填 {@code orgCode}）。
     */
    @Test
    void query_shouldDispatchToUserPositionMapperWhenPosition() {
        when(userPositionMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(UserPositionEntity.builder().id(3L).orgId(30L).build()));
        when(orgMapper.selectByIds(Set.of(30L)))
                .thenReturn(List.of(OrgEntity.builder().id(30L).code("ORG030").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.POSITION,
                SyncBizPageQuery.builder().page(1).pageSize(10).allowedOrgIds(Set.of(30L)).build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(3L);
        assertThat(rows.get(0).getCode()).isNull();
        assertThat(rows.get(0).getUserCode()).isNull();
        assertThat(rows.get(0).getOrgCode()).isEqualTo("ORG030");
    }

    /**
     * {@code dataType=POSITION} 时应批量回填关联用户的业务编码 {@code userCode} 与关联组织的
     * 业务编码 {@code orgCode}：多条记录关联同一用户/同一组织时，只应各调用一次
     * {@link UserMapper#selectByIds}/{@link OrgMapper#selectByIds}，避免逐行单独查询产生
     * N+1（app-sync-drop-changelog change design.md Decision 8，五次实现后修正）。
     */
    @Test
    void query_shouldBatchFillUserCodeAndOrgCode_whenPosition() {
        when(userPositionMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(
                List.of(UserPositionEntity.builder().id(3L).userId(100L).orgId(30L).build(),
                        UserPositionEntity.builder().id(4L).userId(100L).orgId(30L).build()));
        when(userMapper.selectByIds(Set.of(100L)))
                .thenReturn(List.of(UserEntity.builder().id(100L).code("USER100").build()));
        when(orgMapper.selectByIds(Set.of(30L)))
                .thenReturn(List.of(OrgEntity.builder().id(30L).code("ORG030").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.POSITION,
                SyncBizPageQuery.builder().page(1).pageSize(10).build());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getUserCode()).isEqualTo("USER100");
        assertThat(rows.get(1).getUserCode()).isEqualTo("USER100");
        assertThat(rows.get(0).getOrgCode()).isEqualTo("ORG030");
        assertThat(rows.get(1).getOrgCode()).isEqualTo("ORG030");
        assertThat(rows.get(0).getCode()).isNull();
        verify(userMapper, times(1)).selectByIds(any());
        verify(orgMapper, times(1)).selectByIds(any());
    }

    /**
     * {@code dataType=APP} 时应查询 {@link AppMapper}。
     */
    @Test
    void query_shouldDispatchToAppMapperWhenApp() {
        when(appMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(AppEntity.builder().id(4L).code("APP001").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.APP,
                SyncBizPageQuery.builder().page(1).pageSize(10).build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCode()).isEqualTo("APP001");
    }

    /**
     * {@code dataType=ROLE} 时应查询 {@link RoleMapper}。
     */
    @Test
    void query_shouldDispatchToRoleMapperWhenRole() {
        when(roleMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any()))
                .thenReturn(List.of(RoleEntity.builder().id(5L).code("ROLE001").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.ROLE,
                SyncBizPageQuery.builder().page(1).pageSize(10).build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCode()).isEqualTo("ROLE001");
    }

    /**
     * {@code dataType=DICT} 时应查询 {@link DictItemMapper}，结果行的 {@code code} 取字典项
     * 自身编码，{@code dictTypeCode} 从批量查询的 {@link DictTypeMapper#selectByIds}
     * 结果回填（app-sync-drop-changelog change design.md Decision 7，三次实现后修正）。
     */
    @Test
    void query_shouldDispatchToDictItemMapperWhenDict() {
        when(dictItemMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(
                List.of(DictItemEntity.builder().id(6L).dictTypeId(60L).code("MALE").status(2000).build()));
        when(dictTypeMapper.selectByIds(Set.of(60L)))
                .thenReturn(List.of(DictTypeEntity.builder().id(60L).code("GENDER").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.DICT,
                SyncBizPageQuery.builder().page(1).pageSize(10).build());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCode()).isEqualTo("MALE");
        assertThat(rows.get(0).getDictTypeCode()).isEqualTo("GENDER");
        // DICT 不是五类版本化同步实体之一（app-sync-changelog-pull change design.md
        // Decision 5），version 恒为 null。
        assertThat(rows.get(0).getVersion()).isNull();
    }

    /**
     * 同一字典项编码在不同字典类型下均应正常返回，各自的 {@code dictTypeCode} 应正确区分；
     * 本页出现多个不同的 {@code dictTypeId} 时，仍只应调用一次
     * {@link DictTypeMapper#selectByIds}，避免逐行单独查询产生 N+1。
     */
    @Test
    void query_shouldReturnSameCodeUnderDifferentDictTypes() {
        when(dictItemMapper.selectSyncPullPage(anyInt(), anyInt(), any(), any(), any(), any())).thenReturn(
                List.of(DictItemEntity.builder().id(6L).dictTypeId(60L).code("A").status(2000).build(),
                        DictItemEntity.builder().id(7L).dictTypeId(70L).code("A").status(2000).build()));
        when(dictTypeMapper.selectByIds(Set.of(60L, 70L))).thenReturn(
                List.of(DictTypeEntity.builder().id(60L).code("TYPE_A").build(),
                        DictTypeEntity.builder().id(70L).code("TYPE_B").build()));

        List<SyncBizPageRow> rows = resolver.query(SyncDomain.DICT,
                SyncBizPageQuery.builder().page(1).pageSize(10).codes(List.of("A")).build());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCode()).isEqualTo("A");
        assertThat(rows.get(0).getDictTypeCode()).isEqualTo("TYPE_A");
        assertThat(rows.get(1).getCode()).isEqualTo("A");
        assertThat(rows.get(1).getDictTypeCode()).isEqualTo("TYPE_B");
        verify(dictTypeMapper, times(1)).selectByIds(any());
    }
}
