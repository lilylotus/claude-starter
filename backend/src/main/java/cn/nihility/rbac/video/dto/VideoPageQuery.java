package cn.nihility.rbac.video.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 视频分页查询参数，默认值由 Controller 侧的 {@code @RequestParam(defaultValue = ...)}
 * 给出，此处不设默认值。
 */
@Getter
@Setter
@Schema(description = "视频分页查询参数")
public class VideoPageQuery {

    /** 页码，从 1 开始。 */
    @Min(value = 1, message = "页码不能小于 1")
    @Schema(description = "页码，默认第 1 页")
    private Integer page;

    /** 每页条数。 */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Schema(description = "每页条数")
    private Integer pageSize;

    /** 按原始文件名模糊匹配的关键字，可选。 */
    @Schema(description = "按原始文件名模糊匹配的关键字")
    private String keyword;

    /** 创建时间范围起点（含），可选。 */
    @Schema(description = "创建时间范围起点（含）")
    private LocalDateTime startTime;

    /** 创建时间范围终点（含），可选。 */
    @Schema(description = "创建时间范围终点（含）")
    private LocalDateTime endTime;
}
