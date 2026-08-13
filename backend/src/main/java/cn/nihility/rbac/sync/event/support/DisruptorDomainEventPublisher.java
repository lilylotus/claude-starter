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
