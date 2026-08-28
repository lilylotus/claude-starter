package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.config.SyncProperties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link DisruptorDomainEventPublisher} 的单元测试，重点覆盖"事务提交后才真正发布事件"
 * 这一行为（app-sync-org-scope-and-app-change-log change design.md Decision 9 补充说明——
 * 修复组织范围判定读到未提交业务数据导致的假阴性）。
 */
class DisruptorDomainEventPublisherTest {

    private DomainChangeEventProcessor processor;
    private DisruptorDomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        processor = mock(DomainChangeEventProcessor.class);
        SyncProperties properties = new SyncProperties();
        properties.setRingBufferSize(16);
        DomainEntityVersionResolver versionResolver = mock(DomainEntityVersionResolver.class);
        lenient().when(versionResolver.resolve(any())).thenReturn(1L);
        publisher = new DisruptorDomainEventPublisher(processor, properties, new SnowflakeIdGenerator(properties),
                versionResolver);
        publisher.start();
    }

    @AfterEach
    void tearDown() {
        publisher.stop();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * {@code start}/{@code stop} 应正确切换 {@code isRunning} 状态（Spring {@code SmartLifecycle}
     * 启动/优雅停机场景）；{@code stop} 后再次 {@code start} 应能重新建立 RingBuffer 并恢复
     * 正常投递，验证优雅停机不会遗留半启动状态。本阶段仍是进程内 Disruptor，只覆盖
     * "单实例正常运行与优雅停机条件下可靠处理"；异常崩溃（非本类 {@code stop} 触发的场景）
     * 仍可能在业务提交后、事件入队或流水持久化前丢失事件，由 digest/全量对账发现并修复
     * （app-sync-changelog-pull change design.md Context/Risks，proposal.md"阶段性可靠性
     * 边界"，与本类 Javadoc 一致）。
     */
    @Test
    void startAndStop_shouldToggleRunningState_andSupportRestart() throws InterruptedException {
        assertThat(publisher.isRunning()).isTrue();

        publisher.stop();
        assertThat(publisher.isRunning()).isFalse();

        publisher.start();
        assertThat(publisher.isRunning()).isTrue();

        CountDownLatch latch = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(processor).process(org.mockito.ArgumentMatchers.any());

        publisher.publish(DomainChangeEvent.builder().dataType("ROLE").bizId(3L).build());

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 没有活动事务时，{@code publish} 应立即把事件投递给消费者，不等待任何回调。
     */
    @Test
    void publish_shouldDispatchImmediately_whenNoActiveTransaction() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(processor).process(org.mockito.ArgumentMatchers.any());

        DomainChangeEvent event = DomainChangeEvent.builder().dataType("ORG").bizId(1L).build();
        publisher.publish(event);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    /**
     * 存在活动事务时，{@code publish} SHALL NOT 立即投递事件，只有事务提交
     * （{@code afterCommit}）后才真正投递，避免消费者线程读到调用方事务里尚未提交的数据。
     */
    @Test
    void publish_shouldDeferUntilAfterCommit_whenTransactionActive() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(processor).process(org.mockito.ArgumentMatchers.any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            DomainChangeEvent event = DomainChangeEvent.builder().dataType("USER").bizId(2L).build();
            publisher.publish(event);

            // 事务未提交前，不应触发消费者（等待一小段时间确认确实没有触发，而不是刚好还没轮到）。
            assertThat(latch.await(300, TimeUnit.MILLISECONDS)).isFalse();
            verifyNoInteractions(processor);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(processor).process(org.mockito.ArgumentMatchers.any());
    }
}
