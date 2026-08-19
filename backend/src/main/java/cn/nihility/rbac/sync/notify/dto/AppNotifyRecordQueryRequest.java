package cn.nihility.rbac.sync.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 通知日志分页查询的筛选参数，除 {@link #appRefId} 外均可选（add-app-sync-notify-pull-logs
 * change design.md Decision 3）。
 */
@Getter
@Setter
@Schema(description = "通知日志分页查询参数")
public class AppNotifyRecordQueryRequest {

    /** 应用 id（{@code tab_app.id}），精确匹配，由 controller 从路径参数回填，不接受外部输入。 */
    private Long appRefId;

    /** 通知状态：1=成功，2=失败，精确匹配，可选。 */
    @Schema(description = "通知状态：1=成功，2=失败")
    private Integer notifyStatus;

    /** 通知发起时间范围起点（含），可选。 */
    @Schema(description = "通知发起时间范围起点（含）")
    private LocalDateTime startTime;

    /** 通知发起时间范围终点（含），可选。 */
    @Schema(description = "通知发起时间范围终点（含）")
    private LocalDateTime endTime;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
