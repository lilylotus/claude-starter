package cn.nihility.rbac.workflow.outbox.support;

import cn.nihility.rbac.workflow.outbox.consumer.OutboxEventConsumer;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.service.EventConsumeService;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 事件"消费编排"组件（production-approval-lifecycle change design.md 第9/10节，
 * tasks.md 7.2），角色定位对照
 * {@code cn.nihility.rbac.sync.notify.support.NotifySendCoordinator}："抢占/领取已经在更底层
 * 完成，本组件负责在此基础上驱动实际消费与最终状态流转"，但比 notify 场景多一层"按
 * {@code consumerCode} 区分的消费唯一键去重"——notify 场景只有单一消费者，Outbox 场景同一事件
 * 可能被多个不同消费者各自消费一次。
 * <p>
 * {@link #consume} 本身**不**声明 {@code @Transactional}：每个消费者各自的
 * {@link EventConsumeService#consumeOnce} 独立开启/提交自己的物理事务，一个消费者的成功不依赖
 * 其他消费者是否成功，也不会因为其他消费者失败而被回滚——调用方（包括测试）不应该在外层再包一层
 * 事务，否则会让所有消费者共享同一个物理事务，破坏"部分消费者成功的进度独立持久化、下次重试只
 * 重做失败的那部分"这一关键语义。
 * <p>
 * 本轮（7.2）不注册任何生产用途的 {@link OutboxEventConsumer} 实现，构造函数注入的
 * {@code List<OutboxEventConsumer>} 在生产容器里允许是空列表；测试为避免让测试专用的桩消费者
 * 泄漏进生产 Spring 上下文，不通过容器自动收集，而是在测试里手动 {@code new} 本类并显式传入
 * 桩消费者列表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventConsumptionCoordinator {

    /** 容器中收集到的全部消费者；本轮（7.2）生产环境允许为空列表。 */
    private final List<OutboxEventConsumer> outboxEventConsumers;

    /** 单消费者维度"去重 + 消费"原子服务。 */
    private final EventConsumeService eventConsumeService;

    /** Outbox 事件生产/领取服务，用于最终把整体事件标记成功/失败。 */
    private final OutboxEventService outboxEventService;

    /**
     * 对一条已领取事件执行消费编排：遍历所有 {@code supports} 命中的消费者，逐个独立处理——
     * 某个消费者失败不阻止其余消费者被消费（各消费者产生的副作用相互独立，例如未来的业务执行器
     * 与通知器，没有理由因为通知失败而放弃已经执行成功的业务变更，反之亦然），全部处理完毕后按
     * "是否存在真正执行失败的消费者"（命中去重跳过不算失败）决定整体标记成功还是失败。
     * <p>
     * 标记成功/失败均携带 {@code claimedEvent} 自身的 {@code leaseToken}：若消费耗时较长导致
     * 租约在处理期间被判定过期、被其他 worker 重新抢占，{@link OutboxEventService#markSucceeded}
     * /{@link OutboxEventService#markFailed} 内部的 CAS 会因 {@code lease_token} 不匹配而返回
     * {@code false}，此时仅记录 warn 日志，不额外重试——到期扫描本身就是兜底，事件会由新的持有者
     * 或下一轮扫描重新处理。
     *
     * @param claimedEvent 已通过 {@link OutboxEventService#claimDueEvents} 领取到的事件
     *                     （携带本次领取的 {@code leaseToken}）
     */
    public void consume(OutboxEventEntity claimedEvent) {
        boolean anyConsumerFailed = false;
        for (OutboxEventConsumer consumer : outboxEventConsumers) {
            if (!consumer.supports(claimedEvent.getEventType())) {
                continue;
            }
            try {
                eventConsumeService.consumeOnce(claimedEvent, consumer);
            } catch (Exception ex) {
                anyConsumerFailed = true;
                log.warn("Outbox 事件[{}]消费者[{}]消费失败，事件将按退避策略重试或转人工处理",
                        claimedEvent.getEventId(), consumer.consumerCode(), ex);
            }
        }

        boolean finalized = anyConsumerFailed
                ? outboxEventService.markFailed(claimedEvent)
                : outboxEventService.markSucceeded(claimedEvent);
        if (!finalized) {
            log.warn(
                    "Outbox 事件[{}]消费编排本地处理已完成，但租约 token[{}] 已不再由本次持有者持有"
                            + "（可能因租约过期已被其他 worker 重新抢占，或事件已由其他调用提前终态化），"
                            + "本次处理结果未能落地最终状态，事件将由新的持有者或下一轮到期扫描重新处理",
                    claimedEvent.getEventId(), claimedEvent.getLeaseToken());
        }
    }
}
