package cn.nihility.rbac.sync.notify.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.sync.changelog.entity.AppDataChangeLogEntity;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.notify.constant.NotifyTaskStatus;
import cn.nihility.rbac.sync.notify.entity.AppNotifyRecordEntity;
import cn.nihility.rbac.sync.notify.mapper.AppNotifyRecordMapper;
import cn.nihility.rbac.sync.notify.support.NotifyRetryScheduleCalculator;
import cn.nihility.rbac.sync.transform.BizSnapshotResolver;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link AppNotifyTaskServiceImpl} 的单元测试：覆盖 {@code enqueueTask} 的幂等落库
 * （已存在直接复用、并发唯一键冲突时回退查询已存在行、目标应用配置查不到时防御性跳过）、
 * {@code claim} 抢占结果透传、{@code resetDeadToPending} 仅对 {@code DEAD} 生效、
 * {@code recordAttemptFailure} 按失败类型分流（不可重试直接死信，不咨询退避计算器；
 * 可重试时委托 {@code NotifyRetryScheduleCalculator} 决定）（app-sync-changelog-pull change
 * design.md Decision 6）。
 */
@ExtendWith(MockitoExtension.class)
class AppNotifyTaskServiceImplTest {

    @Mock
    private AppNotifyRecordMapper appNotifyRecordMapper;

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private BizSnapshotResolver bizSnapshotResolver;

    @Mock
    private NotifyRetryScheduleCalculator notifyRetryScheduleCalculator;

    private AppNotifyTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppNotifyTaskServiceImpl(appNotifyRecordMapper, appConfigMapper, bizSnapshotResolver,
                notifyRetryScheduleCalculator);
    }

    /**
     * 已存在 {@code (appRefId, eventId)} 行时应直接复用，不重新插入。
     */
    @Test
    void enqueueTask_shouldReuseExistingRow_whenAlreadyEnqueued() {
        AppNotifyRecordEntity existing = AppNotifyRecordEntity.builder().id(1L).build();
        when(appNotifyRecordMapper.selectOne(any())).thenReturn(existing);

        AppNotifyRecordEntity result = service.enqueueTask(sampleEvent(), sampleChangeLog(), 1L);

        assertThat(result).isSameAs(existing);
        verify(appNotifyRecordMapper, never()).insert(any(AppNotifyRecordEntity.class));
    }

    /**
     * 首次落库时应构造 {@code PENDING} 任务并插入，请求体快照携带 {@code changeSeq}。
     */
    @Test
    void enqueueTask_shouldInsertPendingTask_whenNotExists() {
        when(appNotifyRecordMapper.selectOne(any())).thenReturn(null);
        when(appConfigMapper.selectOne(any())).thenReturn(sampleTarget());

        AppNotifyRecordEntity result = service.enqueueTask(sampleEvent(), sampleChangeLog(), 1L);

        ArgumentCaptor<AppNotifyRecordEntity> captor = ArgumentCaptor.forClass(AppNotifyRecordEntity.class);
        verify(appNotifyRecordMapper).insert(captor.capture());
        AppNotifyRecordEntity inserted = captor.getValue();
        assertThat(inserted.getAppRefId()).isEqualTo(1L);
        assertThat(inserted.getEventId()).isEqualTo(888L);
        assertThat(inserted.getChangeSeq()).isEqualTo(100L);
        assertThat(inserted.getEntityVersion()).isEqualTo(3L);
        assertThat(inserted.getTaskStatus()).isEqualTo(NotifyTaskStatus.PENDING);
        assertThat(inserted.getRetryCount()).isZero();
        assertThat(inserted.getRequestBody()).contains("\"changeSeq\":\"100\"").contains("\"eventId\":\"888\"");
        assertThat(result).isSameAs(inserted);
    }

    /**
     * 目标应用的对外接口配置查不到时应防御性跳过，返回 {@code null}，不插入任何行。
     */
    @Test
    void enqueueTask_shouldReturnNull_whenTargetConfigNotFound() {
        when(appNotifyRecordMapper.selectOne(any())).thenReturn(null);
        when(appConfigMapper.selectOne(any())).thenReturn(null);

        AppNotifyRecordEntity result = service.enqueueTask(sampleEvent(), sampleChangeLog(), 1L);

        assertThat(result).isNull();
        verify(appNotifyRecordMapper, never()).insert(any(AppNotifyRecordEntity.class));
    }

    /**
     * 并发插入导致唯一键冲突时，应回退查询并返回已存在的行，而不是向外抛出异常。
     */
    @Test
    void enqueueTask_shouldFallBackToExistingRow_whenDuplicateKeyOnConcurrentInsert() {
        AppNotifyRecordEntity existingAfterRace = AppNotifyRecordEntity.builder().id(2L).build();
        when(appNotifyRecordMapper.selectOne(any())).thenReturn(null, existingAfterRace);
        when(appConfigMapper.selectOne(any())).thenReturn(sampleTarget());
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate")).when(appNotifyRecordMapper)
                .insert(any(AppNotifyRecordEntity.class));

        AppNotifyRecordEntity result = service.enqueueTask(sampleEvent(), sampleChangeLog(), 1L);

        assertThat(result).isSameAs(existingAfterRace);
        verify(appNotifyRecordMapper, times(2)).selectOne(any());
    }

    /**
     * {@code claim} 应把 mapper 的受影响行数（1/0）转换为布尔结果。
     */
    @Test
    void claim_shouldReturnTrue_whenOneRowAffected() {
        when(appNotifyRecordMapper.update(eq(null), any())).thenReturn(1);

        boolean claimed = service.claim(1L, NotifyTaskStatus.PENDING, LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(60));

        assertThat(claimed).isTrue();
    }

    /**
     * 抢占失败（0 行受影响，如已被其他路径抢占）时应返回 {@code false}。
     */
    @Test
    void claim_shouldReturnFalse_whenNoRowAffected() {
        when(appNotifyRecordMapper.update(eq(null), any())).thenReturn(0);

        boolean claimed = service.claim(1L, NotifyTaskStatus.RETRY, LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(60));

        assertThat(claimed).isFalse();
    }

    /**
     * 重置成功（原状态确实是 {@code DEAD}）时应返回 {@code true}。
     */
    @Test
    void resetDeadToPending_shouldReturnTrue_whenRowWasDead() {
        when(appNotifyRecordMapper.update(eq(null), any())).thenReturn(1);

        assertThat(service.resetDeadToPending(1L)).isTrue();
    }

    /**
     * 原状态不是 {@code DEAD}（0 行受影响）时应返回 {@code false}，不静默成功。
     */
    @Test
    void resetDeadToPending_shouldReturnFalse_whenRowNotDead() {
        when(appNotifyRecordMapper.update(eq(null), any())).thenReturn(0);

        assertThat(service.resetDeadToPending(1L)).isFalse();
    }

    /**
     * 不可重试类型的失败应直接转死信，不咨询退避计算器。
     */
    @Test
    void recordAttemptFailure_shouldGoDeadDirectly_whenNotRetryableByType() {
        service.recordAttemptFailure(1L, 0, false, 400, "bad request");

        verify(notifyRetryScheduleCalculator, never()).decide(anyInt(), any());
        verify(appNotifyRecordMapper).update(eq(null), any());
    }

    /**
     * 可重试类型的失败应委托退避计算器决定，计算器判定死信时也应落库为死信。
     */
    @Test
    void recordAttemptFailure_shouldConsultCalculator_whenRetryableByType() {
        when(notifyRetryScheduleCalculator.decide(eq(0), any()))
                .thenReturn(NotifyRetryScheduleCalculator.RetryDecision.retry(1,
                        LocalDateTime.now().plusSeconds(30)));

        service.recordAttemptFailure(1L, 0, true, 500, "server error");

        verify(notifyRetryScheduleCalculator).decide(eq(0), any());
        verify(appNotifyRecordMapper).update(eq(null), any());
    }

    /**
     * 构造一条示例事件。
     *
     * @return 示例事件
     */
    private DomainChangeEvent sampleEvent() {
        return DomainChangeEvent.builder()
                .eventId(888L)
                .entityVersion(3L)
                .dataType(SyncDomain.ORG)
                .bizId(1L)
                .operationType(OperationType.UPDATE)
                .operator("1")
                .occurredAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();
    }

    /**
     * 构造一条示例流水。
     *
     * @return 示例流水
     */
    private AppDataChangeLogEntity sampleChangeLog() {
        return AppDataChangeLogEntity.builder().changeSeq(100L).build();
    }

    /**
     * 构造一个示例目标应用对外接口配置。
     *
     * @return 示例配置
     */
    private AppConfigEntity sampleTarget() {
        return AppConfigEntity.builder()
                .appRefId(1L)
                .appId("open-app-1")
                .syncMode(SyncMode.NOTIFY)
                .notifyUrl("http://example.com/notify")
                .build();
    }
}
