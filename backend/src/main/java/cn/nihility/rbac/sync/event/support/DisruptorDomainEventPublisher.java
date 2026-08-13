package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.sync.event.config.SyncProperties;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link DomainEventPublisher} 的进程内 Disruptor 实现（app-sync-notify-pull-api change
 * design.md Decision 4）：单一 {@link DomainChangeEventHandler} 消费者，
 * {@link BlockingWaitStrategy}（默认低 CPU 占用，吞吐量在本场景足够，不追求微秒级延迟），
 * {@link ProducerType#MULTI} 兜底并发发布场景。事件发布方调用 {@link #publish} 后立即返回，
 * 不等待落库/通知完成。
 *
 * <p>切换外部 MQ（RabbitMQ/RocketMQ）的路径：新增一个实现 {@link DomainEventPublisher} 的类，
 * 在 {@code @Configuration} 里替换掉注入的 Bean 即可，{@link DomainChangeEventProcessor} 的
 * 落库+通知逻辑本身与 Disruptor 无关，可以被新 MQ 的消费者直接复用。
 *
 * <p>实现 {@link SmartLifecycle}：应用启动时启动 Disruptor，容器关闭时优雅
 * {@link Disruptor#shutdown()}，避免进程退出丢事件或线程泄漏。
 *
 * <p>{@link #publish} 若在一个仍处于活动状态的 Spring 事务内被调用（业务方法标注
 * {@code @Transactional} 的常见场景），实际把事件放入 RingBuffer 的动作会延迟到该事务
 * {@code afterCommit} 时才执行，而不是立即发布——{@code app-sync-org-scope-and-app-change-log}
 * change 引入的组织范围判定（{@code AppSyncOrgScopeResolver#isUserWithinScope} 等）需要
 * 现查 {@code tab_user_position} 等业务表，如果事件在发布方所在事务提交前就被 Disruptor
 * 消费者线程（另一个数据库连接）处理，会读不到本次事务里刚写入但还未提交的数据，导致组织
 * 范围判定产生假阴性（实测复现：新建用户时同时提交的任职记录因为这个时序问题在候选应用
 * 的组织范围校验里查不到，被错误判定为"不在范围内"，一条变更记录都不落库）。若调用方法
 * 当前没有活动事务（如非 {@code @Transactional} 方法内的单条自动提交语句），行为不变，
 * 立即发布（design.md Decision 9 补充说明）。
 */
@Component
@RequiredArgsConstructor
public class DisruptorDomainEventPublisher implements DomainEventPublisher, SmartLifecycle {

    /** 领域变更事件处理逻辑，注册为唯一的 Disruptor 消费者。 */
    private final DomainChangeEventProcessor processor;

    /** Disruptor 相关配置。 */
    private final SyncProperties syncProperties;

    /** Disruptor 实例，{@link #start()} 时按配置构建并启动。 */
    private volatile Disruptor<DomainChangeEventHolder> disruptor;

    /** 生命周期运行标记。 */
    private volatile boolean running = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void publish(DomainChangeEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishToRingBuffer(event);
                }
            });
            return;
        }
        publishToRingBuffer(event);
    }

    /**
     * 把事件实际放入 RingBuffer，供 {@link #publish} 立即调用或事务提交后回调调用。
     *
     * @param event 领域变更事件
     */
    private void publishToRingBuffer(DomainChangeEvent event) {
        Disruptor<DomainChangeEventHolder> current = disruptor;
        if (current == null) {
            // 未启动（如尚未经过 SmartLifecycle#start，理论上不会发生在正常运行的 Spring
            // 容器中）时不静默丢弃事件，直接抛出，暴露编程错误而不是掩盖问题。
            throw new IllegalStateException("领域事件发布器尚未启动");
        }
        current.getRingBuffer().publishEvent((holder, sequence, publishedEvent) -> holder.setEvent(publishedEvent),
                event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        Disruptor<DomainChangeEventHolder> newDisruptor = new Disruptor<>(DomainChangeEventHolder::new,
                syncProperties.getRingBufferSize(), DaemonThreadFactory.INSTANCE, ProducerType.MULTI,
                new BlockingWaitStrategy());
        newDisruptor.handleEventsWith(new DomainChangeEventHandler(processor));
        newDisruptor.start();
        this.disruptor = newDisruptor;
        this.running = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        Disruptor<DomainChangeEventHolder> current = disruptor;
        if (current != null) {
            current.shutdown();
            disruptor = null;
        }
        running = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
