package cn.nihility.rbac.sso.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 当前请求所携带 SSO 会话 Cookie 的登录态查询结果，供扫码登录确认页判断"当前浏览器是否
 * 已完成登录"使用（add-sso-login-methods change）。
 */
@Getter
@Builder
@Schema(description = "SSO 会话登录态查询结果")
public class SsoSessionStatusVO {

    /** 当前请求携带的 SSO 会话 Cookie 是否有效。 */
    @Schema(description = "当前请求携带的 SSO 会话 Cookie 是否有效")
    private Boolean authenticated;
}
