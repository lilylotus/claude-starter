package cn.nihility.rbac.app.authconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用单点登录协议配置视图对象（app-auth-protocol-config change design.md Decision 2）。
 * 6 个协议接口地址由服务端按该应用的 AppId 实时计算返回，不落库，无论
 * {@code authProtocol} 取值为何都一并返回，方便管理员提前查看/复制。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用单点登录协议配置")
public class AppAuthConfigVO {

    /** 单点登录协议类型：NONE/CAS/OAUTH2。 */
    @Schema(description = "单点登录协议类型：NONE/CAS/OAUTH2")
    private String authProtocol;

    /** CAS service 参数 ANT 匹配规则列表。 */
    @Schema(description = "CAS service 参数 ANT 匹配规则列表")
    private List<String> casServicePatterns;

    /** OAuth2 redirect_uri ANT 匹配规则列表。 */
    @Schema(description = "OAuth2 redirect_uri ANT 匹配规则列表")
    private List<String> oauth2RedirectUriPatterns;

    /** CAS 单点登录接口地址（路径部分）。 */
    @Schema(description = "CAS 单点登录接口地址")
    private String casLoginUrl;

    /** CAS 票据验证接口地址（路径部分）。 */
    @Schema(description = "CAS 票据验证接口地址")
    private String casServiceValidateUrl;

    /** CAS 单点登出接口地址（路径部分）。 */
    @Schema(description = "CAS 单点登出接口地址")
    private String casLogoutUrl;

    /** OAuth2 授权接口地址（路径部分）。 */
    @Schema(description = "OAuth2 授权接口地址")
    private String oauthAuthorizeUrl;

    /** OAuth2 Access Token 接口地址（路径部分）。 */
    @Schema(description = "OAuth2 Access Token 接口地址")
    private String oauthTokenUrl;

    /** OAuth2 用户信息接口地址（路径部分）。 */
    @Schema(description = "OAuth2 用户信息接口地址")
    private String oauthUserInfoUrl;
}
