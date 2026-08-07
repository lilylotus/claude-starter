package cn.nihility.rbac.app.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.dto.AppConfigVO;
import cn.nihility.rbac.app.dto.SecretKeyVO;
import cn.nihility.rbac.app.dto.SignAlgorithmUpdateRequest;
import cn.nihility.rbac.app.dto.SyncConfigUpdateRequest;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppConfigServiceImpl} 的单元测试，重点覆盖默认配置生成、SecretKey 重置、签名算法/
 * 同步配置修改、管辖组织范围校验等分支逻辑（app-api-credentials-config change tasks.md
 * 11.1）。
 */
@ExtendWith(MockitoExtension.class)
class AppConfigServiceImplTest {

    /** 本地开发默认 SM4 密钥（Base64），与 application.yml 里 rbac.app-secret.sm4-key 保持一致。 */
    private static final String SM4_KEY = "R8r2MjLGO0oLMkIVVXcPrQ==";

    /** 被测服务的应用配置数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppConfigMapper appConfigMapper;

    /** 被测服务的应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppMapper appMapper;

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务的操作日志记录组件依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogRecorder operationLogRecorder;

    /** 被测服务的应用对外接口凭证相关配置依赖，使用 Mockito 打桩。 */
    @Mock
    private AppSecretProperties appSecretProperties;

    /** 被测服务实例。 */
    private AppConfigServiceImpl appConfigService;

    /**
     * 每个用例执行前重新构造被测服务。管辖组织范围默认桩为 {@code Optional.empty()}（不受
     * 限制），受限场景在下方单独的用例中覆盖；SM4 密钥默认桩为一个真实生成的 Base64 密钥，
     * 供 {@link Sm4JdkUtils#encrypt}/{@link Sm4JdkUtils#decrypt} 在测试中往返验证。
     */
    @BeforeEach
    void setUp() {
        appConfigService = new AppConfigServiceImpl(appConfigMapper, appMapper, orgScopeService,
                currentOperatorService, operationLogRecorder, appSecretProperties);
        lenient().when(appSecretProperties.getSm4Key()).thenReturn(SM4_KEY);
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(orgScopeService.isOrgIdAllowed(any(), any())).thenAnswer(invocation -> orgScopeService
                .resolveAllowedOrgIds(invocation.getArgument(0))
                .map(allowed -> allowed.contains(invocation.getArgument(1)))
                .orElse(true));
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
    }

    /**
     * 创建默认配置时，应生成 AppId/AccessKey/SecretKey 凭证三元组，SecretKey 落库前需经
     * SM4 加密（不落明文），签名算法默认 SHA256，四个同步开关默认全部关闭。
     */
    @Test
    void createDefaultConfig_shouldGenerateCredentials_andEncryptSecretKey() {
        appConfigService.createDefaultConfig(10L, "1");

        ArgumentCaptor<AppConfigEntity> captor = ArgumentCaptor.forClass(AppConfigEntity.class);
        verify(appConfigMapper).insert(captor.capture());
        AppConfigEntity captured = captor.getValue();

        assertThat(captured.getAppRefId()).isEqualTo(10L);
        assertThat(captured.getAppId()).hasSize(24).matches("[0-9a-f]{24}");
        assertThat(captured.getAccessKey()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(captured.getSecretKey()).isNotBlank();
        // 落库的是 SM4 密文（Base64），不是明文；用同一密钥解密应能拿回一个 48 位十六进制明文。
        String decrypted = Sm4JdkUtils.decrypt(captured.getSecretKey(), SM4_KEY);
        assertThat(decrypted).hasSize(48).matches("[0-9a-f]{48}");
        assertThat(captured.getSecretKey()).isNotEqualTo(decrypted);
        assertThat(captured.getSignAlgorithm()).isEqualTo(SignAlgorithm.SHA256);
        assertThat(captured.getSyncMode()).isEqualTo(SyncMode.PULL);
        assertThat(captured.getSyncOrgEnabled()).isFalse();
        assertThat(captured.getSyncUserEnabled()).isFalse();
        assertThat(captured.getSyncAppEnabled()).isFalse();
        assertThat(captured.getSyncDictEnabled()).isFalse();
        assertThat(captured.getCreateBy()).isEqualTo("1");
        assertThat(captured.getUpdateBy()).isEqualTo("1");
        assertThat(captured.getCreateTime()).isNotNull();
        assertThat(captured.getUpdateTime()).isNotNull();
    }

    /**
     * 查询配置详情时，应转换为不含 SecretKey 字段的视图对象，且不做管辖组织范围校验
     * （对齐 {@code AppServiceImpl#getById} 既有行为）。
     */
    @Test
    void getByAppId_shouldReturnVO_withoutOrgScopeCheck() {
        when(appConfigMapper.selectOne(any())).thenReturn(buildConfigEntity(10L));

        AppConfigVO vo = appConfigService.getByAppId(10L);

        assertThat(vo.getAppId()).isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(vo.getAccessKey()).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        assertThat(vo.getSignAlgorithm()).isEqualTo(SignAlgorithm.SHA256);
        verify(appMapper, never()).selectById(any());
        verify(orgScopeService, never()).isOrgIdAllowed(any(), any());
    }

    /**
     * 查询一个不存在的配置时，应抛出业务异常。
     */
    @Test
    void getByAppId_shouldThrowBusinessException_whenNotFound() {
        when(appConfigMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> appConfigService.getByAppId(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
    }

    /**
     * 重置 SecretKey 时，应生成一个新的明文并加密落库，响应体返回明文（新旧值不同），
     * 且落库值与响应体明文不同（不落明文）。
     */
    @Test
    void resetSecretKey_shouldGenerateNewValue_andReturnPlainText() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        String oldCipherText = configEntity.getSecretKey();
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);

        SecretKeyVO result = appConfigService.resetSecretKey(10L);

        assertThat(result.getSecretKey()).hasSize(48).matches("[0-9a-f]{48}");

        ArgumentCaptor<AppConfigEntity> captor = ArgumentCaptor.forClass(AppConfigEntity.class);
        verify(appConfigMapper).updateById(captor.capture());
        AppConfigEntity updated = captor.getValue();
        assertThat(updated.getSecretKey()).isNotEqualTo(oldCipherText);
        assertThat(updated.getSecretKey()).isNotEqualTo(result.getSecretKey());
        String decrypted = Sm4JdkUtils.decrypt(updated.getSecretKey(), SM4_KEY);
        assertThat(decrypted).isEqualTo(result.getSecretKey());

        // 操作日志不得记录密钥明文/密文，只记录"发生了重置"这一事实。
        ArgumentCaptor<java.util.Map<String, Object>> afterCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(operationLogRecorder).recordUpdate(org.mockito.ArgumentMatchers.eq("app"),
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("测试应用"),
                any(java.util.Map.class), afterCaptor.capture());
        assertThat(afterCaptor.getValue()).doesNotContainValue(result.getSecretKey());
        assertThat(afterCaptor.getValue()).doesNotContainValue(updated.getSecretKey());
    }

    /**
     * 管辖范围受限时，重置一个不在管辖范围内的应用的 SecretKey，应复用"应用不存在"错误文案，
     * 不额外暴露越权探测信号（org-scope-write-guard change design.md Decision 2）。
     */
    @Test
    void resetSecretKey_shouldThrowBusinessException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        assertThatThrownBy(() -> appConfigService.resetSecretKey(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 管辖范围受限且应用所属组织在管辖范围内时，重置应正常放行。
     */
    @Test
    void resetSecretKey_shouldSucceed_whenOrgInScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(100L)));

        SecretKeyVO result = appConfigService.resetSecretKey(10L);

        assertThat(result.getSecretKey()).isNotBlank();
        verify(appConfigMapper).updateById(any(AppConfigEntity.class));
    }

    /**
     * 应用不存在（或已被逻辑删除）时，重置 SecretKey 应拒绝。
     */
    @Test
    void resetSecretKey_shouldThrowBusinessException_whenAppNotFound() {
        when(appMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> appConfigService.resetSecretKey(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 修改签名算法时，应更新字段并记录操作日志，返回更新后的视图对象。
     */
    @Test
    void updateSignAlgorithm_shouldUpdateField() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);

        SignAlgorithmUpdateRequest request = new SignAlgorithmUpdateRequest();
        request.setSignAlgorithm(SignAlgorithm.SM3);

        AppConfigVO vo = appConfigService.updateSignAlgorithm(10L, request);

        assertThat(vo.getSignAlgorithm()).isEqualTo(SignAlgorithm.SM3);
        verify(appConfigMapper).updateById(configEntity);
        verify(operationLogRecorder).recordUpdate(org.mockito.ArgumentMatchers.eq("app"),
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("测试应用"),
                any(java.util.Map.class), any(java.util.Map.class));
    }

    /**
     * 管辖范围受限时，修改一个不在管辖范围内的应用的签名算法，应复用"应用不存在"错误文案。
     */
    @Test
    void updateSignAlgorithm_shouldThrowBusinessException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        SignAlgorithmUpdateRequest request = new SignAlgorithmUpdateRequest();
        request.setSignAlgorithm(SignAlgorithm.SM3);

        assertThatThrownBy(() -> appConfigService.updateSignAlgorithm(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 非法的签名算法取值应被 Bean Validation 拒绝（{@code @Pattern} 校验），不应到达服务层。
     */
    @Test
    void signAlgorithmUpdateRequest_shouldRejectInvalidValue() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            SignAlgorithmUpdateRequest invalid = new SignAlgorithmUpdateRequest();
            invalid.setSignAlgorithm("MD5");
            Set<ConstraintViolation<SignAlgorithmUpdateRequest>> violations = validator.validate(invalid);
            assertThat(violations).isNotEmpty();

            SignAlgorithmUpdateRequest valid = new SignAlgorithmUpdateRequest();
            valid.setSignAlgorithm(SignAlgorithm.SHA256);
            assertThat(validator.validate(valid)).isEmpty();

            SignAlgorithmUpdateRequest blank = new SignAlgorithmUpdateRequest();
            blank.setSignAlgorithm("");
            assertThat(validator.validate(blank)).isNotEmpty();
        }
    }

    /**
     * 非法的同步方式取值应被 Bean Validation 拒绝（{@code @Pattern} 校验），不应到达服务层。
     */
    @Test
    void syncConfigUpdateRequest_shouldRejectInvalidSyncMode() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            SyncConfigUpdateRequest invalid = validSyncConfigUpdateRequest();
            invalid.setSyncMode("WEBHOOK");
            assertThat(validator.validate(invalid)).isNotEmpty();

            SyncConfigUpdateRequest blank = validSyncConfigUpdateRequest();
            blank.setSyncMode("");
            assertThat(validator.validate(blank)).isNotEmpty();

            SyncConfigUpdateRequest valid = validSyncConfigUpdateRequest();
            assertThat(validator.validate(valid)).isEmpty();
        }
    }

    /**
     * 构造一个通过 Bean Validation 的合法 {@link SyncConfigUpdateRequest}，供校验类用例复用。
     *
     * @return 合法的同步配置修改请求
     */
    private SyncConfigUpdateRequest validSyncConfigUpdateRequest() {
        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(true);
        request.setSyncUserEnabled(true);
        request.setSyncAppEnabled(true);
        request.setSyncDictEnabled(true);
        request.setSyncMode(SyncMode.PULL);
        return request;
    }

    /**
     * 修改同步配置时，应更新四个开关字段并记录操作日志。
     */
    @Test
    void updateSyncConfig_shouldUpdateAllFlags() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(true);
        request.setSyncUserEnabled(true);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(true);
        request.setSyncMode(SyncMode.PULL);

        AppConfigVO vo = appConfigService.updateSyncConfig(10L, request);

        assertThat(vo.getSyncOrgEnabled()).isTrue();
        assertThat(vo.getSyncUserEnabled()).isTrue();
        assertThat(vo.getSyncAppEnabled()).isFalse();
        assertThat(vo.getSyncDictEnabled()).isTrue();
        assertThat(vo.getSyncMode()).isEqualTo(SyncMode.PULL);
        verify(appConfigMapper).updateById(configEntity);
    }

    /**
     * 管辖范围受限且应用所属组织在管辖范围内时，修改同步配置应正常放行。
     */
    @Test
    void updateSyncConfig_shouldSucceed_whenOrgInScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(100L)));

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(true);
        request.setSyncUserEnabled(false);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(false);
        request.setSyncMode(SyncMode.PULL);

        AppConfigVO vo = appConfigService.updateSyncConfig(10L, request);

        assertThat(vo.getSyncOrgEnabled()).isTrue();
        verify(appConfigMapper).updateById(any(AppConfigEntity.class));
    }

    /**
     * 管辖范围受限时，修改一个不在管辖范围内的应用的同步配置，应复用"应用不存在"错误文案。
     */
    @Test
    void updateSyncConfig_shouldThrowBusinessException_whenOrgOutOfScope() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(true);
        request.setSyncUserEnabled(true);
        request.setSyncAppEnabled(true);
        request.setSyncDictEnabled(true);
        request.setSyncMode(SyncMode.PULL);

        assertThatThrownBy(() -> appConfigService.updateSyncConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 同步方式为 PULL 时，不要求通知回调接口地址，可以为空。
     */
    @Test
    void updateSyncConfig_shouldNotRequireNotifyUrl_whenPullMode() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(false);
        request.setSyncUserEnabled(false);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(false);
        request.setSyncMode(SyncMode.PULL);

        AppConfigVO vo = appConfigService.updateSyncConfig(10L, request);

        assertThat(vo.getSyncMode()).isEqualTo(SyncMode.PULL);
        verify(appConfigMapper).updateById(configEntity);
    }

    /**
     * 同步方式为 NOTIFY 时，通知回调接口地址为空应拒绝。
     */
    @Test
    void updateSyncConfig_shouldThrowBusinessException_whenNotifyModeMissingUrl() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(false);
        request.setSyncUserEnabled(false);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(false);
        request.setSyncMode(SyncMode.NOTIFY);

        assertThatThrownBy(() -> appConfigService.updateSyncConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("接口地址不能为空");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 同步方式为 NOTIFY 时，通知回调接口地址格式不合法（非 http/https）应拒绝。
     */
    @Test
    void updateSyncConfig_shouldThrowBusinessException_whenNotifyUrlInvalidFormat() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(false);
        request.setSyncUserEnabled(false);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(false);
        request.setSyncMode(SyncMode.NOTIFY);
        request.setNotifyUrl("ftp://not-http-or-https.example.com/callback");

        assertThatThrownBy(() -> appConfigService.updateSyncConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("接口地址格式不正确");
        verify(appConfigMapper, never()).updateById(any(AppConfigEntity.class));
    }

    /**
     * 同步方式为 NOTIFY 且接口地址合法时，应正常保存通知地址与自定义参数，
     * 查询接口应能把 JSON 文本还原为 Map。
     */
    @Test
    void updateSyncConfig_shouldPersistNotifyUrlAndParams_whenNotifyModeValid() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.ENABLED);
        AppConfigEntity configEntity = buildConfigEntity(10L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appConfigMapper.selectOne(any())).thenReturn(configEntity);

        SyncConfigUpdateRequest request = new SyncConfigUpdateRequest();
        request.setSyncOrgEnabled(true);
        request.setSyncUserEnabled(false);
        request.setSyncAppEnabled(false);
        request.setSyncDictEnabled(false);
        request.setSyncMode(SyncMode.NOTIFY);
        request.setNotifyUrl("https://partner.example.com/callback");
        request.setNotifyParams(Map.of("token", "abc123", "source", "rbac"));

        AppConfigVO vo = appConfigService.updateSyncConfig(10L, request);

        assertThat(vo.getSyncMode()).isEqualTo(SyncMode.NOTIFY);
        assertThat(vo.getNotifyUrl()).isEqualTo("https://partner.example.com/callback");
        assertThat(vo.getNotifyParams()).containsEntry("token", "abc123").containsEntry("source", "rbac");

        ArgumentCaptor<AppConfigEntity> captor = ArgumentCaptor.forClass(AppConfigEntity.class);
        verify(appConfigMapper).updateById(captor.capture());
        assertThat(captor.getValue().getNotifyParams()).contains("abc123").contains("rbac");
    }

    /**
     * 查询配置详情时，{@code notifyParams} 为空（未配置过通知）应还原为空 Map 而不是 null，
     * 便于前端直接渲染，不需要额外判空。
     */
    @Test
    void getByAppId_shouldReturnEmptyNotifyParamsMap_whenNeverConfigured() {
        when(appConfigMapper.selectOne(any())).thenReturn(buildConfigEntity(10L));

        AppConfigVO vo = appConfigService.getByAppId(10L);

        assertThat(vo.getNotifyParams()).isNotNull().isEmpty();
        assertThat(vo.getSyncMode()).isEqualTo(SyncMode.PULL);
    }

    /**
     * 应用已被逻辑删除时，修改签名算法应拒绝，与"应用不存在"一致。
     */
    @Test
    void updateSignAlgorithm_shouldThrowBusinessException_whenAppDeleted() {
        AppEntity appEntity = buildAppEntity(10L, 100L, AppStatus.DELETED);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        SignAlgorithmUpdateRequest request = new SignAlgorithmUpdateRequest();
        request.setSignAlgorithm(SignAlgorithm.SM3);

        assertThatThrownBy(() -> appConfigService.updateSignAlgorithm(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
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
     * 构造一个测试用的应用配置实体，SecretKey 以一个真实 SM4 密文占位。
     *
     * @param appRefId 应用 id
     * @return 应用配置实体
     */
    private AppConfigEntity buildConfigEntity(long appRefId) {
        LocalDateTime now = LocalDateTime.now();
        return AppConfigEntity.builder()
                .id(1L)
                .appRefId(appRefId)
                .appId("aaaaaaaaaaaaaaaaaaaaaaaa")
                .accessKey("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .secretKey(Sm4JdkUtils.encrypt("c".repeat(48), SM4_KEY))
                .signAlgorithm(SignAlgorithm.SHA256)
                .syncOrgEnabled(false)
                .syncUserEnabled(false)
                .syncAppEnabled(false)
                .syncDictEnabled(false)
                .syncMode(SyncMode.PULL)
                .createBy("1")
                .createTime(now)
                .updateBy("1")
                .updateTime(now)
                .build();
    }
}
