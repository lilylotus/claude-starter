package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DomainChangeEventProcessor} 的单元测试：验证"先落库出多条记录，后逐条触发通知"
 * 的调用顺序，落库/单条通知异常都不应向外传播（design.md Decision 4，避免 Disruptor
 * 消费者线程因单条事件异常而阻塞后续事件处理；单条记录通知异常不影响其余记录见
 * app-sync-org-scope-and-app-change-log change design.md Decision 6）。
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
     * 正常场景下应先落库拿到变更记录列表，再对每条记录各自触发通知判断。
     */
    @Test
    void process_shouldRecordThenNotifyEachChangeLog() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog1 = AppDataChangeLogEntity.builder().id(1024L).appRefId(1L).build();
        AppDataChangeLogEntity changeLog2 = AppDataChangeLogEntity.builder().id(1025L).appRefId(2L).build();
        when(appDataChangeLogService.record(event)).thenReturn(List.of(changeLog1, changeLog2));

        processor.process(event);

        verify(appDataChangeLogService).record(event);
        verify(appNotifyService).notifyIfConfigured(changeLog1);
        verify(appNotifyService).notifyIfConfigured(changeLog2);
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
     * 其中一条记录的通知阶段抛异常时，不应向外传播，且不影响其余记录继续触发通知。
     */
    @Test
    void process_shouldContinueOtherChangeLogsWhenOneNotifyFails() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity failingChangeLog = AppDataChangeLogEntity.builder().id(1024L).appRefId(1L).build();
        AppDataChangeLogEntity okChangeLog = AppDataChangeLogEntity.builder().id(1025L).appRefId(2L).build();
        when(appDataChangeLogService.record(event)).thenReturn(List.of(failingChangeLog, okChangeLog));
        org.mockito.Mockito.doThrow(new RuntimeException("notify error"))
                .when(appNotifyService).notifyIfConfigured(failingChangeLog);

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(appNotifyService).notifyIfConfigured(okChangeLog);
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
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
