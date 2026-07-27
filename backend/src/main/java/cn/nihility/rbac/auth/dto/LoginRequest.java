package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求参数，{@code account}/{@code password} 均为客户端使用 RSA 公钥
 * （{@code RSA/ECB/OAEPWithSHA-256AndMGF1Padding}）加密后的 Base64 密文，
 * 网络请求体中不出现明文账号密码。
 */
@Getter
@Setter
@Schema(description = "登录请求参数")
public class LoginRequest {

    /** RSA 加密后的账号密文（Base64），明文对应用户编号（{@code tab_user.code}）。 */
    @NotBlank(message = "账号不能为空")
    @Schema(description = "RSA 加密后的账号密文（Base64）")
    private String account;

    /** RSA 加密后的密码密文（Base64）。 */
    @NotBlank(message = "密码不能为空")
    @Schema(description = "RSA 加密后的密码密文（Base64）")
    private String password;
}
