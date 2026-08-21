package cn.nihility.rbac.sso.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.entity.UserPasswordEntity;
import cn.nihility.rbac.auth.mapper.UserPasswordMapper;
import cn.nihility.rbac.auth.util.PasswordDigestUtils;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.RsaJdkUtils;
import cn.nihility.rbac.loginlog.constant.LoginFailReason;
import cn.nihility.rbac.loginlog.constant.LoginResult;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import cn.nihility.rbac.sso.session.SsoSessionCookieUtils;
import cn.nihility.rbac.sso.session.SsoSessionIdHasher;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link SsoLoginController} 的测试，起真实 MySQL/Redis 连接，覆盖 close-sso-log-and-policy-gaps
 * change tasks.md 1.4：账号解密失败、账号不存在、账号已停用、账号已删除、密码不正确、登录成功
 * 六类场景各自写入 {@code tab_login_log} 中正确的登录结果/失败原因，且对外错误提示文案保持
 * 统一（不额外泄露信息）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SsoLoginControllerTest {

    /** 测试用登录密码明文。 */
    private static final String PASSWORD_PLAIN = "Test#123456";

    /** MockMvc 客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /** 登录相关配置，复用其中的 RSA 公钥对请求体加密。 */
    @Autowired
    private RbacLoginProperties loginProperties;

    /** 用户数据访问接口。 */
    @Autowired
    private UserMapper userMapper;

    /** 用户密码数据访问接口。 */
    @Autowired
    private UserPasswordMapper userPasswordMapper;

    /** 登录日志数据访问接口，用于断言写入的日志字段。 */
    @Autowired
    private LoginLogMapper loginLogMapper;

    /** 本用例插入的测试用户 id。 */
    private Long userId;

    /** 本用例插入的测试用户账号（{@code tab_user.code}）。 */
    private String userCode;

    /** "账号不存在"场景产生的登录日志 id，供 {@link #tearDown()} 清理。 */
    private Long unknownAccountLoginLogId;

    /** "账号解密失败"场景产生的登录日志 id，供 {@link #tearDown()} 清理。 */
    private Long decryptFailedLoginLogId;

    /**
     * 每个用例前插入一个独立的启用状态测试用户及其密码。
     */
    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        userCode = "sso-login-test-" + UUID.randomUUID();

        UserEntity user = UserEntity.builder()
                .name("SSO登录测试用户")
                .code(userCode)
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

        userPasswordMapper.insert(UserPasswordEntity.builder()
                .userId(userId)
                .passwordDigest(PasswordDigestUtils.digest(PASSWORD_PLAIN, "salt"))
                .salt("salt")
                .firstLogin(Boolean.FALSE)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build());
    }

    /**
     * 每个用例结束后清理本用例插入的测试数据（用户、密码、产生的登录日志）。
     */
    @AfterEach
    void tearDown() {
        if (userId != null) {
            loginLogMapper.delete(new LambdaQueryWrapper<LoginLogEntity>().eq(LoginLogEntity::getUserId, userId));
            userPasswordMapper.delete(new LambdaQueryWrapper<UserPasswordEntity>().eq(UserPasswordEntity::getUserId, userId));
            userMapper.deleteById(userId);
        }
        if (unknownAccountLoginLogId != null) {
            loginLogMapper.deleteById(unknownAccountLoginLogId);
        }
        if (decryptFailedLoginLogId != null) {
            loginLogMapper.deleteById(decryptFailedLoginLogId);
        }
    }

    /**
     * 账号、密码密文均能正常解密，账号存在、状态启用且密码正确时登录成功，应签发 SSO 会话 Cookie
     * 并记录一条成功的登录日志，其 {@code sessionId} 应等于本次签发的会话令牌的 SHA-256 摘要
     * （add-sso-protocol-access-log change design.md Decision 6）。
     */
    @Test
    void login_shouldSucceed_andRecordSuccessLog_whenAccountAndPasswordCorrect() throws Exception {
        String setCookieHeader = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(userCode, PASSWORD_PLAIN)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andReturn().getResponse().getHeader("Set-Cookie");
        String sessionToken = extractSessionToken(setCookieHeader);

        List<LoginLogEntity> logs = queryLogsForUser();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getLoginResult()).isEqualTo(LoginResult.SUCCESS);
        assertThat(logs.get(0).getFailReason()).isNull();
        assertThat(logs.get(0).getLoginAccount()).isEqualTo(userCode);
        assertThat(logs.get(0).getUserId()).isEqualTo(userId);
        assertThat(logs.get(0).getSessionId()).isEqualTo(SsoSessionIdHasher.hash(sessionToken));
    }

    /**
     * 账号不存在时登录失败，对外仍返回统一的"账号或密码不正确"提示，但登录日志应记录
     * "账号不存在"这一更细粒度的失败原因。
     */
    @Test
    void login_shouldFail_andRecordAccountNotFoundLog_whenAccountUnknown() throws Exception {
        String unknownAccount = "unknown-account-" + UUID.randomUUID();
        String body = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(unknownAccount, PASSWORD_PLAIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertLoginFailedMessage(body);

        List<LoginLogEntity> logs = loginLogMapper.selectList(new LambdaQueryWrapper<LoginLogEntity>()
                .eq(LoginLogEntity::getLoginAccount, unknownAccount));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getLoginResult()).isEqualTo(LoginResult.FAILED);
        assertThat(logs.get(0).getFailReason()).isEqualTo(LoginFailReason.ACCOUNT_NOT_FOUND);
        assertThat(logs.get(0).getSessionId()).isNull();
        unknownAccountLoginLogId = logs.get(0).getId();
    }

    /**
     * 账号已停用时登录失败，不校验密码，记录失败原因为"账号已停用"。
     */
    @Test
    void login_shouldFail_andRecordAccountDisabledLog_whenAccountDisabled() throws Exception {
        userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId).set(UserEntity::getStatus, UserStatus.DISABLED));

        String body = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(userCode, PASSWORD_PLAIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertLoginFailedMessage(body);

        List<LoginLogEntity> logs = queryLogsForUser();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFailReason()).isEqualTo(LoginFailReason.ACCOUNT_DISABLED);
        assertThat(logs.get(0).getSessionId()).isNull();
    }

    /**
     * 账号已删除时登录失败，不校验密码，记录失败原因为"账号已删除"（与"账号不存在"区分开）。
     */
    @Test
    void login_shouldFail_andRecordAccountDeletedLog_whenAccountDeleted() throws Exception {
        userMapper.update(null, new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, userId).set(UserEntity::getStatus, UserStatus.DELETED));

        String body = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(userCode, PASSWORD_PLAIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertLoginFailedMessage(body);

        List<LoginLogEntity> logs = queryLogsForUser();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFailReason()).isEqualTo(LoginFailReason.ACCOUNT_DELETED);
        assertThat(logs.get(0).getSessionId()).isNull();
    }

    /**
     * 密码不正确时登录失败，记录失败原因为"密码不正确"。
     */
    @Test
    void login_shouldFail_andRecordPasswordMismatchLog_whenPasswordIncorrect() throws Exception {
        String body = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequestJson(userCode, "wrong-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertLoginFailedMessage(body);

        List<LoginLogEntity> logs = queryLogsForUser();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getFailReason()).isEqualTo(LoginFailReason.PASSWORD_MISMATCH);
        assertThat(logs.get(0).getSessionId()).isNull();
    }

    /**
     * 账号/密码密文解密失败时登录失败，记录日志时账号、用户 id、用户姓名均为 {@code null}，
     * 失败原因为"账号解密失败"。
     */
    @Test
    void login_shouldFail_andRecordDecryptFailedLog_whenCipherTextInvalid() throws Exception {
        Map<String, String> requestBody = Map.of("account", "not-a-valid-base64-cipher-text",
                "password", RsaJdkUtils.encrypt(PASSWORD_PLAIN, loginProperties.getPublicKey()));
        String body = mockMvc.perform(post("/api/authn/sso/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JacksonUtils.toJson(requestBody)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertLoginFailedMessage(body);

        List<LoginLogEntity> logs = loginLogMapper.selectList(new LambdaQueryWrapper<LoginLogEntity>()
                .isNull(LoginLogEntity::getLoginAccount)
                .eq(LoginLogEntity::getFailReason, LoginFailReason.DECRYPT_FAILED)
                .orderByDesc(LoginLogEntity::getId)
                .last("limit 1"));
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getUserId()).isNull();
        assertThat(logs.get(0).getUserName()).isNull();
        assertThat(logs.get(0).getSessionId()).isNull();
        decryptFailedLoginLogId = logs.get(0).getId();
    }

    /**
     * 断言对外返回的错误提示文案保持统一的"账号或密码不正确"，不因场景不同而泄露差异。
     *
     * @param responseBody 响应体 JSON 字符串
     */
    private void assertLoginFailedMessage(String responseBody) {
        Map<String, Object> json = JacksonUtils.toObj(responseBody, JacksonUtils.MAP_OBJECT_TYPE_REFERENCE);
        assertThat(json.get("message")).isEqualTo("账号或密码不正确");
    }

    /**
     * 查询本用例测试用户产生的全部登录日志。
     *
     * @return 登录日志列表
     */
    private List<LoginLogEntity> queryLogsForUser() {
        return loginLogMapper.selectList(new LambdaQueryWrapper<LoginLogEntity>().eq(LoginLogEntity::getUserId, userId));
    }

    /**
     * 从 {@code Set-Cookie} 响应头字符串中提取 {@code sso_session} Cookie 的原始令牌值。
     *
     * @param setCookieHeader {@code Set-Cookie} 响应头原始值
     * @return 提取出的会话令牌
     */
    private String extractSessionToken(String setCookieHeader) {
        String prefix = SsoSessionCookieUtils.COOKIE_NAME + "=";
        int start = setCookieHeader.indexOf(prefix) + prefix.length();
        int end = setCookieHeader.indexOf(';', start);
        return setCookieHeader.substring(start, end);
    }

    /**
     * 构造一个账号、密码均使用测试用 RSA 公钥加密的登录请求体 JSON。
     *
     * @param account  明文账号
     * @param password 明文密码
     * @return 请求体 JSON 字符串
     */
    private String loginRequestJson(String account, String password) {
        Map<String, String> requestBody = Map.of(
                "account", RsaJdkUtils.encrypt(account, loginProperties.getPublicKey()),
                "password", RsaJdkUtils.encrypt(password, loginProperties.getPublicKey()));
        return JacksonUtils.toJson(requestBody);
    }
}
