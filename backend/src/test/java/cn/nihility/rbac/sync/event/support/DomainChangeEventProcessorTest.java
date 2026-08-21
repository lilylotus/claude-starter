package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
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
 * 场景），以及"组织/用户/任职变更后策略自动重新执行"分支（close-sso-log-and-policy-gaps
 * change design.md Decision 2）：命中数据域时逐条重新执行全部启用策略、单条策略执行失败
 * 不影响其余策略、非命中数据域不触发任何策略执行。
 */
@ExtendWith(MockitoExtension.class)
class DomainChangeEventProcessorTest {

    @Mock
    private NotifyCandidateResolver notifyCandidateResolver;

    @Mock
    private AppNotifyService appNotifyService;

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private PolicyExecutionService policyExecutionService;

    private DomainChangeEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DomainChangeEventProcessor(notifyCandidateResolver, appNotifyService, policyMapper,
                policyExecutionService);
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
        doThrow(new RuntimeException("notify error")).when(appNotifyService).notifyIfConfigured(event, 1L);

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(appNotifyService).notifyIfConfigured(event, 2L);
    }

    /**
     * 数据域为 {@code USER} 时，应查询全部启用状态策略并逐条重新执行。
     */
    @Test
    void process_shouldReExecuteAllEnabledPolicies_whenDataTypeIsUser() {
        DomainChangeEvent event = userEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenReturn(List.of(
                PolicyEntity.builder().id(10L).status(PolicyStatus.ENABLED).build(),
                PolicyEntity.builder().id(20L).status(PolicyStatus.ENABLED).build()));

        processor.process(event);

        verify(policyExecutionService).execute(10L, event.getOperator());
        verify(policyExecutionService).execute(20L, event.getOperator());
    }

    /**
     * 数据域不属于组织/用户/任职时，不应触发任何策略执行。
     */
    @Test
    void process_shouldNotReExecutePolicies_whenDataTypeNotEligible() {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(1L)
                .operationType(OperationType.CREATE)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of());

        processor.process(event);

        verify(policyMapper, never()).selectList(any());
        verify(policyExecutionService, never()).execute(any(), any());
    }

    /**
     * 其中一条策略重新执行失败时，不应影响其余策略继续重新执行，也不应向外传播异常。
     */
    @Test
    void process_shouldContinueOtherPolicies_whenOnePolicyExecutionFails() {
        DomainChangeEvent event = userEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenReturn(List.of(
                PolicyEntity.builder().id(10L).status(PolicyStatus.ENABLED).build(),
                PolicyEntity.builder().id(20L).status(PolicyStatus.ENABLED).build()));
        doThrow(new RuntimeException("execute error")).when(policyExecutionService)
                .execute(10L, event.getOperator());

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(policyExecutionService).execute(20L, event.getOperator());
    }

    /**
     * 策略重新执行分支整体异常（如查询启用策略列表失败）时，不应向外传播，也不影响通知
     * 候选逻辑已经完成的处理。
     */
    @Test
    void process_shouldNotPropagateException_whenPolicyQueryFails() {
        DomainChangeEvent event = userEvent();
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of());
        when(policyMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 构造一个 {@code USER} 数据域的示例领域变更事件。
     *
     * @return 示例事件
     */
    private DomainChangeEvent userEvent() {
        return DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(1L)
                .operationType(OperationType.CREATE)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();
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
