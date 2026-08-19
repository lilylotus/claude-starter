package cn.nihility.rbac.sync.openapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import cn.nihility.rbac.sync.openapi.dto.SyncPullPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncPullRequest;
import cn.nihility.rbac.sync.pull.record.service.AppPullRecordService;
import cn.nihility.rbac.sync.scope.AppSyncOrgScopeResolver;
import cn.nihility.rbac.sync.transform.FieldMappingTransformer;
import cn.nihility.rbac.sync.transform.SyncBizPageQuery;
import cn.nihility.rbac.sync.transform.SyncBizPageQueryResolver;
import cn.nihility.rbac.sync.transform.SyncBizPageRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SyncPullServiceImpl} 的单元测试，覆盖数据类型合法性校验、同步总开关/数据域未开通
 * 返回空结果（而非报错）、分页参数（page 默认 1、pageSize 回退到数据域配置）、组织范围
 * 下推到分页查询、字段映射转换调用、拉取日志写入及其请求参数摘要、整页响应结构
 * （顶层 dataType/page/pageSize/dataSize/latestUpdateTime 回显，records 每条元素合并
 * bizId/bizCode/bizStatus/updateTime，其中 bizStatus 不受字段映射配置影响）
 * （app-sync-drop-changelog change design.md/tasks.md 5/6/8/11）。
 */
@ExtendWith(MockitoExtension.class)
class SyncPullServiceImplTest {

    @Mock
    private AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppSyncOrgScopeResolver appSyncOrgScopeResolver;

    @Mock
    private SyncBizPageQueryResolver syncBizPageQueryResolver;

    @Mock
    private FieldMappingTransformer fieldMappingTransformer;

    @Mock
    private AppPullRecordService appPullRecordService;

    private SyncPullServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncPullServiceImpl(appSyncDomainConfigMapper, appConfigMapper, appSyncOrgScopeResolver,
                syncBizPageQueryResolver, fieldMappingTransformer, appPullRecordService);
        OpenApiCallerContext.set(AppConfigEntity.builder().appRefId(1L).build());
        // 同步总开关默认桩为开启，不影响既有场景；关闭场景在下方单独用例中覆盖。
        lenient().when(appConfigMapper.selectOne(any())).thenReturn(
                AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(true).build());
    }

    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /**
     * 非法的数据类型应直接拒绝（区别于"合法但未开通"）。
     */
    @Test
    void pull_shouldRejectInvalidDataType() {
        SyncPullRequest request = SyncPullRequest.builder().dataType("NOT_A_DOMAIN").build();

        assertThatThrownBy(() -> service.pull(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * 同步总开关关闭时应直接返回 records 为空的整页对象，不查询数据域配置或分页数据，
     * 拉取日志仍照常记录；page/pageSize 仍应回显（回退到默认值），dataSize 为 0、
     * latestUpdateTime 为 null（app-sync-drop-changelog change design.md Decision 9，
     * 四次实现后修正）。
     */
    @Test
    void pull_shouldReturnEmpty_whenSyncMasterDisabled() {
        when(appConfigMapper.selectOne(any())).thenReturn(
                AppConfigEntity.builder().appRefId(1L).syncMasterEnabled(false).build());
        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();

        SyncPullPageVO result = service.pull(request);

        assertThat(result.getDataType()).isEqualTo(SyncDomain.ORG);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(20);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getDataSize()).isZero();
        assertThat(result.getLatestUpdateTime()).isNull();
        verify(appSyncDomainConfigMapper, never()).selectOne(any());
        verify(appPullRecordService).record(eq(1L), eq(SyncDomain.ORG), any(), eq(0));
    }

    /**
     * 数据域未开通同步时应返回 records 为空的整页对象，不查询分页数据，dataSize 为 0、
     * latestUpdateTime 为 null（design.md Decision 9，四次实现后修正）。
     */
    @Test
    void pull_shouldReturnEmpty_whenDomainDisabled() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(false).build());
        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();

        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getDataSize()).isZero();
        assertThat(result.getLatestUpdateTime()).isNull();
        verify(syncBizPageQueryResolver, never()).query(any(), any());
    }

    /**
     * 应用对外接口配置查不到（防御性场景）时，应保守视为总开关关闭，返回 records 为空的
     * 整页对象，dataSize 为 0、latestUpdateTime 为 null（design.md Decision 9，
     * 四次实现后修正）。
     */
    @Test
    void pull_shouldReturnEmpty_whenAppConfigNotFound() {
        when(appConfigMapper.selectOne(any())).thenReturn(null);
        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();

        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getDataSize()).isZero();
        assertThat(result.getLatestUpdateTime()).isNull();
        verify(syncBizPageQueryResolver, never()).query(any(), any());
    }

    /**
     * 翻到最后一页（数据域已开通，但分页查询结果为空）时也应返回 dataSize 为 0、
     * latestUpdateTime 为 null 的整页对象，作为拉取完毕的标识（design.md Decision 9，
     * 四次实现后修正）。
     */
    @Test
    void pull_shouldReturnZeroDataSizeAndNullLatestUpdateTime_whenLastPage() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).page(99).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getDataSize()).isZero();
        assertThat(result.getLatestUpdateTime()).isNull();
    }

    /**
     * 数据域已开通时应分页查询并按字段映射转换后返回，未传 page/pageSize 时应回退到默认
     * page=1、该数据域配置的 pageSize；响应顶层应正确回显 dataType/page/pageSize，
     * records 每条元素应合并 bizId/bizCode/updateTime、不单独携带 dataType。
     */
    @Test
    void pull_shouldReturnTransformedRecords_withDefaultPageAndConfiguredPageSize() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        LocalDateTime updateTime = LocalDateTime.now();
        SyncBizPageRow row = SyncBizPageRow.builder().id(88L).code("ORG001").updateTime(updateTime)
                .data(Map.of("code", "ORG001")).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row));
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.ORG), any()))
                .thenReturn(Map.of("orgCode", "ORG001"));

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getDataType()).isEqualTo(SyncDomain.ORG);
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getPageSize()).isEqualTo(30);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getDataSize()).isEqualTo(1);
        assertThat(result.getLatestUpdateTime()).isEqualTo(updateTime);
        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).containsEntry("bizId", 88L);
        assertThat(record).containsEntry("bizCode", "ORG001");
        assertThat(record).containsEntry("updateTime", updateTime);
        assertThat(record).containsEntry("orgCode", "ORG001");
        assertThat(record).doesNotContainKey("dataType");

        org.mockito.ArgumentCaptor<SyncBizPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SyncBizPageQuery.class);
        verify(syncBizPageQueryResolver).query(eq(SyncDomain.ORG), captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(30);
        assertThat(captor.getValue().getAllowedOrgIds()).isNull();
    }

    /**
     * 本页返回多条记录时，响应顶层 dataSize 应等于记录数、latestUpdateTime 应等于排序后
     * 最后一条记录（按 updateTime ASC, id ASC）的更新时间，而不是遍历比较取得
     * （app-sync-drop-changelog change design.md Decision 9，四次实现后修正）。
     */
    @Test
    void pull_shouldReturnDataSizeAndLatestUpdateTime_forMultipleRecords() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now();
        SyncBizPageRow row1 = SyncBizPageRow.builder().id(1L).code("ORG001").updateTime(t1)
                .data(Map.of("code", "ORG001")).build();
        SyncBizPageRow row2 = SyncBizPageRow.builder().id(2L).code("ORG002").updateTime(t2)
                .data(Map.of("code", "ORG002")).build();
        // 查询结果本来就已按 updateTime ASC, id ASC 排序，row2（更新时间晚）排在最后。
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row1, row2));
        when(fieldMappingTransformer.transform(any(), any(), any())).thenReturn(Map.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getDataSize()).isEqualTo(2);
        assertThat(result.getLatestUpdateTime()).isEqualTo(t2);
    }

    /**
     * 显式传入的正数 {@code pageSize} 应优先于数据域配置的默认值。
     */
    @Test
    void pull_shouldPreferExplicitPageSize() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).page(2).pageSize(5).build();
        service.pull(request);

        org.mockito.ArgumentCaptor<SyncBizPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SyncBizPageQuery.class);
        verify(syncBizPageQueryResolver).query(eq(SyncDomain.ORG), captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    /**
     * 非正数的 {@code pageSize} 应视同未传，回退到数据域配置的默认值。
     */
    @Test
    void pull_shouldFallbackToConfiguredPageSize_whenRequestedPageSizeNonPositive() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(30)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).pageSize(0).build();
        service.pull(request);

        org.mockito.ArgumentCaptor<SyncBizPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SyncBizPageQuery.class);
        verify(syncBizPageQueryResolver).query(eq(SyncDomain.ORG), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(30);
    }

    /**
     * ORG/USER/POSITION 三个数据域应下推组织范围过滤到分页查询参数。
     */
    @Test
    void pull_shouldPushDownOrgScope_forOrgScopeDomains() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.USER).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.USER)).thenReturn(Optional.of(Set.of(10L)));
        when(syncBizPageQueryResolver.query(eq(SyncDomain.USER), any())).thenReturn(List.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.USER).build();
        service.pull(request);

        org.mockito.ArgumentCaptor<SyncBizPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SyncBizPageQuery.class);
        verify(syncBizPageQueryResolver).query(eq(SyncDomain.USER), captor.capture());
        assertThat(captor.getValue().getAllowedOrgIds()).containsExactly(10L);
    }

    /**
     * APP/ROLE 数据域不受组织范围概念约束，不应调用组织范围解析组件，分页参数的
     * {@code allowedOrgIds} 恒为 {@code null}。
     */
    @Test
    void pull_shouldNotResolveOrgScope_forAppDomain() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.APP).syncEnabled(true).pageSize(20)
                        .build());
        when(syncBizPageQueryResolver.query(eq(SyncDomain.APP), any())).thenReturn(List.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.APP).build();
        service.pull(request);

        verify(appSyncOrgScopeResolver, never()).resolveAllowedOrgIds(any(), any());
        org.mockito.ArgumentCaptor<SyncBizPageQuery> captor =
                org.mockito.ArgumentCaptor.forClass(SyncBizPageQuery.class);
        verify(syncBizPageQueryResolver).query(eq(SyncDomain.APP), captor.capture());
        assertThat(captor.getValue().getAllowedOrgIds()).isNull();
    }

    /**
     * 应写入一条拉取日志，请求摘要包含页码、每页大小与本次传入的过滤条件概要，返回记录条数
     * 与实际返回结果一致。
     */
    @Test
    void pull_shouldRecordPullLogWithRequestSummary() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        SyncBizPageRow row = SyncBizPageRow.builder().id(1L).code("ORG001").updateTime(LocalDateTime.now())
                .data(Map.of()).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row));
        when(fieldMappingTransformer.transform(any(), any(), any())).thenReturn(Map.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).page(1)
                .ids(List.of(1L, 2L, 3L)).build();
        service.pull(request);

        org.mockito.ArgumentCaptor<String> summaryCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(appPullRecordService).record(eq(1L), eq(SyncDomain.ORG), summaryCaptor.capture(), eq(1));
        assertThat(summaryCaptor.getValue()).contains("page=1").contains("pageSize=20").contains("ids=3个");
    }

    /**
     * 拉取日志写入发生异常不应影响本次拉取请求的响应结果（add-app-sync-notify-pull-logs
     * change spec"日志写入失败不影响拉取主流程"场景）。
     */
    @Test
    void pull_shouldReturnNormally_whenRecordPullLogFails() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(false).build());
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(appPullRecordService).record(any(), any(), any(), anyInt());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).isEmpty();
    }

    /**
     * 未配置字段映射（字段映射转换结果原样透传业务字段，含 {@code status}）时，记录里应
     * 额外包含 {@code bizStatus} 固定键，其值应与业务表当前状态一致（app-sync-drop-changelog
     * change design.md Decision 1，二次实现后修正）。
     */
    @Test
    void pull_shouldIncludeBizStatus_consistentWithEntityStatus_whenNoFieldMapping() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        LocalDateTime updateTime = LocalDateTime.now();
        SyncBizPageRow row = SyncBizPageRow.builder().id(88L).code("ORG001").updateTime(updateTime).status(2000)
                .data(Map.of("code", "ORG001", "status", 2000)).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row));
        // 未配置字段映射时透传原始业务字段，含 status。
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.ORG), any()))
                .thenReturn(Map.of("code", "ORG001", "status", 2000));

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).containsEntry("bizStatus", 2000);
        assertThat(record).containsEntry("status", 2000);
    }

    /**
     * 配置了字段映射且映射结果不包含 {@code status} 字段时，记录里应仍然包含
     * {@code bizStatus} 固定键，值取自业务表当前状态，不因字段映射未包含 {@code status}
     * 而缺失（app-sync-drop-changelog change design.md Decision 1，二次实现后修正）。
     */
    @Test
    void pull_shouldIncludeBizStatus_whenFieldMappingExcludesStatus() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        LocalDateTime updateTime = LocalDateTime.now();
        SyncBizPageRow row = SyncBizPageRow.builder().id(88L).code("ORG001").updateTime(updateTime).status(2000)
                .data(Map.of("code", "ORG001", "status", 2000)).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row));
        // 配置了字段映射，转换结果只包含已映射字段，不含 status。
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.ORG), any()))
                .thenReturn(Map.of("orgCode", "ORG001"));

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).doesNotContainKey("status");
        assertThat(record).containsEntry("bizStatus", 2000);
    }

    /**
     * 停用（3000）/已删除（-1000）记录的 {@code bizStatus} 应分别正确反映业务表当前状态
     * （app-sync-drop-changelog change design.md Decision 1，二次实现后修正）。
     */
    @Test
    void pull_shouldReflectDisabledAndDeletedStatus_inBizStatus() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        LocalDateTime updateTime = LocalDateTime.now();
        SyncBizPageRow disabledRow = SyncBizPageRow.builder().id(1L).code("ORG001").updateTime(updateTime)
                .status(3000).data(Map.of()).build();
        SyncBizPageRow deletedRow = SyncBizPageRow.builder().id(2L).code("ORG002").updateTime(updateTime)
                .status(-1000).data(Map.of()).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any()))
                .thenReturn(List.of(disabledRow, deletedRow));
        when(fieldMappingTransformer.transform(any(), any(), any())).thenReturn(Map.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0)).containsEntry("bizStatus", 3000);
        assertThat(result.getRecords().get(1)).containsEntry("bizStatus", -1000);
    }

    /**
     * {@code dataType=DICT} 时应正常拉取并在记录里返回 {@code dictTypeCode} 固定键
     * （app-sync-drop-changelog change design.md Decision 7，三次实现后修正）。
     */
    @Test
    void pull_shouldReturnDictTypeCode_whenDataTypeIsDict() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.DICT).syncEnabled(true).pageSize(20)
                        .build());
        SyncBizPageRow row = SyncBizPageRow.builder().id(9L).code("MALE").updateTime(LocalDateTime.now())
                .status(2000).dictTypeCode("GENDER").data(Map.of("code", "MALE")).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.DICT), any())).thenReturn(List.of(row));
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.DICT), any()))
                .thenReturn(Map.of("code", "MALE"));

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.DICT).build();
        SyncPullPageVO result = service.pull(request);

        assertThat(result.getDataType()).isEqualTo(SyncDomain.DICT);
        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).containsEntry("dictTypeCode", "GENDER");
        assertThat(record).doesNotContainKey("userCode");
        verify(appSyncOrgScopeResolver, never()).resolveAllowedOrgIds(any(), any());
    }

    /**
     * 其余数据域（如组织）的记录不应包含 {@code userCode}/{@code dictTypeCode} 这两个领域
     * 特定固定键（键本身不应出现，而不是"键存在值为 null"）（design.md Decision 7/8，
     * 三次实现后修正）。
     */
    @Test
    void pull_shouldNotIncludeUserCodeOrDictTypeCode_forOrgDomain() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.ORG).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.ORG)).thenReturn(Optional.empty());
        SyncBizPageRow row = SyncBizPageRow.builder().id(1L).code("ORG001").updateTime(LocalDateTime.now())
                .status(2000).data(Map.of("code", "ORG001")).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.ORG), any())).thenReturn(List.of(row));
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.ORG), any()))
                .thenReturn(Map.of("code", "ORG001"));

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.ORG).build();
        SyncPullPageVO result = service.pull(request);

        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).doesNotContainKey("userCode");
        assertThat(record).doesNotContainKey("orgCode");
        assertThat(record).doesNotContainKey("dictTypeCode");
    }

    /**
     * {@code dataType=POSITION} 时记录应同时包含 {@code userCode}（关联用户的业务编码）与
     * {@code orgCode}（关联组织的业务编码）两个领域特定固定键（app-sync-drop-changelog change
     * design.md Decision 8，五次实现后修正）。
     */
    @Test
    void pull_shouldReturnUserCodeAndOrgCode_whenDataTypeIsPosition() {
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(
                AppSyncDomainConfigEntity.builder().syncDomain(SyncDomain.POSITION).syncEnabled(true).pageSize(20)
                        .build());
        when(appSyncOrgScopeResolver.resolveAllowedOrgIds(1L, SyncDomain.POSITION)).thenReturn(Optional.empty());
        SyncBizPageRow row = SyncBizPageRow.builder().id(10L).code(null).updateTime(LocalDateTime.now())
                .status(2000).userCode("USER100").orgCode("ORG030").data(Map.of()).build();
        when(syncBizPageQueryResolver.query(eq(SyncDomain.POSITION), any())).thenReturn(List.of(row));
        when(fieldMappingTransformer.transform(eq(1L), eq(SyncDomain.POSITION), any())).thenReturn(Map.of());

        SyncPullRequest request = SyncPullRequest.builder().dataType(SyncDomain.POSITION).build();
        SyncPullPageVO result = service.pull(request);

        Map<String, Object> record = result.getRecords().get(0);
        assertThat(record).containsEntry("userCode", "USER100");
        assertThat(record).containsEntry("orgCode", "ORG030");
    }
}
