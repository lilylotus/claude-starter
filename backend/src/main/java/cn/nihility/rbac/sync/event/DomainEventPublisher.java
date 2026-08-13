package cn.nihility.rbac.sync.event;

/**
 * 数据变更事件发布抽象（app-sync-notify-pull-api change design.md Decision 4），不向发布方
 * 暴露具体消息载体细节（当前实现为进程内 Disruptor 环形缓冲区，见
 * {@code cn.nihility.rbac.sync.event.support.DisruptorDomainEventPublisher}）。发布方
 * （组织/用户/任职/应用/角色各业务模块的 {@code *ServiceImpl}）调用 {@link #publish} 后
 * 立即返回，不等待落库/通知完成；未来替换为外部消息队列（RabbitMQ/RocketMQ）时只需替换
 * 本接口的实现类，不要求变更任何发布方代码。
 */
public interface DomainEventPublisher {

    /**
     * 发布一条领域数据变更事件。
     *
     * @param event 事件负载
     */
    void publish(DomainChangeEvent event);
}
