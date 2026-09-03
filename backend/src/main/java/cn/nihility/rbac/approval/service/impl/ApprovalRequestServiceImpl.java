package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.ApprovalSubmitRequest;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.approval.entity.ApprovalRequestEntity;
import cn.nihility.rbac.approval.mapper.ApprovalRequestMapper;
import cn.nihility.rbac.approval.mapstruct.ApprovalConvert;
import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.approval.service.ApprovalRequestService;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.UserService;
import cn.nihility.rbac.workflow.assignee.support.TaskAuthorizationService;
import cn.nihility.rbac.workflow.constant.ApprovalAction;
import cn.nihility.rbac.workflow.constant.ProcessInstanceStatus;
import cn.nihility.rbac.workflow.constant.TaskStatus;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ApprovalRecordEntity;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalRecordMapper;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 主数据变更审批申请业务实现。多级审批相关逻辑通过 {@link ApprovalProcessService} 转调用通用
 * 审批引擎 {@code WorkflowService}，本类不直接依赖 Flowable API（workflow-approval-engine
 * change design.md Decision 8）。
 */
@Service
@RequiredArgsConstructor
public class ApprovalRequestServiceImpl implements ApprovalRequestService {

    /** 合法业务对象类型，同时也是 {@code tab_wf_process_instance.business_type} 的合法取值范围。 */
    private static final Set<String> SUPPORTED_BIZ_TYPES = Set.of(
            FormFieldBizType.ORG,
            FormFieldBizType.USER,
            FormFieldBizType.POSITION,
            FormFieldBizType.APP);

    /** 任职记录里表达"主职"的任职类型编码。 */
    private static final String PRIMARY_POSITION_TYPE = "primary";

    /** 审批申请数据访问接口。 */
    private final ApprovalRequestMapper approvalRequestMapper;

    /** 主数据审批流程接口，内部委托通用审批引擎。 */
    private final ApprovalProcessService approvalProcessService;

    /** 审批任务数据访问接口，用于定位申请当前所处节点的待处理任务。 */
    private final ApprovalTaskMapper approvalTaskMapper;

    /** 流程实例数据访问接口，用于判断本次审批后流程是否推进到最终状态。 */
    private final ProcessInstanceMapper processInstanceMapper;

    /** 审批轨迹数据访问接口，用于读取系统终止场景的具体原因说明。 */
    private final ApprovalRecordMapper approvalRecordMapper;

    /** 任务处理越权校验服务，复用引擎内部的 assignee/candidateUser/candidateGroup 判定逻辑，
     *  用于"待我审批"过滤当前用户在当前节点是否命中。 */
    private final TaskAuthorizationService taskAuthorizationService;

    /** 用户任职记录数据访问接口，用于提交申请时解析发起人所属组织。 */
    private final UserPositionMapper userPositionMapper;

    /** 组织业务接口。 */
    private final OrgService orgService;

    /** 用户业务接口。 */
    private final UserService userService;

    /** 任职业务接口。 */
    private final PositionService positionService;

    /** 应用业务接口。 */
    private final AppService appService;

    /** 管辖组织范围接口。 */
    private final OrgScopeService orgScopeService;

    /** Bean Validation 校验器。 */
    private final Validator validator;

    /** 用户展示名解析服务。 */
    private final UserDisplayService userDisplayService;

    /** 操作日志记录器。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public WriteOperationResultVO<?> submit(ApprovalSubmitRequest request) {
        return submit(
                request.getBizType(),
                request.getOperationType(),
                request.getTargetId(),
                request.getRequestPayload());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public WriteOperationResultVO<?> submit(
            String bizType,
            String operationType,
            Long targetId,
            Object payload) {
        validateTypesAndTarget(bizType, operationType, targetId);
        Object typedPayload = convertPayload(bizType, operationType, payload);
        validatePayload(typedPayload);

        validateScope(bizType, operationType, targetId, typedPayload);
        LocalDateTime now = LocalDateTime.now();
        Long applicantId = requireCurrentUserId();
        String currentUserId = applicantId.toString();
        ApprovalRequestEntity entity = ApprovalRequestEntity.builder()
                .bizType(bizType)
                .operationType(operationType)
                .targetId(targetId)
                .requestPayload(typedPayload == null ? null : JacksonUtils.toJson(typedPayload))
                .status(ApprovalRequestStatus.PENDING)
                .createBy(currentUserId)
                .createTime(now)
                .updateBy(currentUserId)
                .updateTime(now)
                .build();
        approvalRequestMapper.insert(entity);

        Long applicantOrgId = resolveApplicantOrgId(applicantId);
        WorkflowInstanceResult process = approvalProcessService.start(entity.getId(), bizType, applicantId, applicantOrgId);
        entity.setProcessInstanceId(process.processInstanceId());
        entity.setFlowableProcessInstanceId(process.flowableProcessInstanceId());
        entity.setCurrentNodeName(process.currentNodeName());
        ApprovalTaskEntity openTask = findOpenTask(process.processInstanceId());
        if (openTask != null) {
            entity.setFlowableTaskId(openTask.getFlowableTaskId());
        }
        approvalRequestMapper.updateById(entity);
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(Set.of(currentUserId));
        return WriteOperationResultVO.pending(toVO(entity, displayNames));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void approve(Long id, String opinion) {
        ApprovalRequestEntity entity = getExisting(id);
        if (!Objects.equals(entity.getStatus(), ApprovalRequestStatus.PENDING)) {
            throw new BusinessException("审批申请已被处理");
        }
        Long approverId = requireCurrentUserId();
        ApprovalTaskEntity task = requireCurrentTask(entity.getProcessInstanceId());

        approvalProcessService.approve(task.getId(), approverId, opinion);

        ProcessInstanceEntity instance = processInstanceMapper.selectById(entity.getProcessInstanceId());
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        switch (instance.getStatus()) {
            case ProcessInstanceStatus.RUNNING -> advanceNode(entity, instance, approverId);
            case ProcessInstanceStatus.APPROVED -> finalizeApproval(entity, approverId, opinion);
            case ProcessInstanceStatus.TERMINATED -> terminateAsRejected(entity, approverId, instance);
            default -> throw new BusinessException("流程实例状态异常：" + instance.getStatus());
        }
    }

    /**
     * 非最终节点通过：仅推进流程，更新当前节点名称，申请状态保持待审批。
     */
    private void advanceNode(ApprovalRequestEntity entity, ProcessInstanceEntity instance, Long approverId) {
        LocalDateTime now = LocalDateTime.now();
        approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, entity.getId())
                .set(ApprovalRequestEntity::getCurrentNodeName, instance.getCurrentNodeName())
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
    }

    /**
     * 最终节点通过：以提交人身份重新校验管辖范围并执行既有业务写操作，成功后申请置为已通过；
     * 与 {@link ApprovalProcessService#approve} 处于同一数据库事务内，业务写操作失败会连同流程
     * 推进一并回滚（本项目 Flowable 与业务表共用同一 DataSource/事务管理器，见最终报告说明）。
     */
    private void finalizeApproval(ApprovalRequestEntity entity, Long approverId, String opinion) {
        Object payload = convertPayload(entity.getBizType(), entity.getOperationType(), entity.getRequestPayload());
        Long submitterId = parseUserId(entity.getCreateBy());
        Object result;
        try {
            CurrentUserContext.setUserId(submitterId);
            validateScope(entity.getBizType(), entity.getOperationType(), entity.getTargetId(), payload);
            result = executeWrite(entity.getBizType(), entity.getOperationType(), entity.getTargetId(), payload);
        } finally {
            CurrentUserContext.setUserId(approverId);
        }

        Long resultTargetId = extractTargetId(result);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<ApprovalRequestEntity> wrapper = new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, entity.getId())
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.APPROVED)
                .set(ApprovalRequestEntity::getApproverId, approverId)
                .set(ApprovalRequestEntity::getApproveTime, now)
                .set(ApprovalRequestEntity::getOpinion, opinion)
                .set(ApprovalRequestEntity::getCurrentNodeName, null)
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now);
        if (Objects.equals(entity.getOperationType(), ApprovalOperationType.CREATE)) {
            wrapper.set(ApprovalRequestEntity::getResultTargetId, resultTargetId);
        }
        approvalRequestMapper.update(null, wrapper);
        recordRequestStatusChange(entity, ApprovalRequestStatus.APPROVED, approverId, opinion);
    }

    /**
     * 空审批人策略为 {@code REJECT} 触发的系统终止：等同于"已拒绝"处理。
     */
    private void terminateAsRejected(ApprovalRequestEntity entity, Long approverId, ProcessInstanceEntity instance) {
        String reason = latestTerminateReason(instance.getId());
        String finalOpinion = "系统终止：" + reason;
        LocalDateTime now = LocalDateTime.now();
        approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, entity.getId())
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.REJECTED)
                .set(ApprovalRequestEntity::getApproverId, approverId)
                .set(ApprovalRequestEntity::getApproveTime, now)
                .set(ApprovalRequestEntity::getOpinion, finalOpinion)
                .set(ApprovalRequestEntity::getCurrentNodeName, null)
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        recordRequestStatusChange(entity, ApprovalRequestStatus.REJECTED, approverId, finalOpinion);
    }

    /**
     * 读取流程实例最近一条系统终止轨迹的说明文字。
     */
    private String latestTerminateReason(Long processInstanceId) {
        ApprovalRecordEntity record = approvalRecordMapper.selectOne(new LambdaQueryWrapper<ApprovalRecordEntity>()
                .eq(ApprovalRecordEntity::getProcessInstanceId, processInstanceId)
                .eq(ApprovalRecordEntity::getAction, ApprovalAction.TERMINATE)
                .orderByDesc(ApprovalRecordEntity::getId)
                .last("LIMIT 1"));
        return record != null && StringUtils.hasText(record.getRemark()) ? record.getRemark() : "无审批人自动终止";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void reject(Long id, String opinion) {
        if (!StringUtils.hasText(opinion)) {
            throw new BusinessException("拒绝意见不能为空");
        }
        ApprovalRequestEntity entity = getExisting(id);
        if (!Objects.equals(entity.getStatus(), ApprovalRequestStatus.PENDING)) {
            throw new BusinessException("审批申请已被处理");
        }
        Long approverId = requireCurrentUserId();
        ApprovalTaskEntity task = requireCurrentTask(entity.getProcessInstanceId());

        approvalProcessService.reject(task.getId(), approverId, opinion);

        LocalDateTime now = LocalDateTime.now();
        approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, id)
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.REJECTED)
                .set(ApprovalRequestEntity::getApproverId, approverId)
                .set(ApprovalRequestEntity::getApproveTime, now)
                .set(ApprovalRequestEntity::getOpinion, opinion)
                .set(ApprovalRequestEntity::getCurrentNodeName, null)
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        recordRequestStatusChange(entity, ApprovalRequestStatus.REJECTED, approverId, opinion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void cancel(Long id) {
        ApprovalRequestEntity entity = getExisting(id);
        Long currentUserId = requireCurrentUserId();
        if (!Objects.equals(entity.getCreateBy(), currentUserId.toString())) {
            throw new BusinessException("只能撤回本人提交的审批申请");
        }
        if (!Objects.equals(entity.getStatus(), ApprovalRequestStatus.PENDING)) {
            throw new BusinessException("只有待审批申请可以撤回");
        }
        approvalProcessService.withdraw(entity.getProcessInstanceId(), currentUserId);

        LocalDateTime now = LocalDateTime.now();
        int updated = approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, id)
                .eq(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.PENDING)
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.CANCELLED)
                .set(ApprovalRequestEntity::getCurrentNodeName, null)
                .set(ApprovalRequestEntity::getUpdateBy, currentUserId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("只有待审批申请可以撤回");
        }
        recordRequestStatusChange(entity, ApprovalRequestStatus.CANCELLED, currentUserId, null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ApprovalRequestVO> pageMine(
            String bizType,
            String operationType,
            Integer status,
            Integer page,
            Integer pageSize) {
        LambdaQueryWrapper<ApprovalRequestEntity> wrapper = buildQuery(bizType, operationType, status)
                .eq(ApprovalRequestEntity::getCreateBy, requireCurrentUserId().toString())
                .orderByDesc(ApprovalRequestEntity::getCreateTime)
                .orderByDesc(ApprovalRequestEntity::getId);
        return queryPage(wrapper, page, pageSize);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 不再是"仅持有 {@code ApprovalManagement:request:approve} 权限点即可看到全部待审批申请"，
     * 而是限定为"当前用户在该申请当前所处节点被解析为指定处理人或候选人（用户/角色维度）"
     * （master-data-approval-workflow spec.md "审批申请查询" Requirement）。
     */
    @Override
    public PageResult<ApprovalRequestVO> pagePending(
            String bizType,
            String operationType,
            Integer page,
            Integer pageSize) {
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectivePageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
        Set<Long> candidateIds = resolvePendingCandidateRequestIds(requireCurrentUserId());
        if (candidateIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L, effectivePage, effectivePageSize);
        }
        LambdaQueryWrapper<ApprovalRequestEntity> wrapper = buildQuery(
                bizType,
                operationType,
                ApprovalRequestStatus.PENDING)
                .in(ApprovalRequestEntity::getId, candidateIds)
                .orderByAsc(ApprovalRequestEntity::getCreateTime)
                .orderByAsc(ApprovalRequestEntity::getId);
        return queryPage(wrapper, page, pageSize);
    }

    /**
     * 解析当前用户在通用审批引擎里命中的待处理任务，反查这些任务所属流程实例关联的
     * {@code tab_approval_request.id} 候选集合。命中判定复用
     * {@link TaskAuthorizationService#isAuthorized}，与引擎内部 {@code approve}/{@code reject}
     * 的越权校验保持完全一致的口径。
     */
    private Set<Long> resolvePendingCandidateRequestIds(Long currentUserId) {
        List<ApprovalTaskEntity> openTasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED));
        if (openTasks.isEmpty()) {
            return Set.of();
        }
        Set<Long> matchedProcessInstanceIds = openTasks.stream()
                .filter(task -> taskAuthorizationService.isAuthorized(task, currentUserId))
                .map(ApprovalTaskEntity::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (matchedProcessInstanceIds.isEmpty()) {
            return Set.of();
        }
        List<ProcessInstanceEntity> instances = processInstanceMapper.selectList(
                new LambdaQueryWrapper<ProcessInstanceEntity>()
                        .in(ProcessInstanceEntity::getId, matchedProcessInstanceIds)
                        .in(ProcessInstanceEntity::getBusinessType, SUPPORTED_BIZ_TYPES));
        return instances.stream()
                .map(ProcessInstanceEntity::getBusinessId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 构造审批申请分页查询条件。
     */
    private LambdaQueryWrapper<ApprovalRequestEntity> buildQuery(
            String bizType,
            String operationType,
            Integer status) {
        LambdaQueryWrapper<ApprovalRequestEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(bizType), ApprovalRequestEntity::getBizType, bizType);
        wrapper.eq(StringUtils.hasText(operationType), ApprovalRequestEntity::getOperationType, operationType);
        wrapper.eq(status != null, ApprovalRequestEntity::getStatus, status);
        return wrapper;
    }

    /**
     * 执行分页查询并填充展示字段。
     */
    private PageResult<ApprovalRequestVO> queryPage(
            LambdaQueryWrapper<ApprovalRequestEntity> wrapper,
            Integer page,
            Integer pageSize) {
        int effectivePage = page != null && page > 0 ? page : 1;
        int effectivePageSize = pageSize != null && pageSize > 0 ? pageSize : 10;
        Page<ApprovalRequestEntity> resultPage = approvalRequestMapper.selectPage(
                new Page<>(effectivePage, effectivePageSize),
                wrapper);
        Set<String> userIdTexts = resultPage.getRecords().stream()
                .map(ApprovalRequestEntity::getApproverId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.toCollection(HashSet::new));
        resultPage.getRecords().stream()
                .map(ApprovalRequestEntity::getCreateBy)
                .filter(Objects::nonNull)
                .forEach(userIdTexts::add);
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(userIdTexts);
        List<ApprovalRequestVO> records = resultPage.getRecords().stream()
                .map(entity -> toVO(entity, displayNames))
                .toList();
        return PageResult.of(records, resultPage);
    }

    /**
     * 转换申请视图并填充 JSON 请求体、审批人名称和更新目标当前值。
     */
    private ApprovalRequestVO toVO(ApprovalRequestEntity entity, Map<String, String> displayNames) {
        ApprovalRequestVO vo = ApprovalConvert.INSTANCE.toRequestVO(entity);
        if (StringUtils.hasText(entity.getRequestPayload())) {
            vo.setRequestPayload(JacksonUtils.toObj(entity.getRequestPayload(), JacksonUtils.MAP_OBJECT_TYPE_REFERENCE));
        }
        if (entity.getApproverId() != null) {
            vo.setApproverName(displayNames.getOrDefault(entity.getApproverId().toString(), "未知用户"));
        }
        if (StringUtils.hasText(entity.getCreateBy())) {
            vo.setCreateByName(displayNames.getOrDefault(entity.getCreateBy(), "未知用户"));
        }
        if (Objects.equals(entity.getOperationType(), ApprovalOperationType.UPDATE)) {
            vo.setTargetSnapshot(getCurrentTarget(entity.getBizType(), entity.getTargetId()));
        }
        return vo;
    }

    /**
     * 读取更新申请目标记录当前值。
     */
    private Object getCurrentTarget(String bizType, Long targetId) {
        try {
            return switch (bizType) {
                case FormFieldBizType.ORG -> orgService.getById(targetId);
                case FormFieldBizType.USER -> userService.getById(targetId);
                case FormFieldBizType.POSITION -> positionService.getById(targetId);
                case FormFieldBizType.APP -> appService.getById(targetId);
                default -> null;
            };
        } catch (BusinessException exception) {
            return null;
        }
    }

    /**
     * 将通用请求体转换为对应模块的 DTO。
     */
    private Object convertPayload(String bizType, String operationType, Object payload) {
        if (!Objects.equals(operationType, ApprovalOperationType.CREATE)
                && !Objects.equals(operationType, ApprovalOperationType.UPDATE)) {
            return null;
        }
        if (payload == null) {
            throw new BusinessException("创建或更新操作的请求内容不能为空");
        }
        Class<?> targetClass = switch (bizType + ':' + operationType) {
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.CREATE -> OrgCreateRequest.class;
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.UPDATE -> OrgUpdateRequest.class;
            case FormFieldBizType.USER + ":" + ApprovalOperationType.CREATE -> UserCreateRequest.class;
            case FormFieldBizType.USER + ":" + ApprovalOperationType.UPDATE -> UserUpdateRequest.class;
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.CREATE -> PositionCreateRequest.class;
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.UPDATE -> PositionUpdateRequest.class;
            case FormFieldBizType.APP + ":" + ApprovalOperationType.CREATE -> AppCreateRequest.class;
            case FormFieldBizType.APP + ":" + ApprovalOperationType.UPDATE -> AppUpdateRequest.class;
            default -> throw new BusinessException("不支持的审批申请类型");
        };
        if (targetClass.isInstance(payload)) {
            return payload;
        }
        if (payload instanceof String json) {
            return JacksonUtils.toObj(json, targetClass);
        }
        return JacksonUtils.convert(payload, targetClass);
    }

    /**
     * 执行 DTO 结构校验。
     */
    private void validatePayload(Object payload) {
        if (payload == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("；"));
            throw new BusinessException(message);
        }
    }

    /**
     * 校验业务对象类型、操作类型与目标 id 组合。
     */
    private void validateTypesAndTarget(String bizType, String operationType, Long targetId) {
        if (!SUPPORTED_BIZ_TYPES.contains(bizType)) {
            throw new BusinessException("不支持的业务对象类型：" + bizType);
        }
        if (!ApprovalOperationType.isSupported(operationType)) {
            throw new BusinessException("不支持的操作类型：" + operationType);
        }
        if (Objects.equals(operationType, ApprovalOperationType.CREATE) && targetId != null) {
            throw new BusinessException("创建操作不能指定目标记录 id");
        }
        if (!Objects.equals(operationType, ApprovalOperationType.CREATE) && targetId == null) {
            throw new BusinessException("非创建操作必须指定目标记录 id");
        }
    }

    /**
     * 在提交与审批通过时按提交人身份执行管辖组织范围校验。
     */
    private void validateScope(String bizType, String operationType, Long targetId, Object payload) {
        Long userId = requireCurrentUserId();
        if (Objects.equals(bizType, FormFieldBizType.USER)) {
            return;
        }
        if (Objects.equals(bizType, FormFieldBizType.ORG)) {
            validateOrgScope(userId, operationType, targetId, payload);
            return;
        }
        if (Objects.equals(bizType, FormFieldBizType.POSITION)) {
            PositionVO current = targetId == null ? null : positionService.getById(targetId);
            Long orgId = payload instanceof PositionCreateRequest request
                    ? request.getOrgId()
                    : payload instanceof PositionUpdateRequest request ? request.getOrgId() : current.getOrgId();
            assertOrgAllowed(userId, orgId);
            return;
        }
        AppVO current = targetId == null ? null : appService.getById(targetId);
        Long orgId = payload instanceof AppCreateRequest request
                ? request.getOrgId()
                : payload instanceof AppUpdateRequest request ? request.getOrgId() : current.getOrgId();
        assertOrgAllowed(userId, orgId);
    }

    /**
     * 校验组织申请的管辖范围。
     */
    private void validateOrgScope(Long userId, String operationType, Long targetId, Object payload) {
        if (Objects.equals(operationType, ApprovalOperationType.CREATE)) {
            assertOrgAllowed(userId, ((OrgCreateRequest) payload).getParentId());
            return;
        }
        OrgVO current = orgService.getById(targetId);
        assertOrgAllowed(userId, targetId);
        if (Objects.equals(operationType, ApprovalOperationType.UPDATE)) {
            Long newParentId = ((OrgUpdateRequest) payload).getParentId();
            if (!Objects.equals(current.getParentId(), newParentId)) {
                assertOrgAllowed(userId, newParentId);
            }
        }
    }

    /**
     * 断言组织 id 位于当前用户管辖范围。
     */
    private void assertOrgAllowed(Long userId, Long orgId) {
        if (!orgScopeService.isOrgIdAllowed(userId, orgId)) {
            throw new BusinessException("无权限操作管辖范围之外的组织");
        }
    }

    /**
     * 调用四个模块既有 Service 方法执行真实写操作。
     */
    private Object executeWrite(String bizType, String operationType, Long targetId, Object payload) {
        return switch (bizType + ':' + operationType) {
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.CREATE ->
                    orgService.create((OrgCreateRequest) payload);
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.UPDATE ->
                    orgService.update(targetId, (OrgUpdateRequest) payload);
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.ENABLE -> orgService.enable(targetId);
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.DISABLE -> orgService.disable(targetId);
            case FormFieldBizType.ORG + ":" + ApprovalOperationType.DELETE -> deleteOrg(targetId);
            case FormFieldBizType.USER + ":" + ApprovalOperationType.CREATE ->
                    userService.create((UserCreateRequest) payload);
            case FormFieldBizType.USER + ":" + ApprovalOperationType.UPDATE ->
                    userService.update(targetId, (UserUpdateRequest) payload);
            case FormFieldBizType.USER + ":" + ApprovalOperationType.ENABLE -> userService.enable(targetId);
            case FormFieldBizType.USER + ":" + ApprovalOperationType.DISABLE -> userService.disable(targetId);
            case FormFieldBizType.USER + ":" + ApprovalOperationType.DELETE -> deleteUser(targetId);
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.CREATE ->
                    positionService.create((PositionCreateRequest) payload);
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.UPDATE ->
                    positionService.update(targetId, (PositionUpdateRequest) payload);
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.ENABLE -> positionService.enable(targetId);
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.DISABLE -> positionService.disable(targetId);
            case FormFieldBizType.POSITION + ":" + ApprovalOperationType.DELETE -> deletePosition(targetId);
            case FormFieldBizType.APP + ":" + ApprovalOperationType.CREATE ->
                    appService.create((AppCreateRequest) payload);
            case FormFieldBizType.APP + ":" + ApprovalOperationType.UPDATE ->
                    appService.update(targetId, (AppUpdateRequest) payload);
            case FormFieldBizType.APP + ":" + ApprovalOperationType.ENABLE -> appService.enable(targetId);
            case FormFieldBizType.APP + ":" + ApprovalOperationType.DISABLE -> appService.disable(targetId);
            case FormFieldBizType.APP + ":" + ApprovalOperationType.DELETE -> deleteApp(targetId);
            default -> throw new BusinessException("不支持的审批申请类型");
        };
    }

    /** 删除组织并返回空结果。 */
    private Object deleteOrg(Long id) {
        orgService.delete(id);
        return null;
    }

    /** 删除用户并返回空结果。 */
    private Object deleteUser(Long id) {
        userService.delete(id);
        return null;
    }

    /** 删除任职并返回空结果。 */
    private Object deletePosition(Long id) {
        positionService.delete(id);
        return null;
    }

    /** 删除应用并返回空结果。 */
    private Object deleteApp(Long id) {
        appService.delete(id);
        return null;
    }

    /**
     * 从创建结果提取主键 id。
     */
    private Long extractTargetId(Object result) {
        return switch (result) {
            case OrgVO value -> value.getId();
            case UserVO value -> value.getId();
            case PositionVO value -> value.getId();
            case AppVO value -> value.getId();
            case null, default -> null;
        };
    }

    /**
     * 查询审批申请。
     */
    private ApprovalRequestEntity getExisting(Long id) {
        ApprovalRequestEntity entity = approvalRequestMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("审批申请不存在");
        }
        return entity;
    }

    /**
     * 查询申请当前所处节点下、状态仍为待处理（{@code PENDING}/{@code CLAIMED}）的审批任务。
     * 正常情况下同一时刻一条申请只对应唯一一条开放任务；查不到说明任务已被处理或申请尚未
     * 成功接入引擎。
     */
    private ApprovalTaskEntity requireCurrentTask(Long processInstanceId) {
        ApprovalTaskEntity task = findOpenTask(processInstanceId);
        if (task == null) {
            throw new BusinessException("审批任务已被处理");
        }
        return task;
    }

    /**
     * 查询流程实例当前开放（{@code PENDING}/{@code CLAIMED}）的审批任务，不存在时返回
     * {@code null}。
     */
    private ApprovalTaskEntity findOpenTask(Long processInstanceId) {
        if (processInstanceId == null) {
            return null;
        }
        return approvalTaskMapper.selectOne(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, processInstanceId)
                .in(ApprovalTaskEntity::getStatus, TaskStatus.PENDING, TaskStatus.CLAIMED)
                .last("LIMIT 1"));
    }

    /**
     * 解析发起人所属组织 id：优先取状态启用、任职类型为"主职"的任职记录所属组织，取不到时
     * 退化为任一状态启用的任职记录所属组织，均无任职记录时返回 {@code null}（组织负责人类
     * {@link cn.nihility.rbac.workflow.assignee.AssigneeResolver} 会按空审批人策略处理）。
     */
    private Long resolveApplicantOrgId(Long userId) {
        List<UserPositionEntity> positions = userPositionMapper.selectList(new LambdaQueryWrapper<UserPositionEntity>()
                .eq(UserPositionEntity::getUserId, userId)
                .eq(UserPositionEntity::getStatus, PositionStatus.ENABLED));
        if (positions.isEmpty()) {
            return null;
        }
        return positions.stream()
                .filter(position -> PRIMARY_POSITION_TYPE.equals(position.getPositionType()))
                .map(UserPositionEntity::getOrgId)
                .findFirst()
                .orElseGet(() -> positions.get(0).getOrgId());
    }

    /**
     * 读取当前登录用户 id。
     */
    private Long requireCurrentUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BusinessException("当前用户未登录");
        }
        return userId;
    }

    /**
     * 解析申请提交人用户 id。
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException exception) {
            throw new BusinessException("审批申请提交人信息无效");
        }
    }

    /**
     * 记录审批申请状态流转操作日志。
     */
    private void recordRequestStatusChange(
            ApprovalRequestEntity entity,
            int targetStatus,
            Long operatorId,
            String opinion) {
        Map<String, Object> before = requestSnapshot(entity.getStatus(), entity.getApproverId(), entity.getOpinion());
        Map<String, Object> after = requestSnapshot(targetStatus, operatorId, opinion);
        operationLogRecorder.recordUpdate(
                OperationLogResourceType.APPROVAL_REQUEST,
                entity.getId(),
                "审批申请#" + entity.getId(),
                before,
                after);
    }

    /**
     * 构造审批申请操作日志快照。
     */
    private Map<String, Object> requestSnapshot(Integer status, Long approverId, String opinion) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("申请状态", status);
        snapshot.put("审批人", approverId);
        snapshot.put("审批意见", opinion);
        return snapshot;
    }
}
