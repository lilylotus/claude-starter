package cn.nihility.rbac.appaccess.override.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 人工例外分页查询参数，全部字段均可选。
 */
@Getter
@Setter
@Schema(description = "人工例外分页查询参数")
public class ManualOverrideQueryRequest {

    /** 用户 id，精确匹配，可选。 */
    @Schema(description = "用户 id，精确匹配")
    private Long userId;

    /** 应用 id，精确匹配，可选。 */
    @Schema(description = "应用 id，精确匹配")
    private Long appId;

    /** 例外类型，精确匹配，可选。 */
    @Schema(description = "例外类型：GRANT/DENY")
    private String overrideType;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
