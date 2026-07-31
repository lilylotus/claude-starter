package cn.nihility.rbac.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.dto.ChangePasswordRequest;
import cn.nihility.rbac.auth.dto.LoginRequest;
import cn.nihility.rbac.auth.dto.LoginResponse;
import cn.nihility.rbac.auth.dto.RefreshRequest;
import cn.nihility.rbac.auth.dto.TokenPair;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.RsaJdkUtils;
import cn.nihility.rbac.loginlog.constant.LoginFailReason;
import cn.nihility.rbac.loginlog.service.LoginLogRecorder;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AuthServiceImpl} 的单元测试，覆盖登录成功/失败（账号不存在、密码错误、账号停用）、
 * 令牌刷新成功/失败、修改密码成功/失败等分支（password-login-auth spec.md 对应 Scenario）。
 * 使用 {@link RsaJdkUtils#generateKeyPair()} 在测试内生成一套真实密钥对，模拟客户端用公钥
 * 加密账号密码、服务端用私钥解密的完整链路，不依赖外部服务。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的密码业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PasswordService passwordService;

    /** 被测服务的会话令牌业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private TokenService tokenService;

    /** 被测服务的登录日志记录依赖，使用 Mockito 打桩，校验各失败/成功分支的记录调用。 */
    @Mock
    private LoginLogRecorder loginLogRecorder;

    /** 测试用 RSA 公钥。 */
    private String publicKey;

    /** 测试用 RSA 私钥。 */
    private String privateKey;

    /** 被测服务实例。 */
    private AuthServiceImpl authService;

    /**
     * 每个用例执行前生成一套测试专用 RSA 密钥对并构造被测服务，清空线程级当前登录用户标记。
     */
    @BeforeEach
    void setUp() {
        Pair<String, String> keyPair = RsaJdkUtils.generateKeyPair();
        publicKey = keyPair.getLeft();
        privateKey = keyPair.getRight();

        RbacLoginProperties loginProperties = new RbacLoginProperties();
        loginProperties.setPublicKey(publicKey);
        loginProperties.setPrivateKey(privateKey);

        authService = new AuthServiceImpl(loginProperties, userMapper, passwordService, tokenService, loginLogRecorder);
        CurrentUserContext.clear();
    }

    /**
     * 每个用例结束后清空线程级当前登录用户标记，避免污染后续用例。
     */
    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    /**
     * 账号密码正确时登录成功，应返回令牌与首登标识（spec.md "账号密码正确时登录成功" Scenario）。
     */
    @Test
    void login_shouldSucceed_whenAccountAndPasswordCorrect() {
        UserEntity user = buildUser(1L, "U001", UserStatus.ENABLED);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verifyPassword(1L, "Default#123456")).thenReturn(true);
        when(passwordService.isFirstLogin(1L)).thenReturn(true);
        LocalDateTime now = LocalDateTime.now();
        when(tokenService.issue(1L)).thenReturn(TokenPair.builder()
                .accessKey("access-key")
                .accessExpireAt(now.plusHours(2))
                .refreshKey("refresh-key")
                .refreshExpireAt(now.plusDays(7))
                .build());

        LoginResponse response = authService.login(loginRequest("U001", "Default#123456"));

        assertThat(response.getAccessKey()).isEqualTo("access-key");
        assertThat(response.getRefreshKey()).isEqualTo("refresh-key");
        assertThat(response.getFirstLogin()).isTrue();
        verify(loginLogRecorder).recordSuccess("U001", 1L, "张三");
        verify(loginLogRecorder, never()).recordFailure(any(), any(), any(), any());
    }

    /**
     * 账号不存在时登录失败，应返回业务错误，不签发令牌（spec.md "账号不存在时登录失败" Scenario）。
     */
    @Test
    void login_shouldThrowBusinessException_whenAccountNotFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login(loginRequest("unknown", "Default#123456")))
                .isInstanceOf(BusinessException.class);
        verify(tokenService, never()).issue(anyLong());
        verify(loginLogRecorder).recordFailure("unknown", null, null, LoginFailReason.ACCOUNT_NOT_FOUND);
        verify(loginLogRecorder, never()).recordSuccess(any(), any(), any());
    }

    /**
     * 密码错误时登录失败（spec.md "密码错误时登录失败" Scenario）。
     */
    @Test
    void login_shouldThrowBusinessException_whenPasswordIncorrect() {
        UserEntity user = buildUser(1L, "U001", UserStatus.ENABLED);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordService.verifyPassword(eq(1L), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest("U001", "wrong-password")))
                .isInstanceOf(BusinessException.class);
        verify(tokenService, never()).issue(anyLong());
        verify(loginLogRecorder).recordFailure("U001", 1L, "张三", LoginFailReason.PASSWORD_MISMATCH);
        verify(loginLogRecorder, never()).recordSuccess(any(), any(), any());
    }

    /**
     * 账号已停用时登录失败，不签发令牌，也不再校验密码（spec.md "账号已停用或已删除时登录失败" Scenario）。
     */
    @Test
    void login_shouldThrowBusinessException_whenAccountDisabled() {
        UserEntity user = buildUser(1L, "U001", UserStatus.DISABLED);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(loginRequest("U001", "Default#123456")))
                .isInstanceOf(BusinessException.class);
        verify(passwordService, never()).verifyPassword(anyLong(), any());
        verify(tokenService, never()).issue(anyLong());
        verify(loginLogRecorder).recordFailure("U001", 1L, "张三", LoginFailReason.ACCOUNT_DISABLED);
        verify(loginLogRecorder, never()).recordSuccess(any(), any(), any());
    }

    /**
     * 账号已删除时登录失败，不签发令牌，也不再校验密码，与"账号不存在"分别记录不同的失败原因
     * （spec.md "账号已停用或已删除时登录失败" Scenario，design.md Decision 1）。
     */
    @Test
    void login_shouldThrowBusinessException_whenAccountDeleted() {
        UserEntity user = buildUser(1L, "U001", UserStatus.DELETED);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatThrownBy(() -> authService.login(loginRequest("U001", "Default#123456")))
                .isInstanceOf(BusinessException.class);
        verify(passwordService, never()).verifyPassword(anyLong(), any());
        verify(tokenService, never()).issue(anyLong());
        verify(loginLogRecorder).recordFailure("U001", 1L, "张三", LoginFailReason.ACCOUNT_DELETED);
        verify(loginLogRecorder, never()).recordSuccess(any(), any(), any());
    }

    /**
     * 账号/密码密文解密失败时登录失败，记录日志时不带明文账号（本来就解不出来），也不查询用户、
     * 不签发令牌（design.md Decision 1 解密阶段场景）。
     */
    @Test
    void login_shouldThrowBusinessException_whenDecryptFailed() {
        LoginRequest request = new LoginRequest();
        request.setAccount("not-a-valid-base64-cipher-text");
        request.setPassword(RsaJdkUtils.encrypt("Default#123456", publicKey));

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BusinessException.class);
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
        verify(tokenService, never()).issue(anyLong());
        verify(loginLogRecorder).recordFailure(null, null, null, LoginFailReason.DECRYPT_FAILED);
        verify(loginLogRecorder, never()).recordSuccess(any(), any(), any());
    }

    /**
     * refresh-key 有效时刷新成功，应返回新 access-key（spec.md "refresh-key 有效时刷新成功" Scenario）。
     */
    @Test
    void refresh_shouldReturnNewAccessKey_whenRefreshKeyValid() {
        LocalDateTime now = LocalDateTime.now();
        when(tokenService.refresh("refresh-key")).thenReturn(TokenPair.builder()
                .accessKey("new-access-key")
                .accessExpireAt(now.plusHours(2))
                .refreshKey("refresh-key")
                .refreshExpireAt(now.plusDays(7))
                .build());

        RefreshRequest request = new RefreshRequest();
        request.setRefreshKey("refresh-key");

        var response = authService.refresh(request);

        assertThat(response.getAccessKey()).isEqualTo("new-access-key");
    }

    /**
     * refresh-key 已过期/无效时，刷新失败应直接透传 {@link TokenService} 抛出的业务异常
     * （spec.md "refresh-key 已过期时刷新失败" Scenario）。
     */
    @Test
    void refresh_shouldPropagateBusinessException_whenRefreshKeyInvalid() {
        when(tokenService.refresh("bad-refresh-key"))
                .thenThrow(new BusinessException(401, "刷新令牌无效或已过期，请重新登录"));

        RefreshRequest request = new RefreshRequest();
        request.setRefreshKey("bad-refresh-key");

        assertThatThrownBy(() -> authService.refresh(request)).isInstanceOf(BusinessException.class);
    }

    /**
     * 旧密码正确时修改密码成功（spec.md "旧密码正确时修改成功" Scenario）。
     */
    @Test
    void changePassword_shouldSucceed_whenOldPasswordCorrect() {
        CurrentUserContext.setUserId(1L);
        when(passwordService.verifyPassword(1L, "OldPass#123")).thenReturn(true);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("OldPass#123");
        request.setNewPassword("NewPass#456");

        authService.changePassword(request);

        verify(passwordService).updatePassword(1L, "NewPass#456");
    }

    /**
     * 旧密码错误时修改密码失败，不更新密码记录（spec.md "旧密码错误时修改失败" Scenario）。
     */
    @Test
    void changePassword_shouldThrowBusinessException_whenOldPasswordIncorrect() {
        CurrentUserContext.setUserId(1L);
        when(passwordService.verifyPassword(1L, "WrongOld#123")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("WrongOld#123");
        request.setNewPassword("NewPass#456");

        assertThatThrownBy(() -> authService.changePassword(request)).isInstanceOf(BusinessException.class);
        verify(passwordService, never()).updatePassword(anyLong(), any());
    }

    /**
     * 构造一个登录请求，账号、密码均使用测试专用公钥加密。
     *
     * @param account  明文账号
     * @param password 明文密码
     * @return 登录请求
     */
    private LoginRequest loginRequest(String account, String password) {
        LoginRequest request = new LoginRequest();
        request.setAccount(RsaJdkUtils.encrypt(account, publicKey));
        request.setPassword(RsaJdkUtils.encrypt(password, publicKey));
        return request;
    }

    /**
     * 构造一个测试用的用户实体。
     *
     * @param id     主键 id
     * @param code   用户编号（登录账号）
     * @param status 状态
     * @return 用户实体
     */
    private UserEntity buildUser(long id, String code, int status) {
        return UserEntity.builder().id(id).name("张三").code(code).status(status).build();
    }
}
