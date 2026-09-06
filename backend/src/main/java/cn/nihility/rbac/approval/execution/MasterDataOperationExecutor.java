package cn.nihility.rbac.approval.execution;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.approval.constant.ApprovalOperationType;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
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
import cn.nihility.rbac.user.service.UserService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ORG/USER/POSITION/APP 四类主数据写操作的转换/管辖范围校验/执行公共组件
 * （production-approval-lifecycle change design.md 第203行，tasks.md 7.3）。从
 * {@code ApprovalRequestServiceImpl} 的同步执行路径（{@code finalizeApproval} 内部调用链）原样
 * 搬迁而来，不改变任何已被既有大量集成测试覆盖过的行为，供同步路径与本轮新增的可靠异步执行
 * 适配器（{@code cn.nihility.rbac.approval.execution.consumer
 * .MasterDataBusinessExecutionConsumer}）共同复用，避免把四类业务对象 × 五种操作的
 * switch-case 逻辑复制粘贴到新类里。
 */
@Component
@RequiredArgsConstructor
public class MasterDataOperationExecutor {

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

    /**
     * 将通用请求体转换为对应模块的 DTO。
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param payload       原始请求体（DTO 实例或其 JSON 字符串）
     * @return 转换后的模块 DTO；{@code ENABLE}/{@code DISABLE}/{@code DELETE} 操作无需请求体，
     *         返回 {@code null}
     */
    public Object convertPayload(
            String bizType,
            String operationType,
            Object payload) {
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
     * 按提交人当前的管辖组织范围重新校验。
     *
     * @param userId        执行校验的用户 id（通常为申请提交人）
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param targetId      目标记录 id，创建操作为空
     * @param payload       已转换的模块 DTO
     */
    public void validateScope(
            Long userId,
            String bizType,
            String operationType,
            Long targetId,
            Object payload) {
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
    private void validateOrgScope(
            Long userId,
            String operationType,
            Long targetId,
            Object payload) {
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
     *
     * @param bizType       业务对象类型
     * @param operationType 操作类型
     * @param targetId      目标记录 id，创建操作为空
     * @param payload       已转换的模块 DTO
     * @return 创建/更新操作返回对应模块的 VO；状态类/删除操作返回 {@code null}
     */
    public Object executeWrite(
            String bizType,
            String operationType,
            Long targetId,
            Object payload) {
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
     * 读取目标记录当前值，用于校验管辖范围/展示变更前快照；目标不存在时返回 {@code null}。
     *
     * @param bizType  业务对象类型
     * @param targetId 目标记录 id
     * @return 目标记录当前值，不存在时为 {@code null}
     */
    public Object getCurrentTarget(String bizType, Long targetId) {
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
     * 从创建结果提取主键 id。
     *
     * @param result {@link #executeWrite} 的返回值
     * @return 创建操作返回的主键 id；非创建操作或结果为空时返回 {@code null}
     */
    public Long extractTargetId(Object result) {
        return switch (result) {
            case OrgVO value -> value.getId();
            case UserVO value -> value.getId();
            case PositionVO value -> value.getId();
            case AppVO value -> value.getId();
            case null, default -> null;
        };
    }
}
