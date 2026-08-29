package cn.nihility.rbac.app.sync.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigUpdateRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncDomainConfigVO;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingSaveRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingVO;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeRequest;
import cn.nihility.rbac.app.sync.dto.AppSyncOrgScopeVO;
import cn.nihility.rbac.app.sync.entity.AppSyncDomainConfigEntity;
import cn.nihility.rbac.app.sync.entity.AppSyncFieldMappingEntity;
import cn.nihility.rbac.app.sync.entity.AppSyncOrgScopeEntity;
import cn.nihility.rbac.app.sync.mapper.AppSyncDomainConfigMapper;
import cn.nihility.rbac.app.sync.mapper.AppSyncFieldMappingMapper;
import cn.nihility.rbac.app.sync.mapper.AppSyncOrgScopeMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.sync.openapi.config.SyncRateLimitProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppSyncConfigServiceImpl} 的单元测试，重点覆盖 6 行默认数据域配置生成（含
 * app-sync-notify-pull-api change 新增的任职域）、单个数据域启用开关/分页大小修改、字段映射
 * 整体替换（新增/更新/删除混合场景）及各类拒绝分支、管辖组织范围校验（app-sync-field-mapping
 * change tasks.md 10.1）。
 */
@ExtendWith(MockitoExtension.class)
class AppSyncConfigServiceImplTest {

    /** 被测服务的应用同步数据域配置数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncDomainConfigMapper appSyncDomainConfigMapper;

    /** 被测服务的应用同步字段映射数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncFieldMappingMapper appSyncFieldMappingMapper;

    /** 被测服务的应用同步组织范围数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSyncOrgScopeMapper appSyncOrgScopeMapper;

    /** 被测服务的应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppMapper appMapper;

    /** 被测服务的应用总配置数据访问依赖。 */
    @Mock
    private AppConfigMapper appConfigMapper;

    /** 对外同步接口请求规模上限配置。 */
    private SyncRateLimitProperties syncRateLimitProperties;

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务实例。 */
    private AppSyncConfigServiceImpl appSyncConfigService;

    /**
     * 每个用例执行前重新构造被测服务。管辖组织范围默认桩为 {@code Optional.empty()}（不受
     * 限制），受限场景在下方单独的用例中覆盖。
     */
    @BeforeEach
    void setUp() {
        syncRateLimitProperties = new SyncRateLimitProperties();
        appSyncConfigService = new AppSyncConfigServiceImpl(appSyncDomainConfigMapper, appSyncFieldMappingMapper,
                appSyncOrgScopeMapper, syncRateLimitProperties, appMapper, appConfigMapper, metadataFieldMapper,
                orgScopeService,
                currentOperatorService, operationLogRecorder);
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(orgScopeService.isOrgIdAllowed(any(), any())).thenAnswer(invocation -> orgScopeService
                .resolveAllowedOrgIds(invocation.getArgument(0))
                .map(allowed -> allowed.contains(invocation.getArgument(1)))
                .orElse(true));
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
    }

    /**
     * 创建默认数据域配置时，应插入组织/用户/任职/应用/角色/字典 6 行，均不启用、分页大小默认 20。
     */
    @Test
    void createDefaultDomainConfigs_shouldInsertSixRows() {
        appSyncConfigService.createDefaultDomainConfigs(10L, "1");

        ArgumentCaptor<AppSyncDomainConfigEntity> captor = ArgumentCaptor.forClass(AppSyncDomainConfigEntity.class);
        verify(appSyncDomainConfigMapper, times(6)).insert(captor.capture());

        List<AppSyncDomainConfigEntity> captured = captor.getAllValues();
        assertThat(captured).hasSize(6);
        assertThat(captured).extracting(AppSyncDomainConfigEntity::getSyncDomain)
                .containsExactly(SyncDomain.ORG, SyncDomain.USER, SyncDomain.POSITION, SyncDomain.APP,
                        SyncDomain.ROLE, SyncDomain.DICT);
        assertThat(captured).allSatisfy(entity -> {
            assertThat(entity.getAppRefId()).isEqualTo(10L);
            assertThat(entity.getSyncEnabled()).isFalse();
            assertThat(entity.getPageSize()).isEqualTo(20);
            assertThat(entity.getCreateBy()).isEqualTo("1");
        });
    }

    /**
     * 修改一个数据域的启用开关与分页大小时，应正常更新并记录操作日志。
     */
    @Test
    void updateDomainConfig_shouldUpdateFields() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppSyncDomainConfigEntity configEntity = buildDomainConfigEntity(1L, 10L, SyncDomain.ORG, false, 20);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appSyncDomainConfigMapper.selectOne(any())).thenReturn(configEntity);

        AppSyncDomainConfigUpdateRequest request = new AppSyncDomainConfigUpdateRequest();
        request.setSyncEnabled(true);
        request.setPageSize(50);

        AppSyncDomainConfigVO vo = appSyncConfigService.updateDomainConfig(10L, SyncDomain.ORG, request);

        assertThat(vo.getSyncEnabled()).isTrue();
        assertThat(vo.getPageSize()).isEqualTo(50);
        verify(appSyncDomainConfigMapper).updateById(configEntity);
        verify(operationLogRecorder).recordUpdate(eq("app"), eq(10L), eq("测试应用"), any(), any());
    }

    /**
     * 非法的数据域取值应拒绝。
     */
    @Test
    void updateDomainConfig_shouldThrowException_whenDomainInvalid() {
        AppSyncDomainConfigUpdateRequest request = new AppSyncDomainConfigUpdateRequest();
        request.setSyncEnabled(true);
        request.setPageSize(20);

        assertThatThrownBy(() -> appSyncConfigService.updateDomainConfig(10L, "FOO", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的数据域");
        verify(appSyncDomainConfigMapper, never()).updateById(any(AppSyncDomainConfigEntity.class));
    }

    /** 分页大小超过硬上限时应在更新配置和递增 epoch 前拒绝。 */
    @Test
    void updateDomainConfig_shouldRejectRequest_whenPageSizeExceedsLimit() {
        AppSyncDomainConfigUpdateRequest request = new AppSyncDomainConfigUpdateRequest();
        request.setSyncEnabled(true);
        request.setPageSize(501);

        assertThatThrownBy(() -> appSyncConfigService.updateDomainConfig(10L, SyncDomain.ORG, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("拉取分页大小不能超过 500");
        verify(appSyncDomainConfigMapper, never()).updateById(any(AppSyncDomainConfigEntity.class));
        verify(appConfigMapper, never()).incrementConfigEpoch(anyLong(), any(), any());
    }

    /**
     * 管辖范围受限时，修改一个不在管辖范围内的应用的数据域配置，应复用"应用不存在"错误文案。
     */
    @Test
    void updateDomainConfig_shouldThrowException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        AppSyncDomainConfigUpdateRequest request = new AppSyncDomainConfigUpdateRequest();
        request.setSyncEnabled(true);
        request.setPageSize(20);

        assertThatThrownBy(() -> appSyncConfigService.updateDomainConfig(10L, SyncDomain.ORG, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appSyncDomainConfigMapper, never()).updateById(any(AppSyncDomainConfigEntity.class));
    }

    /**
     * 查询字段映射时，{@code syncDomain=DICT} 应直接返回空列表，不查询数据库。
     */
    @Test
    void listFieldMappings_shouldReturnEmptyList_whenDomainIsDict() {
        List<AppSyncFieldMappingVO> result = appSyncConfigService.listFieldMappings(10L, SyncDomain.DICT);

        assertThat(result).isEmpty();
        verify(appSyncFieldMappingMapper, never()).selectByAppRefIdAndDomain(any(), any());
    }

    /**
     * 整体替换字段映射（新增+更新混合场景）：先按 (appRefId, syncDomain) 删除既有行，再按提交
     * 内容批量插入，返回保存后的视图对象列表。
     */
    @Test
    void replaceFieldMappings_shouldDeleteThenInsert() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appSyncFieldMappingMapper.selectCount(any())).thenReturn(1L);
        MetadataFieldEntity metadataField = buildMetadataField(1L, SyncDomain.ORG, "code", "组织编码");
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);
        when(appSyncFieldMappingMapper.selectByAppRefIdAndDomain(10L, SyncDomain.ORG)).thenReturn(List.of(
                AppSyncFieldMappingRow.builder()
                        .id(2L)
                        .metadataFieldId(1L)
                        .fieldName("组织编码")
                        .fieldCode("code")
                        .appFieldName("orgCode")
                        .appFieldCode("org_code")
                        .transformType(TransformType.NO_TRANSFORM)
                        .build()));

        AppSyncFieldMappingSaveRequest request = new AppSyncFieldMappingSaveRequest();
        request.setMetadataFieldId(1L);
        request.setAppFieldName("orgCode");
        request.setAppFieldCode("org_code");
        request.setTransformType(TransformType.NO_TRANSFORM);

        List<AppSyncFieldMappingVO> result =
                appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request));

        verify(appSyncFieldMappingMapper).delete(any());
        ArgumentCaptor<AppSyncFieldMappingEntity> captor = ArgumentCaptor.forClass(AppSyncFieldMappingEntity.class);
        verify(appSyncFieldMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(10L);
        assertThat(captor.getValue().getSyncDomain()).isEqualTo(SyncDomain.ORG);
        assertThat(captor.getValue().getMetadataFieldId()).isEqualTo(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldName()).isEqualTo("组织编码");
    }

    /**
     * {@code metadataFieldId} 不存在时应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenMetadataFieldNotFound() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(metadataFieldMapper.selectById(99L)).thenReturn(null);

        AppSyncFieldMappingSaveRequest request = validSaveRequest(99L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("源字段不存在或未启用");
        verify(appSyncFieldMappingMapper, never()).delete(any());
    }

    /**
     * {@code metadataFieldId} 状态未启用时应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenMetadataFieldDisabled() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, SyncDomain.ORG, "code", "组织编码");
        metadataField.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("源字段不存在或未启用");
    }

    /**
     * {@code metadataFieldId} 对应的 {@code bizType} 与 {@code syncDomain} 不一致时应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenBizTypeMismatch() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, SyncDomain.USER, "code", "用户编号");
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于数据域");
    }

    /**
     * 请求列表内 {@code metadataFieldId} 重复时应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenDuplicateMetadataFieldId() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        AppSyncFieldMappingSaveRequest request1 = validSaveRequest(1L);
        AppSyncFieldMappingSaveRequest request2 = validSaveRequest(1L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG,
                List.of(request1, request2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复添加映射");
        verify(appSyncFieldMappingMapper, never()).delete(any());
    }

    /**
     * 转换方式为固定值时，转换取值为空应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenFixedValueMissing() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, SyncDomain.ORG, "code", "组织编码");
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);
        request.setTransformType(TransformType.FIXED_VALUE);
        request.setTransformValue(null);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转换取值不能为空");
    }

    /**
     * 转换方式为转换脚本且语法错误时应拒绝保存。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenScriptSyntaxInvalid() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, SyncDomain.ORG, "code", "组织编码");
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);
        request.setTransformType(TransformType.SCRIPT);
        request.setTransformValue("function transform(value) { return value.toUpperCase(; }");

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转换脚本语法错误");
    }

    /**
     * 字典数据域不支持字段级同步配置，保存时应拒绝。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenDomainIsDict() {
        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.DICT, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持字段级同步配置");
        verify(appMapper, never()).selectById(anyLong());
    }

    /**
     * 管辖范围受限时，保存一个不在管辖范围内的应用的字段映射，应复用"应用不存在"错误文案。
     */
    @Test
    void replaceFieldMappings_shouldThrowException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        AppSyncFieldMappingSaveRequest request = validSaveRequest(1L);

        assertThatThrownBy(() -> appSyncConfigService.replaceFieldMappings(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appSyncFieldMappingMapper, never()).delete(any());
    }

    /**
     * 查询同步范围时，非法数据域（APP/ROLE/DICT）应拒绝，不查询数据库。
     */
    @Test
    void listOrgScope_shouldThrowException_whenDomainNotSupported() {
        assertThatThrownBy(() -> appSyncConfigService.listOrgScope(10L, SyncDomain.APP))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持同步范围配置");
        verify(appSyncOrgScopeMapper, never()).selectByAppRefIdAndDomain(any(), any());
    }

    /**
     * 查询同步范围时，合法数据域应委托给 Mapper 查询并直接返回。
     */
    @Test
    void listOrgScope_shouldDelegateToMapper_whenDomainSupported() {
        when(appSyncOrgScopeMapper.selectByAppRefIdAndDomain(10L, SyncDomain.ORG)).thenReturn(List.of(
                AppSyncOrgScopeVO.builder().orgId(100L).orgName("总部").includeChildren(true).build()));

        List<AppSyncOrgScopeVO> result = appSyncConfigService.listOrgScope(10L, SyncDomain.ORG);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgName()).isEqualTo("总部");
    }

    /**
     * 整体替换同步范围（先删后插）：应按 (appRefId, syncDomain) 删除既有行，再按提交内容
     * 批量插入，返回保存后的视图对象列表。
     */
    @Test
    void replaceOrgScope_shouldDeleteThenInsert() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appSyncOrgScopeMapper.selectCount(any())).thenReturn(2L);
        when(appSyncOrgScopeMapper.selectByAppRefIdAndDomain(10L, SyncDomain.ORG)).thenReturn(List.of(
                AppSyncOrgScopeVO.builder().orgId(200L).orgName("分部").includeChildren(false).build()));

        AppSyncOrgScopeRequest request = new AppSyncOrgScopeRequest();
        request.setOrgId(200L);
        request.setIncludeChildren(false);

        List<AppSyncOrgScopeVO> result = appSyncConfigService.replaceOrgScope(10L, SyncDomain.ORG, List.of(request));

        verify(appSyncOrgScopeMapper).delete(any());
        ArgumentCaptor<AppSyncOrgScopeEntity> captor = ArgumentCaptor.forClass(AppSyncOrgScopeEntity.class);
        verify(appSyncOrgScopeMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(10L);
        assertThat(captor.getValue().getSyncDomain()).isEqualTo(SyncDomain.ORG);
        assertThat(captor.getValue().getOrgId()).isEqualTo(200L);
        assertThat(captor.getValue().getIncludeChildren()).isFalse();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgName()).isEqualTo("分部");
    }

    /**
     * 提交空列表时应改为"全部数据"：删除既有行后不再插入任何行。
     */
    @Test
    void replaceOrgScope_shouldClearScope_whenRequestsEmpty() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appSyncOrgScopeMapper.selectCount(any())).thenReturn(1L);

        List<AppSyncOrgScopeVO> result = appSyncConfigService.replaceOrgScope(10L, SyncDomain.ORG, List.of());

        verify(appSyncOrgScopeMapper).delete(any());
        verify(appSyncOrgScopeMapper, never()).insert(any(AppSyncOrgScopeEntity.class));
        assertThat(result).isEmpty();
    }

    /**
     * 非法数据域（APP/ROLE/DICT）应拒绝保存，不做任何数据库操作。
     */
    @Test
    void replaceOrgScope_shouldThrowException_whenDomainNotSupported() {
        AppSyncOrgScopeRequest request = new AppSyncOrgScopeRequest();
        request.setOrgId(200L);
        request.setIncludeChildren(false);

        assertThatThrownBy(() -> appSyncConfigService.replaceOrgScope(10L, SyncDomain.DICT, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持同步范围配置");
        verify(appMapper, never()).selectById(anyLong());
        verify(appSyncOrgScopeMapper, never()).delete(any());
    }

    /**
     * 请求列表内 {@code orgId} 重复时应拒绝保存。
     */
    @Test
    void replaceOrgScope_shouldThrowException_whenDuplicateOrgId() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        AppSyncOrgScopeRequest request1 = new AppSyncOrgScopeRequest();
        request1.setOrgId(200L);
        request1.setIncludeChildren(false);
        AppSyncOrgScopeRequest request2 = new AppSyncOrgScopeRequest();
        request2.setOrgId(200L);
        request2.setIncludeChildren(true);

        assertThatThrownBy(() -> appSyncConfigService.replaceOrgScope(10L, SyncDomain.ORG,
                List.of(request1, request2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复添加");
        verify(appSyncOrgScopeMapper, never()).delete(any());
    }

    /** 组织范围根超过硬上限时应在修改数据库前拒绝。 */
    @Test
    void replaceOrgScope_shouldRejectRequest_whenRootCountExceedsLimit() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        List<AppSyncOrgScopeRequest> requests = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> {
                    AppSyncOrgScopeRequest request = new AppSyncOrgScopeRequest();
                    request.setOrgId((long) index);
                    request.setIncludeChildren(false);
                    return request;
                })
                .toList();

        assertThatThrownBy(() -> appSyncConfigService.replaceOrgScope(10L, SyncDomain.ORG, requests))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织范围根数量不能超过 100");
        verify(appSyncOrgScopeMapper, never()).delete(any());
        verify(appSyncOrgScopeMapper, never()).insert(any(AppSyncOrgScopeEntity.class));
    }

    /**
     * 管辖范围受限时，保存一个不在管辖范围内的应用的同步范围，应复用"应用不存在"错误文案。
     */
    @Test
    void replaceOrgScope_shouldThrowException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        AppSyncOrgScopeRequest request = new AppSyncOrgScopeRequest();
        request.setOrgId(200L);
        request.setIncludeChildren(false);

        assertThatThrownBy(() -> appSyncConfigService.replaceOrgScope(10L, SyncDomain.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appSyncOrgScopeMapper, never()).delete(any());
    }

    /**
     * 构造一个通过基本校验的字段映射保存请求。
     *
     * @param metadataFieldId 源字段 id
     * @return 字段映射保存请求
     */
    private AppSyncFieldMappingSaveRequest validSaveRequest(Long metadataFieldId) {
        AppSyncFieldMappingSaveRequest request = new AppSyncFieldMappingSaveRequest();
        request.setMetadataFieldId(metadataFieldId);
        request.setAppFieldName("orgCode");
        request.setAppFieldCode("org_code");
        request.setTransformType(TransformType.NO_TRANSFORM);
        return request;
    }

    /**
     * 构造一个测试用的应用实体。
     *
     * @param id     主键 id
     * @param orgId  所属组织 id
     * @param status 状态
     * @return 应用实体
     */
    private AppEntity buildAppEntity(long id, long orgId, int status) {
        return AppEntity.builder()
                .id(id)
                .name("测试应用")
                .code("app000")
                .ownerId(1L)
                .orgId(orgId)
                .showOrder(0)
                .status(status)
                .build();
    }

    /**
     * 构造一个测试用的数据域配置实体。
     *
     * @param id         主键 id
     * @param appRefId   应用 id
     * @param syncDomain 数据域
     * @param enabled    是否启用
     * @param pageSize   分页大小
     * @return 数据域配置实体
     */
    private AppSyncDomainConfigEntity buildDomainConfigEntity(long id, long appRefId, String syncDomain,
            boolean enabled, int pageSize) {
        LocalDateTime now = LocalDateTime.now();
        return AppSyncDomainConfigEntity.builder()
                .id(id)
                .appRefId(appRefId)
                .syncDomain(syncDomain)
                .syncEnabled(enabled)
                .pageSize(pageSize)
                .createBy("1")
                .createTime(now)
                .updateBy("1")
                .updateTime(now)
                .build();
    }

    /**
     * 构造一个测试用的元数据字段实体，状态默认启用。
     *
     * @param id        主键 id
     * @param bizType   业务对象类型
     * @param fieldCode 字段标识
     * @param fieldName 字段名称
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildMetadataField(long id, String bizType, String fieldCode, String fieldName) {
        return MetadataFieldEntity.builder()
                .id(id)
                .bizType(bizType)
                .tableName("tab_" + bizType.toLowerCase())
                .columnName(fieldCode)
                .columnType("VARCHAR(64)")
                .fieldCode(fieldCode)
                .fieldName(fieldName)
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
