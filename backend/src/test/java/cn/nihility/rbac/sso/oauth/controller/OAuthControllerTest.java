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
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
 * 校验成功/401。断言 {@code Location} 响应头、JSON 响应体、错误状态码。
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

    /** 测试用 client_secret 明文。 */
    private static final String CLIENT_SECRET_PLAIN = "test-client-secret";

    /** 本用例插入的应用 id。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识（即 OAuth2 client_id）。 */
    private String clientId;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

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
                .casServicePatterns(JacksonUtils.toJson(List.of()))
                .oauth2RedirectUriPatterns(JacksonUtils.toJson(List.of("https://partner.example.com/**")))
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
    }

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
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
    }

    /**
     * 合法授权码换取令牌成功，返回 access token 与 refresh token；用同一授权码再次请求令牌
     * 应被拒绝（一次性消费，spec.md "合法授权码换取令牌成功且不可重复使用" Scenario）。
     */
    @Test
    void token_authorizationCode_shouldSucceedOnce_thenRejectSecondUse() throws Exception {
        String code = issueAuthorizationCode();

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

        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andExpect(status().isBadRequest());
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
    }

    /**
     * 请求令牌时提供的 {@code redirect_uri} 与获取该授权码时使用的 {@code redirect_uri} 不同
     * 时应拒绝签发（spec.md "redirect_uri 与签发授权码时不一致时拒绝" Scenario）。
     */
    @Test
    void token_authorizationCode_shouldReject_whenRedirectUriMismatch() throws Exception {
        String code = issueAuthorizationCode();

        mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", "https://partner.example.com/other-callback")
                        .param("code", code))
                .andExpect(status().isBadRequest());
    }

    /**
     * 合法 {@code refresh_token} 刷新成功，签发新的 access token（只传
     * {@code refresh_token}/{@code grant_type} 两个参数，不要求 {@code client_id}/
     * {@code client_secret}，spec.md "合法 refresh_token 刷新成功" Scenario）。
     */
    @Test
    void token_refreshToken_shouldSucceed() throws Exception {
        String code = issueAuthorizationCode();
        String tokenResponseBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "authorization_code")
                        .param("client_id", clientId)
                        .param("client_secret", CLIENT_SECRET_PLAIN)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .param("code", code))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> issued = JacksonUtils.toObj(tokenResponseBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        String refreshToken = (String) issued.get("refresh_token");

        String refreshedBody = mockMvc.perform(post("/api/authn/oauth/token")
                        .param("grant_type", "refresh_token")
                        .param("refresh_token", refreshToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> refreshed = JacksonUtils.toObj(refreshedBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(refreshed.get("access_token")).isNotNull();
        assertThat(refreshed.get("access_token")).isNotEqualTo(issued.get("access_token"));
        assertThat(refreshed).doesNotContainKey("refresh_token");
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
    }

    /**
     * 不支持的 {@code grant_type} 取值应返回 400。
     */
    @Test
    void token_shouldReject_whenGrantTypeUnsupported() throws Exception {
        mockMvc.perform(post("/api/authn/oauth/token").param("grant_type", "password"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 携带一个有效的 access token 请求用户信息接口，应返回该令牌签发时绑定用户的基本身份信息，
     * 除固定的 {@code sub} 字段外，其余字段按默认的"用户ID + 姓名"两个字段动态生成
     * （spec.md "合法令牌查询用户信息成功"、"用户信息字段按映射配置动态生成" Scenario）。
     */
    @Test
    void userinfo_shouldReturnUserInfo_whenTokenValid() throws Exception {
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
        assertThat(userInfo.get("name")).isEqualTo("OAuth测试用户");
        assertThat(userInfo).containsKey("id");
        assertThat(userInfo).doesNotContainKey("username");
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
    }

    /**
     * 未携带 {@code Authorization} 请求头时同样应拒绝，返回 401。
     */
    @Test
    void userinfo_shouldReturnUnauthorized_whenAuthorizationHeaderMissing() throws Exception {
        mockMvc.perform(get("/api/authn/oauth/userinfo"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * 走完整授权流程（登录态 + authorize）签发一枚授权码，供 {@code /token} 相关用例使用。
     *
     * @return 签发的授权码
     * @throws Exception MockMvc 调用异常
     */
    private String issueAuthorizationCode() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        String location = mockMvc.perform(get("/api/authn/oauth/authorize")
                        .param("response_type", "code")
                        .param("client_id", clientId)
                        .param("redirect_uri", ALLOWED_REDIRECT_URI)
                        .cookie(cookie))
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.indexOf("code=") + "code=".length());
    }
}
