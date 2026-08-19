package cn.nihility.rbac.sync.event.support;

import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.notify.support.NotifyCandidateResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 领域变更事件的真正处理逻辑："直接判定当前哪些应用是本次事件的候选应用（数据域启用+总
 * 开关开启+同步方式为通知+组织范围匹配），对每个匹配应用各自触发一次通知"，不再经过任何
 * 持久化中转（app-sync-drop-changelog change design.md Decision 6）。不依赖 Disruptor
 * API，供 {@link DomainChangeEventHandler}（Disruptor 消费者）与未来外部 MQ 消费者共同调用，
 * 切换消息载体时本类逻辑可直接复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainChangeEventProcessor {

    /** 通知候选应用判定组件。 */
    private final NotifyCandidateResolver notifyCandidateResolver;

    /** 应用通知发送业务逻辑接口。 */
    private final AppNotifyService appNotifyService;

    /**
     * 处理一条领域变更事件：判定当前匹配的候选应用列表，逐个应用触发"该应用当前是否为
     * NOTIFY 模式"的通知判断，单个应用通知异常不影响其余应用（沿用既有 catch 风格）。整体
     * 再 catch 所有异常，避免消费者线程因单条事件处理异常而中断，导致 RingBuffer 阻塞、
     * 后续事件无法消费。
     *
     * @param event 领域变更事件
     */
    public void process(DomainChangeEvent event) {
        try {
            List<Long> matchedAppRefIds = notifyCandidateResolver.resolveCandidateAppRefIds(event);
            for (Long appRefId : matchedAppRefIds) {
                try {
                    appNotifyService.notifyIfConfigured(event, appRefId);
                } catch (Exception e) {
                    log.warn("向应用[{}]触发变更通知失败，跳过并继续处理其余应用：dataType={}, bizId={}", appRefId,
                            event.getDataType(), event.getBizId(), e);
                }
            }
        } catch (Exception e) {
            log.error("处理领域变更事件失败：dataType={}, bizId={}, operationType={}",
                    event.getDataType(), event.getBizId(), event.getOperationType(), e);
        }
    }
}
