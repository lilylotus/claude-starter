package cn.nihility.rbac.sync.event;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

/**
 * 领域数据变更事件负载（app-sync-notify-pull-api change design.md Decision 4），不可变
 * POJO，供 {@link DomainEventPublisher} 发布方构造、消费者（落库 + 通知）读取。
 */
@Getter
@Builder
public class DomainChangeEvent {

    /**
     * 数据类型，{@code cn.nihility.rbac.app.sync.constant.SyncDomain} 五个可变更取值之一
     * （ORG/USER/POSITION/APP/ROLE，不含 DICT）。
     */
    private final String dataType;

    /** 被变更对象主键 id。 */
    private final Long bizId;

    /**
     * 操作类型码值，复用 {@code cn.nihility.rbac.operationlog.constant.OperationType}
     * 的 CREATE/UPDATE/ENABLE/DISABLE/DELETE 五个常量。
     */
    private final int operationType;

    /**
     * 实体当前字段快照（DELETE 为删除前快照），key 为实体属性名（camelCase），与
     * {@code tab_metadata_field.field_code} 对齐，供拉取接口的字段映射转换阶段使用。
     */
    private final Map<String, Object> snapshot;

    /** 操作人（当前登录用户 id 文本），用于变更记录审计字段。 */
    private final String operator;

    /** 事件发生时间。 */
    private final LocalDateTime occurredAt;
}
