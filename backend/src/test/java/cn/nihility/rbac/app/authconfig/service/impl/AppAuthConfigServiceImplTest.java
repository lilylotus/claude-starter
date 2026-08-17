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
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
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
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppMapper appMapper;

    @Mock
    private OrgScopeService orgScopeService;

    @Mock
    private CurrentOperatorService currentOperatorService;

    @Mock
    private OperationLogRecorder operationLogRecorder;

    private AppAuthConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppAuthConfigServiceImpl(appAuthConfigMapper, appConfigMapper, appMapper, orgScopeService,
                currentOperatorService, operationLogRecorder);
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
        assertThat(JacksonUtils.toObj(captured.getCasServicePatterns(), JacksonUtils.LIST_STRING_TYPE_REFERENCE))
                .isEmpty();
        assertThat(JacksonUtils.toObj(captured.getOauth2RedirectUriPatterns(), JacksonUtils.LIST_STRING_TYPE_REFERENCE))
                .isEmpty();
        assertThat(captured.getCreateBy()).isEqualTo("1");
        assertThat(captured.getUpdateBy()).isEqualTo("1");
    }

    /**
     * 查询已配置 CAS 协议的应用，应返回协议类型、匹配列表，以及按 AppId 计算出的 CAS 三个
     * 协议接口地址（含 OAuth2 三个地址，无论当前协议类型为何都一并返回）。
     */
    @Test
    void getByAppId_shouldReturnConfig_withComputedUrls() {
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.CAS, List.of("https://partner.example.com/**"),
                List.of());
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigVO vo = service.getByAppId(10L);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(vo.getCasServicePatterns()).containsExactly("https://partner.example.com/**");
        assertThat(vo.getOauth2RedirectUriPatterns()).isEmpty();
        assertThat(vo.getCasLoginUrl()).isEqualTo("/api/authn/cas/open-app-id-10/login");
        assertThat(vo.getCasServiceValidateUrl()).isEqualTo("/api/authn/cas/open-app-id-10/p3/serviceValidate");
        assertThat(vo.getCasLogoutUrl()).isEqualTo("/api/authn/cas/open-app-id-10/logout");
        assertThat(vo.getOauthAuthorizeUrl()).isEqualTo("/api/authn/oauth/authorize");
        assertThat(vo.getOauthTokenUrl()).isEqualTo("/api/authn/oauth/token");
        assertThat(vo.getOauthUserInfoUrl()).isEqualTo("/api/authn/oauth/userinfo");
    }

    /**
     * 查不到认证配置记录时（存量应用先于本表存在），应懒创建一条默认记录后返回，而不是抛
     * "应用不存在"。
     */
    @Test
    void getByAppId_shouldLazyCreateDefault_whenNotFound() {
        AppAuthConfigEntity defaultEntity = buildEntity(10L, AuthProtocol.NONE, List.of(), List.of());
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
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.NONE, List.of(), List.of());
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setCasServicePatterns(List.of("https://partner.example.com/**"));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.CAS);
        assertThat(vo.getCasServicePatterns()).containsExactly("https://partner.example.com/**");
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
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of(), List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setCasServicePatterns(List.of());

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
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of(), List.of()));

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
        AppAuthConfigEntity entity = buildEntity(10L, AuthProtocol.CAS, List.of("https://partner.example.com/**"),
                List.of());
        when(appAuthConfigMapper.selectOne(any())).thenReturn(entity);

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.NONE);

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getAuthProtocol()).isEqualTo(AuthProtocol.NONE);
        assertThat(vo.getCasServicePatterns()).isEmpty();
        assertThat(vo.getOauth2RedirectUriPatterns()).isEmpty();
    }

    /**
     * 匹配规则列表在保存前应去除空白项与重复项。
     */
    @Test
    void updateConfig_shouldTrimAndDedupePatterns() {
        when(appMapper.selectById(10L)).thenReturn(buildAppEntity(10L, 100L));
        when(appAuthConfigMapper.selectOne(any())).thenReturn(buildEntity(10L, AuthProtocol.NONE, List.of(), List.of()));

        AppAuthConfigUpdateRequest request = new AppAuthConfigUpdateRequest();
        request.setAuthProtocol(AuthProtocol.CAS);
        request.setCasServicePatterns(List.of(" https://a.example.com/** ", "https://a.example.com/**", "  "));

        AppAuthConfigVO vo = service.updateConfig(10L, request);

        assertThat(vo.getCasServicePatterns()).containsExactly("https://a.example.com/**");
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

    private AppAuthConfigEntity buildEntity(long appRefId, String authProtocol, List<String> casServicePatterns,
            List<String> oauth2RedirectUriPatterns) {
        LocalDateTime now = LocalDateTime.now();
        return AppAuthConfigEntity.builder()
                .id(1L)
                .appRefId(appRefId)
                .authProtocol(authProtocol)
                .casServicePatterns(JacksonUtils.toJson(casServicePatterns))
                .oauth2RedirectUriPatterns(JacksonUtils.toJson(oauth2RedirectUriPatterns))
                .createBy("1")
                .createTime(now)
                .updateBy("1")
                .updateTime(now)
                .build();
    }
}
