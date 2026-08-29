package cn.nihility.rbac.sync.event.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.changelog.service.AppDataChangeLogService;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.service.AppNotifyTaskService;
import cn.nihility.rbac.sync.notify.support.NotifyCandidateResolver;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * {@link DomainChangeRecorder} 的单元测试：验证调用顺序（先写流水、再解析候选应用、逐个
 * 候选应用创建通知任务）与"候选应用为空时返回空任务列表""目标应用配置查不到导致
 * {@code enqueueTask} 返回 {@code null} 时应从结果中过滤掉"两个边界场景（真正的事务原子性
 * 由 {@code @Transactional} 注解与 Spring 容器保证，不在纯 Mockito 单测范围内，见
 * app-sync-changelog-pull change design.md Decision 6）。
 */
@ExtendWith(MockitoExtension.class)
class DomainChangeRecorderTest {

    @Mock
    private AppDataChangeLogService appDataChangeLogService;

    @Mock
    private NotifyCandidateResolver notifyCandidateResolver;

    @Mock
    private AppNotifyTaskService appNotifyTaskService;

    private DomainChangeRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new DomainChangeRecorder(appDataChangeLogService, notifyCandidateResolver, appNotifyTaskService);
    }

    /**
     * 正常场景下应先写流水，再为每个候选应用创建/复用通知任务，结果携带流水与全部任务。
     */
    @Test
    void record_shouldAppendChangeLogThenEnqueueTaskForEachCandidate() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        when(appDataChangeLogService.append(event)).thenReturn(changeLog);
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of(1L, 2L));
        AppNotifyRecordEntity task1 = AppNotifyRecordEntity.builder().id(11L).appRefId(1L).build();
        AppNotifyRecordEntity task2 = AppNotifyRecordEntity.builder().id(12L).appRefId(2L).build();
        when(appNotifyTaskService.enqueueTask(event, changeLog, 1L)).thenReturn(task1);
        when(appNotifyTaskService.enqueueTask(event, changeLog, 2L)).thenReturn(task2);

        DomainChangeRecordResult result = recorder.record(event);

        assertThat(result.changeLog()).isSameAs(changeLog);
        assertThat(result.tasks()).containsExactly(task1, task2);
        verify(appDataChangeLogService).append(event);
        verify(appNotifyTaskService).enqueueTask(event, changeLog, 1L);
        verify(appNotifyTaskService).enqueueTask(event, changeLog, 2L);
    }

    /**
     * 候选应用为空时应返回空任务列表，不调用 {@code enqueueTask}。
     */
    @Test
    void record_shouldReturnEmptyTasks_whenNoCandidateApps() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        when(appDataChangeLogService.append(event)).thenReturn(changeLog);
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of());

        DomainChangeRecordResult result = recorder.record(event);

        assertThat(result.tasks()).isEmpty();
        verify(appNotifyTaskService, org.mockito.Mockito.never()).enqueueTask(any(), any(), any());
    }

    /**
     * {@code enqueueTask} 对某个候选应用返回 {@code null}（目标应用配置查不到）时，应从
     * 结果任务列表中过滤掉，不产生 {@code null} 元素。
     */
    @Test
    void record_shouldFilterOutNullTasks() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        when(appDataChangeLogService.append(event)).thenReturn(changeLog);
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of(1L, 2L));
        AppNotifyRecordEntity task2 = AppNotifyRecordEntity.builder().id(12L).appRefId(2L).build();
        when(appNotifyTaskService.enqueueTask(event, changeLog, 1L)).thenReturn(null);
        when(appNotifyTaskService.enqueueTask(eq(event), eq(changeLog), eq(2L))).thenReturn(task2);

        DomainChangeRecordResult result = recorder.record(event);

        assertThat(result.tasks()).containsExactly(task2);
    }

    /**
     * 任一候选应用任务插入失败时，流水与此前已插入任务所在的同一本地事务必须回滚。
     */
    @Test
    void record_shouldRollbackWholeTransaction_whenAnyTaskInsertionFails() {
        DomainChangeEvent event = sampleEvent();
        AppDataChangeLogEntity changeLog = AppDataChangeLogEntity.builder().changeSeq(100L).build();
        when(appDataChangeLogService.append(event)).thenReturn(changeLog);
        when(notifyCandidateResolver.resolveCandidateAppRefIds(event)).thenReturn(List.of(1L, 2L));
        when(appNotifyTaskService.enqueueTask(event, changeLog, 1L))
                .thenReturn(AppNotifyRecordEntity.builder().id(11L).build());
        when(appNotifyTaskService.enqueueTask(event, changeLog, 2L))
                .thenThrow(new RuntimeException("insert failed"));
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        TransactionInterceptor interceptor = new TransactionInterceptor(transactionManager,
                new AnnotationTransactionAttributeSource());
        ProxyFactory proxyFactory = new ProxyFactory(recorder);
        proxyFactory.addAdvice(interceptor);
        DomainChangeRecorder transactionalRecorder = (DomainChangeRecorder) proxyFactory.getProxy();

        assertThatThrownBy(() -> transactionalRecorder.record(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("insert failed");

        assertThat(transactionManager.isRolledBack()).isTrue();
        assertThat(transactionManager.isCommitted()).isFalse();
        verify(appNotifyTaskService).enqueueTask(event, changeLog, 1L);
        verify(appNotifyTaskService).enqueueTask(event, changeLog, 2L);
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

    /** 记录代理事务最终提交或回滚结果的最小事务管理器。 */
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private boolean committed;

        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            // 无外部资源；仅验证 Spring 事务拦截器对异常选择回滚。
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }

        private boolean isCommitted() {
            return committed;
        }

        private boolean isRolledBack() {
            return rolledBack;
        }
    }
}
