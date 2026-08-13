package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DomainChangeEventProcessor} 的单元测试：验证"先落库后通知"的调用顺序，
 * 以及落库/通知任一环节抛异常都不应向外传播（design.md Decision 4，避免 Disruptor
 * 消费者线程因单条事件异常而阻塞后续事件处理）。
 */
@ExtendWith(MockitoExtension.class)
class DomainChangeEventProcessorTest {

    @Mock
    private AppDataChangeLogService appDataChangeLogService;

    @Mock
    private AppNotifyService appNotifyService;

    private DomainChangeEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DomainChangeEventProcessor(appDataChangeLogService, appNotifyService);
    }

    /**
     * 正常场景下应先落库拿到变更记录，再用该记录触发通知。
     */
    @Test
    void process_shouldRecordThenNotify() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().id(1024L).build();
        when(appDataChangeLogService.record(event)).thenReturn(changeLog);

        processor.process(event);

        verify(appDataChangeLogService).record(event);
        verify(appNotifyService).notifyMatchedApps(changeLog);
    }

    /**
     * 落库阶段抛异常时，不应向外传播（消费者线程需要继续处理后续事件）。
     */
    @Test
    void process_shouldNotPropagateExceptionWhenRecordFails() {
        DomainChangeEvent event = sampleEvent();
        when(appDataChangeLogService.record(event)).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 通知阶段抛异常时，不应向外传播。
     */
    @Test
    void process_shouldNotPropagateExceptionWhenNotifyFails() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().id(1024L).build();
        when(appDataChangeLogService.record(event)).thenReturn(changeLog);
        org.mockito.Mockito.doThrow(new RuntimeException("notify error"))
                .when(appNotifyService).notifyMatchedApps(any());

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 构造一个示例领域变更事件。
     *
     * @return 示例事件
     */
    private DomainChangeEvent sampleEvent() {
        return DomainChangeEvent.builder()
                .dataType(SyncDomain.ORG)
                .bizId(1L)
                .operationType(OperationType.CREATE)
                .snapshot(Map.of("code", "ORG001"))
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
