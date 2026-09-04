package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.entity.BusinessLockEntity;
import cn.nihility.rbac.workflow.mapper.BusinessLockMapper;
import cn.nihility.rbac.workflow.service.BusinessLockService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * {@link BusinessLockServiceImpl} 真实数据库集成测试（production-approval-lifecycle change
 * design.md 第8节，tasks.md 6.2）。{@link BusinessLockService#acquire}/{@link
 * BusinessLockService#release} 各自都是独立的 {@code @Transactional(REQUIRED)} 方法，本类
 * 直接通过注入的代理 Bean 调用（不在外层再包一层测试事务），每次调用即为一次真实提交的物理
 * 事务，能够真实验证"占用状态跨事务持久化"以及并发场景下的互斥语义，而不是同一事务内的
 * 临时可见性。
 */
@SpringBootTest
class BusinessLockServiceImplTest {

    /** 测试专用业务对象类型前缀，避免与真实业务数据混淆，测试结束后按该前缀清理。 */
    private static final String TEST_BIZ_TYPE = "TEST_LOCK_6_2";

    /** 业务活动申请锁服务。 */
    @Autowired
    private BusinessLockService businessLockService;

    /** 业务活动申请锁数据访问接口，用于测试清理。 */
    @Autowired
    private BusinessLockMapper businessLockMapper;

    /** 测试用目标 key 序号生成器。 */
    private static final AtomicLong TARGET_KEY_SEQ = new AtomicLong();

    /**
     * 每个测试方法结束后删除本类插入的全部测试锁行，避免在共享开发库中残留
     * （本类没有测试事务自动回滚兜底）。
     */
    @AfterEach
    void cleanup() {
        businessLockMapper.delete(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE));
    }

    /** 生成一个本测试类专用、不与其他测试冲突的 targetKey。 */
    private String nextTargetKey() {
        return "target-" + TARGET_KEY_SEQ.incrementAndGet();
    }

    /** 首次获取应成功新建锁行并占用。 */
    @Test
    void acquire_shouldCreateAndOccupyLockRow_whenNotExists() {
        String targetKey = nextTargetKey();

        businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 1L, 100L);

        BusinessLockEntity row = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE)
                .eq(BusinessLockEntity::getTargetKey, targetKey));
        assertThat(row).isNotNull();
        assertThat(row.getActiveRequestId()).isEqualTo(1L);
    }

    /** 同一目标连续两次获取：第二次应被拒绝，锁行仍归第一次占用者持有。 */
    @Test
    void acquire_shouldRejectSecondCall_whenAlreadyOccupied() {
        String targetKey = nextTargetKey();

        businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 1L, 100L);

        assertThatThrownBy(() -> businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该目标已有进行中的审批");

        BusinessLockEntity row = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE)
                .eq(BusinessLockEntity::getTargetKey, targetKey));
        assertThat(row.getActiveRequestId()).isEqualTo(1L);
    }

    /** 释放后同一目标应能被新的申请重新占用，锁行本身被复用而不是重新插入。 */
    @Test
    void acquire_shouldSucceed_afterRelease() {
        String targetKey = nextTargetKey();

        businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 1L, 100L);
        businessLockService.release(TEST_BIZ_TYPE, targetKey, 1L, 100L);
        businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 2L, 100L);

        BusinessLockEntity row = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE)
                .eq(BusinessLockEntity::getTargetKey, targetKey));
        assertThat(row.getActiveRequestId()).isEqualTo(2L);
        assertThat(row.getRevision()).isGreaterThan(1L);
    }

    /** 释放时校验占用者：非当前占用者发起的释放应静默跳过，不影响真正占用者的锁状态。 */
    @Test
    void release_shouldBeNoop_whenNotCurrentHolder() {
        String targetKey = nextTargetKey();
        businessLockService.acquire(TEST_BIZ_TYPE, targetKey, 1L, 100L);

        businessLockService.release(TEST_BIZ_TYPE, targetKey, 999L, 100L);

        BusinessLockEntity row = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE)
                .eq(BusinessLockEntity::getTargetKey, targetKey));
        assertThat(row.getActiveRequestId()).isEqualTo(1L);
    }

    /**
     * 真实并发测试：两个线程几乎同时对同一目标发起 {@code acquire}，只有一个应该成功，另一个
     * 必须收到 {@code BusinessException} 拒绝，不允许出现"两个都成功"这种破坏互斥语义的结果
     * （design.md 第8节"同一业务目标同时间只有一条活动变更"）。
     */
    @Test
    void acquire_concurrentCalls_onlyOneShouldSucceed() throws Exception {
        String targetKey = nextTargetKey();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        try {
            List<CompletableFuture<Void>> futures = List.of(
                    submitAcquireAttempt(executor, barrier, targetKey, 11L, successCount, conflictCount),
                    submitAcquireAttempt(executor, barrier, targetKey, 12L, successCount, conflictCount));
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        BusinessLockEntity row = businessLockMapper.selectOne(new LambdaQueryWrapper<BusinessLockEntity>()
                .eq(BusinessLockEntity::getBizType, TEST_BIZ_TYPE)
                .eq(BusinessLockEntity::getTargetKey, targetKey));
        assertThat(row.getActiveRequestId()).isIn(11L, 12L);
    }

    /**
     * 提交一次异步的 {@code acquire} 尝试：两个线程都先在栅栏处等待，尽量让两次调用真实地
     * 几乎同时发起，再各自统计成功/冲突结果。
     */
    private CompletableFuture<Void> submitAcquireAttempt(
            ExecutorService executor,
            CyclicBarrier barrier,
            String targetKey,
            Long requestId,
            AtomicInteger successCount,
            AtomicInteger conflictCount) {
        return CompletableFuture.runAsync(() -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                businessLockService.acquire(TEST_BIZ_TYPE, targetKey, requestId, 100L);
                successCount.incrementAndGet();
            } catch (BusinessException ex) {
                conflictCount.incrementAndGet();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }, executor);
    }
}
