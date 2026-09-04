package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.OperationRequestStatus;
import cn.nihility.rbac.workflow.constant.WorkflowErrorCode;
import cn.nihility.rbac.workflow.dslv2.util.DigestUtils;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作幂等服务实现：在实际执行业务逻辑前先插入幂等标记行，插入语句命中
 * {@code request_key} 唯一约束冲突即视为重复请求。此前实现只按"存在即跳过"处理，本轮
 * （production-approval-lifecycle change design.md 第8节，tasks.md 6.2）补齐 payload 摘要
 * 比对：命中冲突后不再无条件短路，而是重新读取首次落库的
 * {@code payload_hash}——一致则判定为真实重试，直接返回首次执行结果；不一致则抛出
 * {@code IDEMPOTENCY_CONFLICT} 业务异常，避免"复用同一 X-Request-Id 却提交了不同内容"被
 * 误判为无害重试而静默丢弃。
 * <p>
 * 插入与 {@code action} 内部写操作依赖调用方处于同一个数据库事务：一旦 {@code action}
 * 抛出异常导致事务整体回滚，幂等标记行随之撤销，同一幂等键可以安全重试。唯一键冲突本身
 * （MySQL InnoDB 下单条语句的约束冲突不会让当前事务进入不可用状态，这一点与 Postgres 不同）
 * 在本类内被捕获后不会向上传播，调用方所在事务不会因此被标记为 rollback-only。
 * <p>
 * 重读已提交结果这一步**没有**采用 {@code TransactionTemplate}
 * + {@code PROPAGATION_REQUIRES_NEW} 切新事务（design.md 字面描述的示例写法之一）：真实测试
 * 发现该写法会破坏一个已有且合理的调用模式——同一个业务事务内先后两次调用同一幂等操作（如
 * {@code WorkflowV2ReassignmentService} 的既有测试用同一 {@code requestKey} 连续调用两次
 * {@code reassign} 验证幂等；这类调用天然发生在同一个 {@code @Transactional} 方法/同一个
 * 测试事务内，第一次插入尚未提交）。{@code REQUIRES_NEW} 会挂起当前事务、用另一个物理连接
 * 查询，而 MySQL 的已提交读隔离语义下这个新事务天然看不到当前事务里"已插入但未提交"的那一行，
 * 导致误判为"记录不存在"。改为在**当前事务内**对命中冲突的这一行补一次
 * {@code SELECT ... FOR UPDATE} 加锁读取——不同于普通只读 SELECT 受 REPEATABLE READ
 * 快照限制，加锁读取总是读最新已提交版本，因此同时能正确处理"同一事务内的自身插入"（本就
 * 对自己可见）与"另一个事务已提交的插入"（加锁读取绕开快照直接读最新）两种场景，不需要额外
 * 事务上下文切换，与 {@code BusinessLockServiceImpl} 处理同类冲突的方式保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyServiceImpl implements IdempotencyService {

    /** 操作幂等记录数据访问接口。 */
    private final OperationRequestMapper operationRequestMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T executeOnce(
            String requestKey,
            String operation,
            Long operatorId,
            Long taskId,
            Object payload,
            Supplier<T> action) {
        if (!StringUtils.hasText(requestKey)) {
            return action.get();
        }
        String payloadHash = DigestUtils.sha256(JacksonUtils.toJson(payload));
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        OperationRequestEntity record = OperationRequestEntity.builder()
                .requestKey(requestKey)
                .taskId(taskId)
                .operatorId(operatorId)
                .operation(operation)
                .status(OperationRequestStatus.SUCCESS)
                .payloadHash(payloadHash)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build();
        try {
            operationRequestMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.info("幂等键 {} 已存在操作记录，重新读取已提交结果比对 payload：operation={}", requestKey, operation);
            return reuseCommittedResult(requestKey, operation, payloadHash);
        }

        T result = action.get();
        record.setResultText(JacksonUtils.toJson(result));
        record.setUpdateTime(LocalDateTime.now());
        operationRequestMapper.updateById(record);
        return result;
    }

    /**
     * 在当前事务内对命中唯一键冲突的这一行补一次 {@code SELECT ... FOR UPDATE} 加锁读取
     * （不切新事务，理由见类注释）。命中同 payload 摘要视为真实重试直接返回原结果；命中不同
     * 摘要判定为幂等冲突，拒绝处理。
     */
    private <T> T reuseCommittedResult(String requestKey, String operation, String payloadHash) {
        OperationRequestEntity existing = operationRequestMapper.selectOne(
                new LambdaQueryWrapper<OperationRequestEntity>()
                        .eq(OperationRequestEntity::getRequestKey, requestKey)
                        .last("LIMIT 1 FOR UPDATE"));
        if (existing == null) {
            // 理论上不会发生：刚刚触发唯一键冲突说明该 request_key 必然已存在于数据库；
            // 保留兜底异常而不是静默继续，避免掩盖真正的数据不一致问题。
            throw new BusinessException("幂等记录查询异常，请使用新的请求标识重试");
        }
        if (!Objects.equals(existing.getPayloadHash(), payloadHash)) {
            throw new BusinessException(
                    WorkflowErrorCode.IDEMPOTENCY_CONFLICT,
                    "幂等键 " + requestKey + " 已提交过不同内容的请求，拒绝处理（IDEMPOTENCY_CONFLICT）");
        }
        log.info("幂等键 {} 命中同 payload 重试，直接返回操作 {} 的原结果", requestKey, operation);
        return deserializeResult(existing.getResultText());
    }

    /**
     * 反序列化首次执行落库的结果快照；空文本（历史行未落过结果，或原结果本就是 JSON
     * {@code null}）统一返回 {@code null}。
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeResult(String resultText) {
        if (!StringUtils.hasText(resultText) || "null".equals(resultText)) {
            return null;
        }
        return (T) JacksonUtils.toObj(resultText, Object.class);
    }
}
