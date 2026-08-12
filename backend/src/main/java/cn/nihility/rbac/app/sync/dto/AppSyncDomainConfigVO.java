package cn.nihility.rbac.app.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用同步数据域配置视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "应用同步数据域配置")
public class AppSyncDomainConfigVO {

    /** 数据域：ORG/USER/APP/ROLE/DICT。 */
    @Schema(description = "数据域：ORG/USER/APP/ROLE/DICT")
    private String syncDomain;

    /** 是否允许同步该数据域。 */
    @Schema(description = "是否允许同步该数据域")
    private Boolean syncEnabled;

    /** 每次拉取分页大小。 */
    @Schema(description = "每次拉取分页大小")
    private Integer pageSize;
}
