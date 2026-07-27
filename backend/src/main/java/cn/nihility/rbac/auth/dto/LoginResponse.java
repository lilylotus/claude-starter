package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录成功响应，携带签发的 access-key/refresh-key 及首登标识。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    /** 访问令牌（不含横线的 UUID 字符串），后续业务请求通过 {@code identity-token} 请求头携带。 */
    @Schema(description = "访问令牌")
    private String accessKey;

    /** 访问令牌过期时间。 */
    @Schema(description = "访问令牌过期时间")
    private LocalDateTime accessExpireAt;

    /** 刷新令牌（不含横线的 UUID 字符串），用于静默换取新的访问令牌。 */
    @Schema(description = "刷新令牌")
    private String refreshKey;

    /** 刷新令牌过期时间。 */
    @Schema(description = "刷新令牌过期时间")
    private LocalDateTime refreshExpireAt;

    /** 是否处于待首次登录强制改密状态，前端据此决定是否重定向到强制改密页面。 */
    @Schema(description = "是否处于待首次登录强制改密状态")
    private Boolean firstLogin;
}
