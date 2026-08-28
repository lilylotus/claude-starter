package cn.nihility.rbac.sync.notify.support;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知任务到期扫描调度器（app-sync-changelog-pull change design.md Decision 6）：按
 * {@code rbac.sync.notify-retry.scheduler-poll-interval-seconds} 轮询间隔扫描到期
 * {@code PENDING}（即时发送优化因进程崩溃等原因未执行到的兜底）、到期 {@code RETRY}、
 * 租约超时的 {@code PROCESSING}，每轮最多取 {@code scheduler-batch-size} 条，逐条提交到
 * {@link NotifySendCoordinator} 处理；单条任务提交异常不影响其余任务，等待下一轮重新捞取。
 * {@code RbacApplication} 已 {@code @EnableScheduling}，不需要再加。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyRetryScheduler {

    /** 通知任务落库与状态机流转业务逻辑接口，提供到期任务扫描。 */
    private final AppNotifyTaskService appNotifyTaskService;

    /** 通知任务"抢占 + 发送 + 状态流转"编排组件。 */
    private final NotifySendCoordinator notifySendCoordinator;

    /** 通知重试相关配置。 */
    private final NotifyRetryProperties notifyRetryProperties;

    /**
     * 定时扫描入口，轮询间隔取自 {@link NotifyRetryProperties#getSchedulerPollIntervalMillis()}。
     */
    @Scheduled(fixedDelayString = "#{notifyRetryProperties.schedulerPollIntervalMillis}")
    public void scanAndDispatch() {
        LocalDateTime now = LocalDateTime.now();
        List<AppNotifyRecordEntity> dueTasks =
                appNotifyTaskService.scanDueTasks(now, notifyRetryProperties.getSchedulerBatchSize());
        for (AppNotifyRecordEntity task : dueTasks) {
            try {
                notifySendCoordinator.submitClaimedSend(task);
            } catch (Exception e) {
                log.warn("提交待处理通知任务[{}]到发送线程池失败，等待下一轮调度重新捞取", task.getId(), e);
            }
        }
    }
}
