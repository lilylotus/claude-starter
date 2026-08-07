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
 * 修改应用数据同步配置的请求参数：四个数据域各一个独立布尔开关（design.md Decision 4
 * 用户澄清：本次不做更细粒度的范围限定），加上整个应用一份的基础同步配置项——同步方式
 * （通知/拉取）及通知模式下的回调地址、自定义参数。同步方式为 NOTIFY 时接口地址是否合法
 * 属于跨字段校验，Bean Validation 在此不做（{@code notifyUrl} 本身不加 {@code @NotBlank}），
 * 由服务层按 {@code syncMode} 取值决定是否校验。
 */
@Getter
@Setter
@Schema(description = "修改同步配置请求参数")
public class SyncConfigUpdateRequest {

    /** 是否允许同步组织数据，必填。 */
    @NotNull(message = "同步组织数据开关不能为空")
    @Schema(description = "是否允许同步组织数据")
    private Boolean syncOrgEnabled;

    /** 是否允许同步用户数据，必填。 */
    @NotNull(message = "同步用户数据开关不能为空")
    @Schema(description = "是否允许同步用户数据")
    private Boolean syncUserEnabled;

    /** 是否允许同步应用数据，必填。 */
    @NotNull(message = "同步应用数据开关不能为空")
    @Schema(description = "是否允许同步应用数据")
    private Boolean syncAppEnabled;

    /** 是否允许同步字典数据，必填。 */
    @NotNull(message = "同步字典数据开关不能为空")
    @Schema(description = "是否允许同步字典数据")
    private Boolean syncDictEnabled;

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
}
