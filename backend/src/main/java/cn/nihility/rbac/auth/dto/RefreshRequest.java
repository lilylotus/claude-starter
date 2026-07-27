package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 刷新令牌请求参数。
 */
@Getter
@Setter
@Schema(description = "刷新令牌请求参数")
public class RefreshRequest {

    /** 刷新令牌。 */
    @NotBlank(message = "刷新令牌不能为空")
    @Schema(description = "刷新令牌（refresh-key）")
    private String refreshKey;
}
