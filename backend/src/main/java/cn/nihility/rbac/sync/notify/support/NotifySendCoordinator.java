package cn.nihility.rbac.sync.notify.support;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.dto.NotifyAttemptOutcome;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通知任务"抢占 + 实际发送 + 状态流转"编排组件（app-sync-changelog-pull change design.md
 * Decision 6）：由 {@code DomainChangeEventProcessor}（事务提交后的即时发送优化）与
 * {@link NotifyRetryScheduler}（到期扫描兜底）共同复用，两条路径最终都落到同一个
 * "先原子抢占为 PROCESSING、抢占失败直接放弃、抢占成功才真正发起 HTTP 请求"的编排逻辑，
 * 避免同一条任务被并发重复发送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifySendCoordinator {

    /** 通知任务落库与状态机流转业务逻辑接口。 */
    private final AppNotifyTaskService appNotifyTaskService;

    /** 应用通知实际发送业务逻辑接口。 */
    private final AppNotifyService appNotifyService;

    /** 通知重试相关配置，提供 {@code PROCESSING} 租约时长。 */
    private final NotifyRetryProperties notifyRetryProperties;

    /** 通知发送专用线程池。 */
    private final NotifySendExecutor notifySendExecutor;

    /**
     * 提交一次"即时发送优化"：任务刚在本地事务内落库为 {@code PENDING}，事务提交后立即
     * 异步尝试发送一次，仅作低延迟优化——即使这一步因为进程崩溃没有执行，任务已经是
     * {@code PENDING} 落库状态，{@link NotifyRetryScheduler} 的到期扫描能够兜底捞到它
     * （design.md Decision 6）。
     *
     * @param task 刚落库的 {@code PENDING} 任务
     */
    public void submitImmediateSend(AppNotifyRecordEntity task) {
        notifySendExecutor.submit(() -> attemptSend(task, NotifyTaskStatus.PENDING));
    }

    /**
     * 提交一次"调度器捞取"发送：{@link NotifyRetryScheduler} 扫描到的到期任务，携带扫描时刻
     * 读到的当前状态（{@code PENDING}/{@code RETRY}/{@code PROCESSING}），作为抢占时的
     * 期望状态。
     *
     * @param task 调度器扫描到的到期任务
     */
    public void submitClaimedSend(AppNotifyRecordEntity task) {
        notifySendExecutor.submit(() -> attemptSend(task, task.getTaskStatus()));
    }

    /**
     * 先原子抢占任务为 {@code PROCESSING}，抢占失败（已被其他路径抢占，或状态已变化）时
     * 直接放弃，不重复发送；抢占成功后发起一次实际 HTTP 请求，并按结果驱动状态机流转。
     *
     * @param task           待处理任务（{@code requestBody}/{@code notifyUrl} 等快照字段
     *                       取自调用方持有的快照，抢占前不会失效）
     * @param expectedStatus 抢占时的期望当前状态
     */
    private void attemptSend(AppNotifyRecordEntity task, String expectedStatus) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseUntil = now.plusSeconds(notifyRetryProperties.getLeaseSeconds());
        if (!appNotifyTaskService.claim(task.getId(), expectedStatus, now, leaseUntil)) {
            log.debug("通知任务[{}]抢占失败，已被其他路径处理或状态已变化，跳过", task.getId());
            return;
        }

        NotifyAttemptOutcome outcome;
        try {
            outcome = appNotifyService.sendOnce(task);
        } catch (Exception e) {
            // sendOnce 内部已经把已知的失败场景（网络异常、非 2xx 状态码等）转换为
            // NotifyAttemptOutcome，这里只是防御性兜底，避免遗漏的运行时异常导致任务
            // 永远卡在 PROCESSING（等待租约超时后才能被调度器重新捞回，属于可接受的降级，
            // 但仍先记录日志便于排查）。
            log.warn("通知任务[{}]发送时发生未预期异常，等待租约超时后由调度器重新捞取", task.getId(), e);
            return;
        }

        if (outcome.success()) {
            appNotifyTaskService.markSuccess(task.getId(), outcome.httpStatus());
            return;
        }
        int retryCountBeforeAttempt = task.getRetryCount() == null ? 0 : task.getRetryCount();
        appNotifyTaskService.recordAttemptFailure(task.getId(), retryCountBeforeAttempt, outcome.retryable(),
                outcome.httpStatus(), outcome.errorMsg());
    }
}
