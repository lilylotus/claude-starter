package cn.nihility.rbac.sync.notify.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 通知日志列表行视图对象（add-app-sync-notify-pull-logs change design.md Decision 3）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "通知日志列表行")
public class AppNotifyRecordVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 应用 id。 */
    @Schema(description = "应用 id")
    private Long appRefId;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE，历史数据可能为空。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE，历史数据可能为空")
    private String dataType;

    /** 被变更对象 id，历史数据可能为空。 */
    @Schema(description = "被变更对象 id，历史数据可能为空")
    private Long bizId;

    /** 被变更业务对象名称；历史、未知类型或当前业务数据不存在时为空。 */
    @Schema(description = "被变更业务对象名称，无法解析时为空", nullable = true)
    private String bizName;

    /** 通知状态：1=成功，2=失败。 */
    @Schema(description = "通知状态：1=成功，2=失败")
    private Integer notifyStatus;

    /** 外部接口返回的 HTTP 状态码，失败且未收到响应时为空。 */
    @Schema(description = "外部接口返回的 HTTP 状态码，失败且未收到响应时为空")
    private Integer httpStatus;

    /** 失败原因摘要。 */
    @Schema(description = "失败原因摘要")
    private String errorMsg;

    /** 本次回调实际使用的地址快照，历史数据可能为空。 */
    @Schema(description = "本次回调实际使用的地址快照，历史数据可能为空")
    private String notifyUrl;

    /** 通知发起时间。 */
    @Schema(description = "通知发起时间")
    private LocalDateTime createTime;

    /**
     * 任务状态：PENDING/PROCESSING/RETRY/SUCCESS/DEAD（app-sync-changelog-pull change
     * design.md Decision 6）。仅 {@code DEAD} 状态的记录可调用手动重推接口。
     */
    @Schema(description = "任务状态：PENDING/PROCESSING/RETRY/SUCCESS/DEAD，仅 DEAD 可手动重推")
    private String taskStatus;

    /** 已失败尝试次数。 */
    @Schema(description = "已失败尝试次数")
    private Integer retryCount;

    /** 下次允许重试时间，{@code taskStatus} 不是 {@code RETRY} 时通常为空。 */
    @Schema(description = "下次允许重试时间")
    private LocalDateTime nextRetryTime;
}
