package cn.nihility.rbac.sso.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * SSO 登录响应。
 */
@Getter
@Builder
@Schema(description = "SSO 登录响应")
public class SsoLoginResponse {

    /** 是否需要完成首次登录密码修改。 */
    @Schema(description = "是否需要完成首次登录密码修改")
    private Boolean firstLogin;
}
