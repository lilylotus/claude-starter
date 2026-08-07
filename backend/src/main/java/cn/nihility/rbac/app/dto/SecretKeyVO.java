package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 重置 SecretKey 接口的响应体，携带新生成的 SecretKey 明文。这是整个应用对外接口配置能力
 * 中唯一会返回明文 SecretKey 的地方，且仅在本次响应中出现一次，此后任何查询接口都不会再
 * 返回明文（app-api-credentials-config change design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "重置 SecretKey 响应，明文仅本次展示，关闭后不再可查")
public class SecretKeyVO {

    /** 新生成的 SecretKey 明文，仅本次响应可见。 */
    @Schema(description = "新生成的 SecretKey 明文，仅本次响应可见，请妥善保管")
    private String secretKey;
}
