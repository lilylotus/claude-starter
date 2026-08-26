package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改密码请求参数。当前登录用户身份由调用接口从已认证会话中读取，不通过请求体传入。
 */
@Getter
@Setter
@Schema(description = "修改密码请求参数")
public class ChangePasswordRequest {

    /** 旧密码明文。 */
    @NotBlank(message = "原密码不能为空")
    @Schema(description = "原密码")
    private String oldPassword;

    /** 新密码明文。 */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "新密码长度需在 6-64 个字符之间")
    @Schema(description = "新密码")
    private String newPassword;
}
