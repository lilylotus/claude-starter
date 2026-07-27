package cn.nihility.rbac.auth.controller;

import cn.nihility.rbac.auth.dto.ChangePasswordRequest;
import cn.nihility.rbac.auth.dto.LoginRequest;
import cn.nihility.rbac.auth.dto.LoginResponse;
import cn.nihility.rbac.auth.dto.PublicKeyVO;
import cn.nihility.rbac.auth.dto.RefreshRequest;
import cn.nihility.rbac.auth.dto.RefreshResponse;
import cn.nihility.rbac.auth.service.AuthService;
import cn.nihility.rbac.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录认证接口：获取 RSA 公钥、口令登录、access-key 刷新、修改密码。前三个接口在
 * {@code IdentityAuthFilter} 白名单内，无需携带 {@code identity-token}；修改密码接口
 * 需要携带有效 {@code identity-token}，但豁免"首登强制改密"拦截。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "登录认证", description = "登录、令牌刷新、修改密码相关接口")
public class AuthController {

    /** 登录认证业务逻辑接口。 */
    private final AuthService authService;

    /**
     * 获取当前生效的 RSA 公钥。
     *
     * @return 公钥信息
     */
    @Operation(summary = "获取登录公钥", description = "客户端登录前使用该公钥对账号、密码分别加密，无需身份校验")
    @GetMapping("/api/auth/public-key")
    public PublicKeyVO publicKey() {
        return authService.getPublicKey();
    }

    /**
     * 口令登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    @Operation(summary = "登录", description = "账号、密码均为 RSA 公钥加密后的 Base64 密文，无需身份校验")
    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 刷新 access-key。
     *
     * @param request 刷新请求
     * @return 刷新响应
     */
    @Operation(summary = "刷新令牌", description = "使用有效的 refresh-key 换取新的 access-key，无需身份校验")
    @PostMapping("/api/auth/refresh")
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 修改当前登录用户的密码。
     *
     * @param request 修改密码请求
     * @return 无业务数据的成功响应
     */
    @Operation(summary = "修改密码",
            description = "需携带有效 identity-token，校验旧密码正确后更新；若原本处于首登待改密状态，修改成功后清除该状态")
    @PostMapping("/api/auth/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return Result.success();
    }
}
