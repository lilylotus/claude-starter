package cn.nihility.rbac.app.authconfig.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingSaveRequest;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingVO;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.entity.AppUserinfoFieldMappingEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.authconfig.mapper.AppUserinfoFieldMappingMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import java.time.LocalDateTime;
import java.util.List;
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
 * {@link AppAuthConfigServiceImpl} 的单元测试，覆盖 app-auth-protocol-config change
 * spec.md 全部场景：默认配置生成、查询（含只读协议接口地址计算、懒创建默认行）、管辖组织
 * 范围校验、协议类型与匹配列表的关联校验、操作日志记录。
 */
@ExtendWith(MockitoExtension.class)
class AppAuthConfigServiceImplTest {

    @Mock
    private AppAuthConfigMapper appAuthConfigMapper;

    @Mock
    private AppUserinfoFieldMappingMapper appUserinfoFieldMappingMapper;

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppMapper appMapper;

    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    @Mock
    private OrgScopeService orgScopeService;

    @Mock
    private CurrentOperatorService currentOperatorService;

    @Mock
    private OperationLogRecorder operationLogRecorder;

    private AppAuthConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppAuthConfigServiceImpl(appAuthConfigMapper, appUserinfoFieldMappingMapper, appConfigMapper,
                appMapper, metadataFieldMapper, orgScopeService, currentOperatorService, operationLogRecorder);
        lenient().when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        lenient().when(orgScopeService.isOrgIdAllowed(any(), any())).thenAnswer(invocation -> orgScopeService
                .resolveAllowedOrgIds(invocation.getArgument(0))
                .map(allowed -> allowed.contains(invocation.getArgument(1)))
                .orElse(true));
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
        lenient().when(appConfigMapper.selectOne(any())).thenReturn(buildAppConfigEntity(10L));
    }

    /**
     * 新建应用自动生成默认认证配置：协议类型"无"，两个匹配列表均为空。
     */
    @Test
    void createDefaultConfig_shouldInsertDefaultRow() {
        service.createDefaultConfig(10L, "1");

        ArgumentCaptor<AppAuthConfigEntity> captor = ArgumentCaptor.forClass(AppAuthConfigEntity.class);
        verify(appAuthConfigMapper).insert(captor.capture());
        AppAuthConfigEntity captured = captor.getValue();

        assertThat(captured.getAppRefId()).isEqualTo(10L);
        assertThat(captured.getAuthProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(JacksonUtils.toObj(captured.getServicePatterns(), JacksonUtils.LIST_STRING_TYPE_REFERENCE))
                .isEmpty();
        assertThat(JacksonUtils.toObj(captured.getLoginMethods(), JacksonUtils.LIST_STRING_TYPE_REFERENCE))
                .containsExactly(LoginMethod.PASSWORD);
        assertThat(captured.getCreateBy()).isEqualTo("1");
        assertThat(captured.getUpdateBy()).isEqualTo("1");
    }

    /**
     * 查询已配置 CAS 协议的应用，应返回协议类型、匹配列表，以及按 AppId 计算出的 CAS 三个
     * 协议接口地址（含 OAuth2 三个地址，无论当前协议类型为何都一并返回）。
     */
    @Test
    void getByAppId_shouldReturnConfig_withComputedUrls() {
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigVO vo = service.getByAppId(10L);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(vo.getServicePatterns()).containsExactly("https://partner.example.com/**");
        assertThat(vo.getCasLoginUrl()).isEqualTo("/api/authn/cas/open-app-id-10/login");
        assertThat(vo.getCasServiceValidateUrl()).isEqualTo("/api/authn/cas/open-app-id-10/p3/serviceValidate");
        assertThat(vo.getCasLogoutUrl()).isEqualTo("/api/authn/cas/open-app-id-10/logout");
        assertThat(vo.getOauthAuthorizeUrl()).isEqualTo("/api/authn/oauth/authorize");
        assertThat(vo.getOauthTokenUrl()).isEqualTo("/api/authn/oauth/token");
        assertThat(vo.getOauthUserInfoUrl()).isEqualTo("/api/authn/oauth/userinfo");
    }

    /**
     * 存量数据 {@code loginMethods} 为空（未设置）时，查询应按仅允许口令登录处理
     * （add-sso-login-methods change design.md Decision 1，防御性兼容）。
     */
    @Test
    void getByAppId_shouldDefaultLoginMethodsToPasswordOnly_whenBlank() {
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.NONE, List.of());
        entity.setLoginMethods(null);
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigVO vo = service.getByAppId(10L);

        assertThat(vo.getLoginMethods()).containsExactly(LoginMethod.PASSWORD);
    }

    /**
     * 已保存的 {@code loginMethods} 应原样返回。
     */
    @Test
    void getByAppId_shouldReturnSavedLoginMethods() {
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.NONE, List.of());
        entity.setLoginMethods(JacksonUtils.toJson(List.of(LoginMethod.PASSWORD, LoginMethod.SMS)));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigVO vo = service.getByAppId(10L);

        assertThat(vo.getLoginMethods()).containsExactlyInAnyOrder(LoginMethod.PASSWORD, LoginMethod.SMS);
    }

    /**
     * 提交的登录认证方式列表缺少 {@code PASSWORD} 时应自动补齐，而不是拒绝请求
     * （design.md Decision 1）。
     */
    @Test
    void updateConfig_shouldAutoAddPassword_whenMissingFromLoginMethods() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLoginMethods(List.of(LoginMethod.QRCODE));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getLoginMethods()).containsExactlyInAnyOrder(LoginMethod.PASSWORD, LoginMethod.QRCODE);
    }

    /**
     * 未提交任何登录认证方式（{@code null}）时应保存为仅含 {@code PASSWORD}。
     */
    @Test
    void updateConfig_shouldSavePasswordOnly_whenLoginMethodsNull() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLoginMethods(null);

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getLoginMethods()).containsExactly(LoginMethod.PASSWORD);
    }

    /**
     * 提交非法的登录认证方式取值时应拒绝保存，不落库。
     */
    @Test
    void updateConfig_shouldRejectInvalidLoginMethod() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLoginMethods(List.of("WECHAT"));

        assertThatThrownBy(() -> service.updateConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非法的登录认证方式");
        verify(appAuthConfigMapper, never()).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 合法提交的登录认证方式列表应原样保存（含 {@code PASSWORD} 本身不重复）。
     */
    @Test
    void updateConfig_shouldSaveLoginMethods_whenValid() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLoginMethods(List.of(LoginMethod.PASSWORD, LoginMethod.SMS, LoginMethod.QRCODE));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getLoginMethods())
                .containsExactlyInAnyOrder(LoginMethod.PASSWORD, LoginMethod.SMS, LoginMethod.QRCODE);
    }

    /**
     * 查不到认证配置记录时（存量应用先于本表存在），应懒创建一条默认记录后返回，而不是抛
     * "应用不存在"。
     */
    @Test
    void getByAppId_shouldLazyCreateDefault_whenNotFound() {
        AppAuthConfigEntity defaultEntity = buildEntity(10L, AuthProtocol.NONE, List.of());
        when(appAuthConfigMapper.selectOne(any())).thenReturn(null, defaultEntity);

        AppAuthConfigVO vo = service.getByAppId(10L);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.NONE);
        verify(appAuthConfigMapper).insert(any(AppAuthConfigEntity.class));
        verify(appAuthConfigMapper, times(2)).selectOne(any());
    }

    /**
     * 无管辖权限时修改被拒绝，不落库。
     */
    @Test
    void updateConfig_shouldRejectWhenOrgOutOfScope() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);

        assertThatThrownBy(() -> service.updateConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appAuthConfigMapper, never()).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 修改成功记录操作日志：把协议类型从"无"改为 CAS，并提交一条 service 匹配规则。
     */
    @Test
    void updateConfig_shouldRecordOperationLog_onSuccess() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.NONE, List.of());
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setServicePatterns(List.of("https://partner.example.com/**"));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(vo.getServicePatterns()).containsExactly("https://partner.example.com/**");
        verify(appAuthConfigMapper).updateById(any(AppAuthConfigEntity.class));
        verify(operationLogRecorder).recordUpdate(org.mockito.ArgumentMatchers.eq("app"),
                org.mockito.ArgumentMatchers.eq(10L), org.mockito.ArgumentMatchers.eq("测试应用"),
                any(Map.class), any(Map.class));
    }

    /**
     * 选择 CAS 协议但未提供匹配规则时应拒绝。
     */
    @Test
    void updateConfig_shouldRejectCasWithoutPatterns() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setServicePatterns(List.of());

        assertThatThrownBy(() -> service.updateConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要一条规则");
        verify(appAuthConfigMapper, never()).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 选择 OAuth2.0 协议但未提供匹配规则时应拒绝。
     */
    @Test
    void updateConfig_shouldRejectOauth2WithoutPatterns() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.OAUTH2);

        assertThatThrownBy(() -> service.updateConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少需要一条规则");
        verify(appAuthConfigMapper, never()).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 协议类型改回"无"时，应清空历史匹配列表（不保留旧值）。
     */
    @Test
    void updateConfig_shouldClearPatterns_whenProtocolNone() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.CAS, List.of("https://partner.example.com/**"));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(vo.getServicePatterns()).isEmpty();
    }

    /**
     * 协议类型从 CAS 切换为 OAuth2.0 时，应沿用同一份 {@code servicePatterns} 存储，用本次
     * 提交的新列表整体替换，不存在"旧 CAS 列表"与"新 OAuth2.0 列表"并存或混淆的情况
     * （unify-app-auth-service-patterns change spec.md "协议类型从 CAS 切换为 OAuth2.0 时
     * 沿用同一份匹配列表存储" Scenario）。
     */
    @Test
    void updateConfig_shouldReplacePatterns_whenSwitchingCasToOauth2() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.CAS, List.of("https://cas.example.com/**"));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.OAUTH2);
        request.setServicePatterns(List.of("https://oauth.example.com/**"));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.OAUTH2);
        assertThat(vo.getServicePatterns()).containsExactly("https://oauth.example.com/**");
        assertThat(entity.getServicePatterns())
                .isEqualTo(JacksonUtils.toJson(List.of("https://oauth.example.com/**")));
    }

    /**
     * 登出通知回调地址非法（非 http/https URL）时应拒绝保存，不落库（add-sso-single-logout
     * change spec.md "非法地址格式被拒绝" Scenario）。
     */
    @Test
    void updateConfig_shouldRejectInvalidLogoutNotifyUrl() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLogoutNotifyUrl("not-a-valid-url");

        assertThatThrownBy(() -> service.updateConfig(10L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登出通知回调地址格式不正确");
        verify(appAuthConfigMapper, never()).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 登出通知回调地址留空时应正常保存成功（add-sso-single-logout change spec.md "登出通知
     * 回调地址留空时保存成功" Scenario）。
     */
    @Test
    void updateConfig_shouldSaveSuccessfully_whenLogoutNotifyUrlBlank() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLogoutNotifyUrl("  ");

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getLogoutNotifyUrl()).isNull();
        verify(appAuthConfigMapper).updateById(any(AppAuthConfigEntity.class));
    }

    /**
     * 提交一个合法的登出通知回调地址时应正常保存并回填到视图对象。
     */
    @Test
    void updateConfig_shouldSaveLogoutNotifyUrl_whenValid() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);
        request.setLogoutNotifyUrl("https://partner.example.com/sso/logout-notify");

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getLogoutNotifyUrl()).isEqualTo("https://partner.example.com/sso/logout-notify");
    }

    /**
     * 匹配规则列表在保存前应去除空白项与重复项。
     */
    @Test
    void updateConfig_shouldTrimAndDedupePatterns() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setServicePatterns(List.of(" https://a.example.com/** ", "https://a.example.com/**", "  "));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getServicePatterns()).containsExactly("https://a.example.com/**");
    }

    private AppEntity buildAppEntity(long id, long orgId) {
        return AppEntity.builder()
                .id(id)
                .name("测试应用")
                .code("app000")
                .ownerId(1L)
                .orgId(orgId)
                .showOrder(0)
                .status(AppStatus.ENABLED)
                .build();
    }

    private AppConfigEntity buildAppConfigEntity(long appRefId) {
        return AppConfigEntity.builder()
                .id(1L)
                .appRefId(appRefId)
                .appId("open-app-id-" + appRefId)
                .accessKey("access-key")
                .build();
    }

    private AppAuthConfigEntity buildEntity(long appRefId, String authProtocol, List<String> servicePatterns) {
        LocalDateTime now = LocalDateTime.now();
        return AppAuthConfigEntity.builder()
                .id(1L)
                .appRefId(appRefId)
                .authProtocol(authProtocol)
                .servicePatterns(JacksonUtils.toJson(servicePatterns))
                .createBy("1")
                .createTime(now)
                .updateBy("1")
                .updateTime(now)
                .build();
    }

    /**
     * 该应用在 {@code tab_app_userinfo_field_mapping} 无任何记录时，应返回现算的默认两行
     * （"用户ID"、"姓名"），且不触发任何写库操作（add-sso-userinfo-field-mapping change
     * spec.md "未配置过映射时返回默认两行" Scenario）。
     */
    @Test
    void listUserinfoFieldMappings_shouldReturnDefaultTwoRows_whenNoRecords() {
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of());
        MetadataFieldEntity nameField = buildMetadataField(2L, FormFieldBizType.USER, "name", "用户姓名");
        when(metadataFieldMapper.selectOne(any())).thenReturn(nameField);

        List<AppUserinfoFieldMappingVO> result = service.listUserinfoFieldMappings(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMetadataFieldId()).isNull();
        assertThat(result.get(0).getAppFieldCode()).isEqualTo("id");
        assertThat(result.get(0).getFieldName()).isEqualTo("用户ID");
        assertThat(result.get(1).getMetadataFieldId()).isEqualTo(2L);
        assertThat(result.get(1).getAppFieldCode()).isEqualTo("name");
        verify(appUserinfoFieldMappingMapper, never()).insert(any(AppUserinfoFieldMappingEntity.class));
    }

    /**
     * 该应用已保存过映射记录时，应直接返回已保存内容，而不是默认值（spec.md "已配置过映射时
     * 返回已保存内容" Scenario）。
     */
    @Test
    void listUserinfoFieldMappings_shouldReturnSavedRows_whenRecordsExist() {
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of(
                AppUserinfoFieldMappingRow.builder()
                        .id(5L)
                        .metadataFieldId(null)
                        .fieldName("用户ID")
                        .fieldCode("id")
                        .appFieldName("uid")
                        .appFieldCode("uid")
                        .transformType(TransformType.NO_TRANSFORM)
                        .build()));

        List<AppUserinfoFieldMappingVO> result = service.listUserinfoFieldMappings(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppFieldCode()).isEqualTo("uid");
        verify(metadataFieldMapper, never()).selectOne(any());
    }

    /**
     * 保存成功时应先删后插，整体替换语义（spec.md "保存成功替换全部映射行" Scenario）。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldDeleteThenInsert() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appUserinfoFieldMappingMapper.selectCount(any())).thenReturn(1L);
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of(
                AppUserinfoFieldMappingRow.builder()
                        .id(9L)
                        .metadataFieldId(null)
                        .fieldName("用户ID")
                        .fieldCode("id")
                        .appFieldName("用户ID")
                        .appFieldCode("id")
                        .transformType(TransformType.NO_TRANSFORM)
                        .build()));

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(null, "id");

        List<AppUserinfoFieldMappingVO> result = service.replaceUserinfoFieldMappings(10L, List.of(request));

        verify(appUserinfoFieldMappingMapper).delete(any());
        ArgumentCaptor<AppUserinfoFieldMappingEntity> captor =
                ArgumentCaptor.forClass(AppUserinfoFieldMappingEntity.class);
        verify(appUserinfoFieldMappingMapper).insert(captor.capture());
        assertThat(captor.getValue().getAppRefId()).isEqualTo(10L);
        assertThat(captor.getValue().getMetadataFieldId()).isNull();
        assertThat(result).hasSize(1);
    }

    /**
     * 请求列表内 {@code appFieldCode} 重复时应拒绝保存（spec.md "应用侧字段编码重复时拒绝
     * 保存" Scenario）。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldThrowException_whenAppFieldCodeDuplicate() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        AppUserinfoFieldMappingSaveRequest request1 = validUserinfoSaveRequest(null, "id");
        AppUserinfoFieldMappingSaveRequest request2 = validUserinfoSaveRequest(null, "id");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request1, request2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复");
        verify(appUserinfoFieldMappingMapper, never()).delete(any());
    }

    /**
     * {@code metadataFieldId} 为 {@code null} 时视为固定的"用户ID"伪字段，合法，无需查询
     * 元数据字段即可通过校验。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldAllowNullMetadataFieldId() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(appUserinfoFieldMappingMapper.selectByAppRefId(10L)).thenReturn(List.of());

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(null, "id");

        service.replaceUserinfoFieldMappings(10L, List.of(request));

        verify(metadataFieldMapper, never()).selectById(any());
    }

    /**
     * 引用不存在的元数据字段时应拒绝保存（spec.md "引用未启用或不属于 USER 域的元数据字段时
     * 拒绝保存" Scenario）。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldThrowException_whenMetadataFieldNotFound() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        when(metadataFieldMapper.selectById(1L)).thenReturn(null);

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(1L, "displayName");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地字段不存在或未启用");
    }

    /**
     * 引用已停用的元数据字段时应拒绝保存。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldThrowException_whenMetadataFieldDisabled() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, FormFieldBizType.USER, "name", "用户姓名");
        metadataField.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(1L, "displayName");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("本地字段不存在或未启用");
    }

    /**
     * 引用的元数据字段 {@code bizType} 不是 {@code USER} 时应拒绝保存。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldThrowException_whenBizTypeMismatch() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);
        MetadataFieldEntity metadataField = buildMetadataField(1L, FormFieldBizType.ORG, "code", "组织编码");
        when(metadataFieldMapper.selectById(1L)).thenReturn(metadataField);

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(1L, "displayName");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于用户域");
    }

    /**
     * 转换方式为转换脚本且语法错误时应拒绝保存（spec.md "转换脚本语法错误时拒绝保存"
     * Scenario）。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldThrowException_whenScriptSyntaxInvalid() {
        AppEntity appEntity = buildAppEntity(10L, 100L);
        when(appMapper.selectById(10L)).thenReturn(appEntity);

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(null, "id");
        request.setTransformType(TransformType.SCRIPT);
        request.setTransformValue("function transform(value) { return value.toUpperCase(; }");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转换脚本语法错误");
        verify(appUserinfoFieldMappingMapper, never()).delete(any());
    }

    /**
     * 无管辖权限时保存应被拒绝，不落库（spec.md "无管辖权限时保存被拒绝" Scenario）。
     */
    @Test
    void replaceUserinfoFieldMappings_shouldRejectWhenOrgOutOfScope() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of(999L)));

        AppUserinfoFieldMappingSaveRequest request = validUserinfoSaveRequest(null, "id");

        assertThatThrownBy(() -> service.replaceUserinfoFieldMappings(10L, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在");
        verify(appUserinfoFieldMappingMapper, never()).delete(any());
    }

    /**
     * 构造一个通过基本校验的用户信息字段映射保存请求。
     *
     * @param metadataFieldId 本地字段 id，可能为 {@code null}
     * @param appFieldCode    应用侧目标字段编码
     * @return 用户信息字段映射保存请求
     */
    private AppUserinfoFieldMappingSaveRequest validUserinfoSaveRequest(Long metadataFieldId, String appFieldCode) {
        AppUserinfoFieldMappingSaveRequest request = new AppUserinfoFieldMappingSaveRequest();
        request.setMetadataFieldId(metadataFieldId);
        request.setAppFieldName("displayName");
        request.setAppFieldCode(appFieldCode);
        request.setTransformType(TransformType.NO_TRANSFORM);
        return request;
    }

    /**
     * 构造一个测试用的元数据字段实体。
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
