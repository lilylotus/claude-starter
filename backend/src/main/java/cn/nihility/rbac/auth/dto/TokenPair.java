package cn.nihility.rbac.auth.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 令牌对内部传输对象，{@link cn.nihility.rbac.auth.service.TokenService} 签发/刷新会话后
 * 返回，由上层 {@code AuthService} 组装为对外的 {@link LoginResponse}/{@link RefreshResponse}，
 * 不直接作为控制器返回值暴露。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenPair {

    /** 访问令牌。 */
    private String accessKey;

    /** 访问令牌过期时间。 */
    private LocalDateTime accessExpireAt;

    /** 刷新令牌。 */
    private String refreshKey;

    /** 刷新令牌过期时间。 */
    private LocalDateTime refreshExpireAt;
}
