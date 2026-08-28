package cn.nihility.rbac.sync.openapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.changelog.mapper.AppDataChangeLogMapper;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncDigestVO;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.transform.FieldMappingTransformer;
import cn.nihility.rbac.sync.transform.SyncBizPageQueryResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageRow;
import cn.nihility.rbac.sync.transform.SyncRecordAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SyncDigestServiceImpl} 的单元测试，覆盖非法数据类型拒绝、同步总开关/数据域未开通
 * 场景下摘要退化为"空输入摘要"但仍返回 {@code currentMaxSeq}/{@code configEpoch}、排序稳定性
 * （多次调用同一份数据摘要值相同）、大数据量场景按批查询而不是一次性加载
 * （app-sync-changelog-pull change design.md Decision 10，tasks.md 5.1）。
 */
@ExtendWith(MockitoExtension.class)
class SyncDigestServiceImplTest {

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    @Mock
    private AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    @Mock
    private SyncBizPageQueryResolver syncBizPageQueryResolver;

    @Mock
    private FieldMappingTransformer fieldMappingTransformer;

    @Mock
    private AppDataChangeLogMapper appDataChangeLogMapper;

    private SyncDigestServiceImpl service;

    @BeforeEach
    void setUp() {
        SyncRecordAssembler syncRecordAssembler = new SyncRecordAssembler(fieldMappingTransformer);
        service = new SyncDigestServiceImpl(appConfigMapper, appSyncDomainConfigMapper, appSyncOrgScopeResolver,
                syncBizPageQueryResolver, syncRecordAssembler, appDataChangeLogMapper);
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());
        // 字段映射转换器原样透传，聚焦验证摘要服务自身的分批扫描/编码/累加逻辑。
        org.mockito.Mockito.lenient().when(fieldMappingTransformer.transform(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /**
     * 非法的 {@code entityType} 应直接拒绝。
     */
    @Test
    void digest_shouldRejectInvalidEntityType() {
        assertThatThrownBy(() -> service.digest("NOT_A_DOMAIN")).isInstanceOf(BusinessException.class);
    }

    /**
     * {@code DICT} 应被接受（{@code /digest} 比 {@code /changes} 多支持一个 DICT）。
     */
    @Test
    void digest_shouldAcceptDictEntityType() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(false).build());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(null);

        SyncDigestVO result = service.digest(SyncDomain.DICT);

        assertThat(result.getEntityType()).isEqualTo(SyncDomain.DICT);
        assertThat(result.getRecordCount()).isZero();
    }

    /**
     * 同步总开关关闭时应退化为"空输入摘要"（记录数 0），但仍应返回 {@code currentMaxSeq}/
     * {@code configEpoch}，不查询业务数据。
     */
    @Test
    void digest_shouldReturnEmptyDigest_whenSyncMasterDisabled() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(false).configEpoch(9L).build());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(500L);

        SyncDigestVO result = service.digest(SyncDomain.ORG);

        assertThat(result.getRecordCount()).isZero();
        assertThat(result.getAlgorithm()).isEqualTo("SHA-256");
        assertThat(result.getCurrentMaxSeq()).isEqualTo("500");
        assertThat(result.getConfigEpoch()).isEqualTo("9");
        assertThat(result.getDigestValue()).isNotBlank();
        verify(syncBizPageQueryResolver, org.mockito.Mockito.never()).queryDigestBatch(any(), any(), anyInt(), any());
    }

    /**
     * 变更流水表为空时 {@code currentMaxSeq} 应返回 "0"。
     */
    @Test
    void digest_shouldReturnZeroCurrentMaxSeq_whenChangeLogEmpty() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(false).build());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(null);

        SyncDigestVO result = service.digest(SyncDomain.ORG);

        assertThat(result.getCurrentMaxSeq()).isEqualTo("0");
    }

    /**
     * 排序稳定性：同一份数据多次调用应得到相同的摘要值。
     */
    @Test
    void digest_shouldBeStable_acrossMultipleCalls() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(10L);

        SyncBizPageRow row = SyncBizPageRow.builder().id(1L).code("ORG001").status(2000).version(1L)
                .data(Map.of("code", "ORG001")).build();
        // 首批只返回 1 条（不满内部批大小），服务端据此判定底层已扫描到末尾，不会再发起第二次
        // 查询，因此不需要为 lastId=1L 的场景额外打桩。
        when(syncBizPageQueryResolver.queryDigestBatch(eq(SyncDomain.ORG), isNull(), anyInt(), any()))
                .thenReturn(List.of(row));

        SyncDigestVO first = service.digest(SyncDomain.ORG);
        SyncDigestVO second = service.digest(SyncDomain.ORG);

        assertThat(first.getDigestValue()).isEqualTo(second.getDigestValue());
        assertThat(first.getRecordCount()).isEqualTo(1);
    }

    /**
     * 字段插入顺序不同但内容相同的两条记录应产生相同摘要（依赖
     * {@code SyncDigestCanonicalCodec} 的键排序能力，本用例验证摘要服务端到端调用链路上
     * 该能力生效）。
     */
    @Test
    void digest_shouldBeStable_regardlessOfFieldInsertionOrder() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(10L);

        Map<String, Object> dataOrderA = new java.util.LinkedHashMap<>();
        dataOrderA.put("name", "组织一");
        dataOrderA.put("code", "ORG001");
        Map<String, Object> dataOrderB = new java.util.LinkedHashMap<>();
        dataOrderB.put("code", "ORG001");
        dataOrderB.put("name", "组织一");

        SyncBizPageRow rowA = SyncBizPageRow.builder().id(1L).code("ORG001").status(2000).version(1L)
                .data(dataOrderA).build();
        SyncBizPageRow rowB = SyncBizPageRow.builder().id(1L).code("ORG001").status(2000).version(1L)
                .data(dataOrderB).build();

        when(syncBizPageQueryResolver.queryDigestBatch(eq(SyncDomain.ORG), isNull(), anyInt(), any()))
                .thenReturn(List.of(rowA));
        String digestA = service.digest(SyncDomain.ORG).getDigestValue();

        when(syncBizPageQueryResolver.queryDigestBatch(eq(SyncDomain.ORG), isNull(), anyInt(), any()))
                .thenReturn(List.of(rowB));
        String digestB = service.digest(SyncDomain.ORG).getDigestValue();

        assertThat(digestA).isEqualTo(digestB);
    }

    /**
     * 大数据量场景应按批查询而不是一次性整表加载：本用例用两批数据（首批"满批"触发继续扫描，
     * 次批"不满批"触发终止）验证 {@code queryDigestBatch} 按批多次调用、{@code lastId}
     * 正确递进。
     */
    @Test
    void digest_shouldScanInBatches_notLoadAllAtOnce() {
        when(appConfigMapper.selectOne(any()))
                .thenReturn(AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(appDataChangeLogMapper.selectMaxChangeSeq()).thenReturn(1000L);

        // 首批构造 200 条记录（与实现内部批大小一致），触发服务端认为"可能还有更多"继续扫描
        // 第二批；第二批只返回 1 条（不满批），触发终止，验证一共调用两次而不是一次性查询。
        List<SyncBizPageRow> firstBatch = new ArrayList<>();
        for (long id = 1; id <= 200; id++) {
            firstBatch.add(SyncBizPageRow.builder().id(id).code("ORG" + id).status(2000).version(1L)
                    .data(Map.of("code", "ORG" + id)).build());
        }
        SyncBizPageRow lastRow =
                SyncBizPageRow.builder().id(201L).code("ORG201").status(2000).version(1L)
                        .data(Map.of("code", "ORG201")).build();

        when(syncBizPageQueryResolver.queryDigestBatch(eq(SyncDomain.ORG), isNull(), anyInt(), any()))
                .thenReturn(firstBatch);
        when(syncBizPageQueryResolver.queryDigestBatch(eq(SyncDomain.ORG), eq(200L), anyInt(), any()))
                .thenReturn(List.of(lastRow));

        SyncDigestVO result = service.digest(SyncDomain.ORG);

        assertThat(result.getRecordCount()).isEqualTo(201);
        verify(syncBizPageQueryResolver, times(2)).queryDigestBatch(eq(SyncDomain.ORG), any(), anyInt(), any());
    }
}
