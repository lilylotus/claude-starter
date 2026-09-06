package cn.nihility.rbac.workflow.outbox.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.outbox.constant.OutboxEventStatus;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import cn.nihility.rbac.workflow.outbox.mapper.OutboxEventMapper;
import cn.nihility.rbac.workflow.outbox.service.OutboxEventService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link OutboxEventServiceImpl} 真实并发集成测试（production-approval-lifecycle change
 * design.md 第10节"MySQL 5.7 领取方式...没有抢到则跳过""租约 token 用于完成/续期
 * CAS，防旧 worker 覆盖新 worker"，tasks.md 7.1）。本类不使用测试专用回滚事务：
 * {@link OutboxEventService} 各方法自身已声明 {@code @Transactional(REQUIRED)}，测试线程
 * 直接调用注入的代理 Bean 即可各自拥有独立的真实物理事务，能够真实验证并发场景下的 CAS
 * 互斥语义（与 {@code TaskClaimConcurrencyIntegrationTest}/{@code BusinessLockServiceImplTest}
 * 同样的理由）。
 */
@SpringBootTest
class OutboxEventServiceImplConcurrencyIntegrationTest {

    /** 测试专用事件类型，避免与真实业务事件混淆。 */
    private static final String TEST_EVENT_TYPE = "TEST_EVENT_TYPE";

    /** Outbox 事件生产/领取服务。 */
    @Autowired
    private OutboxEventService outboxEventService;

    /** Outbox 事件数据访问接口，用于测试断言、清理与人为制造租约过期的测试夹具操作。 */
    @Autowired
    private OutboxEventMapper outboxEventMapper;

    /** 本方法使用过的 eventId，测试结束后清理，避免在共享开发库残留。 */
    private String eventIdToCleanup;

    @AfterEach
    void cleanup() {
        if (eventIdToCleanup != null) {
            outboxEventMapper.delete(new LambdaQueryWrapper<OutboxEventEntity>()
                    .eq(OutboxEventEntity::getEventId, eventIdToCleanup));
        }
    }

    /**
     * 两个 worker 几乎同时对同一条到期事件发起领取：真实并发下必须恰好一个成功拿到租约，
     * 另一个必须领取为空（不允许两个都成功，那意味着 CAS 条件更新失效）。连续跑一次即可
     * 稳定复现（数据库行锁级别的互斥，不依赖偶然的时间窗口）。
     */
    @Test
    void claimDueEvents_concurrentClaims_onlyOneShouldSucceed() throws Exception {
        String eventId = "IT-OUTBOX-CONCURRENT-CLAIM-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<OutboxEventEntity> claimAttempt = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return outboxEventService.claimDueEvents(50).stream()
                    .filter(e -> eventId.equals(e.getEventId()))
                    .findFirst()
                    .orElse(null);
        };

        Future<OutboxEventEntity> future1 = executor.submit(claimAttempt);
        Future<OutboxEventEntity> future2 = executor.submit(claimAttempt);
        OutboxEventEntity result1 = future1.get(30, TimeUnit.SECONDS);
        OutboxEventEntity result2 = future2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        long successCount = Stream.of(result1, result2).filter(Objects::nonNull).count();
        assertThat(successCount).as("两个并发领取中应恰好一个真正抢到租约").isEqualTo(1);

        OutboxEventEntity winner = result1 != null ? result1 : result2;
        OutboxEventEntity row = outboxEventMapper.selectById(winner.getId());
        assertThat(row.getStatus()).isEqualTo(OutboxEventStatus.LEASED);
        assertThat(row.getLeaseToken()).isEqualTo(winner.getLeaseToken());
    }

    /**
     * fencing 验证之一：worker A 拿到租约后，系统认为其租约已过期（此处用直接更新
     * {@code lease_until} 到过去模拟真实的租约超时流逝），worker B 重新领取成功、持有新
     * token；随后 A 才迟来地尝试用自己手里的旧 token 上报失败，必须被拒绝
     * （{@link OutboxEventService#markFailed} 返回 {@code false}），不能把 B 已经在处理的
     * 记录状态搅乱。
     */
    @Test
    void markFailed_shouldBeRejected_whenLeaseAlreadyReclaimedByAnotherWorker() {
        String eventId = "IT-OUTBOX-FENCE-FAILED-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        OutboxEventEntity[] claims = claimTwiceAfterSimulatedExpiry(eventId);
        OutboxEventEntity claimedByA = claims[0];
        OutboxEventEntity claimedByB = claims[1];

        boolean staleResult = outboxEventService.markFailed(claimedByA);

        assertThat(staleResult).as("A 持有的租约 token 已过期失效，迟来的失败上报必须被拒绝").isFalse();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedByA.getId());
        assertThat(row.getStatus()).as("记录状态不应被 A 的过期上报搅乱，仍应是 B 持有租约中")
                .isEqualTo(OutboxEventStatus.LEASED);
        assertThat(row.getLeaseToken()).isEqualTo(claimedByB.getLeaseToken());
        assertThat(row.getAttemptCount()).as("A 的失败上报不应生效，attemptCount 不应被递增").isZero();
    }

    /**
     * fencing 验证之二：与 {@link #markFailed_shouldBeRejected_whenLeaseAlreadyReclaimedByAnotherWorker}
     * 同样的场景，改为 A 迟来地尝试用旧 token 上报成功，同样必须被拒绝，不能把 B 正在持有的
     * 租约错误地标记为终态成功。
     */
    @Test
    void markSucceeded_shouldBeRejected_whenLeaseAlreadyReclaimedByAnotherWorker() {
        String eventId = "IT-OUTBOX-FENCE-SUCCESS-" + UUID.randomUUID();
        eventIdToCleanup = eventId;
        OutboxEventEntity[] claims = claimTwiceAfterSimulatedExpiry(eventId);
        OutboxEventEntity claimedByA = claims[0];
        OutboxEventEntity claimedByB = claims[1];

        boolean staleResult = outboxEventService.markSucceeded(claimedByA);

        assertThat(staleResult).as("A 持有的租约 token 已过期失效，迟来的成功上报必须被拒绝").isFalse();
        OutboxEventEntity row = outboxEventMapper.selectById(claimedByA.getId());
        assertThat(row.getStatus()).as("记录状态不应被 A 的过期上报错误地标记为终态成功，仍应是 B 持有租约中")
                .isEqualTo(OutboxEventStatus.LEASED);
        assertThat(row.getLeaseToken()).isEqualTo(claimedByB.getLeaseToken());
    }

    /**
     * 发布一条事件后由 worker A 领取，随后人为把该行的 {@code lease_until} 直接改到过去
     * （模拟真实的租约超时流逝，而不是等待配置的租约时长），再让 worker B 重新扫描领取，
     * 断言两次领取拿到互不相同的 token。
     *
     * @param eventId 本次场景使用的事件 id
     * @return 长度为 2 的数组：下标 0 为 A 的领取快照，下标 1 为 B 的领取快照
     */
    private OutboxEventEntity[] claimTwiceAfterSimulatedExpiry(String eventId) {
        outboxEventService.publish(eventId, "instance-1", 1L, TEST_EVENT_TYPE, Map.of());
        OutboxEventEntity claimedByA = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();

        // 测试夹具操作：直接把租约到期时间改到过去，模拟"系统认为其租约已过期"，不经过
        // CAS（这是测试环境模拟时间流逝的手段，不是生产代码路径）。
        // 减 2 秒并截断到整秒（而不是减 1 秒不截断）：next_retry_time/lease_until 列不带小数秒
        // 精度，MySQL 对超出列精度的小数秒按四舍五入写入，1 秒的余量在四舍五入进位下可能不足以
        // 保证写入后仍严格早于随后立即发起的领取查询所用的 now（同一 OutboxEventServiceImpl
        // 类注释记录过的同一根因），2 秒 + 截断整秒双重保险，避免测试本身出现偶发抖动。
        outboxEventMapper.update(null, new LambdaUpdateWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getId, claimedByA.getId())
                .set(OutboxEventEntity::getLeaseUntil, LocalDateTime.now().minusSeconds(2).withNano(0)));

        OutboxEventEntity claimedByB = outboxEventService.claimDueEvents(50).stream()
                .filter(e -> eventId.equals(e.getEventId()))
                .findFirst()
                .orElseThrow();

        assertThat(claimedByB.getLeaseToken()).isNotEqualTo(claimedByA.getLeaseToken());
        return new OutboxEventEntity[] {claimedByA, claimedByB};
    }
}
