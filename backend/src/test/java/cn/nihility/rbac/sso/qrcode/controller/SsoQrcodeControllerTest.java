package cn.nihility.rbac.sso.qrcode.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionService;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SsoQrcodeController} 的测试，起真实 MySQL/Redis 连接（同项目既有"不做重量级 mock"
 * 的测试风格），覆盖状态机完整流转（待扫码 -&gt; 已扫码 -&gt; 已确认 -&gt; PC 端首次轮询签发
 * 会话 -&gt; 再次轮询视为已过期）、未开启扫码登录时创建会话被拒绝、未登录时确认被拒绝
 * （add-sso-login-methods change tasks.md 6.6）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SsoQrcodeControllerTest {

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

    /** SSO 浏览器会话业务逻辑接口，用于模拟手机浏览器已登录状态。 */
    @Autowired
    private SsoSessionService ssoSessionService;

    /** 字符串 Redis 模板，用于测试结束后清理。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例插入的应用 id。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识。 */
    private String appId;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

    /** 本用例签发的手机端 SSO 会话令牌。 */
    private String mobileSessionToken;

    /**
     * 每个用例结束后清理本用例插入的测试数据。
     */
    @AfterEach
    void tearDown() {
        if (appRefId != null) {
            appAuthConfigMapper.delete(new LambdaQueryWrapper<AppAuthConfigEntity>()
                    .eq(AppAuthConfigEntity::getAppRefId, appRefId));
            appConfigMapper.delete(new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
            appMapper.deleteById(appRefId);
        }
        if (userId != null) {
            userMapper.deleteById(userId);
        }
        if (mobileSessionToken != null) {
            ssoSessionService.revoke(mobileSessionToken);
        }
    }

    /**
     * 目标应用未开启扫码登录时，创建会话应直接拒绝。
     */
    @Test
    void createSession_shouldReject_whenAppDoesNotAllowQrcode() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD));

        mockMvc.perform(post("/api/authn/sso/qrcode/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)))
                .andExpect(jsonPath("$.message").value("该应用未开启扫码登录"));
    }

    /**
     * 未知令牌查询状态应返回 EXPIRED。
     */
    @Test
    void status_shouldReturnExpired_whenTokenUnknown() throws Exception {
        mockMvc.perform(get("/api/authn/sso/qrcode/{token}/status", "unknown-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    /**
     * 未携带有效 SSO 会话时确认登录应被拒绝。
     */
    @Test
    void confirm_shouldReject_whenNotLoggedIn() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD, LoginMethod.QRCODE));
        String token = createSession();

        mockMvc.perform(post("/api/authn/sso/qrcode/{token}/confirm", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)))
                .andExpect(jsonPath("$.message").value("需要先完成登录"));
    }

    /**
     * 完整状态机流转：待扫码 -&gt; 标记已扫码 -&gt; 手机端确认登录 -&gt; PC 端首次轮询签发
     * 会话并返回已确认 -&gt; PC 端再次轮询视为已过期（不重复签发）。
     */
    @Test
    void fullFlow_shouldTransitionThroughAllStates_andIssueSessionOnce() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD, LoginMethod.QRCODE));
        seedUser();
        String token = createSession();

        assertThat(currentStatus(token)).isEqualTo("PENDING");

        mockMvc.perform(post("/api/authn/sso/qrcode/{token}/scan", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertThat(currentStatus(token)).isEqualTo("SCANNED");

        mockMvc.perform(post("/api/authn/sso/qrcode/{token}/confirm", token)
                        .cookie(new Cookie(SsoSessionCookieUtils.COOKIE_NAME, mobileSessionToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String setCookieHeader = mockMvc.perform(get("/api/authn/sso/qrcode/{token}/status", token))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.firstLogin").value(false))
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(setCookieHeader).contains(SsoSessionCookieUtils.COOKIE_NAME + "=");

        assertThat(currentStatus(token)).isEqualTo("EXPIRED");
    }

    /**
     * 手机浏览器扫码后打开确认页标记扫码，令牌不存在时应静默忽略，不报错。
     */
    @Test
    void scan_shouldBeNoop_whenTokenUnknown() throws Exception {
        mockMvc.perform(post("/api/authn/sso/qrcode/{token}/scan", "unknown-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 创建一个二维码登录会话并返回其令牌。
     *
     * @return 会话令牌
     */
    private String createSession() throws Exception {
        String body = mockMvc.perform(post("/api/authn/sso/qrcode/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = JacksonUtils.toObj(body, JsonNode.class).get("data");
        return data.get("token").asText();
    }

    /**
     * 查询指定令牌当前的状态字符串。
     *
     * @param token 会话令牌
     * @return 状态字符串
     */
    private String currentStatus(String token) throws Exception {
        String body = mockMvc.perform(get("/api/authn/sso/qrcode/{token}/status", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = JacksonUtils.toObj(body, JsonNode.class).get("data");
        return data.get("status").asText();
    }

    /**
     * 插入一份带指定登录认证方式的测试应用（CAS 协议）。
     *
     * @param loginMethods 允许的登录认证方式列表
     */
    private void seedApp(List<String> loginMethods) {
        LocalDateTime now = LocalDateTime.now();
        AppEntity app = AppEntity.builder()
                .name("扫码登录测试应用")
                .code("qrcode-test-app-" + UUID.randomUUID())
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
        appId = "qrcode-test-app-" + UUID.randomUUID().toString().replace("-", "");

        AppConfigEntity appConfig = AppConfigEntity.builder()
                .appRefId(appRefId)
                .appId(appId)
                .accessKey(UUID.randomUUID().toString().replace("-", ""))
                .secretKey("dummy-secret-key-ciphertext")
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
                .authProtocol(AuthProtocol.CAS)
                .servicePatterns(JacksonUtils.toJson(List.of("https://partner.example.com/**")))
                .loginMethods(JacksonUtils.toJson(loginMethods))
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appAuthConfigMapper.insert(authConfig);
    }

    /**
     * 插入一个测试用户，并为其签发一个 SSO 会话，模拟"手机浏览器已完成登录"。
     */
    private void seedUser() {
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name("扫码登录测试用户")
                .code("qrcode-login-test-" + UUID.randomUUID())
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
        mobileSessionToken = ssoSessionService.issue(userId);
    }

    /**
     * 构造一个能被 {@code SsoLoginContextResolver} 反解出本用例测试应用 {@code appId} 的
     * CAS 场景 {@code redirect} 原始值。
     *
     * @return redirect 原始值
     */
    private String casRedirect() {
        return "http://sso.example.com/api/authn/cas/" + appId + "/login?service=https://partner.example.com/callback";
    }
}
