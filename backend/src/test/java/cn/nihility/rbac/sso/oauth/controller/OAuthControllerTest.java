package cn.nihility.rbac.sso.oauth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.entity.AppUserinfoFieldMappingEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.authconfig.mapper.AppUserinfoFieldMappingMapper;
import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.appaccess.override.constant.OverrideType;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import cn.nihility.rbac.appaccess.override.mapper.ManualOverrideMapper;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyGrantEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyIpRuleEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyIpRuleMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogEventType;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link OAuthController} 的测试（tasks.md 7.6），起真实 MySQL/Redis 连接，覆盖 spec.md
 * 全部 OAuth2 场景：redirect_uri 白名单匹配/不匹配、未登录跳转 SSO 登录页、state 原样返回、
 * 授权码一次性消费、client_secret 校验、redirect_uri 一致性校验、
 * {@code grant_type=refresh_token} 刷新成功与 refresh_token 不存在/过期时拒绝、userinfo
 * 校验成功/401。断言 {@code Location} 响应头、JSON 响应体、错误状态码。{@code setUp} 默认
 * 为测试用户+应用插入一条 GRANT 人工例外，模拟具备最终生效授权，让原有覆盖授权码/令牌签发
 * 成功场景的用例继续反映其本身要覆盖的分支；未授权场景由
 * {@link #authorize_shouldReject_andNotIssueCode_whenUserNotAuthorizedForApp} 单独覆盖
 * （app-access-authorization change tasks.md 8.5）；身份命中但请求控制不满足/满足两种
 * 场景由 {@link #authorize_shouldReject_whenPolicyRequestControlNotSatisfied}/
 * {@link #authorize_shouldIssueCode_whenPolicyRequestControlSatisfied} 覆盖
 * （app-access-request-control change tasks.md 8.5）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OAuthControllerTest {

    /** 测试用 OAuth2 redirect_uri 白名单规则对应的合法回跳地址前缀。 */
    private static final String ALLOWED_REDIRECT_URI = "https://partner.example.com/callback";

    /** MockMvc 客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /** 应用数据访问接口。 */
    @Autowired
    private AppMapper appMapper;

    /** 应用对外接口凭证配置数据访问接口。 */
    @Autowired
    private AppConfigMapper appConfigMapper;

    /** 应用单点登录协议配置数据访问接口。 */
    @Autowired
    private AppAuthConfigMapper appAuthConfigMapper;

    /** 用户数据访问接口。 */
    @Autowired
    private UserMapper userMapper;

    /** 用户密码数据访问接口。 */
    @Autowired
    private UserPasswordMapper userPasswordMapper;

    /** SSO 浏览器会话业务逻辑接口，测试内直接签发会话，模拟"已登录"状态。 */
    @Autowired
    private SsoSessionService ssoSessionService;

    /** 应用对外接口凭证相关配置，提供 SM4 加密主密钥，供测试构造已加密的 secretKey。 */
    @Autowired
    private AppSecretProperties appSecretProperties;

    /** 应用用户信息响应字段映射数据访问接口，供"sub 固定优先于映射配置"用例插入测试数据。 */
    @Autowired
    private AppUserinfoFieldMappingMapper appUserinfoFieldMappingMapper;

    /** 元数据字段数据访问接口，查询"用户编号"字段 id 构造映射测试数据。 */
    @Autowired
    private MetadataFieldMapper metadataFieldMapper;

    /** 人工例外数据访问接口（app-access-authorization change），测试内直接插入 GRANT
     *  例外模拟测试用户对测试应用具备最终生效授权，覆盖"应用访问授权"接入后的默认拒绝。 */
    @Autowired
    private ManualOverrideMapper manualOverrideMapper;

    /** 策略规则数据访问接口（app-access-request-control change），请求控制场景用例内
     *  直接插入策略与 IP 白名单条件。 */
    @Autowired
    private PolicyMapper policyMapper;

    /** 策略计算结果数据访问接口，请求控制场景用例内直接插入身份命中记录。 */
    @Autowired
    private PolicyGrantMapper policyGrantMapper;

    /** 策略请求控制条件-IP/网段白名单数据访问接口。 */
    @Autowired
    private PolicyIpRuleMapper policyIpRuleMapper;

    /** SSO 协议调用记录数据访问接口（add-sso-protocol-access-log change tasks.md 6.1）。 */
    @Autowired
    private SsoProtocolLogMapper ssoProtocolLogMapper;

    /** 测试用 client_secret 明文。 */
    private static final String CLIENT_SECRET_PLAIN = "test-client-secret";

    /** 本用例插入的应用 id。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识（即 OAuth2 client_id）。 */
    private String clientId;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

    /** 请求控制场景用例内插入的测试策略 id，未使用该场景时为 {@code null}。 */
    private Long policyId;

    /**
     * 每个用例前插入一份独立的测试应用（OAuth2 协议，redirect_uri 白名单为
     * {@code https://partner.example.com/**}）与测试用户。
     */
    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        AppEntity app = AppEntity.builder()
                .name("OAuth2 测试应用")
                .code("oauth-test-" + UUID.randomUUID())
                .ownerId(1L)
                .orgId(1L)
                .showOrder(0)
                .status(AppStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appMapper.insert(app);
        appRefId = app.getId();
        clientId = "oauth-test-client-" + UUID.randomUUID().toString().replace("-", "");

        AppConfigEntity appConfig = AppConfigEntity.builder()
                .appRefId(appRefId)
                .appId(clientId)
                .accessKey(UUID.randomUUID().toString().replace("-", ""))
                .secretKey(Sm4JdkUtils.encrypt(CLIENT_SECRET_PLAIN, appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .syncMode(SyncMode.PULL)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appConfigMapper.insert(appConfig);

        AppAuthConfigEntity authConfig = AppAuthConfigEntity.builder()
                .appRefId(appRefId)
                .authProtocol(AuthProtocol.OAUTH2)
                .servicePatterns(JacksonUtils.toJson(List.of("https://partner.example.com/**")))
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appAuthConfigMapper.insert(authConfig);

        UserEntity user = UserEntity.builder()
                .name("OAuth测试用户")
                .code("oauth-test-user-" + UUID.randomUUID())
                .gender("unknown")
                .showOrder(0)
                .status(UserStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        userId = user.getId();

        UserPasswordEntity password = UserPasswordEntity.builder()
                .userId(userId)
                .passwordDigest(PasswordDigestUtils.digest("Test#123456", "salt"))
                .salt("salt")
                .firstLogin(Boolean.FALSE)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userPasswordMapper.insert(password);

        // app-access-authorization change 接入后，OAuth2 授权码签发前新增最终生效权限
        // 校验：默认插入一条 GRANT 人工例外，让本测试类原有覆盖"授权码/令牌签发成功"场景的
        // 用例继续反映其本身要覆盖的分支；未授权场景由下方专门的用例单独覆盖（不预置该例外）。
        manualOverrideMapper.insert(ManualOverrideEntity.builder()
                .userId(userId)
                .appId(appRefId)
                .overrideType(OverrideType.GRANT)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
        if (clientId != null) {
            ssoProtocolLogMapper.delete(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                    .eq(SsoProtocolLogEntity::getAppId, clientId));
        }
        if (userId != null && appRefId != null) {
            manualOverrideMapper.delete(new LambdaQueryWrapper<ManualOverrideEntity>()
                    .eq(ManualOverrideEntity::getUserId, userId).eq(ManualOverrideEntity::getAppId, appRefId));
        }
        if (appRefId != null) {
            appUserinfoFieldMappingMapper.delete(new LambdaQueryWrapper<AppUserinfoFieldMappingEntity>()
                    .eq(AppUserinfoFieldMappingEntity::getAppRefId, appRefId));
            appAuthConfigMapper.delete(new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
            appConfigMapper.delete(new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
            appMapper.deleteById(appRefId);
        }
        if (userId != null) {
            userPasswordMapper.delete(new LambdaQueryWrapper<UserPasswordEntity>().eq(UserPasswordEntity::getUserId, userId));
            userMapper.deleteById(userId);
        }
        if (policyId != null) {
            policyIpRuleMapper.delete(new LambdaQueryWrapper<PolicyIpRuleEntity>()
                    .eq(PolicyIpRuleEntity::getPolicyId, policyId));
            policyGrantMapper.delete(new LambdaQueryWrapper<PolicyGrantEntity>()
                    .eq(PolicyGrantEntity::getPolicyId, policyId));
            policyMapper.deleteById(policyId);
        }
    }

    /**
     * redirect_uri 未匹配任何已配置规则时，系统应拒绝且不发生任何重定向（无 {@code Location}
     * 响应头），防止开放重定向（spec.md "redirect_uri 未匹配任何规则被拒绝" Scenario）。
     */
    @Test
    void authorize_shouldReject_andNotRedirect_whenRedirectUriNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", "https://evil.example.com/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getAppRefId()).isEqualTo(appRefId);
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * redirect_uri 校验通过但当前浏览器未持有有效 SSO 会话时，应重定向到 SSO 登录页。
     */
    @Test
    void authorize_shouldRedirectToSsoLoginPage_whenSessionMissing() throws Exception {
        mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/sso/login?redirect=")));

        assertThat(ssoProtocolLogMapper.selectList(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(SsoProtocolLogEntity::getAppId, clientId))).isEmpty();
    }

    /**
     * 已登录且校验通过时应签发授权码并重定向回 {@code redirect_uri}，且原样携带 {@code state}
     * （spec.md "state 原样返回" Scenario）。
     */
    @Test
    void authorize_shouldIssueCodeAndRedirect_withStatePreserved() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        String location = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("state", "xyz-state")
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith(ALLOWED_REDIRECT_URI + "?code=");
        assertThat(location).endsWith("&state=xyz-state");

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getAppRefId()).isEqualTo(appRefId);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
    }

    /**
     * 浏览器会话有效但账号仍待首次登录改密时，不得签发 OAuth2 授权码，应携带原始授权请求
     * 和强制改密标识重定向到 SSO 登录页。
     */
    @Test
    void authorize_shouldRedirectToPasswordChangeAndNotIssueCode_whenFirstLogin() throws Exception {
        userPasswordMapper.update(null, new LambdaUpdateWrapper<UserPasswordEntity>()
                .eq(UserPasswordEntity::getUserId, userId)
                .set(UserPasswordEntity::getFirstLogin, true));
        String sessionToken = ssoSessionService.issue(userId);

        String location = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("state", "first-login-state")
                        .cookie(new Cookie("sso_session", sessionToken)))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith("/sso/login?redirect=");
        assertThat(location).endsWith("&forcePasswordChange=true");
        assertThat(location).doesNotContain("code=");
        assertThat(ssoProtocolLogMapper.selectList(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(SsoProtocolLogEntity::getAppId, clientId))).isEmpty();
    }

    /**
     * redirect_uri 校验通过且已登录，但当前用户不具备访问该应用的最终生效授权（没有任何
     * 人工例外/策略授权）时，应拒绝且不签发授权码、不发生重定向
     * （app-access-authorization change spec.md "用户无最终生效授权时拒绝签发授权码"
     * Scenario）。
     */
    @Test
    void authorize_shouldReject_andNotIssueCode_whenUserNotAuthorizedForApp() throws Exception {
        manualOverrideMapper.delete(new LambdaQueryWrapper<ManualOverrideEntity>()
                .eq(ManualOverrideEntity::getUserId, userId).eq(ManualOverrideEntity::getAppId, appRefId));
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        String body = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> json = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(json.get("code")).isEqualTo(403);
        assertThat(json.get("message")).isEqualTo("当前用户无权访问该应用");

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getFailReason()).isEqualTo("当前用户无权访问该应用");
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
        // 本用例未预置任何策略授权记录（人工例外已删除），属于"候选为空"分支，并非由具体
        // 策略拒绝，拒绝来源策略 id 应为空（policy-condition-exclusive-priority change
        // tasks.md 5.6）。
        assertThat(logEntity.getDeniedPolicyId()).isNull();
    }

    /**
     * 身份命中某启用中策略（配置了 IP 白名单，仅允许 {@code 10.0.0.0/8} 网段访问），但当前
     * 请求的客户端 IP（MockMvc 默认 {@code 127.0.0.1}）不在该白名单内，且不存在能覆盖此次
     * 访问的 GRANT 人工例外时，应拒绝且不签发授权码（app-access-request-control change
     * spec.md "身份命中但当前请求不满足命中策略的请求控制时拒绝签发授权码" Scenario）。
     */
    @Test
    void authorize_shouldReject_whenPolicyRequestControlNotSatisfied() throws Exception {
        manualOverrideMapper.delete(new LambdaQueryWrapper<ManualOverrideEntity>()
                .eq(ManualOverrideEntity::getUserId, userId).eq(ManualOverrideEntity::getAppId, appRefId));
        insertIpRestrictedPolicyGrant("10.0.0.0/8");
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        String body = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Location"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> json = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(json.get("code")).isEqualTo(403);

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
        // 排在最前的候选策略（本用例只插入了一条）请求控制不满足，拒绝来源策略 id 应记为
        // 该策略的 id（policy-condition-exclusive-priority change tasks.md 5.6）。
        assertThat(logEntity.getDeniedPolicyId()).isEqualTo(policyId);
    }

    /**
     * 身份命中某启用中策略（配置了 IP 白名单，允许 {@code 127.0.0.0/8} 网段访问，覆盖
     * MockMvc 默认的客户端 IP {@code 127.0.0.1}）时，应正常签发授权码
     * （app-access-request-control change spec.md 判定规则 Scenario）。
     */
    @Test
    void authorize_shouldIssueCode_whenPolicyRequestControlSatisfied() throws Exception {
        manualOverrideMapper.delete(new LambdaQueryWrapper<ManualOverrideEntity>()
                .eq(ManualOverrideEntity::getUserId, userId).eq(ManualOverrideEntity::getAppId, appRefId));
        insertIpRestrictedPolicyGrant("127.0.0.0/8");
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        String location = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).startsWith(ALLOWED_REDIRECT_URI + "?code=");

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
    }

    /**
     * 插入一条启用中的策略，配置给定 IP 白名单，并为测试用户+应用产生一条身份命中的策略
     * 授权记录，供请求控制场景用例复用；插入的策略 id 记录到 {@link #policyId} 供
     * {@link #tearDown} 清理。
     *
     * @param ipCidr IP 白名单条目
     */
    private void insertIpRestrictedPolicyGrant(String ipCidr) {
        LocalDateTime now = LocalDateTime.now();
        PolicyEntity policy = PolicyEntity.builder()
                .name("OAuth2 请求控制测试策略")
                .status(PolicyStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        policyMapper.insert(policy);
        policyId = policy.getId();

        policyIpRuleMapper.insert(PolicyIpRuleEntity.builder()
                .policyId(policyId)
                .ipCidr(ipCidr)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());

        policyGrantMapper.insert(PolicyGrantEntity.builder()
                .policyId(policyId)
                .userId(userId)
                .appId(appRefId)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 合法授权码换取令牌成功，返回 access token 与 refresh token；用同一授权码再次请求令牌
     * 应被拒绝（一次性消费，spec.md "合法授权码换取令牌成功且不可重复使用" Scenario）。
     */
    @Test
    void token_authorizationCode_shouldSucceedOnce_thenRejectSecondUse() throws Exception {
        IssuedAuthorizationCode issuedCode = issueAuthorizationCodeWithSession();
        String code = issuedCode.code();

        String body = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> tokenBody = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(tokenBody.get("access_token")).isNotNull();
        assertThat(tokenBody.get("refresh_token")).isNotNull();
        assertThat(tokenBody.get("token_type")).isEqualTo("Bearer");

        SsoProtocolLogEntity successLog = latestSsoProtocolLog(SsoProtocolLogEventType.TOKEN);
        assertThat(successLog.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(successLog.getUserId()).isEqualTo(userId);
        assertThat(successLog.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(issuedCode.sessionToken()));

        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andExpect(status().isBadRequest());

        SsoProtocolLogEntity failureLog = latestSsoProtocolLog(SsoProtocolLogEventType.TOKEN);
        assertThat(failureLog.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(failureLog.getUserId()).isNull();
        assertThat(failureLog.getSessionId()).isNull();
    }

    /**
     * {@code client_secret} 不匹配时应拒绝签发令牌，返回 401
     * （spec.md "client_secret 不匹配时拒绝签发" Scenario）。
     */
    @Test
    void token_authorizationCode_shouldReject_whenClientSecretMismatch() throws Exception {
        String code = issueAuthorizationCode();

        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", "wrong-secret")
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andExpect(status().isUnauthorized());

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.TOKEN);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getAppRefId()).isEqualTo(appRefId);
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 请求令牌时提供的 {@code redirect_uri} 与获取该授权码时使用的 {@code redirect_uri} 不同
     * 时应拒绝签发（spec.md "redirect_uri 与签发授权码时不一致时拒绝" Scenario）。
     */
    @Test
    void token_authorizationCode_shouldReject_whenRedirectUriMismatch() throws Exception {
        IssuedAuthorizationCode issuedCode = issueAuthorizationCodeWithSession();

        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", "https://partner.example.com/other-callback")
                        .param("code", issuedCode.code()))
                .andExpect(status().isBadRequest());

        // 授权码本身合法（只是 redirect_uri 与签发时不一致），payload 已解析出，userId/
        // sessionId 均不应因本次判定失败而丢弃（design.md Decision 4 "视情况而定" 分支）。
        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.TOKEN);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(issuedCode.sessionToken()));
    }

    /**
     * 合法 {@code refresh_token} 刷新成功，签发新的 access token 与新的 refresh token（只传
     * {@code refresh_token}/{@code grant_type} 两个参数，不要求 {@code client_id}/
     * {@code client_secret}）；旧 refresh token 应被立即消费失效，再次用旧值请求刷新应被拒绝
     * （add-sso-single-logout change spec.md "合法 refresh_token 刷新成功且旧值被消费"
     * Scenario）。
     */
    @Test
    void token_refreshToken_shouldSucceed_andRotateRefreshToken() throws Exception {
        IssuedAuthorizationCode issuedCode = issueAuthorizationCodeWithSession();
        String tokenResponseBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", issuedCode.code()))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> issued = JacksonUtils.toObj(tokenResponseBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        String refreshToken = (String) issued.get("refresh_token");
        SsoProtocolLogEntity authorizeLog = latestSsoProtocolLog(SsoProtocolLogEventType.AUTHORIZE);

        String refreshedBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> refreshed = JacksonUtils.toObj(refreshedBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(refreshed.get("access_token")).isNotNull();
        assertThat(refreshed.get("access_token")).isNotEqualTo(issued.get("access_token"));
        assertThat(refreshed.get("refresh_token")).isNotNull();
        assertThat(refreshed.get("refresh_token")).isNotEqualTo(refreshToken);
        String rotatedAccessToken = (String) refreshed.get("access_token");

        SsoProtocolLogEntity successLog = latestSsoProtocolLog(SsoProtocolLogEventType.TOKEN);
        assertThat(successLog.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(successLog.getUserId()).isEqualTo(userId);
        assertThat(successLog.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(issuedCode.sessionToken()));

        // “刷新链路延续会话”核心断言：用刷新后签发的新 access token 调用 userinfo 产生的
        // 调用记录，session_id 应与最初签发授权码时的 authorize 调用记录完全一致
        // （add-sso-protocol-access-log change design.md Decision 6/spec.md "令牌刷新后
        // 新令牌延续同一会话标识" Scenario，tasks.md 7.8 "刷新链路存活" 核心用例）。
        mockMvc.perform(get("/api/authn/oauth/userinfo").header("Authorization", "Bearer " + rotatedAccessToken))
                .andExpect(status().isOk());
        SsoProtocolLogEntity userinfoLog = latestSsoProtocolLog(SsoProtocolLogEventType.USERINFO);
        assertThat(userinfoLog.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(userinfoLog.getSessionId()).isNotNull();
        assertThat(userinfoLog.getSessionId()).isEqualTo(authorizeLog.getSessionId());

        // 旧 refresh token 已一次性消费失效，再次使用应被拒绝
        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isBadRequest());

        // handleRefreshTokenGrant 不要求请求携带 client_id，本次失败记录的原始 appId 为空，
        // 无法按 clientId 过滤定位，改用不按 appId 过滤的全局查询（同
        // token_refreshToken_shouldReject_whenRefreshTokenUnknown 用例）。
        SsoProtocolLogEntity failureLog = latestSsoProtocolLogGlobal(SsoProtocolLogEventType.TOKEN);
        assertThat(failureLog.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(failureLog.getUserId()).isNull();
        assertThat(failureLog.getSessionId()).isNull();
    }

    /**
     * {@code refresh_token} 不存在或已过期时应拒绝签发新的 access token
     * （spec.md "refresh_token 不存在或已过期时拒绝" Scenario）。
     */
    @Test
    void token_refreshToken_shouldReject_whenRefreshTokenUnknown() throws Exception {
        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", "unknown-refresh-token"))
                .andExpect(status().isBadRequest());

        SsoProtocolLogEntity logEntity = latestSsoProtocolLogGlobal(SsoProtocolLogEventType.TOKEN);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 不支持的 {@code grant_type} 取值应返回 400。
     */
    @Test
    void token_shouldReject_whenGrantTypeUnsupported() throws Exception {
        mockMvc.perform(post("/api/authn/oauth/token").param("grant_type", "password"))
                .andExpect(status().isBadRequest());

        SsoProtocolLogEntity logEntity = latestSsoProtocolLogGlobal(SsoProtocolLogEventType.TOKEN);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getFailReason()).isEqualTo("grant_type 不支持");
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 携带一个有效的 access token 请求用户信息接口，应返回该令牌签发时绑定用户的基本身份信息，
     * 除固定的 {@code sub} 字段外，其余字段按默认的"用户ID + 姓名"两个字段动态生成
     * （spec.md "合法令牌查询用户信息成功"、"用户信息字段按映射配置动态生成" Scenario）。
     */
    @Test
    void userinfo_shouldReturnUserInfo_whenTokenValid() throws Exception {
        IssuedAuthorizationCode issuedCode = issueAuthorizationCodeWithSession();
        String tokenResponseBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", issuedCode.code()))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> issued = JacksonUtils.toObj(tokenResponseBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        String accessToken = (String) issued.get("access_token");

        String body = mockMvc.perform(get("/api/authn/oauth/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> userInfo = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(userInfo.get("sub")).isEqualTo(String.valueOf(userId));
        assertThat(userInfo.get("name")).isEqualTo("OAuth测试用户");
        assertThat(userInfo).containsKey("id");
        assertThat(userInfo).doesNotContainKey("username");

        SsoProtocolLogEntity logEntity = latestSsoProtocolLog(SsoProtocolLogEventType.USERINFO);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.SUCCESS);
        assertThat(logEntity.getUserId()).isEqualTo(userId);
        assertThat(logEntity.getSessionId()).isEqualTo(SsoSessionIdHasher.hash(issuedCode.sessionToken()));
    }

    /**
     * 某行用户信息字段映射的应用侧字段编码恰好配置成 {@code sub} 时，响应体的 {@code sub}
     * 字段最终仍以协议规定的固定用户 id 为准，不受该行映射的转换结果影响
     * （add-sso-userinfo-field-mapping change spec.md "映射字段编码与 sub 冲突时固定值优先"
     * Scenario）。
     */
    @Test
    void userinfo_shouldKeepFixedSub_whenMappingAppFieldCodeConflictsWithSub() throws Exception {
        MetadataFieldEntity codeField = metadataFieldMapper.selectOne(new LambdaQueryWrapper<MetadataFieldEntity>()
                .eq(MetadataFieldEntity::getBizType, FormFieldBizType.USER)
                .eq(MetadataFieldEntity::getColumnName, "code"));
        LocalDateTime now = LocalDateTime.now();
        appUserinfoFieldMappingMapper.insert(AppUserinfoFieldMappingEntity.builder()
                .appRefId(appRefId)
                .metadataFieldId(codeField.getId())
                .appFieldName("sub")
                .appFieldCode("sub")
                .transformType(TransformType.NO_TRANSFORM)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());

        String code = issueAuthorizationCode();
        String tokenResponseBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> issued = JacksonUtils.toObj(tokenResponseBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        String accessToken = (String) issued.get("access_token");

        String body = mockMvc.perform(get("/api/authn/oauth/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> userInfo = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(userInfo.get("sub")).isEqualTo(String.valueOf(userId));
    }

    /**
     * 携带的 access token 已过期或不存在时应拒绝请求，返回 401
     * （spec.md "令牌过期或不存在时拒绝" Scenario）。
     */
    @Test
    void userinfo_shouldReturnUnauthorized_whenTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/authn/oauth/userinfo").header("Authorization", "Bearer unknown-access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));

        SsoProtocolLogEntity logEntity = latestSsoProtocolLogGlobal(SsoProtocolLogEventType.USERINFO);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getAppId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 未携带 {@code Authorization} 请求头时同样应拒绝，返回 401。
     */
    @Test
    void userinfo_shouldReturnUnauthorized_whenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/authn/oauth/userinfo"))
                .andExpect(status().isUnauthorized());

        SsoProtocolLogEntity logEntity = latestSsoProtocolLogGlobal(SsoProtocolLogEventType.USERINFO);
        assertThat(logEntity.getResult()).isEqualTo(SsoProtocolLogResult.FAILED);
        assertThat(logEntity.getUserId()).isNull();
        assertThat(logEntity.getSessionId()).isNull();
    }

    /**
     * 走完整授权流程（登录态 + authorize）签发一枚授权码，供 {@code /token} 相关用例使用。
     *
     * @return 签发的授权码字符串
     * @throws Exception MockMvc 调用异常
     */
    private String issueAuthorizationCode() throws Exception {
        return issueAuthorizationCodeWithSession().code();
    }

    /**
     * 走完整授权流程（登录态 + authorize）签发一枚授权码，同时返回本次登录使用的 SSO 会话
     * 令牌（供断言 {@code sessionId} 是否正确关联，add-sso-protocol-access-log change
     * design.md Decision 6）。
     *
     * @return 签发的授权码与本次使用的 SSO 会话令牌
     * @throws Exception MockMvc 调用异常
     */
    private IssuedAuthorizationCode issueAuthorizationCodeWithSession() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        String location = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .cookie(cookie))
                .andReturn().getResponse().getHeader("Location");
        String code = location.substring(location.indexOf("code=") + "code=".length());
        return new IssuedAuthorizationCode(code, sessionToken);
    }

    /**
     * {@link #issueAuthorizationCodeWithSession()} 的返回值，携带签发的授权码与本次登录
     * 使用的 SSO 会话令牌（原始令牌，非哈希值）。
     *
     * @param code         签发的授权码
     * @param sessionToken 本次登录使用的 SSO 会话令牌
     */
    private record IssuedAuthorizationCode(String code, String sessionToken) {
    }

    /**
     * 查询本用例插入的应用（{@link #clientId}）最近一条指定事件类型的 SSO 协议调用记录
     * （add-sso-protocol-access-log change tasks.md 6.1），断言必须存在，否则用例失败。
     *
     * @param eventType 事件类型
     * @return 最近一条对应事件类型的调用记录
     */
    private SsoProtocolLogEntity latestSsoProtocolLog(String eventType) {
        List<SsoProtocolLogEntity> logs = ssoProtocolLogMapper.selectList(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(SsoProtocolLogEntity::getAppId, clientId)
                .eq(SsoProtocolLogEntity::getEventType, eventType)
                .orderByDesc(SsoProtocolLogEntity::getId));
        assertThat(logs).isNotEmpty();
        return logs.get(0);
    }

    /**
     * 查询最近一条指定事件类型的 SSO 协议调用记录，不按 appId 过滤——供请求里未携带
     * {@code client_id} 参数（如 {@code refresh_token} 授权类型、{@code userinfo} 端点未携带
     * 有效令牌）的场景使用，此时记录的原始 {@code appId} 字段为空，无法按 {@link #clientId}
     * 过滤定位。测试按顺序串行执行，断言紧跟在触发调用之后，取全表最新一条即为本次调用产生
     * 的记录。
     *
     * @param eventType 事件类型
     * @return 最近一条对应事件类型的调用记录
     */
    private SsoProtocolLogEntity latestSsoProtocolLogGlobal(String eventType) {
        List<SsoProtocolLogEntity> logs = ssoProtocolLogMapper.selectList(new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(SsoProtocolLogEntity::getEventType, eventType)
                .orderByDesc(SsoProtocolLogEntity::getId));
        assertThat(logs).isNotEmpty();
        return logs.get(0);
    }
}
