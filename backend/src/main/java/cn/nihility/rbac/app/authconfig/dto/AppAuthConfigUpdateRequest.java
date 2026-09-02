package cn.nihility.rbac.app.authconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改应用单点登录协议配置的请求参数。协议类型与匹配列表之间的关联校验（CAS/OAUTH2
 * 要求 {@code servicePatterns} 非空、NONE 要求其清空）属于跨字段业务规则，不用 Bean
 * Validation 表达，在 service 层校验（app-auth-protocol-config change design.md
 * Decision 3；unify-app-auth-service-patterns change 合并为单一字段）。
 * {@code logoutNotifyUrl} 是否为合法 HTTP/HTTPS URL 同样在 service 层校验（对齐
 * {@code SyncConfigUpdateRequest#notifyUrl} 既有模式），此处只做长度约束
 * （add-sso-single-logout change design.md Decision 3）。
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

    /**
     * 回跳地址 ANT 匹配规则列表，CAS/OAuth2.0 等协议共用同一份存储，authProtocol=CAS 或
     * OAUTH2 时至少一条。
     */
    @Schema(description = "回跳地址 ANT 匹配规则列表，协议类型为 CAS 或 OAuth2.0 时至少一条")
    private List<String> servicePatterns;

    /**
     * 允许的登录认证方式，取值 {@code PASSWORD}/{@code SMS}/{@code QRCODE} 的子集，出现
     * 取值范围外的字符串直接拒绝；缺少 {@code PASSWORD} 时由服务端自动补齐，不拒绝请求
     * （service 层校验）。
     */
    @Schema(description = "允许的登录认证方式，取值 PASSWORD/SMS/QRCODE 的子集，缺少 PASSWORD 时服务端自动补齐")
    private List<String> loginMethods;

    /** 登出通知回调地址，可选，允许留空；非空时必须是合法的 http/https URL（service 层校验）。 */
    @Size(max = 255, message = "登出通知回调地址长度不能超过 255 个字符")
    @Schema(description = "登出通知回调地址，可选，非空时必须是合法的 http/https URL")
    private String logoutNotifyUrl;
}
