package cn.nihility.rbac.workflow.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.WorkflowErrorCode;
import cn.nihility.rbac.workflow.dslv2.util.DigestUtils;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * {@link IdempotencyServiceImpl} 单元测试（workflow-approval-engine change tasks.md 6.1；
 * payload 摘要比对相关场景为 production-approval-lifecycle change tasks.md 6.2 新增）。命中
 * 唯一键冲突后的重读固定在当前事务内完成（{@code SELECT ... FOR UPDATE}），不涉及额外的事务
 * 管理器依赖，单元测试无需 mock {@code PlatformTransactionManager}（见
 * {@link IdempotencyServiceImpl} 类注释：曾尝试过 {@code REQUIRES_NEW} 新事务方案，被真实测试
 * 证伪后改为当前实现）。
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

        String result = service.executeOnce(null, ApprovalAction.APPROVE, 1L, 2L, "payload", () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(counter.get()).isEqualTo(1);
        verify(operationRequestMapper, never()).insert(any(OperationRequestEntity.class));
    }

    /** 首次请求应落库并执行一次，且落库时写入了 payload 摘要与结果快照。 */
    @Test
    void executeOnce_shouldInsertMarkerAndRunOnFirstRequest() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        AtomicInteger counter = new AtomicInteger();

        String result = service.executeOnce("req-1", ApprovalAction.APPROVE, 1L, 2L, "payload", () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(counter.get()).isEqualTo(1);
        verify(operationRequestMapper, times(1)).insert(any(OperationRequestEntity.class));
        verify(operationRequestMapper, times(1)).updateById(any(OperationRequestEntity.class));
    }

    /** 重复提交同一幂等键、且 payload 与首次一致：应短路跳过，不重复执行，直接返回原结果。 */
    @Test
    void executeOnce_shouldReturnOriginalResultOnSamePayloadRetry() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        doThrow(new DuplicateKeyException("duplicate")).when(operationRequestMapper)
                .insert(any(OperationRequestEntity.class));
        OperationRequestEntity existing = OperationRequestEntity.builder()
                .requestKey("req-1")
                .payloadHash(sha256Of("payload"))
                .resultText("\"done\"")
                .build();
        when(operationRequestMapper.selectOne(any())).thenReturn(existing);
        AtomicInteger counter = new AtomicInteger();

        String result = service.executeOnce("req-1", ApprovalAction.APPROVE, 1L, 2L, "payload", () -> {
            counter.incrementAndGet();
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(counter.get()).isZero();
    }

    /** 重复提交同一幂等键、但 payload 与首次不一致：应拒绝并报 IDEMPOTENCY_CONFLICT，不执行。 */
    @Test
    void executeOnce_shouldRejectWhenPayloadDiffersOnSameKey() {
        service = new IdempotencyServiceImpl(operationRequestMapper);
        doThrow(new DuplicateKeyException("duplicate")).when(operationRequestMapper)
                .insert(any(OperationRequestEntity.class));
        OperationRequestEntity existing = OperationRequestEntity.builder()
                .requestKey("req-1")
                .payloadHash(sha256Of("first-payload"))
                .resultText("\"done\"")
                .build();
        when(operationRequestMapper.selectOne(any())).thenReturn(existing);
        AtomicInteger counter = new AtomicInteger();

        assertThatThrownBy(() -> service.executeOnce("req-1", ApprovalAction.APPROVE, 1L, 2L, "second-payload", () -> {
            counter.incrementAndGet();
            return "done";
        }))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo(WorkflowErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(counter.get()).isZero();
    }

    /** 复用生产代码一致的摘要算法计算期望值，避免测试对具体哈希实现细节做重复断言。 */
    private String sha256Of(String payload) {
        return DigestUtils.sha256(JacksonUtils.toJson(payload));
    }
}
