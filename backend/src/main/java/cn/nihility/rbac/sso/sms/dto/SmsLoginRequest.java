package cn.nihility.rbac.sso.sms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 短信验证码登录请求参数。
 */
@Getter
@Setter
@Schema(description = "短信验证码登录请求参数")
public class SmsLoginRequest {

    /** SSO 登录页 {@code redirect} 参数原始值，用于反解出目标应用并校验其是否允许短信登录。 */
    @NotBlank(message = "redirect 不能为空")
    @Schema(description = "SSO 登录页 redirect 参数原始值")
    private String redirect;

    /** 登录使用的手机号。 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String mobile;

    /** 短信验证码。 */
    @NotBlank(message = "验证码不能为空")
    @Schema(description = "短信验证码")
    private String code;
}
