package cn.nihility.rbac.image.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 图片分页查询参数（add-image-gallery change tasks.md 4.2）。图片管理页分页列表使用全部
 * 字段（支持 {@code keyword}/{@code startTime}/{@code endTime} 筛选，design.md
 * Decision 10）；图片展示页 feed 接口只使用 {@code page}/{@code pageSize}，不传筛选字段。
 * 默认值由 Controller 侧的 {@code @RequestParam(defaultValue = ...)} 给出，此处不设默认值
 * 以免与之混淆。
 */
@Getter
@Setter
@Schema(description = "图片分页查询参数")
public class ImagePageQuery {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，默认第 1 页")
    private Integer page;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Schema(description = "每页条数")
    private Integer pageSize;

    /** 按原始文件名模糊匹配的关键字，图片管理页列表专用，可选。 */
    @Schema(description = "按原始文件名模糊匹配的关键字，图片展示 feed 接口不使用")
    private String keyword;

    /** 创建时间范围起点（含），图片管理页列表专用，可选。 */
    @Schema(description = "创建时间范围起点（含），图片展示 feed 接口不使用")
    private LocalDateTime startTime;

    /** 创建时间范围终点（含），图片管理页列表专用，可选。 */
    @Schema(description = "创建时间范围终点（含），图片展示 feed 接口不使用")
    private LocalDateTime endTime;
}
