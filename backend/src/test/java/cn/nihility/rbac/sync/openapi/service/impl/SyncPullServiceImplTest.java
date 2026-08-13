package cn.nihility.rbac.sync.openapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRecordVO;
import cn.nihility.rbac.sync.transform.BizSnapshotResolver;
import cn.nihility.rbac.sync.transform.FieldMappingTransformer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SyncPullServiceImpl} 的单元测试，重点覆盖数据类型合法性校验、数据域未开通返回
 * 空结果（而非报错）、按 sequence 单数据域/多数据域的 limit 兜底策略、字段映射转换调用
 * （app-sync-notify-pull-api change design.md Decision 9），以及 {@code data} 字段改为
 * 现查业务表当前数据、业务表查不到对应行时该记录被跳过
 * （fix-app-sync-pull-live-data change design.md Decision 2）。
 */
@ExtendWith(MockitoExtension.class)
class SyncPullServiceImplTest {

    @Mock
    private AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    @Mock
    private AppDataChangeLogService appDataChangeLogService;

    @Mock
    private FieldMappingTransformer fieldMappingTransformer;

    @Mock
    private BizSnapshotResolver bizSnapshotResolver;

    private SyncPullServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncPullServiceImpl(appSyncDomainConfigMapper, appDataChangeLogService,
                fieldMappingTransformer, bizSnapshotResolver);
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());
    }

    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /**
     * 非法的数据类型应直接拒绝（区别于"合法但未开通"）。
     */
    @Test
    void pullByBizIds_shouldRejectInvalidDataType() {
        assertThatThrownBy(() -> service.pullByBizIds("DICT", List.of(1L)))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 数据域未开通同步时应返回空列表，不查询变更记录表。
     */
    @Test
    void pullByBizIds_shouldReturnEmptyWhenDomainDisabled() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncEnabled(false).build());

        List<SyncPullRecordVO> result = service.pullByBizIds(SyncDomain.ORG, List.of(1L, 2L));

        assertThat(result).isEmpty();
        verify(appDataChangeLogService, org.mockito.Mockito.never()).selectLatestByBizIds(any(), any(), any());
    }

    /**
     * 数据域已开通时应查询变更记录并按字段映射转换后返回。
     */
    @Test
    void pullByBizIds_shouldReturnTransformedRecordsWhenDomainEnabled() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncEnabled(true).build());
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1024L)
                .dataType(SyncDomain.ORG)
                .bizId(88L)
                .operationType(1)
                .createTime(LocalDateTime.now())
                .build();
        when(appDataChangeLogService.selectLatestByBizIds(1L, SyncDomain.ORG, List.of(88L)))
                .thenReturn(List.of(changeLog));
        when(bizSnapshotResolver.resolve(SyncDomain.ORG, 88L)).thenReturn(Map.of("code", "ORG001"));
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.ORG), any()))
                .thenReturn(Map.of("orgCode", "ORG001"));

        List<SyncPullRecordVO> result = service.pullByBizIds(SyncDomain.ORG, List.of(88L));

        assertThat(result).hasSize(1);
        SyncPullRecordVO vo = result.get(0);
        assertThat(vo.getSequence()).isEqualTo(1024L);
        assertThat(vo.getBizId()).isEqualTo(88L);
        assertThat(vo.getDataType()).isEqualTo(SyncDomain.ORG);
        assertThat(vo.getData()).containsEntry("orgCode", "ORG001");
    }

    /**
     * 业务表查不到 {@code bizId} 对应的当前数据时，该条记录应被跳过，不出现在返回结果中。
     */
    @Test
    void pullByBizIds_shouldSkipRecordWhenBizRowNotFound() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncEnabled(true).build());
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder()
                .id(1025L)
                .dataType(SyncDomain.ORG)
                .bizId(99L)
                .operationType(1)
                .createTime(LocalDateTime.now())
                .build();
        when(appDataChangeLogService.selectLatestByBizIds(1L, SyncDomain.ORG, List.of(99L)))
                .thenReturn(List.of(changeLog));
        when(bizSnapshotResolver.resolve(SyncDomain.ORG, 99L)).thenReturn(null);

        List<SyncPullRecordVO> result = service.pullByBizIds(SyncDomain.ORG, List.of(99L));

        assertThat(result).isEmpty();
        verify(fieldMappingTransformer, org.mockito.Mockito.never()).transform(any(), any(), any());
    }

    /**
     * 按 sequence 查询指定数据类型且未传 {@code limit} 时，应使用该数据域配置的
     * {@code pageSize}。
     */
    @Test
    void pullBySequence_shouldUseDomainPageSizeWhenLimitAbsent() {
        when(appSyncDomainConfigMapper.selectList(any())).thenReturn(List.of(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30).build()));
        when(appDataChangeLogService.selectBySequence(eq(1L), eq(List.of(SyncDomain.ORG)), eq(1000L), eq(30)))
                .thenReturn(List.of());

        service.pullBySequence(SyncDomain.ORG, 1000L, null);

        verify(appDataChangeLogService).selectBySequence(eq(1L), eq(List.of(SyncDomain.ORG)), eq(1000L), eq(30));
    }

    /**
     * {@code dataType} 缺省时应聚合该应用全部已开通数据域，limit 取各域 pageSize 的最小值。
     */
    @Test
    void pullBySequence_shouldUseMinPageSizeAcrossEnabledDomainsWhenDataTypeAbsent() {
        when(appSyncDomainConfigMapper.selectList(any())).thenReturn(List.of(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30).build(),
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.USER).syncEnabled(true).pageSize(10).build()));
        when(appDataChangeLogService.selectBySequence(eq(1L), anyCollection(), eq(1000L), eq(10)))
                .thenReturn(List.of());

        service.pullBySequence(null, 1000L, null);

        verify(appDataChangeLogService).selectBySequence(eq(1L), anyCollection(), eq(1000L), eq(10));
    }

    /**
     * 显式指定的 {@code limit} 应优先于数据域默认 {@code pageSize}。
     */
    @Test
    void pullBySequence_shouldPreferExplicitLimit() {
        when(appSyncDomainConfigMapper.selectList(any())).thenReturn(List.of(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30).build()));
        when(appDataChangeLogService.selectBySequence(eq(1L), eq(List.of(SyncDomain.ORG)), eq(1000L), eq(5)))
                .thenReturn(List.of());

        service.pullBySequence(SyncDomain.ORG, 1000L, 5);

        verify(appDataChangeLogService).selectBySequence(eq(1L), eq(List.of(SyncDomain.ORG)), eq(1000L), eq(5));
    }
}
