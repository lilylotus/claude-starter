package cn.nihility.rbac.app.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改应用同步数据域配置的请求参数：某个数据域的启用开关与拉取分页大小。
 */
@Getter
@Setter
@Schema(description = "修改同步数据域配置请求参数")
public class AppSyncDomainConfigUpdateRequest {

    /** 是否允许同步该数据域，必填。 */
    @NotNull(message = "启用开关不能为空")
    @Schema(description = "是否允许同步该数据域")
    private Boolean syncEnabled;

    /** 每次拉取分页大小，必填，最小 1。 */
    @NotNull(message = "拉取分页大小不能为空")
    @Min(value = 1, message = "拉取分页大小最小为 1")
    @Schema(description = "每次拉取分页大小，最小 1")
    private Integer pageSize;
}
