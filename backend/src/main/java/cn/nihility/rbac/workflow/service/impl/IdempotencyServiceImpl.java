package cn.nihility.rbac.workflow.service.impl;

import cn.nihility.rbac.workflow.constant.OperationRequestStatus;
import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import cn.nihility.rbac.workflow.mapper.OperationRequestMapper;
import cn.nihility.rbac.workflow.service.IdempotencyService;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作幂等服务实现：在实际执行业务逻辑前先插入幂等标记行，插入语句命中
 * {@code request_key} 唯一约束冲突即视为重复请求直接短路，不执行 {@code action}
 * （workflow-approval-engine change design.md Decision 6）。插入与 {@code action}
 * 内部写操作依赖调用方处于同一个数据库事务：一旦 {@code action} 抛出异常导致事务整体回滚，
 * 幂等标记行随之撤销，同一幂等键可以安全重试，因此不需要额外维护 {@code FAILED} 状态的
 * 清理逻辑。
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
            Supplier<T> action) {
        if (!StringUtils.hasText(requestKey)) {
            return action.get();
        }
        LocalDateTime now = LocalDateTime.now();
        String operatorText = operatorId == null ? null : operatorId.toString();
        OperationRequestEntity record = OperationRequestEntity.builder()
                .requestKey(requestKey)
                .taskId(taskId)
                .operatorId(operatorId)
                .operation(operation)
                .status(OperationRequestStatus.SUCCESS)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build();
        try {
            operationRequestMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            log.info("幂等键 {} 已处理过操作 {}，本次请求短路跳过", requestKey, operation);
            return null;
        }
        return action.get();
    }
}
