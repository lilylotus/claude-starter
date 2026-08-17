package cn.nihility.rbac.app.authconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改应用单点登录协议配置的请求参数。协议类型与两个匹配列表之间的关联校验（CAS/OAUTH2
 * 要求对应列表非空、NONE 要求两个列表均清空）属于跨字段业务规则，不用 Bean Validation
 * 表达，在 service 层校验（app-auth-protocol-config change design.md Decision 3）。
 */
@Getter
@Setter
@Schema(description = "修改应用单点登录协议配置请求参数")
public class AppAuthConfigUpdateRequest {

    /** 单点登录协议类型，必填，只允许 {@code NONE}/{@code CAS}/{@code OAUTH2}。 */
    @NotBlank(message = "协议类型不能为空")
    @Pattern(regexp = "^(NONE|CAS|OAUTH2)$", message = "协议类型只能是 NONE、CAS 或 OAUTH2")
    @Schema(description = "单点登录协议类型，只能是 NONE、CAS 或 OAUTH2")
    private String authProtocol;

    /** CAS service 参数 ANT 匹配规则列表，authProtocol=CAS 时至少一条。 */
    @Schema(description = "CAS service 参数 ANT 匹配规则列表，协议类型为 CAS 时至少一条")
    private List<String> casServicePatterns;

    /** OAuth2 redirect_uri ANT 匹配规则列表，authProtocol=OAUTH2 时至少一条。 */
    @Schema(description = "OAuth2 redirect_uri ANT 匹配规则列表，协议类型为 OAuth2.0 时至少一条")
    private List<String> oauth2RedirectUriPatterns;
}
