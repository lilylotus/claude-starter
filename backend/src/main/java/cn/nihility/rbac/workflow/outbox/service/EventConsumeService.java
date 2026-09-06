package cn.nihility.rbac.workflow.outbox.service;

import cn.nihility.rbac.workflow.outbox.consumer.OutboxEventConsumer;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;

/**
 * 单个消费者维度的"消费唯一键去重 + 消费执行"原子服务接口（production-approval-lifecycle
 * change design.md 第9/10节，tasks.md 7.2）。由
 * {@code cn.nihility.rbac.workflow.outbox.support.OutboxEventConsumptionCoordinator} 对每个
 * {@code supports} 命中的消费者分别调用一次，各消费者各自拥有独立的物理事务。
 */
public interface EventConsumeService {

    /**
     * 在一个事务内完成：先乐观 {@code INSERT} 一条 {@code (eventId, consumer.consumerCode())}
     * 消费标记，命中唯一键冲突说明该事件已被该消费者消费过，直接跳过、不重复调用
     * {@link OutboxEventConsumer#consume}；未冲突则在同一事务内调用
     * {@link OutboxEventConsumer#consume}，其抛出的任何异常都会使整个事务（含刚插入的消费标记）
     * 一并回滚，从而保证"成功业务结果与消费标记原子提交"——不会出现"业务结果落库了但消费标记
     * 没落"或反过来的半提交状态。
     *
     * @param event    已被领取（{@code LEASED}）的 Outbox 事件
     * @param consumer 待执行的消费者
     * @return {@code true} 表示本次真正调用了 {@link OutboxEventConsumer#consume}（新消费）；
     *         {@code false} 表示命中去重、消费逻辑本次未被调用（此前已消费过）
     */
    boolean consumeOnce(OutboxEventEntity event, OutboxEventConsumer consumer);
}
