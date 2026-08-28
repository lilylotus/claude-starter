package cn.nihility.rbac.sync.openapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.changelog.service.AppSyncMetadataService;
import cn.nihility.rbac.sync.cursor.service.AppSyncCursorService;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesRequest;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.scope.ScopePrefix;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SyncChangesServiceImpl} 的单元测试，覆盖非法数据类型拒绝、同步总开关/数据域未开通
 * 返回空结果、游标过期报错、ORG/POSITION 组织范围前缀下推、USER 数据域批量过滤循环扫描、
 * nextSeq/hasMore 语义、投递水位推进（app-sync-changelog-pull change design.md Decision
 * 4/9/10/11，tasks.md 4.3/4.4/4.5）。
 */
@ExtendWith(MockitoExtension.class)
class SyncChangesServiceImplTest {

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    @Mock
    private AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    @Mock
    private AppDataChangeLogMapper appDataChangeLogMapper;

    @Mock
    private AppSyncMetadataService appSyncMetadataService;

    @Mock
    private AppSyncCursorService appSyncCursorService;

    private SyncChangesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncChangesServiceImpl(appConfigMapper, appSyncDomainConfigMapper, appSyncOrgScopeResolver,
                appDataChangeLogMapper, appSyncMetadataService, appSyncCursorService);
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());
    }

    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /**
     * 非法的 {@code entityType} 应直接拒绝。
     */
    @Test
    void changes_shouldRejectInvalidEntityType() {
        SyncChangesRequest request = SyncChangesRequest.builder().entityType("NOT_A_DOMAIN").build();

        assertThatThrownBy(() -> service.changes(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * {@code DICT} 不在 {@code /changes} 支持范围内，应拒绝（区别于 {@code /pull}/{@code /digest}）。
     */
    @Test
    void changes_shouldRejectDictEntityType() {
        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.DICT).build();

        assertThatThrownBy(() -> service.changes(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * 同步总开关关闭时应返回空结果，{@code nextSeq} 等于本次请求的 {@code sinceSeq}，
     * {@code hasMore} 为 false，不查询保留窗口、不推进投递水位。
     */
    @Test
    void changes_shouldReturnEmpty_whenSyncMasterDisabled() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(false).configEpoch(3L).build());
        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.ORG).sinceSeq("50").build();

        SyncChangesPageVO result = service.changes(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getSinceSeq()).isEqualTo("50");
        assertThat(result.getNextSeq()).isEqualTo("50");
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getConfigEpoch()).isEqualTo("3");
        verify(appSyncMetadataService, never()).getRetentionFloorSeq();
        verify(appSyncCursorService, never()).advance(any(), any(), anyLong());
    }

    /**
     * 数据域未开通同步时应返回空结果，不查询保留窗口、不推进投递水位。
     */
    @Test
    void changes_shouldReturnEmpty_whenDomainDisabled() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any()))
                .thenReturn(AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(false).build());
        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.ORG).build();

        SyncChangesPageVO result = service.changes(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getNextSeq()).isEqualTo("0");
        verify(appDataChangeLogMapper, never()).selectChanges(any(), any(), anyInt(), any());
    }

    /**
     * {@code sinceSeq} 早于保留窗口下界时应抛出业务异常，提示改走全量拉取重建。
     */
    @Test
    void changes_shouldThrow_whenSinceSeqBeforeRetentionFloor() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any()))
                .thenReturn(AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(1000L);
        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.ORG).sinceSeq("500").build();

        assertThatThrownBy(() -> service.changes(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * 非法的 {@code sinceSeq}（非数字）应拒绝。
     */
    @Test
    void changes_shouldRejectInvalidSinceSeq() {
        SyncChangesRequest request =
                SyncChangesRequest.builder().entityType(SyncDomain.ORG).sinceSeq("not-a-number").build();

        assertThatThrownBy(() -> service.changes(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * ORG 数据域应解析组织范围前缀并下推给底层流水查询；单批查询即返回不足 pageSize 条时，
     * {@code hasMore} 应为 false，{@code nextSeq} 取该批最后一条的 {@code changeSeq}，投递
     * 水位应被推进。
     */
    @Test
    void changes_shouldPushDownScopePrefixes_forOrgDomain() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).configEpoch(7L).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(0L);
        List<ScopePrefix> prefixes = List.of(new ScopePrefix("1/10", true));
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.ORG)).thenReturn(prefixes);
        AppDataChangeLogEntity entity = AppDataChangeLogEntity.builder().changeSeq(105L).eventId(9001L)
                .entityType(SyncDomain.ORG).entityId(10L).operationType("UPDATE").entityVersion(3L)
                .changeTime(LocalDateTime.of(2026, 1, 1, 0, 0)).build();
        when(appDataChangeLogMapper.selectChanges(eq(SyncDomain.ORG), eq(0L), eq(20), eq(prefixes)))
                .thenReturn(List.of(entity));

        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.ORG).build();
        SyncChangesPageVO result = service.changes(request);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getChangeSeq()).isEqualTo("105");
        assertThat(result.getRecords().get(0).getEventId()).isEqualTo("9001");
        assertThat(result.getRecords().get(0).getEntityId()).isEqualTo("10");
        assertThat(result.getRecords().get(0).getEntityVersion()).isEqualTo("3");
        assertThat(result.getNextSeq()).isEqualTo("105");
        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getConfigEpoch()).isEqualTo("7");
        verify(appSyncCursorService).advance(1L, SyncDomain.ORG, 105L);
    }

    /**
     * ORG 数据域未配置组织范围（零行）时，应传 {@code null} 前缀（不限制）给底层查询，而不是
     * 空列表。
     */
    @Test
    void changes_shouldPassNullPrefixes_whenOrgScopeNotRestricted() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(0L);
        when(appSyncOrgScopeResolver.resolveScopePrefixes(1L, SyncDomain.ORG)).thenReturn(List.of());
        when(appDataChangeLogMapper.selectChanges(any(), any(), anyInt(), any())).thenReturn(List.of());

        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.ORG).build();
        service.changes(request);

        ArgumentCaptor<List<ScopePrefix>> captor = ArgumentCaptor.forClass(List.class);
        verify(appDataChangeLogMapper).selectChanges(eq(SyncDomain.ORG), eq(0L), eq(20), captor.capture());
        assertThat(captor.getValue()).isNull();
    }

    /**
     * APP/ROLE 数据域不应调用组织范围解析组件，直接传 {@code null} 前缀。
     */
    @Test
    void changes_shouldNotResolveScopePrefixes_forAppDomain() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.APP).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(0L);
        when(appDataChangeLogMapper.selectChanges(any(), any(), anyInt(), any())).thenReturn(List.of());

        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.APP).build();
        service.changes(request);

        verify(appSyncOrgScopeResolver, never()).resolveScopePrefixes(any(), any());
    }

    /**
     * USER 数据域：底层流水扫描到的候选记录经批量过滤后部分被排除，应继续循环扫描直到
     * 攒够 pageSize 条可见结果或底层流水耗尽；{@code nextSeq} 应等于"本轮已扫描到的最后一条
     * 底层流水"，而不是最后一条可见结果。
     */
    @Test
    void changes_shouldLoopScan_untilPageFilled_forUserDomain() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.USER).syncEnabled(true).pageSize(2)
                        .build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(0L);

        AppDataChangeLogEntity e1 = AppDataChangeLogEntity.builder().changeSeq(101L).entityId(1001L).eventId(1L)
                .entityType(SyncDomain.USER).operationType("UPDATE").entityVersion(1L).build();
        AppDataChangeLogEntity e2 = AppDataChangeLogEntity.builder().changeSeq(102L).entityId(1002L).eventId(2L)
                .entityType(SyncDomain.USER).operationType("UPDATE").entityVersion(1L).build();
        // 第一批扫描 2 条（pageSize=2），其中 1002 被范围过滤掉，只剩 1 条可见，不足 pageSize，
        // 应继续扫描第二批。
        when(appDataChangeLogMapper.selectChanges(eq(SyncDomain.USER), eq(0L), eq(2), eq(null)))
                .thenReturn(List.of(e1, e2));
        when(appSyncOrgScopeResolver.filterUsersWithinScope(1L, Set.of(1001L, 1002L))).thenReturn(Set.of(1001L));

        AppDataChangeLogEntity e3 = AppDataChangeLogEntity.builder().changeSeq(103L).entityId(1003L).eventId(3L)
                .entityType(SyncDomain.USER).operationType("UPDATE").entityVersion(1L).build();
        // 第二批只需要再扫 1 条（remaining=1）。
        when(appDataChangeLogMapper.selectChanges(eq(SyncDomain.USER), eq(102L), eq(1), eq(null)))
                .thenReturn(List.of(e3));
        when(appSyncOrgScopeResolver.filterUsersWithinScope(1L, Set.of(1003L))).thenReturn(Set.of(1003L));

        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.USER).build();
        SyncChangesPageVO result = service.changes(request);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getEntityId()).isEqualTo("1001");
        assertThat(result.getRecords().get(1).getEntityId()).isEqualTo("1003");
        assertThat(result.getNextSeq()).isEqualTo("103");
        verify(appSyncCursorService).advance(1L, SyncDomain.USER, 103L);
    }

    /**
     * 全部候选均被过滤掉、可见结果为空时，只要扫描过底层流水，{@code nextSeq} 也应前进到
     * 本轮扫描到的最后一条底层流水（不能原地不动）。
     */
    @Test
    void changes_shouldAdvanceNextSeq_evenWhenAllFilteredOut() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.USER).syncEnabled(true).pageSize(5)
                        .build());
        when(appSyncMetadataService.getRetentionFloorSeq()).thenReturn(0L);

        AppDataChangeLogEntity e1 = AppDataChangeLogEntity.builder().changeSeq(201L).entityId(2001L).eventId(1L)
                .entityType(SyncDomain.USER).operationType("UPDATE").entityVersion(1L).build();
        // 第一批扫描 5 条（pageSize=5）但只有 1 条底层记录，判定为已耗尽（返回条数 < 请求条数）。
        when(appDataChangeLogMapper.selectChanges(eq(SyncDomain.USER), eq(0L), eq(5), eq(null)))
                .thenReturn(List.of(e1));
        when(appSyncOrgScopeResolver.filterUsersWithinScope(1L, Set.of(2001L))).thenReturn(Set.of());

        SyncChangesRequest request = SyncChangesRequest.builder().entityType(SyncDomain.USER).build();
        SyncChangesPageVO result = service.changes(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getNextSeq()).isEqualTo("201");
        assertThat(result.isHasMore()).isFalse();
        verify(appSyncCursorService).advance(1L, SyncDomain.USER, 201L);
    }
}
