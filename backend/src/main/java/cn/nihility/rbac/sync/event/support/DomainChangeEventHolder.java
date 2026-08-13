package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.event.DomainChangeEvent;

/**
 * Disruptor 环形缓冲区承载的事件包装对象。{@link DomainChangeEvent} 本身是不可变
 * POJO（Builder 构造），Disruptor 要求 {@code RingBuffer} 预分配可复用的槽位对象，
 * 因此用本类作为槽位对象，每次发布时替换其持有的事件负载引用，而不是让 Disruptor
 * 直接管理不可变事件对象本身。
 */
public class DomainChangeEventHolder {

    /** 当次发布的事件负载。 */
    private DomainChangeEvent event;

    /**
     * 获取当前持有的事件负载。
     *
     * @return 事件负载
     */
    public DomainChangeEvent getEvent() {
        return event;
    }

    /**
     * 替换当前持有的事件负载。
     *
     * @param event 事件负载
     */
    public void setEvent(DomainChangeEvent event) {
        this.event = event;
    }
}
