package cn.nihility.rbac.sync.notify.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 出站通知请求体（app-sync-notify-pull-api change design.md Decision 8）：只传"指针"，
 * 不传完整数据，外部应用收到通知后应主动调用拉取接口获取真正数据。
 */
@Getter
@Builder
public class NotifyPayload {

    /** 本次变更的序列号（{@code tab_app_data_change_log.id}）。 */
    private final Long sequence;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE。 */
    private final String dataType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    private final String operationType;

    /** 被变更对象 id。 */
    private final Long bizId;

    /** 变更发生时间。 */
    private final LocalDateTime occurredAt;

    /** 应用配置的通知自定义参数，原样透传。 */
    private final Map<String, String> extra;
}
