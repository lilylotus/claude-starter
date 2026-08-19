package cn.nihility.rbac.sync.pull.record.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 拉取日志列表行视图对象（add-app-sync-notify-pull-logs change design.md Decision 3）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "拉取日志列表行")
public class AppPullRecordVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 应用 id。 */
    @Schema(description = "应用 id")
    private Long appRefId;

    /** 请求的数据类型。 */
    @Schema(description = "请求的数据类型")
    private String dataType;

    /** 请求参数摘要。 */
    @Schema(description = "请求参数摘要")
    private String requestSummary;

    /** 本次返回的记录条数。 */
    @Schema(description = "本次返回的记录条数")
    private Integer resultCount;

    /** 拉取发起时间。 */
    @Schema(description = "拉取发起时间")
    private LocalDateTime createTime;
}
