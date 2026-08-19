package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.service.AppNotifyService;
import cn.nihility.rbac.sync.notify.support.NotifyCandidateResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DomainChangeEventProcessor} 的单元测试：验证"直接判定候选应用并逐个触发通知"
 * 的调用顺序，候选判定/单个应用通知异常都不应向外传播（app-sync-drop-changelog change
 * design.md Decision 6，避免 Disruptor 消费者线程因单条事件异常而阻塞后续事件处理；单个
 * 应用通知异常不影响其余应用见 app-sync-notify-pull spec"一个应用通知失败不影响其他应用"
 * 场景）。
 */
@ExtendWith(MockitoExtension.class)
class DomainChangeEventProcessorTest {

    @Mock
    private NotifyCandidateResolver notifyCandidateResolver;

    @Mock
    private AppNotifyService appNotifyService;

    private DomainChangeEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DomainChangeEventProcessor(notifyCandidateResolver, appNotifyService);
    }

    /**
     * 正常场景下应先判定候选应用列表，再对每个匹配应用各自触发通知。
     */
    @Test
    void process_shouldResolveCandidatesThenNotifyEachApp() {
        DomainChangeEvent event = sampleEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of(1L, 2L));

        processor.process(event);

        verify(notifyCandidateResolver).resolveCandidateAppRefIds(event);
        verify(appNotifyService).notifyIfConfigured(event, 1L);
        verify(appNotifyService).notifyIfConfigured(event, 2L);
    }

    /**
     * 候选判定阶段抛异常时，不应向外传播（消费者线程需要继续处理后续事件）。
     */
    @Test
    void process_shouldNotPropagateExceptionWhenResolveCandidatesFails() {
        DomainChangeEvent event = sampleEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 其中一个应用的通知阶段抛异常时，不应向外传播，且不影响其余应用继续触发通知。
     */
    @Test
    void process_shouldContinueOtherAppsWhenOneNotifyFails() {
        DomainChangeEvent event = sampleEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of(1L, 2L));
        org.mockito.Mockito.doThrow(new RuntimeException("notify error"))
                .when(appNotifyService).notifyIfConfigured(event, 1L);

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(appNotifyService).notifyIfConfigured(event, 2L);
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
