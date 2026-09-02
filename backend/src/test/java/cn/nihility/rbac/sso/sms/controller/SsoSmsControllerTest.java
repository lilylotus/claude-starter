package cn.nihility.rbac.sso.sms.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SsoSmsController} 的测试，起真实 MySQL/Redis 连接（同项目既有"不做重量级 mock"的
 * 测试风格），覆盖应用未开启短信登录时拒绝、发送+登录端到端成功流程、验证码错误时统一失败
 * 提示（add-sso-login-methods change）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SsoSmsControllerTest {

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

    /** 字符串 Redis 模板，用于测试内直接读取验证码/清理数据。 */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 本用例插入的应用 id，供 {@link #tearDown()} 清理。 */
    private Long appRefId;

    /** 本用例插入的应用对外标识。 */
    private String appId;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

    /** 本用例使用的测试手机号。 */
    private String mobile;

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
        if (mobile != null) {
            stringRedisTemplate.delete(java.util.Set.of("sso:sms:code:" + mobile, "sso:sms:cooldown:" + mobile,
                    "sso:sms:attempts:" + mobile));
            stringRedisTemplate.keys("sso:sms:daily:" + mobile + ":*").forEach(stringRedisTemplate::delete);
        }
    }

    /**
     * 目标应用未开启短信登录时，发送验证码接口应直接拒绝。
     */
    @Test
    void sendCode_shouldReject_whenAppDoesNotAllowSms() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD));
        mobile = randomMobile();

        mockMvc.perform(post("/api/authn/sso/sms/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect(), "mobile", mobile))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)))
                .andExpect(jsonPath("$.message").value("该应用未开启短信验证码登录"));
    }

    /**
     * 目标应用已开启短信登录、手机号唯一匹配一个启用状态用户时，发送验证码 + 提交正确验证码
     * 登录应端到端成功，签发 SSO 会话 Cookie。
     */
    @Test
    void sendCodeAndLogin_shouldSucceed_whenAppAllowsSmsAndMobileMatches() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD, LoginMethod.SMS));
        seedUser();

        mockMvc.perform(post("/api/authn/sso/sms/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect(), "mobile", mobile))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String code = stringRedisTemplate.opsForValue().get("sso:sms:code:" + mobile);
        assertThat(code).isNotBlank();

        mockMvc.perform(post("/api/authn/sso/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect(), "mobile", mobile, "code", code))))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.firstLogin").value(false));
    }

    /**
     * 提交错误验证码时应返回统一的失败提示。
     */
    @Test
    void login_shouldFail_withGenericMessage_whenCodeIncorrect() throws Exception {
        seedApp(List.of(LoginMethod.PASSWORD, LoginMethod.SMS));
        seedUser();

        mockMvc.perform(post("/api/authn/sso/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(Map.of("redirect", casRedirect(), "mobile", mobile, "code", "000000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(0)))
                .andExpect(jsonPath("$.message").value("验证码不正确或已过期"));
    }

    /**
     * 插入一份带指定登录认证方式的测试应用（CAS 协议）。
     *
     * @param loginMethods 允许的登录认证方式列表
     */
    private void seedApp(List<String> loginMethods) {
        LocalDateTime now = LocalDateTime.now();
        AppEntity app = AppEntity.builder()
                .name("短信登录测试应用")
                .code("sms-test-app-" + UUID.randomUUID())
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
        appId = "sms-test-app-" + UUID.randomUUID().toString().replace("-", "");

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
     * 插入一个测试用户，绑定本用例的测试手机号。
     */
    private void seedUser() {
        mobile = randomMobile();
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = UserEntity.builder()
                .name("短信登录测试用户")
                .code("sms-login-test-" + UUID.randomUUID())
                .gender("unknown")
                .mobile(mobile)
                .showOrder(0)
                .status(UserStatus.ENABLED)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
        userMapper.insert(user);
        userId = user.getId();
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

    /**
     * 生成一个符合手机号格式的随机测试手机号。
     *
     * @return 随机手机号
     */
    private String randomMobile() {
        StringBuilder sb = new StringBuilder("1");
        for (int i = 0; i < 10; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(0, 10));
        }
        return sb.toString();
    }
}
