package cn.nihility.rbac.auth.service.impl;

import cn.nihility.rbac.auth.config.RbacLoginProperties;
import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.dto.ChangePasswordRequest;
import cn.nihility.rbac.auth.dto.LoginRequest;
import cn.nihility.rbac.auth.dto.LoginResponse;
import cn.nihility.rbac.auth.dto.PublicKeyVO;
import cn.nihility.rbac.auth.dto.RefreshRequest;
import cn.nihility.rbac.auth.dto.RefreshResponse;
import cn.nihility.rbac.auth.dto.TokenPair;
import cn.nihility.rbac.auth.service.AuthService;
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
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 登录认证业务逻辑实现。账号使用 {@code tab_user.code}（用户编号）作为登录标识；账号不存在、
 * 密码不匹配、账号已停用/已删除均归一为同一条提示信息，不向客户端泄露具体区别
 * （spec.md "账号不存在时登录失败" Scenario）。登录场景需要读取 {@code user} 模块的
 * {@link UserMapper} 按账号查用户，这是登录固有的、单向的模块依赖，不同于"重置密码"场景中
 * {@code user} 模块反向依赖本模块 {@link PasswordService} 的方向。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 登录失败的统一提示信息，不区分账号不存在/密码错误/账号不可用，避免信息泄露。 */
    private static final String LOGIN_FAILED_MESSAGE = "账号或密码不正确";

    /** 登录相关配置：RSA 密钥对。 */
    private final RbacLoginProperties loginProperties;

    /** 用户数据访问接口，登录时按账号（用户编号）查询用户。 */
    private final UserMapper userMapper;

    /** 密码业务逻辑接口。 */
    private final PasswordService passwordService;

    /** 会话令牌业务逻辑接口。 */
    private final TokenService tokenService;

    /** 登录日志记录组件，记录每一次登录尝试（成功 + 失败）。 */
    private final LoginLogRecorder loginLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public PublicKeyVO getPublicKey() {
        return PublicKeyVO.builder().publicKey(loginProperties.getPublicKey()).build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String account;
        String password;
        try {
            account = decrypt(request.getAccount());
            password = decrypt(request.getPassword());
        } catch (BusinessException e) {
            // 解密失败根本拿不到明文账号，不记录密文本身——密文不是登录日志该保留的信息。
            loginLogRecorder.recordFailure(null, null, null, LoginFailReason.DECRYPT_FAILED);
            throw e;
        }

        // 不再 .ne(status, DELETED)：查询本身排除已删除账号会导致"账号不存在"与"账号已
        // 删除"两种情况在 SQL 层面就已经合并成同一个 null 结果，Java 代码无论怎么写都
        // 区分不出来，交给下面的显式分支按状态判断。
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, account));
        if (user == null) {
            loginLogRecorder.recordFailure(account, null, null, LoginFailReason.ACCOUNT_NOT_FOUND);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
        if (Objects.equals(user.getStatus(), UserStatus.DELETED)) {
            loginLogRecorder.recordFailure(account, user.getId(), user.getName(), LoginFailReason.ACCOUNT_DELETED);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
        if (!Objects.equals(user.getStatus(), UserStatus.ENABLED)) {
            loginLogRecorder.recordFailure(account, user.getId(), user.getName(), LoginFailReason.ACCOUNT_DISABLED);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
        if (!passwordService.verifyPassword(user.getId(), password)) {
            loginLogRecorder.recordFailure(account, user.getId(), user.getName(), LoginFailReason.PASSWORD_MISMATCH);
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }

        loginLogRecorder.recordSuccess(account, user.getId(), user.getName(), null);
        TokenPair tokenPair = tokenService.issue(user.getId());
        boolean firstLogin = passwordService.isFirstLogin(user.getId());
        return LoginResponse.builder()
                .accessKey(tokenPair.getAccessKey())
                .accessExpireAt(tokenPair.getAccessExpireAt())
                .refreshKey(tokenPair.getRefreshKey())
                .refreshExpireAt(tokenPair.getRefreshExpireAt())
                .firstLogin(firstLogin)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RefreshResponse refresh(RefreshRequest request) {
        TokenPair tokenPair = tokenService.refresh(request.getRefreshKey());
        return RefreshResponse.builder()
                .accessKey(tokenPair.getAccessKey())
                .accessExpireAt(tokenPair.getAccessExpireAt())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(AuthErrorCode.UNAUTHORIZED, "未登录，请先登录");
        }
        if (!passwordService.verifyPassword(userId, request.getOldPassword())) {
            throw new BusinessException("原密码不正确");
        }
        passwordService.updatePassword(userId, request.getNewPassword());
    }

    /**
     * 使用配置的 RSA 私钥解密客户端提交的密文，解密失败（密文格式错误、密钥不匹配等）统一
     * 归一为登录失败提示，不向客户端暴露解密异常细节。
     *
     * @param cipherTextBase64 Base64 编码的 RSA 密文
     * @return 解密后的明文
     */
    private String decrypt(String cipherTextBase64) {
        try {
            return RsaJdkUtils.decrypt(cipherTextBase64, loginProperties.getPrivateKey());
        } catch (Exception e) {
            throw new BusinessException(LOGIN_FAILED_MESSAGE);
        }
    }
}
