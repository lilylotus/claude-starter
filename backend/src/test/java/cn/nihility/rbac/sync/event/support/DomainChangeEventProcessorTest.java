package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.appaccess.policy.constant.PolicyStatus;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.support.NotifySendCoordinator;
import cn.nihility.rbac.userrole.entity.UserRoleRuleEntity;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleMapper;
import cn.nihility.rbac.userrole.service.UserRoleRuleExecutionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DomainChangeEventProcessor} 的单元测试：验证"先调用 {@link DomainChangeRecorder}
 * 完成落库（流水 + 全部候选应用 PENDING 通知任务同事务），落库成功后对每个新建任务提交一次
 * 即时发送优化"的调用顺序与先后依赖关系（app-sync-changelog-pull change design.md
 * Decision 6）；落库失败时不应触发任何即时发送；单个任务提交异常不影响其余任务（避免
 * Disruptor 消费者线程因单条事件异常而阻塞后续事件处理）；以及"组织/用户/任职变更后策略
 * 自动重新执行"分支（close-sso-log-and-policy-gaps change design.md Decision 2）：命中数据
 * 域时逐条重新执行全部启用策略、单条策略执行失败不影响其余策略、非命中数据域不触发任何
 * 策略执行、且不卷入落库是否成功。
 */
@ExtendWith(MockitoExtension.class)
class DomainChangeEventProcessorTest {

    @Mock
    private DomainChangeRecorder domainChangeRecorder;

    @Mock
    private NotifySendCoordinator notifySendCoordinator;

    @Mock
    private PolicyMapper policyMapper;

    @Mock
    private PolicyExecutionService policyExecutionService;

    @Mock
    private UserRoleRuleMapper userRoleRuleMapper;

    @Mock
    private UserRoleRuleExecutionService userRoleRuleExecutionService;

    private DomainChangeEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new DomainChangeEventProcessor(domainChangeRecorder, notifySendCoordinator, policyMapper,
                policyExecutionService, userRoleRuleMapper, userRoleRuleExecutionService);
        // 默认打桩为查不到任何用户角色规则，聚焦其他分支的用例无需关心该分支的调用细节；
        // 关注用户角色规则自动重新执行分支本身行为的用例（见下方）会用更具体的打桩覆盖。
        lenient().when(userRoleRuleMapper.selectList(any())).thenReturn(List.of());
        // 默认打桩为落库成功但没有候选应用，聚焦策略重新执行分支的用例无需关心通知分支的
        // 调用细节，只需保证 result != null 从而不影响流程继续；关注落库/通知分支本身行为
        // 的用例（见下方）会用更具体的打桩覆盖这个默认值。
        lenient().when(domainChangeRecorder.record(any()))
                .thenReturn(new DomainChangeRecordResult(AppDataChangeLogEntity.builder().changeSeq(1L).build(),
                        List.of()));
    }

    /**
     * 正常场景下应先调用落库编排组件，落库成功后对每个新建任务各自提交一次即时发送优化。
     */
    @Test
    void process_shouldRecordThenSubmitImmediateSendForEachTask() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        AppNotifyRecordEntity task1 = AppNotifyRecordEntity.builder().id(1L).appRefId(1L).build();
        AppNotifyRecordEntity task2 = AppNotifyRecordEntity.builder().id(2L).appRefId(2L).build();
        when(domainChangeRecorder.record(event)).thenReturn(new DomainChangeRecordResult(changeLog,
                List.of(task1, task2)));

        processor.process(event);

        verify(domainChangeRecorder).record(event);
        verify(notifySendCoordinator).submitImmediateSend(task1);
        verify(notifySendCoordinator).submitImmediateSend(task2);
    }

    /**
     * 落库失败时，不应向外传播异常，且不应触发任何即时发送优化。
     */
    @Test
    void process_shouldSkipImmediateSend_whenRecordFails() {
        DomainChangeEvent event = sampleEvent();
        when(domainChangeRecorder.record(event)).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();

        verify(notifySendCoordinator, never()).submitImmediateSend(any());
    }

    /**
     * 其中一个任务的即时发送提交阶段抛异常时，不应向外传播，且不影响其余任务继续提交。
     */
    @Test
    void process_shouldContinueOtherTasksWhenOneSubmitFails() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        AppNotifyRecordEntity task1 = AppNotifyRecordEntity.builder().id(1L).appRefId(1L).build();
        AppNotifyRecordEntity task2 = AppNotifyRecordEntity.builder().id(2L).appRefId(2L).build();
        when(domainChangeRecorder.record(event)).thenReturn(new DomainChangeRecordResult(changeLog,
                List.of(task1, task2)));
        doThrow(new RuntimeException("submit error")).when(notifySendCoordinator).submitImmediateSend(task1);

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(notifySendCoordinator).submitImmediateSend(task2);
    }

    /**
     * 数据域为 {@code USER} 时，应查询全部启用状态策略并逐条重新执行。
     */
    @Test
    void process_shouldReExecuteAllEnabledPolicies_whenDataTypeIsUser() {
        DomainChangeEvent event = userEvent();
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
        when(policyMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 数据域为 {@code ORG}/{@code USER}/{@code POSITION} 时，应查询全部用户角色规则（不做
     * 状态过滤）并逐条重新执行，{@code operator} 必须来自 {@code event.getOperator()}，不能
     * 依赖 {@code CurrentOperatorService}（本测试完全脱离登录上下文运行，验证调用参数即可
     * 证明未走后者，否则会重现 close-sso-log-and-policy-gaps change 归档记录过的历史 bug）。
     */
    @Test
    void process_shouldReExecuteAllUserRoleRules_whenDataTypeIsOrgUserOrPosition() {
        for (String dataType : List.of(SyncDomain.ORG, SyncDomain.USER, SyncDomain.POSITION)) {
            DomainChangeEvent event = DomainChangeEvent.builder()
                    .dataType(dataType)
                    .bizId(1L)
                    .operationType(OperationType.CREATE)
                    .operator("op-" + dataType)
                    .occurredAt(LocalDateTime.now())
                    .build();
            when(userRoleRuleMapper.selectList(any())).thenReturn(List.of(
                    UserRoleRuleEntity.builder().id(10L).roleId(1L).build(),
                    UserRoleRuleEntity.builder().id(20L).roleId(2L).build()));

            processor.process(event);

            verify(userRoleRuleExecutionService).execute(10L, event.getOperator());
            verify(userRoleRuleExecutionService).execute(20L, event.getOperator());
        }
    }

    /**
     * 数据域不属于组织/用户/任职时，不应触发任何用户角色规则执行。
     */
    @Test
    void process_shouldNotReExecuteUserRoleRules_whenDataTypeNotEligible() {
        DomainChangeEvent event = DomainChangeEvent.builder()
                .dataType(SyncDomain.APP)
                .bizId(1L)
                .operationType(OperationType.CREATE)
                .operator("1")
                .occurredAt(LocalDateTime.now())
                .build();

        processor.process(event);

        verify(userRoleRuleMapper, never()).selectList(any());
        verify(userRoleRuleExecutionService, never()).execute(any(), any());
    }

    /**
     * 其中一条用户角色规则重新执行失败时，不应影响其余规则继续重新执行，也不应向外传播
     * 异常，且不影响策略自动重新执行分支已经完成的处理。
     */
    @Test
    void process_shouldContinueOtherUserRoleRules_whenOneRuleExecutionFails() {
        DomainChangeEvent event = userEvent();
        when(userRoleRuleMapper.selectList(any())).thenReturn(List.of(
                UserRoleRuleEntity.builder().id(10L).roleId(1L).build(),
                UserRoleRuleEntity.builder().id(20L).roleId(2L).build()));
        doThrow(new RuntimeException("execute error")).when(userRoleRuleExecutionService)
                .execute(10L, event.getOperator());

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
        verify(userRoleRuleExecutionService).execute(20L, event.getOperator());
    }

    /**
     * 用户角色规则重新执行分支整体异常（如查询规则列表失败）时，不应向外传播，也不影响
     * 策略自动重新执行分支已经完成的处理。
     */
    @Test
    void process_shouldNotPropagateException_whenUserRoleRuleQueryFails() {
        DomainChangeEvent event = userEvent();
        when(userRoleRuleMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        assertThatCode(() -> processor.process(event)).doesNotThrowAnyException();
    }

    /**
     * 用户角色规则自动重新执行分支异常不应影响策略自动重新执行分支：两者各自独立
     * try/catch，一个分支失败不阻断另一个分支执行。
     */
    @Test
    void process_shouldStillReExecutePolicies_whenUserRoleRuleBranchFails() {
        DomainChangeEvent event = userEvent();
        when(policyMapper.selectList(any())).thenReturn(List.of(
                PolicyEntity.builder().id(10L).status(PolicyStatus.ENABLED).build()));
        when(userRoleRuleMapper.selectList(any())).thenThrow(new RuntimeException("db error"));

        processor.process(event);

        verify(policyExecutionService).execute(10L, event.getOperator());
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
