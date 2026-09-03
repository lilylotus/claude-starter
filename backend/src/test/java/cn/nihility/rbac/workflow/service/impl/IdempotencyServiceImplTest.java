package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link IdempotencyServiceImpl} 单元测试（workflow-approval-engine change tasks.md 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest {

    @Mock
    private OperationRequestMapper operationRequestMapper;

    private IdempotencyServiceImpl service;

    /** 未携带幂等键时应直接执行且不落库。 */
    @Test
    void executeOnce_shouldRunDirectlyWhenKeyBlank() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        AtomicInteger counter = new AtomicInteger();

        String result = service.executeOnce(null, ApprovalAction.APPROVE, 1L, 2L, () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(counter.get()).isEqualTo(1);
        verify(operationRequestMapper, never()).insert(any(OperationRequestEntity.class));
    }

    /** 首次请求应落库并执行一次。 */
    @Test
    void executeOnce_shouldInsertMarkerAndRunOnFirstRequest() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        AtomicInteger counter = new AtomicInteger();

        String result = service.executeOnce("req-1", ApprovalAction.APPROVE, 1L, 2L, () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(counter.get()).isEqualTo(1);
        verify(operationRequestMapper, times(1)).insert(any(OperationRequestEntity.class));
    }

    /** 重复提交同一幂等键：插入命中唯一键冲突时应短路跳过，不重复执行。 */
    @Test
    void executeOnce_shouldSkipExecutionOnDuplicateKey() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        doThrow(new DuplicateKeyException("duplicate")).when(operationRequestMapper)
                .insert(any(OperationRequestEntity.class));
        AtomicInteger counter = new AtomicInteger();

        String result = service.executeOnce("req-1", ApprovalAction.APPROVE, 1L, 2L, () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isNull();
        assertThat(counter.get()).isZero();
    }
}
