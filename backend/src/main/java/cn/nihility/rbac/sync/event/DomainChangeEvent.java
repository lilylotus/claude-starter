package cn.nihility.rbac.sync.event;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 领域数据变更事件负载（app-sync-notify-pull-api change design.md Decision 4），不可变
 * POJO，供 {@link DomainEventPublisher} 发布方构造、消费者（落库 + 通知）读取。
 */
@Getter
@Builder(toBuilder = true)
public class DomainChangeEvent {

    /** 雪花算法生成的全局事件标识。 */
    private final Long eventId;

    /** 本次变更完成后的实体版本。 */
    private final Long entityVersion;

    /** 变更前组织范围路径，仅 ORG/POSITION 使用。 */
    private final String orgScopePathBefore;

    /** 变更后组织范围路径，仅 ORG/POSITION 使用。 */
    private final String orgScopePathAfter;

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

    /** 操作人（当前登录用户 id 文本），用于变更记录审计字段。 */
    private final String operator;

    /** 事件发生时间。 */
    private final LocalDateTime occurredAt;
}
