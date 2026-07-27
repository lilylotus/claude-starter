package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录用 RSA 公钥响应。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录公钥响应")
public class PublicKeyVO {

    /** RSA 公钥（X.509 DER + Base64）。 */
    @Schema(description = "RSA 公钥（Base64 编码）")
    private String publicKey;
}
