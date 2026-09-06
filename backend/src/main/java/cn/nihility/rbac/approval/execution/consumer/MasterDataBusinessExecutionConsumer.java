package cn.nihility.rbac.approval.execution.consumer;

import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.execution.MasterDataOperationExecutor;
import cn.nihility.rbac.approval.execution.dto.BusinessExecutionTriggerPayload;
import cn.nihility.rbac.approval.execution.entity.BusinessExecutionEntity;
import cn.nihility.rbac.approval.execution.mapper.BusinessExecutionMapper;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.workflow.constant.ExecutionStatus;
import cn.nihility.rbac.workflow.outbox.constant.OutboxEventType;
import cn.nihility.rbac.workflow.outbox.consumer.OutboxEventConsumer;
import cn.nihility.rbac.workflow.outbox.entity.OutboxEventEntity;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ORG/USER 主数据变更的可靠异步执行消费者（production-approval-lifecycle change design.md
 * 第9/10/203行，tasks.md 7.3）。
 * <p>
 * <b>事件类型选择</b>：{@code supports} 命中 {@link OutboxEventType#PROCESS_APPROVED}——
 * design.md 第267行列出的事件类型集合里，{@code PROCESS_APPROVED}（"流程终审通过"）是唯一语义
 * 与"审批已通过、需要执行业务变更"直接对应的类型；{@code TASK_*} 描述的是单个审批节点任务的
 * 生命周期，{@code BUSINESS_SUCCEEDED}/{@code BUSINESS_FAILED} 是业务执行**完成后**才产生的
 * 结果事件（供通知等下游消费），不是触发执行的输入；{@code CC_CREATED} 与业务执行无关。因此
 * 选择 {@code PROCESS_APPROVED} 作为触发本消费者的事件类型。
 * <p>
 * <b>范围边界（tasks.md 7.3）</b>：本轮只实现 ORG/USER 两类业务对象，{@link #SUPPORTED_BIZ_TYPES}
 * 显式排除 POSITION/APP——即使 {@link MasterDataOperationExecutor} 内部的转换/校验/执行方法
 * 本身对四类业务对象一视同仁（该组件是从既有同步路径原样搬迁的公共逻辑，不因本轮范围收窄而
 * 阉割），本消费者仍在 {@link #consume} 入口处显式拒绝处理 POSITION/APP 类型的触发事件，保持
 * 任务边界清晰、便于分别验证；7.4 落地时只需把这两个类型加入 {@link #SUPPORTED_BIZ_TYPES}，
 * 不需要新增任何转换/校验/执行逻辑。
 * <p>
 * <b>不做的事</b>：本轮不实现 {@code base_revision}/目标版本冲突检测、更精细的失败分类体系
 * （7.5 范围）——遇到 {@link BusinessException}（如目标已被删除、唯一键冲突）统一映射为
 * {@link ExecutionStatus#FAILED_MANUAL}；不引入独立的 {@code EXECUTING} 中间态落库
 * ——本消费者的全部写操作（业务写 + 执行尝试记录 + 申请执行状态更新）与
 * {@code EventConsumeService#consumeOnce} 插入的消费去重行处于同一个物理事务，事务提交前对
 * 外部读者完全不可见，中途崩溃等价于整个事务回滚、下次重试从头开始，因此一个只在同一未提交
 * 事务内短暂存在的 {@code EXECUTING} 状态没有可观测价值，直接从 {@code PENDING} 写终态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MasterDataBusinessExecutionConsumer implements OutboxEventConsumer {

    /** 消费者标识，落库到 {@code tab_wf_event_consume.consumer_code}。 */
    private static final String CONSUMER_CODE = "BUSINESS_EXECUTOR";

    /** 本轮（7.3）支持的业务对象类型，POSITION/APP 留给 7.4。 */
    private static final Set<String> SUPPORTED_BIZ_TYPES = Set.of(FormFieldBizType.ORG, FormFieldBizType.USER);

    /** 可执行的申请执行状态前置集合：{@code PENDING}（等待执行）/{@code EXECUTING}（防御性
     *  兼容，理论上不会出现，见类注释"不引入独立的 EXECUTING 中间态落库"）。 */
    private static final Set<String> EXECUTABLE_STATUSES = Set.of(ExecutionStatus.PENDING, ExecutionStatus.EXECUTING);

    /** 错误摘要落库到 {@code error_code} 列的最大长度，与 DDL 列宽一致。 */
    private static final int ERROR_CODE_MAX_LENGTH = 64;

    /** 落库场景下审计字段的固定操作人标识：消费发生在无登录用户上下文的后台流程。 */
    private static final String SYSTEM_OPERATOR = "system";

    /** 审批申请数据访问接口，用于按触发事件携带的 {@code requestId} 反查完整申请行。 */
    private final ApprovalRequestMapper approvalRequestMapper;

    /** 业务执行尝试记录数据访问接口。 */
    private final BusinessExecutionMapper businessExecutionMapper;

    /** ORG/USER/POSITION/APP 主数据写操作公共组件，与同步路径共用（tasks.md 7.3）。 */
    private final MasterDataOperationExecutor masterDataOperationExecutor;

    /**
     * {@inheritDoc}
     */
    @Override
    public String consumerCode() {
        return CONSUMER_CODE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(String eventType) {
        return Objects.equals(eventType, OutboxEventType.PROCESS_APPROVED);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 调用方（{@code EventConsumeService#consumeOnce}）保证本方法与消费去重标记处于同一物理
     * 事务：正常返回（含内部捕获 {@link BusinessException} 后落库 {@code FAILED_MANUAL} 的
     * 分支）视为消费成功，随之一并提交；抛出未捕获的异常（如目标行状态已不可执行）会让整个
     * 事务（含业务写、执行尝试记录、消费去重标记）一并回滚，交由 Outbox 层按原事件重试。
     */
    @Override
    public void consume(OutboxEventEntity event) {
        BusinessExecutionTriggerPayload triggerPayload =
                JacksonUtils.toObj(event.getPayload(), BusinessExecutionTriggerPayload.class);
        Long requestId = triggerPayload.requestId();
        ApprovalRequestEntity request = approvalRequestMapper.selectById(requestId);
        if (request == null) {
            throw new IllegalStateException("审批申请[" + requestId + "]不存在，无法执行业务变更");
        }
        if (!SUPPORTED_BIZ_TYPES.contains(request.getBizType())) {
            log.info("审批申请[{}]业务对象类型[{}]暂不由本消费者处理（POSITION/APP 留待 7.4 实现），跳过",
                    requestId, request.getBizType());
            return;
        }

        int attemptNo = nextAttemptNo(requestId);
        Long submitterId = parseSubmitterId(request.getCreateBy());
        Long previousUserId = CurrentUserContext.getUserId();
        try {
            CurrentUserContext.setUserId(submitterId);
            executeAndRecord(event, request, attemptNo);
        } finally {
            CurrentUserContext.setUserId(previousUserId);
        }
    }

    /**
     * 以提交人身份复用公共组件完成转换/管辖范围重校验/执行写操作，并落库执行尝试记录与申请
     * 执行状态；{@link BusinessException} 映射为 {@code FAILED_MANUAL} 不重新抛出（不需要
     * 自动重试的手动失败），其余异常原样抛出触发整体事务回滚与 Outbox 重试。
     */
    private void executeAndRecord(
            OutboxEventEntity event,
            ApprovalRequestEntity request,
            int attemptNo) {
        Object payload = masterDataOperationExecutor.convertPayload(
                request.getBizType(), request.getOperationType(), request.getRequestPayload());
        Object result;
        try {
            masterDataOperationExecutor.validateScope(
                    CurrentUserContext.getUserId(),
                    request.getBizType(),
                    request.getOperationType(),
                    request.getTargetId(),
                    payload);
            result = masterDataOperationExecutor.executeWrite(
                    request.getBizType(), request.getOperationType(), request.getTargetId(), payload);
        } catch (BusinessException ex) {
            recordAttempt(request.getId(), attemptNo, event.getLeaseToken(),
                    ExecutionStatus.FAILED_MANUAL, truncate(ex.getMessage()), null);
            markRequestExecutionResult(request, ExecutionStatus.FAILED_MANUAL, null);
            return;
        }

        Long resultTargetId = masterDataOperationExecutor.extractTargetId(result);
        recordAttempt(request.getId(), attemptNo, event.getLeaseToken(),
                ExecutionStatus.SUCCEEDED, null, resultTargetId);
        markRequestExecutionResult(request, ExecutionStatus.SUCCEEDED, resultTargetId);
    }

    /**
     * 计算下一次执行尝试序号：同一 {@code requestId} 下已有记录的最大 {@code attemptNo} + 1，
     * 尚无记录时从 1 开始。
     */
    private int nextAttemptNo(Long requestId) {
        List<BusinessExecutionEntity> existing = businessExecutionMapper.selectList(
                new LambdaQueryWrapper<BusinessExecutionEntity>()
                        .eq(BusinessExecutionEntity::getRequestId, requestId)
                        .orderByDesc(BusinessExecutionEntity::getAttemptNo)
                        .last("LIMIT 1"));
        return existing.isEmpty() ? 1 : existing.get(0).getAttemptNo() + 1;
    }

    /**
     * 落库一条业务执行尝试记录。
     */
    private void recordAttempt(
            Long requestId,
            int attemptNo,
            String leaseToken,
            String executionStatus,
            String errorCode,
            Long resultTargetId) {
        LocalDateTime now = LocalDateTime.now();
        BusinessExecutionEntity attempt = BusinessExecutionEntity.builder()
                .requestId(requestId)
                .attemptNo(attemptNo)
                .leaseToken(leaseToken)
                .executionStatus(executionStatus)
                .errorCode(errorCode)
                .resultTargetId(resultTargetId)
                .createBy(SYSTEM_OPERATOR)
                .createTime(now)
                .updateBy(SYSTEM_OPERATOR)
                .updateTime(now)
                .build();
        businessExecutionMapper.insert(attempt);
    }

    /**
     * 以精确条件 {@code UPDATE} + 检查影响行数的 CAS 写法更新申请的业务执行结果：仅当当前
     * {@code execution_status} 仍处于 {@link #EXECUTABLE_STATUSES} 时才生效，{@code CREATE}
     * 操作执行成功时一并回填 {@code result_target_id}（与同步路径 {@code finalizeApproval}
     * 的字段写入语义保持一致）。影响行数不为 1 说明申请执行状态已不在预期范围内（如被并发
     * 处理），属于不应出现的异常场景，抛出触发整体事务回滚、交由 Outbox 重试。
     */
    private void markRequestExecutionResult(
            ApprovalRequestEntity request,
            String executionStatus,
            Long resultTargetId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ApprovalRequestEntity> wrapper = new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, request.getId())
                .in(ApprovalRequestEntity::getExecutionStatus, EXECUTABLE_STATUSES)
                .set(ApprovalRequestEntity::getExecutionStatus, executionStatus)
                .set(ApprovalRequestEntity::getUpdateBy, SYSTEM_OPERATOR)
                .set(ApprovalRequestEntity::getUpdateTime, now);
        if (Objects.equals(request.getOperationType(), ApprovalOperationType.CREATE)
                && Objects.equals(executionStatus, ExecutionStatus.SUCCEEDED)) {
            wrapper.set(ApprovalRequestEntity::getResultTargetId, resultTargetId);
        }
        int updated = approvalRequestMapper.update(null, wrapper);
        if (updated != 1) {
            throw new IllegalStateException("审批申请[" + request.getId() + "]执行状态已不是可执行状态"
                    + "（可能被并发处理），事件将按原事件重试");
        }
    }

    /**
     * 解析申请提交人用户 id。
     */
    private Long parseSubmitterId(String createBy) {
        try {
            return Long.valueOf(createBy);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("审批申请提交人信息无效：" + createBy);
        }
    }

    /**
     * 截断错误摘要以适配 {@code error_code} 列宽。
     */
    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_CODE_MAX_LENGTH ? message : message.substring(0, ERROR_CODE_MAX_LENGTH);
    }
}
