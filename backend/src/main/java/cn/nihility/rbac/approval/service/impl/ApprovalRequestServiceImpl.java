package cn.nihility.rbac.approval.service.impl;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.approval.constant.ApprovalRequestStatus;
import cn.nihility.rbac.approval.dto.ApprovalProcessInstance;
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
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.UserService;
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
 * 主数据变更审批申请业务实现。
 */
@Service
@RequiredArgsConstructor
public class ApprovalRequestServiceImpl implements ApprovalRequestService {

    /** 合法业务对象类型。 */
    private static final Set<String> SUPPORTED_BIZ_TYPES = Set.of(
            FormFieldBizType.ORG,
            FormFieldBizType.USER,
            FormFieldBizType.POSITION,
            FormFieldBizType.APP);

    /** 审批申请数据访问接口。 */
    private final ApprovalRequestMapper approvalRequestMapper;

    /** Flowable 审批流程接口。 */
    private final ApprovalProcessService approvalProcessService;

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
        String currentUserId = requireCurrentUserId().toString();
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

        ApprovalProcessInstance process = approvalProcessService.start(entity.getId());
        entity.setFlowableProcessInstanceId(process.processInstanceId());
        entity.setFlowableTaskId(process.taskId());
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
        Long approverId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        int updated = approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, id)
                .eq(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.PENDING)
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.APPROVED)
                .set(ApprovalRequestEntity::getApproverId, approverId)
                .set(ApprovalRequestEntity::getApproveTime, now)
                .set(ApprovalRequestEntity::getOpinion, opinion)
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("审批申请已被处理");
        }

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
        if (Objects.equals(entity.getOperationType(), ApprovalOperationType.CREATE)) {
            approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                    .eq(ApprovalRequestEntity::getId, id)
                    .set(ApprovalRequestEntity::getResultTargetId, resultTargetId));
        }
        approvalProcessService.complete(entity.getFlowableTaskId(), approverId, true);
        recordRequestStatusChange(entity, ApprovalRequestStatus.APPROVED, approverId, opinion);
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
        Long approverId = requireCurrentUserId();
        LocalDateTime now = LocalDateTime.now();
        int updated = approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, id)
                .eq(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.PENDING)
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.REJECTED)
                .set(ApprovalRequestEntity::getApproverId, approverId)
                .set(ApprovalRequestEntity::getApproveTime, now)
                .set(ApprovalRequestEntity::getOpinion, opinion)
                .set(ApprovalRequestEntity::getUpdateBy, approverId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("审批申请已被处理");
        }
        approvalProcessService.complete(entity.getFlowableTaskId(), approverId, false);
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
        LocalDateTime now = LocalDateTime.now();
        int updated = approvalRequestMapper.update(null, new LambdaUpdateWrapper<ApprovalRequestEntity>()
                .eq(ApprovalRequestEntity::getId, id)
                .eq(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.PENDING)
                .set(ApprovalRequestEntity::getStatus, ApprovalRequestStatus.CANCELLED)
                .set(ApprovalRequestEntity::getUpdateBy, currentUserId.toString())
                .set(ApprovalRequestEntity::getUpdateTime, now));
        if (updated != 1) {
            throw new BusinessException("只有待审批申请可以撤回");
        }
        approvalProcessService.terminate(entity.getFlowableProcessInstanceId());
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
     */
    @Override
    public PageResult<ApprovalRequestVO> pagePending(
            String bizType,
            String operationType,
            Integer page,
            Integer pageSize) {
        LambdaQueryWrapper<ApprovalRequestEntity> wrapper = buildQuery(
                bizType,
                operationType,
                ApprovalRequestStatus.PENDING)
                .orderByAsc(ApprovalRequestEntity::getCreateTime)
                .orderByAsc(ApprovalRequestEntity::getId);
        return queryPage(wrapper, page, pageSize);
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
