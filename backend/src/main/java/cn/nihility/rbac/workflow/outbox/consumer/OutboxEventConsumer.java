package cn.nihility.rbac.workflow.outbox.consumer;

import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;

/**
 * Outbox 事件消费者 SPI（production-approval-lifecycle change design.md 第9/10节，
 * tasks.md 7.2）。同一事件可能被多个不同 {@link #consumerCode()} 的消费者各自独立消费一次
 * （如未来的业务执行器、通知器），区别于同库单一消费者场景（对比
 * {@code cn.nihility.rbac.sync.notify} 模块的通知任务只有一种消费方式）。所有 Spring 容器里的
 * 实现类会被 {@code cn.nihility.rbac.workflow.outbox.support.OutboxEventConsumptionCoordinator}
 * 通过 {@code List<OutboxEventConsumer>} 自动收集；本轮（7.2）不提供任何生产用途实现，容器内
 * 允许是空列表，编排组件需要正确处理"没有任何消费者关心某事件"的场景（直接标记整体成功）。
 */
public interface OutboxEventConsumer {

    /**
     * 消费者唯一标识，落库到 {@code tab_wf_event_consume.consumer_code}，用于按
     * {@code (eventId, consumerCode)} 去重同一消费者对同一事件的重复消费。
     *
     * @return 消费者编码，如 {@code BUSINESS_EXECUTOR}/{@code NOTIFIER}
     */
    String consumerCode();

    /**
     * 判断本消费者是否关心给定事件类型；返回 {@code false} 的事件类型不会调用 {@link #consume}，
     * 也不会在 {@code tab_wf_event_consume} 留下记录。
     *
     * @param eventType 待判断的事件类型（{@code tab_wf_outbox_event.event_type}）
     * @return 是否关心该事件类型
     */
    boolean supports(String eventType);

    /**
     * 执行一次真正的消费逻辑。调用方（{@code OutboxEventConsumptionCoordinator}）保证本方法与
     * "消费标记写入 {@code tab_wf_event_consume}"处于同一个物理事务：本方法内部的任何数据库写
     * 操作都会参与这同一事务，方法正常返回视为消费成功、随消费标记一并提交；抛出任意异常（建议
     * 用运行时异常）视为消费失败，整个事务（含消费标记）一并回滚，不留下部分提交状态。
     *
     * @param event 已被领取（{@code LEASED}）的 Outbox 事件
     */
    void consume(OutboxEventEntity event);
}
