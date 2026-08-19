package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用对外接口配置视图对象。刻意不包含 SecretKey 字段——SecretKey 明文仅在"重置"接口的
 * 响应体中单次返回，本视图对象供查询接口使用，永远不会携带明文或密文
 * （app-api-credentials-config change design.md Decision 5）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用对外接口配置")
public class AppConfigVO {

    /** 对外应用标识（AppId）。 */
    @Schema(description = "对外应用标识（AppId）")
    private String appId;

    /** 对外接口 AccessKey，明文常显。 */
    @Schema(description = "对外接口 AccessKey")
    private String accessKey;

    /** 接口签名算法：SHA256 或 SM3。 */
    @Schema(description = "接口签名算法：SHA256 或 SM3")
    private String signAlgorithm;

    /** 是否需要签名/验签校验。 */
    @Schema(description = "是否需要签名/验签校验")
    private Boolean needSign;

    /** 同步方式：NOTIFY（通知）或 PULL（拉取），整个应用一份。 */
    @Schema(description = "同步方式：NOTIFY（通知）或 PULL（拉取）")
    private String syncMode;

    /** 同步总开关：关闭后不再产生变更记录、不再通知、拉取接口返回空结果。 */
    @Schema(description = "同步总开关：关闭后该应用不再产生新的变更记录、不再收到通知、拉取接口返回空结果")
    private Boolean syncMasterEnabled;

    /** 通知回调接口地址，同步方式为 NOTIFY 时必填。 */
    @Schema(description = "通知回调接口地址，同步方式为 NOTIFY 时必填")
    private String notifyUrl;

    /** 通知请求自定义参数（key-value）。 */
    @Schema(description = "通知请求自定义参数（key-value）")
    private Map<String, String> notifyParams;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
