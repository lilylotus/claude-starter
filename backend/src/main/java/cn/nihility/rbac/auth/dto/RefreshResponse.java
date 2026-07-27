package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 刷新令牌成功响应，仅包含新签发的 access-key 及其过期时间，refresh-key 保持不变。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "刷新令牌响应")
public class RefreshResponse {

    /** 新签发的访问令牌，旧的访问令牌立即失效。 */
    @Schema(description = "新签发的访问令牌")
    private String accessKey;

    /** 新访问令牌过期时间。 */
    @Schema(description = "新访问令牌过期时间")
    private LocalDateTime accessExpireAt;
}
