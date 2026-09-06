package cn.nihility.rbac.workflow.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.OperationRequestStatus;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.OutboxEventMapper;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link OutboxEventServiceImpl} 真实数据库集成测试（production-approval-lifecycle change
 * design.md 第10节，tasks.md 7.1）。不使用测试专用回滚事务：{@link OutboxEventService} 各方法
 * 自身已声明 {@code @Transactional(REQUIRED)}，直接通过注入的代理 Bean 调用即可各自拥有独立
 * 的真实物理事务（与 {@code BusinessLockServiceImplTest} 同样的理由），能够真实验证跨事务持久
 * 化语义，而不是同一事务内的临时可见性。同事务写入的验证（{@link
 * #publish_shouldRollbackTogetherWithCallerTransaction_whenCallerTransactionRollsBack}）例外
 * 地需要一个会真正回滚的外层事务，改用 {@link TransactionTemplate} 显式包裹。
 */
@SpringBootTest
class OutboxEventServiceImplIntegrationTest {

    /** 测试专用事件类型，避免与真实业务事件混淆。 */
    private static final String TEST_EVENT_TYPE = "TEST_EVENT_TYPE";

    /** Outbox 事件生产/领取服务。 */
    @Autowired
    private OutboxEventService outboxEventService;

    /** Outbox 事件数据访问接口，用于测试断言与清理。 */
    @Autowired
    private OutboxEventMapper outboxEventMapper;

    /** 操作幂等记录数据访问接口，充当"同事务内的另一项业务写操作"验证对象。 */
    @Autowired
    private OperationRequestMapper operationRequestMapper;

    /** 事务管理器，用于显式包裹一个会真正回滚的物理事务。 */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 本方法使用过的 eventId，测试结束后清理，避免在共享开发库残留。 */
    private String eventIdToCleanup;

    /** 本方法使用过的 requestKey（幂等记录表），测试结束后清理。 */
    private String requestKeyToCleanup;

    @AfterEach
    void cleanup() {
        if (eventIdToCleanup != null) {
            outboxEventMapper.delete(new LambdaQueryWrapper<OutboxEventEntity>()
                    .eq(OutboxEventEntity::getEventId, eventIdToCleanup));
        }
        if (requestKeyToCleanup != null) {
            operationRequestMapper.delete(new LambdaQueryWrapper<OperationRequestEntity>()
                    .eq(OperationRequestEntity::getRequestKey, requestKeyToCleanup));
        }
    }

    /** 首次发布应新建一条 {@code PENDING} 行，负载序列化为 JSON。 */
    @Test
    void publish_shouldInsertPendingRow_whenEventIdNotExists() {
        String eventId = "IT-OUTBOX-PUBLISH-" + UUID.randomUUID();
        eventIdToCleanup = eventId;

        OutboxEventEntity published = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of("foo", "bar"));

        assertThat(published.getId()).isNotNull();
        assertThat(published.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(published.getAttemptCount()).isZero();
        assertThat(published.getPayload()).contains("foo").contains("bar");

        OutboxEventEntity row = outboxEventMapper.selectById(published.getId());
        assertThat(row).isNotNull();
        assertThat(row.getEventId()).isEqualTo(eventId);
        assertThat(row.getAggregateId()).isEqualTo("instance-1");
    }

    /** 同一 eventId 重复发布应复用已存在行，不新插入。 */
    @Test
    void publish_shouldReuseExistingRow_whenEventIdAlreadyExists() {
        String eventId = "IT-OUTBOX-DUP-" + UUID.randomUUID();
        eventIdToCleanup = eventId;

        OutboxEventEntity first = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of("attempt", 1));
        OutboxEventEntity second = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of("attempt", 2));

        assertThat(second.getId()).isEqualTo(first.getId());
        List<OutboxEventEntity> rows = outboxEventMapper.selectList(
                new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventId));
        assertThat(rows).hasSize(1);
    }

    /**
     * 同事务写入验证（tasks.md 7.1 核心要求）：外层事务先做一次真实业务写入
     * （{@code tab_wf_operation_request}），再调用 {@code publish}，随后手动抛异常触发整个
     * 外层事务回滚；断言两条记录最终都查不到，证明 {@code publish} 与调用方处于同一个物理
     * 事务/连接，而不是各自独立提交。
     */
    @Test
    void publish_shouldRollbackTogetherWithCallerTransaction_whenCallerTransactionRollsBack() {
        String eventId = "IT-OUTBOX-ROLLBACK-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        String requestKey = "IT-OUTBOX-ROLLBACK-REQ-" + UUID.randomUUID();
        requestKeyToCleanup = requestKey;

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        RuntimeException simulatedFailure = new RuntimeException("模拟业务写操作之后的失败，触发整个事务回滚");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            LocalDateTime now = LocalDateTime.now();
            operationRequestMapper.insert(OperationRequestEntity.builder()
                    .requestKey(requestKey)
                    .operation(ApprovalAction.APPROVE)
                    .status(OperationRequestStatus.SUCCESS)
                    .createBy("test").createTime(now).updateBy("test").updateTime(now)
                    .build());

            outboxEventService.publish(eventId, "instance-rollback", 1L, TEST_EVENT_TYPE, Map.of("k", "v"));

            throw simulatedFailure;
        })).isSameAs(simulatedFailure);

        assertThat(outboxEventMapper.selectOne(
                new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getEventId, eventId)))
                .as("外层事务回滚后，同事务写入的 Outbox 事件行不应残留")
                .isNull();
        assertThat(operationRequestMapper.selectOne(
                new LambdaQueryWrapper<OperationRequestEntity>().eq(OperationRequestEntity::getRequestKey, requestKey)))
                .as("外层事务回滚后，同事务的业务写操作本身也不应残留")
                .isNull();
    }

    /** 到期的 PENDING 事件应能被领取，领取后状态变为 LEASED 并携带新租约 token。 */
    @Test
    void claimDueEvents_shouldClaimDuePendingEvent() {
        String eventId = "IT-OUTBOX-CLAIM-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());

        List<OutboxEventEntity> claimed = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .toList();

        assertThat(claimed).hasSize(1);
        OutboxEventEntity claimedEvent = claimed.get(0);
        assertThat(claimedEvent.getStatus()).isEqualTo(OutboxEventStatus.LEASED);
        assertThat(claimedEvent.getLeaseToken()).isNotBlank();
        assertThat(claimedEvent.getLeaseUntil()).isAfter(LocalDateTime.now());

        OutboxEventEntity row = outboxEventMapper.selectById(claimedEvent.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.LEASED);
        assertThat(row.getLeaseToken()).isEqualTo(claimedEvent.getLeaseToken());
    }

    /** 未到期的事件（next_retry_time 在未来）不应被领取。 */
    @Test
    void claimDueEvents_shouldNotClaim_whenNotYetDue() {
        String eventId = "IT-OUTBOX-NOTDUE-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        OutboxEventEntity published = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, published.getId())
                .set(OutboxEventEntity::getNextRetryTime, LocalDateTime.now().plusHours(1)));

        List<OutboxEventEntity> claimed = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .toList();

        assertThat(claimed).isEmpty();
    }

    /** markSucceeded 用正确的 leaseToken 应成功转为终态 SUCCEEDED，之后不再被到期扫描选中。 */
    @Test
    void markSucceeded_shouldTransitionToSucceeded_andExcludedFromFutureScans() {
        String eventId = "IT-OUTBOX-SUCCESS-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        OutboxEventEntity claimedEvent = claimSingle(eventId);

        boolean result = outboxEventService.markSucceeded(claimedEvent);

        assertThat(result).isTrue();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedEvent.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.SUCCEEDED);
        assertThat(row.getLeaseToken()).isNull();
        assertThat(row.getLeaseUntil()).isNull();

        List<OutboxEventEntity> reScanned = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .toList();
        assertThat(reScanned).isEmpty();
    }

    /** markFailed 未达上限时应转回 PENDING、attemptCount 递增、nextRetryTime 按退避计算推迟。 */
    @Test
    void markFailed_shouldRetryWithBackoff_whenAttemptCountBelowMax() {
        String eventId = "IT-OUTBOX-RETRY-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        OutboxEventEntity claimedEvent = claimSingle(eventId);

        boolean result = outboxEventService.markFailed(claimedEvent);

        assertThat(result).isTrue();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedEvent.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(1);
        assertThat(row.getLeaseToken()).isNull();
        assertThat(row.getNextRetryTime()).isAfter(LocalDateTime.now());
    }

    /** markFailed 达到最大尝试次数后应转终态 FAILED（人工处理队列），不再被到期扫描选中。 */
    @Test
    void markFailed_shouldMoveToFailedTerminalState_whenMaxAttemptsReached() {
        String eventId = "IT-OUTBOX-DEAD-ATTEMPTS-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        OutboxEventEntity published = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        // 默认 rbac.workflow.outbox.max-attempts=8：直接把 attempt_count 预置为 7，
        // 使本次失败成为第 8 次尝试，命中最大次数上限，无需真实循环失败 8 次。
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, published.getId())
                .set(OutboxEventEntity::getAttemptCount, 7));
        OutboxEventEntity claimedEvent = claimSingle(eventId);

        boolean result = outboxEventService.markFailed(claimedEvent);

        assertThat(result).isTrue();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedEvent.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(row.getAttemptCount()).isEqualTo(8);

        List<OutboxEventEntity> reScanned = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .toList();
        assertThat(reScanned).isEmpty();
    }

    /** markFailed 命中最大存活时长（即便 attemptCount 很低）同样应转终态 FAILED。 */
    @Test
    void markFailed_shouldMoveToFailedTerminalState_whenMaxAgeReached() {
        String eventId = "IT-OUTBOX-DEAD-AGE-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        OutboxEventEntity published = outboxEventService.publish(
                eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        // 默认 rbac.workflow.outbox.max-age-hours=24：把 create_time 回拨到 25 小时前，
        // 模拟事件已超龄，即使 attempt_count 仍然是 0 也应直接转终态。
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, published.getId())
                .set(OutboxEventEntity::getCreateTime, LocalDateTime.now().minusHours(25)));
        OutboxEventEntity claimedEvent = claimSingle(eventId);
        // claimSingle 内部使用的实体来自 selectDueEvents 的快照，已经携带回拨后的 createTime。

        boolean result = outboxEventService.markFailed(claimedEvent);

        assertThat(result).isTrue();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedEvent.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(row.getAttemptCount()).isEqualTo(1);
    }

    /** 领取给定 eventId 对应的到期事件，断言恰好命中一条并返回。 */
    private OutboxEventEntity claimSingle(String eventId) {
        List<OutboxEventEntity> claimed = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .toList();
        assertThat(claimed).hasSize(1);
        return claimed.get(0);
    }
}
