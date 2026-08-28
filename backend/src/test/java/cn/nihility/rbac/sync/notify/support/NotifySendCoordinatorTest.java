package cn.nihility.rbac.sync.notify.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.dto.NotifyAttemptOutcome;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotifySendCoordinator} 的单元测试：验证"先原子抢占、抢占失败直接放弃、抢占成功
 * 才真正发送"的编排逻辑，以及按发送结果驱动状态机流转（成功/可重试失败/不可重试失败）
 * （app-sync-changelog-pull change design.md Decision 6）。线程池使用真实的
 * {@link NotifySendExecutor}（单线程、同步等待任务完成），验证提交路径本身可用。
 */
@ExtendWith(MockitoExtension.class)
class NotifySendCoordinatorTest {

    @Mock
    private AppNotifyTaskService appNotifyTaskService;

    @Mock
    private AppNotifyService appNotifyService;

    private NotifyRetryProperties notifyRetryProperties;

    private NotifySendExecutor notifySendExecutor;

    private NotifySendCoordinator coordinator;

    @BeforeEach
    void setUp() {
        notifyRetryProperties = new NotifyRetryProperties();
        // 单线程池：保证测试里"提交一个哨兵任务等待其完成"的等待手法严格发生在被测任务
        // 之后，避免多线程并发调度导致哨兵任务先于被测任务完成。
        notifyRetryProperties.setSchedulerBatchSize(1);
        notifySendExecutor = new NotifySendExecutor(notifyRetryProperties);
        coordinator = new NotifySendCoordinator(appNotifyTaskService, appNotifyService, notifyRetryProperties,
                notifySendExecutor);
    }

    /**
     * 抢占失败时不应发起任何实际发送，也不应触发任何状态流转。
     */
    @Test
    void submitImmediateSend_shouldSkipSend_whenClaimFails() throws InterruptedException {
        AppNotifyRecordEntity task = sampleTask();
        when(appNotifyTaskService.claim(eq(1L), eq(NotifyTaskStatus.PENDING), any(), any())).thenReturn(false);

        awaitSubmission(() -> coordinator.submitImmediateSend(task));

        verify(appNotifyService, never()).sendOnce(any());
        verify(appNotifyTaskService, never()).markSuccess(any(), any());
        verify(appNotifyTaskService, never()).recordAttemptFailure(any(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean(),
                any(), any());
    }

    /**
     * 抢占成功且发送成功时应标记为成功。
     */
    @Test
    void submitImmediateSend_shouldMarkSuccess_whenSendSucceeds() throws InterruptedException {
        AppNotifyRecordEntity task = sampleTask();
        when(appNotifyTaskService.claim(eq(1L), eq(NotifyTaskStatus.PENDING), any(), any())).thenReturn(true);
        when(appNotifyService.sendOnce(task)).thenReturn(NotifyAttemptOutcome.success(200));

        awaitSubmission(() -> coordinator.submitImmediateSend(task));

        verify(appNotifyTaskService).markSuccess(1L, 200);
    }

    /**
     * 抢占成功但发送失败时应记录一次尝试失败，透传当前已失败次数与结果分类。
     */
    @Test
    void submitImmediateSend_shouldRecordFailure_whenSendFails() throws InterruptedException {
        AppNotifyRecordEntity task = sampleTask();
        task.setRetryCount(3);
        when(appNotifyTaskService.claim(eq(1L), eq(NotifyTaskStatus.PENDING), any(), any())).thenReturn(true);
        when(appNotifyService.sendOnce(task)).thenReturn(NotifyAttemptOutcome.retry(500, "server error"));

        awaitSubmission(() -> coordinator.submitImmediateSend(task));

        verify(appNotifyTaskService).recordAttemptFailure(1L, 3, true, 500, "server error");
    }

    /**
     * 调度器捞取路径应使用任务当前状态作为抢占期望状态。
     */
    @Test
    void submitClaimedSend_shouldUseTaskCurrentStatusAsExpectedStatus() throws InterruptedException {
        AppNotifyRecordEntity task = sampleTask();
        task.setTaskStatus(NotifyTaskStatus.PROCESSING);
        when(appNotifyTaskService.claim(eq(1L), eq(NotifyTaskStatus.PROCESSING), any(), any())).thenReturn(false);

        awaitSubmission(() -> coordinator.submitClaimedSend(task));

        verify(appNotifyTaskService).claim(eq(1L), eq(NotifyTaskStatus.PROCESSING), any(), any());
    }

    private AppNotifyRecordEntity sampleTask() {
        return AppNotifyRecordEntity.builder()
                .id(1L)
                .appRefId(1L)
                .notifyUrl("http://example.com/notify")
                .requestBody("{}")
                .retryCount(0)
                .build();
    }

    /**
     * 通知发送线程池是异步执行的，测试用一个 {@link CountDownLatch} 等待被测方法内部的
     * {@code Runnable} 真正执行完毕，避免断言在异步任务完成前就跑到。
     *
     * @param submission 触发一次异步提交的动作
     */
    private void awaitSubmission(Runnable submission) throws InterruptedException {
        // Mockito 的桩、验证均基于共享 mock 对象状态，异步线程执行完成后再断言即可，这里
        // 简单地在被测方法返回后短暂等待一次线程池排空，避免引入额外的回调钩子。
        submission.run();
        CountDownLatch latch = new CountDownLatch(1);
        notifySendExecutor.submit(latch::countDown);
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("等待通知发送线程池排空超时");
        }
    }
}
