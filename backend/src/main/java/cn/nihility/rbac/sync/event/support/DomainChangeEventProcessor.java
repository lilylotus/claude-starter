package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 领域变更事件的真正处理逻辑："先按候选应用（及组织/用户/任职三个数据域的按应用同步范围
 * 过滤）落库出多条变更记录，再对每条记录各自判断是否需要触发通知"
 * （app-sync-notify-pull-api change design.md Decision 4；按应用物化见
 * app-sync-org-scope-and-app-change-log change design.md Decision 1/6）。不依赖 Disruptor
 * API，供 {@link DomainChangeEventHandler}（Disruptor 消费者）与未来外部 MQ 消费者共同调用，
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
     * 处理一条领域变更事件：落库出该事件物化给各候选应用的变更记录列表，再逐条记录触发
     * "该应用当前是否为 NOTIFY 模式"的通知判断，单条记录通知异常不影响其余记录（沿用既有
     * catch 风格）。整体再 catch 所有异常，避免消费者线程因单条事件处理异常而中断，导致
     * RingBuffer 阻塞、后续事件无法消费。
     *
     * @param event 领域变更事件
     */
    public void process(DomainChangeEvent event) {
        try {
            List<AppDataChangeLogEntity> changeLogs = appDataChangeLogService.record(event);
            for (AppDataChangeLogEntity changeLog : changeLogs) {
                try {
                    appNotifyService.notifyIfConfigured(changeLog);
                } catch (Exception e) {
                    log.warn("向应用[{}]触发变更通知失败，跳过并继续处理其余应用：dataType={}, bizId={}",
                            changeLog.getAppRefId(), event.getDataType(), event.getBizId(), e);
                }
            }
        } catch (Exception e) {
            log.error("处理领域变更事件失败：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
        }
    }
}
