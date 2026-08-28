package cn.nihility.rbac.sync.notify.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 出站通知请求体（app-sync-notify-pull-api change design.md Decision 8；字段随变更记录表
 * 删除而调整，见 app-sync-drop-changelog change design.md Decision 6）：只传"指针"，不传
 * 完整数据，外部应用收到通知后应主动调用拉取接口获取真正数据。
 */
@Getter
@Builder
public class NotifyPayload {

    /** 雪花事件标识，按十进制字符串输出。 */
    private final String eventId;

    /** 数据库变更游标，按十进制字符串输出。 */
    private final String changeSeq;

    /** 实体结果版本，按十进制字符串输出。 */
    private final String entityVersion;

    /** 数据类型：ORG/USER/POSITION/APP/ROLE。 */
    private final String dataType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    private final String operationType;

    /** 被变更对象 id。 */
    private final String bizId;

    /** 被变更对象的业务编码，POSITION 数据类型没有业务编码字段时为空。 */
    private final String bizCode;

    /** 变更发生时间。 */
    private final LocalDateTime occurredAt;

    /** 应用配置的通知自定义参数，原样透传。 */
    private final Map<String, String> extra;
}
