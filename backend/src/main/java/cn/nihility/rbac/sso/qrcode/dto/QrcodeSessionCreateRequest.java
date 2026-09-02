package cn.nihility.rbac.sso.qrcode.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建二维码登录会话请求参数。
 */
@Getter
@Setter
@Schema(description = "创建二维码登录会话请求参数")
public class QrcodeSessionCreateRequest {

    /** SSO 登录页 {@code redirect} 参数原始值，用于反解出目标应用并校验其是否允许扫码登录。 */
    @NotBlank(message = "redirect 不能为空")
    @Schema(description = "SSO 登录页 redirect 参数原始值")
    private String redirect;
}
