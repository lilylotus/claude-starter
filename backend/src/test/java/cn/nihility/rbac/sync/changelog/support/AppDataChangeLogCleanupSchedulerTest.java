package cn.nihility.rbac.sync.changelog.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.sync.changelog.config.AppDataChangeLogCleanupProperties;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AppDataChangeLogCleanupScheduler} 的单元测试：验证循环调用批次直到某一批实际删除
 * 数少于 {@code batchSize}（说明已追上）为止，单批执行异常不重试、直接结束本轮
 * （tasks.md 3.3）。
 */
@ExtendWith(MockitoExtension.class)
class AppDataChangeLogCleanupSchedulerTest {

    @Mock
    private AppDataChangeLogService appDataChangeLogService;

    private AppDataChangeLogCleanupProperties properties;

    private AppDataChangeLogCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new AppDataChangeLogCleanupProperties();
        properties.setBatchSize(2);
        properties.setRetentionDays(90);
        scheduler = new AppDataChangeLogCleanupScheduler(properties, appDataChangeLogService);
    }

    /**
     * 连续满批时应持续调用下一批，直到某一批实际删除数少于 {@code batchSize}。
     */
    @Test
    void cleanup_shouldLoopUntilBatchSmallerThanBatchSize() {
        when(appDataChangeLogService.cleanupExpiredBatch(any(LocalDateTime.class), eq(2)))
                .thenReturn(2, 2, 1, 0);

        scheduler.cleanup();

        verify(appDataChangeLogService, times(3)).cleanupExpiredBatch(any(LocalDateTime.class), eq(2));
    }

    /**
     * 无过期记录时应只调用一次批次方法即结束。
     */
    @Test
    void cleanup_shouldCallOnce_whenNoExpiredRecords() {
        when(appDataChangeLogService.cleanupExpiredBatch(any(LocalDateTime.class), eq(2))).thenReturn(0);

        scheduler.cleanup();

        verify(appDataChangeLogService, times(1)).cleanupExpiredBatch(any(LocalDateTime.class), eq(2));
    }

    /**
     * 单批执行异常时应直接结束本轮，不重试、不向外传播异常。
     */
    @Test
    void cleanup_shouldStop_whenBatchThrows() {
        when(appDataChangeLogService.cleanupExpiredBatch(any(LocalDateTime.class), eq(2)))
                .thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> scheduler.cleanup()).doesNotThrowAnyException();

        verify(appDataChangeLogService, times(1)).cleanupExpiredBatch(any(LocalDateTime.class), eq(2));
    }
}
