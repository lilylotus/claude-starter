package cn.nihility.rbac.identity.upstream.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncRecordDetailStatus;
import cn.nihility.rbac.identity.upstream.constant.UpstreamSyncStatus;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import cn.nihility.rbac.identity.upstream.entity.UpstreamDomainConfigEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamDomainConfigMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordDetailMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSyncRecordMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamSyncExecutor#syncSource} 的单元测试，覆盖
 * upstream-field-mapping-primary-key change 新增的"数据域未配置主键字段时同步前置判定
 * 失败，不发起取数请求"场景（tasks.md 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamSyncExecutorTest {

    /** 被测组件的上游数据源数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSourceMapper upstreamSourceMapper;

    /** 被测组件的数据域配置数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamDomainConfigMapper upstreamDomainConfigMapper;

    /** 被测组件的字段映射数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 被测组件的同步执行记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSyncRecordMapper upstreamSyncRecordMapper;

    /** 被测组件的同步执行记录明细数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSyncRecordDetailMapper upstreamSyncRecordDetailMapper;

    /** 被测组件的接口方式取数依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamHttpFetcher upstreamHttpFetcher;

    /** 被测组件的数据库表方式取数依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamJdbcFetcher upstreamJdbcFetcher;

    /** 被测组件的字段映射转换依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamFieldMappingTransformer upstreamFieldMappingTransformer;

    /** 被测组件的单行落库处理依赖，使用 Mockito 打桩，本用例断言其不被调用。 */
    @Mock
    private UpstreamRowUpserter upstreamRowUpserter;

    /** 被测组件的 SM4 主密钥配置依赖，使用 Mockito 打桩，本用例不涉及解密逻辑。 */
    @Mock
    private AppSecretProperties appSecretProperties;

    /** 被测组件实例。 */
    private UpstreamSyncExecutor upstreamSyncExecutor;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        upstreamSyncExecutor = new UpstreamSyncExecutor(upstreamSourceMapper, upstreamDomainConfigMapper,
                upstreamFieldMappingMapper, upstreamSyncRecordMapper, upstreamSyncRecordDetailMapper,
                upstreamHttpFetcher, upstreamJdbcFetcher, upstreamFieldMappingTransformer, upstreamRowUpserter,
                appSecretProperties);
    }

    /**
     * {@link CurrentUserContext} 是静态 {@code ThreadLocal}，测试用例之间共享同一个执行
     * 线程，每个用例结束后必须清空，避免污染同一 JVM 内其余测试类的断言
     * （fix-upstream-sync-scheduled-operator-context change）。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 已启用数据域当前的字段映射中没有任何字段标记为"主键标识"时，应在处理任何一行
     * 数据之前就判定本次同步失败，直接写入一条 {@code FAILED}、{@code totalCount=0} 的
     * 执行记录，不发起取数请求（design.md Decision 4），也不会调用字段映射转换/单行
     * 落库处理组件。
     */
    @Test
    void syncSource_shouldFailFast_whenDomainHasNoPrimaryKeyField() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);

        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig)
                .thenReturn(null)
                .thenReturn(null);

        UpstreamFieldMappingRow mappingWithoutPrimaryKey = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(false).build();
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG))
                .thenReturn(List.of(mappingWithoutPrimaryKey));

        upstreamSyncExecutor.syncSource(1L, "MANUAL");

        ArgumentCaptor<UpstreamSyncRecordEntity> captor = ArgumentCaptor.forClass(UpstreamSyncRecordEntity.class);
        verify(upstreamSyncRecordMapper).insert(captor.capture());
        UpstreamSyncRecordEntity record = captor.getValue();
        assertThat(record.getStatus()).isEqualTo(UpstreamSyncStatus.FAILED);
        assertThat(record.getTotalCount()).isEqualTo(0);
        assertThat(record.getFailSummary()).contains("主键字段");

        verifyNoInteractions(upstreamHttpFetcher, upstreamJdbcFetcher, upstreamFieldMappingTransformer,
                upstreamRowUpserter, upstreamSyncRecordDetailMapper);
        verify(upstreamDomainConfigMapper, never()).updateById(any(UpstreamDomainConfigEntity.class));
    }

    /**
     * 数据域成功取数但本轮结果为空（0 行）时，SHALL NOT 写入执行记录（避免"空跑"记录
     * 噪音），但 SHALL 仍然更新该数据域的"上次同步时间"
     * （upstream-sync-record-improvements change design.md Decision 1）。
     */
    @Test
    void syncSource_shouldNotSaveRecord_butUpdateLastSyncTime_whenFetchReturnsEmpty() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).syncType("API").build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);
        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).apiUrl("http://x").apiMethod("GET")
                .build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig).thenReturn(null).thenReturn(null);
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(true).build();
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG))
                .thenReturn(List.of(mapping));
        when(upstreamHttpFetcher.fetch(any(), any(), any())).thenReturn(List.of());

        upstreamSyncExecutor.syncSource(1L, "SCHEDULE");

        verify(upstreamSyncRecordMapper, never()).insert(any(UpstreamSyncRecordEntity.class));
        verifyNoInteractions(upstreamSyncRecordDetailMapper, upstreamFieldMappingTransformer, upstreamRowUpserter);
        ArgumentCaptor<UpstreamDomainConfigEntity> captor = ArgumentCaptor.forClass(UpstreamDomainConfigEntity.class);
        verify(upstreamDomainConfigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getLastSyncTime()).isNotNull();
    }

    /**
     * 取数阶段异常时仍然照常写一条 {@code FAILED} 执行记录（既有行为不受影响），但因为
     * 没有处理任何一行，不产生任何行明细（upstream-sync-record-improvements change
     * design.md：明细来自逐行处理，取数异常场景根本没进入逐行处理）。
     */
    @Test
    void syncSource_shouldSaveRecord_withoutDetails_whenFetchThrows() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).syncType("API").build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);
        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).apiUrl("http://x").apiMethod("GET")
                .build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig).thenReturn(null).thenReturn(null);
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(true).build();
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG))
                .thenReturn(List.of(mapping));
        when(upstreamHttpFetcher.fetch(any(), any(), any())).thenThrow(new RuntimeException("接口不可达"));

        upstreamSyncExecutor.syncSource(1L, "SCHEDULE");

        ArgumentCaptor<UpstreamSyncRecordEntity> captor = ArgumentCaptor.forClass(UpstreamSyncRecordEntity.class);
        verify(upstreamSyncRecordMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UpstreamSyncStatus.FAILED);
        assertThat(captor.getValue().getTotalCount()).isEqualTo(0);
        verifyNoInteractions(upstreamSyncRecordDetailMapper);
    }

    /**
     * 全部行处理成功时，为每一行都写入一条 {@code SUCCESS} 的行明细，明细的
     * {@code syncRecordId} 取自刚写入的执行记录的自增 id（upstream-sync-record-improvements
     * change design.md Decision 3）。
     */
    @Test
    void syncSource_shouldSaveSuccessDetailForEachRow_whenAllRowsSucceed() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).syncType("API").build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);
        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).apiUrl("http://x").apiMethod("GET")
                .build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig).thenReturn(null).thenReturn(null);
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(true).build();
        List<UpstreamFieldMappingRow> mappings = List.of(mapping);
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG)).thenReturn(mappings);
        Map<String, Object> row1 = Map.of("orgCode", "ORG001");
        Map<String, Object> row2 = Map.of("orgCode", "ORG002");
        when(upstreamHttpFetcher.fetch(any(), any(), any())).thenReturn(List.of(row1, row2));
        when(upstreamFieldMappingTransformer.transform(eq(mappings), any())).thenAnswer(inv -> inv.getArgument(1));
        when(upstreamSyncRecordMapper.insert(any(UpstreamSyncRecordEntity.class))).thenAnswer(invocation -> {
            UpstreamSyncRecordEntity entity = invocation.getArgument(0);
            entity.setId(99L);
            return 1;
        });

        upstreamSyncExecutor.syncSource(1L, "SCHEDULE");

        ArgumentCaptor<UpstreamSyncRecordEntity> recordCaptor = ArgumentCaptor.forClass(UpstreamSyncRecordEntity.class);
        verify(upstreamSyncRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo(UpstreamSyncStatus.SUCCESS);
        assertThat(recordCaptor.getValue().getTotalCount()).isEqualTo(2);

        ArgumentCaptor<UpstreamSyncRecordDetailEntity> detailCaptor =
                ArgumentCaptor.forClass(UpstreamSyncRecordDetailEntity.class);
        verify(upstreamSyncRecordDetailMapper, times(2)).insert(detailCaptor.capture());
        List<UpstreamSyncRecordDetailEntity> details = detailCaptor.getAllValues();
        assertThat(details).hasSize(2);
        assertThat(details.get(0).getRowNo()).isEqualTo(1);
        assertThat(details.get(0).getSyncRecordId()).isEqualTo(99L);
        assertThat(details.get(0).getSourceId()).isEqualTo(1L);
        assertThat(details.get(0).getStatus()).isEqualTo(UpstreamSyncRecordDetailStatus.SUCCESS);
        assertThat(details.get(0).getRowData()).contains("ORG001");
        assertThat(details.get(1).getRowNo()).isEqualTo(2);
        assertThat(details.get(1).getRowData()).contains("ORG002");
    }

    /**
     * 部分行处理失败时，成功行写入 {@code SUCCESS} 明细、失败行写入 {@code FAILED} 明细且
     * {@code failReason} 为该行的完整异常消息（不受 {@code fail_summary} 截断前 5 条的
     * 限制，见 upstream-sync-record-improvements change design.md Decision 3）。
     */
    @Test
    void syncSource_shouldSaveFailedDetail_withFullFailReason_whenRowFails() {
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).syncType("API").build();
        when(upstreamSourceMapper.selectById(1L)).thenReturn(source);
        UpstreamDomainConfigEntity orgDomainConfig = UpstreamDomainConfigEntity.builder()
                .id(10L).sourceId(1L).dataType(UpstreamDataType.ORG).enabled(true).apiUrl("http://x").apiMethod("GET")
                .build();
        when(upstreamDomainConfigMapper.selectOne(any()))
                .thenReturn(orgDomainConfig).thenReturn(null).thenReturn(null);
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .id(100L).upstreamFieldCode("orgCode").fieldCode("code").isPrimaryKey(true).build();
        List<UpstreamFieldMappingRow> mappings = List.of(mapping);
        when(upstreamFieldMappingMapper.selectBySourceIdAndDataType(1L, UpstreamDataType.ORG)).thenReturn(mappings);
        Map<String, Object> row1 = Map.of("orgCode", "ORG001");
        when(upstreamHttpFetcher.fetch(any(), any(), any())).thenReturn(List.of(row1));
        when(upstreamFieldMappingTransformer.transform(eq(mappings), any())).thenAnswer(inv -> inv.getArgument(1));
        org.mockito.Mockito.doThrow(new BusinessException("按主键字段匹配到多条已存在的组织记录，无法确定更新目标"))
                .when(upstreamRowUpserter).upsertRow(any(), any(), any(), any());
        when(upstreamSyncRecordMapper.insert(any(UpstreamSyncRecordEntity.class))).thenAnswer(invocation -> {
            UpstreamSyncRecordEntity entity = invocation.getArgument(0);
            entity.setId(88L);
            return 1;
        });

        upstreamSyncExecutor.syncSource(1L, "SCHEDULE");

        ArgumentCaptor<UpstreamSyncRecordEntity> recordCaptor = ArgumentCaptor.forClass(UpstreamSyncRecordEntity.class);
        verify(upstreamSyncRecordMapper).insert(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getStatus()).isEqualTo(UpstreamSyncStatus.FAILED);

        ArgumentCaptor<UpstreamSyncRecordDetailEntity> detailCaptor =
                ArgumentCaptor.forClass(UpstreamSyncRecordDetailEntity.class);
        verify(upstreamSyncRecordDetailMapper).insert(detailCaptor.capture());
        UpstreamSyncRecordDetailEntity detail = detailCaptor.getValue();
        assertThat(detail.getStatus()).isEqualTo(UpstreamSyncRecordDetailStatus.FAILED);
        assertThat(detail.getFailReason()).isEqualTo("按主键字段匹配到多条已存在的组织记录，无法确定更新目标");
    }

    /**
     * 模拟定时轮询后台线程（调用前 {@link CurrentUserContext} 未被设置）触发同步：执行
     * 期间 {@link CurrentUserContext#getUserId()} 应被临时置为保留哨兵用户 id，执行完成后
     * 应恢复为空，不残留给同一线程池后续复用（fix-upstream-sync-scheduled-operator-context
     * change tasks.md 2.1）。
     */
    @Test
    void syncSource_shouldSetSentinelDuringExecution_andClearAfterward_whenNoPriorContext() {
        CurrentUserContext.clear();
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).build();
        AtomicReference<Long> userIdDuringExecution = new AtomicReference<>();
        when(upstreamSourceMapper.selectById(1L)).thenAnswer(invocation -> {
            userIdDuringExecution.set(CurrentUserContext.getUserId());
            return source;
        });
        when(upstreamDomainConfigMapper.selectOne(any())).thenReturn(null);

        upstreamSyncExecutor.syncSource(1L, "SCHEDULE");

        assertThat(userIdDuringExecution.get()).isEqualTo(0L);
        assertThat(CurrentUserContext.getUserId()).isNull();
    }

    /**
     * 模拟管理员手动触发同步（调用前 {@link CurrentUserContext} 已由已认证 HTTP 请求线程
     * 设置为真实登录用户 id）：执行期间应临时替换为保留哨兵用户 id，执行完成后应恢复为
     * 调用前的真实用户 id，而不是被清空或残留哨兵值
     * （fix-upstream-sync-scheduled-operator-context change tasks.md 2.2）。
     */
    @Test
    void syncSource_shouldRestorePreviousUserId_whenCalledFromAuthenticatedThread() {
        CurrentUserContext.setUserId(42L);
        UpstreamSourceEntity source = UpstreamSourceEntity.builder().id(1L).build();
        AtomicReference<Long> userIdDuringExecution = new AtomicReference<>();
        when(upstreamSourceMapper.selectById(1L)).thenAnswer(invocation -> {
            userIdDuringExecution.set(CurrentUserContext.getUserId());
            return source;
        });
        when(upstreamDomainConfigMapper.selectOne(any())).thenReturn(null);

        upstreamSyncExecutor.syncSource(1L, "MANUAL");

        assertThat(userIdDuringExecution.get()).isEqualTo(0L);
        assertThat(CurrentUserContext.getUserId()).isEqualTo(42L);
    }
}
