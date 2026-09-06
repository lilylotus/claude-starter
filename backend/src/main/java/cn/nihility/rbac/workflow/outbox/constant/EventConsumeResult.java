package cn.nihility.rbac.workflow.outbox.constant;

/**
 * Outbox 事件消费结果，对应 {@code tab_wf_event_consume.result}（真实 DDL 见
 * {@code V11__add_production_approval_lifecycle_tables.sql}，production-approval-lifecycle
 * change tasks.md 7.2）。
 */
public final class EventConsumeResult {

    /**
     * 消费成功：本轮编排（tasks.md 7.2，{@code EventConsumeServiceImpl}）里唯一会真正落库的
     * 取值——消费者 {@code consume()} 抛异常时，消费标记 {@code INSERT} 随所在事务一并回滚，
     * 不会留下 {@code FAILED} 行；数据库列默认值也是 {@code SUCCEEDED}。
     */
    public static final String SUCCEEDED = "SUCCEEDED";

    /** 消费失败：DDL 预留取值，本轮实现未写入（原因见 {@link #SUCCEEDED} 说明），供后续如
     *  "记录失败但不参与整体重试"这类消费者语义扩展使用。 */
    public static final String FAILED = "FAILED";

    /**
     * 工具类不允许实例化。
     */
    private EventConsumeResult() {
    }
}
