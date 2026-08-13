package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改应用数据同步配置的请求参数：整个应用一份的基础同步配置项——同步方式（通知/拉取）
 * 及通知模式下的回调地址、自定义参数。组织/用户/任职/应用/角色/字典六个数据域各自的启用开关、
 * 拉取分页大小改由 {@code AppSyncConfigController} 的数据域配置接口单独维护
 * （app-sync-field-mapping change tasks.md 4.6），不再包含在本请求参数内。同步方式为
 * NOTIFY 时接口地址是否合法属于跨字段校验，Bean Validation 在此不做（{@code notifyUrl}
 * 本身不加 {@code @NotBlank}），由服务层按 {@code syncMode} 取值决定是否校验。
 */
@Getter
@Setter
@Schema(description = "修改同步配置请求参数")
public class SyncConfigUpdateRequest {

    /** 同步方式，必填，只能是 NOTIFY 或 PULL。 */
    @NotBlank(message = "同步方式不能为空")
    @Pattern(regexp = "^(NOTIFY|PULL)$", message = "同步方式只能是 NOTIFY 或 PULL")
    @Schema(description = "同步方式：NOTIFY（通知）或 PULL（拉取）")
    private String syncMode;

    /**
     * 通知回调接口地址，是否必填取决于 {@link #syncMode}（服务层校验），此处只做长度约束。
     */
    @Size(max = 255, message = "通知回调接口地址长度不能超过 255 个字符")
    @Schema(description = "通知回调接口地址，同步方式为 NOTIFY 时必填，允许 http/https")
    private String notifyUrl;

    /** 通知请求自定义参数（key-value），可选。 */
    @Schema(description = "通知请求自定义参数（key-value）")
    private Map<String, String> notifyParams;

    /**
     * 是否需要签名/验签校验，必填，与 {@link #syncMode} 一样是整个应用一份的基础配置项
     * （app-sync-notify-pull-api change design.md Decision 3）。
     */
    @NotNull(message = "是否需要签名/验签校验不能为空")
    @Schema(description = "是否需要签名/验签校验")
    private Boolean needSign;
}
