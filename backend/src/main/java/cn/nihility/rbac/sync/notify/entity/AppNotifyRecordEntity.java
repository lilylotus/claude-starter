package cn.nihility.rbac.sync.notify.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用通知发送记录持久化实体，对应表 {@code tab_app_notify_record}
 * （app-sync-notify-pull-api change design.md Decision 7）。仅用于问题排查/展示，
 * 不驱动任何自动重试逻辑。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_notify_record")
public class AppNotifyRecordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 {@code tab_app_data_change_log.id}。 */
    private Long changeLogId;

    /** 关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE，取自触发本次通知的变更记录，历史数据为空。 */
    private String dataType;

    /** 被变更对象 id，取自触发本次通知的变更记录，历史数据为空。 */
    private Long bizId;

    /** 通知状态：1=成功，2=失败。 */
    private Integer notifyStatus;

    /** 外部接口返回的 HTTP 状态码，失败且未收到响应时为空。 */
    private Integer httpStatus;

    /** 失败原因摘要。 */
    private String errorMsg;

    /**
     * 本次回调实际使用的地址快照（发起请求前读取的地址，不是查询时的当前配置值），
     * 历史数据为空（add-app-sync-notify-pull-logs change design.md Decision 1）。
     */
    private String notifyUrl;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
