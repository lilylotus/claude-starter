package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游数据同步执行记录视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上游数据同步执行记录")
public class UpstreamSyncRecordVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 所属上游数据源 id。 */
    @Schema(description = "所属上游数据源 id")
    private Long sourceId;

    /** 数据域：ORG/USER/POSITION。 */
    @Schema(description = "数据域：ORG/USER/POSITION")
    private String dataType;

    /** 触发方式：SCHEDULE（定时触发）/MANUAL（手动触发）。 */
    @Schema(description = "触发方式：SCHEDULE（定时触发）/MANUAL（手动触发）")
    private String triggerType;

    /** 本次同步开始时间。 */
    @Schema(description = "本次同步开始时间")
    private LocalDateTime startTime;

    /** 本次同步结束时间。 */
    @Schema(description = "本次同步结束时间")
    private LocalDateTime endTime;

    /** 执行状态：SUCCESS（全部成功）/PARTIAL（部分失败）/FAILED（全部失败或执行异常）。 */
    @Schema(description = "执行状态：SUCCESS（全部成功）/PARTIAL（部分失败）/FAILED（全部失败或执行异常）")
    private String status;

    /** 处理总行数。 */
    @Schema(description = "处理总行数")
    private Integer totalCount;

    /** 成功行数。 */
    @Schema(description = "成功行数")
    private Integer successCount;

    /** 失败行数。 */
    @Schema(description = "失败行数")
    private Integer failCount;

    /** 失败摘要文本，若有失败，包含前若干条失败原因。 */
    @Schema(description = "失败摘要文本，若有失败，包含前若干条失败原因")
    private String failSummary;
}
