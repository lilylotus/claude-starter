package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 领域变更事件的真正处理逻辑："先落变更记录表拿到 id（即序列号）→ 触发通知"
 * （app-sync-notify-pull-api change design.md Decision 4）。不依赖 Disruptor API，
 * 供 {@link DomainChangeEventHandler}（Disruptor 消费者）与未来外部 MQ 消费者共同调用，
 * 切换消息载体时本类逻辑可直接复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainChangeEventProcessor {

    /** 应用数据变更记录业务逻辑接口。 */
    private final AppDataChangeLogService appDataChangeLogService;

    /** 应用通知发送业务逻辑接口。 */
    private final AppNotifyService appNotifyService;

    /**
     * 处理一条领域变更事件：落库变更记录，再触发匹配应用的通知。整体 catch 所有异常，
     * 避免消费者线程因单条事件处理异常而中断，导致 RingBuffer 阻塞、后续事件无法消费。
     *
     * @param event 领域变更事件
     */
    public void process(DomainChangeEvent event) {
        try {
            AppDataChangeLogEntity changeLog = appDataChangeLogService.record(event);
            appNotifyService.notifyMatchedApps(changeLog);
        } catch (Exception e) {
            log.error("处理领域变更事件失败：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
        }
    }
}
