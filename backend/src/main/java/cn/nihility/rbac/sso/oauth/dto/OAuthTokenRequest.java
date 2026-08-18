package cn.nihility.rbac.sso.oauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code POST /api/authn/oauth/token} 请求参数的内部载体（app-sso-protocol-runtime change
 * design.md Decision 6）：字段均可为空，具体哪些必填按 {@code grantType} 分支在
 * {@code OAuthController} 中校验。控制器层用 {@code @RequestParam} 逐个接收
 * {@code client_id}/{@code client_secret}/{@code redirect_uri}/{@code grant_type}/
 * {@code code}/{@code refresh_token} 表单参数后构造本对象，规避 Spring
 * {@code @ModelAttribute} 默认按 Java 属性名（驼峰）绑定、无法直接映射 OAuth2 标准
 * snake_case 参数名的问题。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth2 令牌请求参数")
public class OAuthTokenRequest {

    /** OAuth2 client_id，{@code authorization_code} 授权类型下必填。 */
    @Schema(description = "client_id，authorization_code 授权类型下必填")
    private String clientId;

    /** OAuth2 client_secret，{@code authorization_code} 授权类型下必填。 */
    @Schema(description = "client_secret，authorization_code 授权类型下必填")
    private String clientSecret;

    /** OAuth2 redirect_uri，须与签发授权码时使用的 redirect_uri 一致。 */
    @Schema(description = "redirect_uri，authorization_code 授权类型下必填")
    private String redirectUri;

    /** 授权类型：{@code authorization_code} 或 {@code refresh_token}。 */
    @Schema(description = "授权类型：authorization_code 或 refresh_token")
    private String grantType;

    /** 授权码，{@code authorization_code} 授权类型下必填。 */
    @Schema(description = "授权码，authorization_code 授权类型下必填")
    private String code;

    /** refresh token，{@code refresh_token} 授权类型下必填。 */
    @Schema(description = "refresh token，refresh_token 授权类型下必填")
    private String refreshToken;
}
