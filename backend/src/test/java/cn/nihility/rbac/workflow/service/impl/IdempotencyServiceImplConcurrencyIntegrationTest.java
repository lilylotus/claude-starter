package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.WorkflowErrorCode;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link IdempotencyServiceImpl} 真实数据库并发集成测试（production-approval-lifecycle change
 * design.md 第8节，tasks.md 6.2）。验证唯一键冲突分支在真实并发下的行为：两个几乎同时提交的
 * 物理事务竞争同一幂等键，只有一个真正执行业务逻辑，另一个通过独立的 {@code REQUIRES_NEW}
 * 新事务重读已提交结果，正常返回而不是抛出未处理的异常。
 * <p>
 * 本类不使用测试专用回滚事务：每个线程各自通过 {@link TransactionTemplate} 显式开启、真实
 * 提交自己的物理事务，模拟两个真实并发请求各自独立的事务边界（与
 * {@code EngineBusinessSharedTransactionIntegrationTest} 同样的理由——测试事务会把被测代码
 * 内部的 {@code REQUIRES_NEW} 语义掩盖掉）。
 */
@SpringBootTest
class IdempotencyServiceImplConcurrencyIntegrationTest {

    /** 操作幂等服务。 */
    @Autowired
    private IdempotencyService idempotencyService;

    /** 事务管理器，用于测试线程各自显式开启物理事务。 */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 操作幂等记录数据访问接口，用于测试清理。 */
    @Autowired
    private OperationRequestMapper operationRequestMapper;

    /** 本方法使用过的幂等键，测试结束后清理，避免在共享开发库残留。 */
    private String requestKeyToCleanup;

    @AfterEach
    void cleanup() {
        if (requestKeyToCleanup != null) {
            operationRequestMapper.delete(new LambdaQueryWrapper<OperationRequestEntity>()
                    .eq(OperationRequestEntity::getRequestKey, requestKeyToCleanup));
        }
    }

    /**
     * 两个线程用同一幂等键、同一 payload 几乎同时提交：只有一个应该真正执行一次业务逻辑，
     * 另一个应该拿到与前者完全一致的返回结果，不能抛出未处理的异常，也不能各自都执行一次
     * （否则说明幂等保护失效）。
     */
    @Test
    void executeOnce_concurrentSamePayload_onlyOneExecutesBusinessLogic() throws Exception {
        String requestKey = "IT-IDEMP-CONCURRENT-" + UUID.randomUUID();
        requestKeyToCleanup = requestKey;
        String payload = "same-payload";
        AtomicInteger executionCount = new AtomicInteger();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<String> task = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
            return transactionTemplate.execute(status -> idempotencyService.executeOnce(
                    requestKey, ApprovalAction.APPROVE, 1L, null, payload, () -> {
                        executionCount.incrementAndGet();
                        return "result-" + Thread.currentThread().threadId();
                    }));
        };

        Future<String> future1 = executor.submit(task);
        Future<String> future2 = executor.submit(task);
        String result1 = future1.get(30, TimeUnit.SECONDS);
        String result2 = future2.get(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(executionCount.get()).isEqualTo(1);
        assertThat(result1).isNotBlank();
        assertThat(result2).isEqualTo(result1);
    }

    /**
     * 同一幂等键先后提交了不同 payload：第二次应拒绝并报 {@code IDEMPOTENCY_CONFLICT}，不能
     * 被当作无害重试直接放行执行新内容。
     */
    @Test
    void executeOnce_shouldRejectConflict_whenSecondCallUsesDifferentPayload() {
        String requestKey = "IT-IDEMP-CONFLICT-" + UUID.randomUUID();
        requestKeyToCleanup = requestKey;

        String first = idempotencyService.executeOnce(
                requestKey, ApprovalAction.APPROVE, 1L, null, "payload-A", () -> "first-result");
        assertThat(first).isEqualTo("first-result");

        assertThatThrownBy(() -> idempotencyService.executeOnce(
                requestKey, ApprovalAction.APPROVE, 1L, null, "payload-B", () -> "second-result"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(WorkflowErrorCode.IDEMPOTENCY_CONFLICT));
    }
}
