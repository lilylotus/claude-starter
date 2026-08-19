package cn.nihility.rbac.sso.cas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
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
 * {@link CasController} 的测试（tasks.md 7.5），起真实 MySQL/Redis 连接，覆盖 spec.md 全部
 * CAS 场景：service 白名单匹配/不匹配、未登录跳转 SSO 登录页、已登录直接签发票据、票据一次性
 * 消费、service 不一致校验失败、登出清除会话。断言 {@code Location} 响应头与 XML 响应体内容。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CasControllerTest {

    /** 测试用 CAS service 白名单规则对应的合法回跳地址前缀。 */
    private static final String ALLOWED_SERVICE = "https://partner.example.com/callback";

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

    /** 本用例插入的应用 id。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识（路径中的 {@code appId}）。 */
    private String appId;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

    /**
     * 每个用例前插入一份独立的测试应用（CAS 协议，service 白名单为
     * {@code https://partner.example.com/**}）与测试用户。
     */
    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();

        AppEntity app = AppEntity.builder()
                .name("CAS 测试应用")
                .code("cas-test-" + UUID.randomUUID())
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
        appId = "cas-test-app-" + UUID.randomUUID().toString().replace("-", "");

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
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        appAuthConfigMapper.insert(authConfig);

        UserEntity user = UserEntity.builder()
                .name("CAS测试用户")
                .code("cas-test-user-" + UUID.randomUUID())
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
     * service 未匹配任何已配置规则时，系统应拒绝且不发生任何重定向（无 {@code Location} 响应头），
     * 防止开放重定向（spec.md "service 未匹配任何规则被拒绝" Scenario）。
     */
    @Test
    void login_shouldReject_andNotRedirect_whenServiceNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", "https://evil.example.com/callback"))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));
    }

    /**
     * service 校验通过但当前浏览器未持有有效 SSO 会话时，应重定向到 SSO 登录页，而不是直接
     * 重定向到 {@code service}（spec.md "未登录时先跳转 SSO 登录页" Scenario）。
     */
    @Test
    void login_shouldRedirectToSsoLoginPage_whenSessionMissing() throws Exception {
        mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/sso/login?redirect=")));
    }

    /**
     * service 校验通过且浏览器持有有效 SSO 会话时，应直接签发服务票据并重定向回
     * {@code service}（spec.md "已登录时直接签发票据" Scenario）。
     */
    @Test
    void login_shouldIssueTicketAndRedirect_whenSessionValid() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(ALLOWED_SERVICE + "?ticket=ST-")));
    }

    /**
     * 合法票据校验成功，且携带 {@code format=XML} 时返回的 CAS 3.0 XML 响应体含正确的用户
     * 标识与默认属性（用户ID、姓名）；用同一票据再次发起验证请求应返回认证失败响应（一次性
     * 消费，spec.md "合法票据校验成功且不可重复使用"、"format=XML 时返回 XML 格式响应"
     * Scenario）。
     */
    @Test
    void serviceValidate_shouldSucceedOnce_thenFailOnSecondValidate() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        String location = mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");
        String ticket = location.substring(location.indexOf("ticket=") + "ticket=".length());

        String successBody = mockMvc.perform(get("/api/authn/cas/{appId}/p3/serviceValidate", appId)
                        .param("service", ALLOWED_SERVICE).param("ticket", ticket).param("format", "XML"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(successBody).contains("<cas:authenticationSuccess>");
        assertThat(successBody).contains("<cas:name>CAS测试用户</cas:name>");

        String failureBody = mockMvc.perform(get("/api/authn/cas/{appId}/p3/serviceValidate", appId)
                        .param("service", ALLOWED_SERVICE).param("ticket", ticket).param("format", "XML"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(failureBody).contains("INVALID_TICKET");
    }

    /**
     * 未指定 {@code format} 参数时默认返回 JSON 格式响应（而不是 XML），属性节点按默认的
     * "用户ID + 姓名"两个字段动态生成（add-sso-userinfo-field-mapping change spec.md
     * "未指定 format 时默认返回 JSON"、"用户属性按字段映射动态生成" Scenario）。
     */
    @Test
    void serviceValidate_shouldReturnJson_byDefault() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        String location = mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andReturn().getResponse().getHeader("Location");
        String ticket = location.substring(location.indexOf("ticket=") + "ticket=".length());

        String body = mockMvc.perform(get("/api/authn/cas/{appId}/p3/serviceValidate", appId)
                        .param("service", ALLOWED_SERVICE).param("ticket", ticket))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> json = JacksonUtils.toObj(body, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        Map<String, Object> serviceResponse = (Map<String, Object>) json.get("serviceResponse");
        Map<String, Object> authenticationSuccess = (Map<String, Object>) serviceResponse.get("authenticationSuccess");
        Map<String, Object> attributes = (Map<String, Object>) authenticationSuccess.get("attributes");
        assertThat(authenticationSuccess.get("user")).isNotNull();
        assertThat(attributes.get("name")).isEqualTo("CAS测试用户");
        assertThat(attributes).containsKey("id");
    }

    /**
     * 票据签发时绑定的 {@code service} 与验证请求携带的 {@code service} 不一致时，应返回认证
     * 失败响应（spec.md "service 不一致时校验失败" Scenario）。
     */
    @Test
    void serviceValidate_shouldFail_whenServiceMismatch() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        String location = mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andReturn().getResponse().getHeader("Location");
        String ticket = location.substring(location.indexOf("ticket=") + "ticket=".length());

        String body = mockMvc.perform(get("/api/authn/cas/{appId}/p3/serviceValidate", appId)
                        .param("service", "https://partner.example.com/other").param("ticket", ticket)
                        .param("format", "XML"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("<cas:authenticationFailure");
    }

    /**
     * service 校验通过且浏览器持有有效 SSO 会话时，登出后应清除该会话（后续该会话校验失效），
     * 并 302 重定向到 {@code service}（**BREAKING**：不再直接返回登出成功文本，
     * add-sso-single-logout change spec.md "登出后原会话失效并跳回 service" Scenario）。
     */
    @Test
    void logout_shouldClearSession_andRedirectToService() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/cas/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));

        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();
    }

    /**
     * 未持有会话时登出接口仍应正常 302 重定向到 {@code service}，不报错（幂等）。
     */
    @Test
    void logout_shouldRedirect_evenWithoutSession() throws Exception {
        mockMvc.perform(get("/api/authn/cas/{appId}/logout", appId).param("service", ALLOWED_SERVICE))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));
    }

    /**
     * service 未匹配任何已配置规则时，登出接口应拒绝且不发生任何重定向、不清除会话
     * （add-sso-single-logout change spec.md "service 未匹配任何规则被拒绝" Scenario）。
     */
    @Test
    void logout_shouldReject_andNotRedirect_whenServiceNotWhitelisted() throws Exception {
        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);

        mockMvc.perform(get("/api/authn/cas/{appId}/logout", appId)
                        .param("service", "https://evil.example.com/callback").cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("Location"));

        assertThat(ssoSessionService.verify(sessionToken)).contains(userId);
    }

    /**
     * 本次会话登录过的应用配置了登出通知回调地址，但该地址不可达（超时/连接失败）时，登出
     * 接口仍应正常、快速地完成会话失效与 302 重定向，不因通知失败而报错或延迟响应
     * （通知为 fire-and-forget，不等待 HTTP 响应，spec.md "回调通知失败不阻塞登出流程"
     * Scenario）。
     */
    @Test
    void logout_shouldCompletePromptly_whenNotifyTargetUnreachable() throws Exception {
        AppAuthConfigEntity authConfig = appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
        authConfig.setLogoutNotifyUrl("http://127.0.0.1:1/unreachable-logout-notify");
        appAuthConfigMapper.updateById(authConfig);

        String sessionToken = ssoSessionService.issue(userId);
        Cookie cookie = new Cookie("sso_session", sessionToken);
        mockMvc.perform(get("/api/authn/cas/{appId}/login", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound());

        long start = System.currentTimeMillis();
        mockMvc.perform(get("/api/authn/cas/{appId}/logout", appId).param("service", ALLOWED_SERVICE).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", ALLOWED_SERVICE));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertThat(elapsedMillis).isLessThan(2000L);
        assertThat(ssoSessionService.verify(sessionToken)).isEmpty();
    }
}
