package cn.nihility.rbac.sync.event.support;

import com.lmax.disruptor.EventHandler;
import lombok.RequiredArgsConstructor;

/**
 * Disruptor 消费者：从 {@link DomainChangeEventHolder} 槽位取出事件负载，转调
 * {@link DomainChangeEventProcessor} 完成真正的落库 + 通知处理，本类只负责与 Disruptor
 * API 对接，不包含业务逻辑（app-sync-notify-pull-api change design.md Decision 4）。
 */
@RequiredArgsConstructor
public class DomainChangeEventHandler implements EventHandler<DomainChangeEventHolder> {

    /** 领域变更事件处理逻辑。 */
    private final DomainChangeEventProcessor processor;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEvent(DomainChangeEventHolder holder, long sequence, boolean endOfBatch) {
        processor.process(holder.getEvent());
    }
}
