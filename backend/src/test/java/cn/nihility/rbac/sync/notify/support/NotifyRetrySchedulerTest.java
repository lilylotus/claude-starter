package cn.nihility.rbac.sync.notify.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.sync.notify.config.NotifyRetryProperties;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link NotifyRetryScheduler} 的首次发送宕机恢复测试。 */
class NotifyRetrySchedulerTest {

    /**
     * 事务已提交但即时线程池尚未接收任务时，下一轮扫描必须捞取 PENDING 并重新提交发送。
     */
    @Test
    void scanAndDispatch_shouldRecoverPendingTask_afterImmediateDispatchWasLost() {
        AppNotifyTaskService taskService = org.mockito.Mockito.mock(AppNotifyTaskService.class);
        NotifySendCoordinator coordinator = org.mockito.Mockito.mock(NotifySendCoordinator.class);
        NotifyRetryProperties properties = new NotifyRetryProperties();
        properties.setSchedulerBatchSize(50);
        AppNotifyRecordEntity pending = AppNotifyRecordEntity.builder()
                .id(11L)
                .taskStatus(NotifyTaskStatus.PENDING)
                .build();
        when(taskService.scanDueTasks(any(), eq(50))).thenReturn(List.of(pending));
        NotifyRetryScheduler scheduler = new NotifyRetryScheduler(taskService, coordinator, properties);

        scheduler.scanAndDispatch();

        verify(taskService).scanDueTasks(any(), eq(50));
        verify(coordinator).submitClaimedSend(pending);
    }
}
