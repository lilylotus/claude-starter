package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.PositionConvert;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任职管理业务逻辑实现，复用用户管理模块既有的 {@code tab_user_position} 表/实体/Mapper，
 * 以组织为导航维度提供独立的查询与维护能力，不影响用户管理内嵌任职子表单的既有行为。
 */
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 用户任职记录数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，与用户管理
     * 内嵌任职子表单（{@code UserServiceImpl}）共用同一份校验逻辑。
     */
    private final PositionDynamicFieldSupport positionDynamicFieldSupport;

    /**
     * 任职记录操作日志的被操作对象名称快照与字段快照共享组件，与用户管理内嵌任职子表单
     * （{@code UserServiceImpl}，{@code syncPositions}）共用同一份快照逻辑。
     */
    private final PositionLogSnapshotSupport positionLogSnapshotSupport;

    /**
     * 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围过滤任职列表
     * （org-scope-data-permission change design.md Decision 5）。
     */
    private final OrgScopeService orgScopeService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize) {
        if (orgId == null) {
            throw new BusinessException("所属组织不能为空");
        }

        // 受限且请求的 orgId 不在允许集合内时，直接返回空分页而不是报错：这个接口的既有
        // 语义是"某个组织下没有任职记录时也是返回空分页，不是报错"，管辖范围之外的组织
        // 对当前调用者而言观感上应该和"这个组织下没有任职记录"一致，不额外暴露"这个 orgId
        // 存在但你无权查看"这种更具体的越权探测信号（org-scope-data-permission change
        // design.md Decision 5）。
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        if (allowedOrgIds.isPresent() && !allowedOrgIds.get().contains(orgId)) {
            return new PageResult<>(List.of(), 0L, page, pageSize);
        }

        IPage<PositionVO> resultPage = userPositionMapper.selectPositionPage(
                new Page<>(page, pageSize), orgId, PositionStatus.DELETED);
        return PageResult.of(resultPage.getRecords(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO getById(Long id) {
        PositionVO vo = userPositionMapper.selectPositionDetail(id, PositionStatus.DELETED);
        if (vo == null) {
            throw new BusinessException("任职记录不存在");
        }
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO create(PositionCreateRequest request) {
        positionDynamicFieldSupport.validate(request, true, null);

        UserPositionEntity entity = PositionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PositionStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        userPositionMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.POSITION, entity.getId(),
                positionLogSnapshotSupport.targetName(entity), positionLogSnapshotSupport.snapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO update(Long id, PositionUpdateRequest request) {
        UserPositionEntity entity = getExistingEntity(id);
        positionDynamicFieldSupport.validate(request, false, id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

        PositionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), beforeSnapshot,
                positionLogSnapshotSupport.snapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO enable(Long id) {
        return changeStatus(id, PositionStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO disable(Long id) {
        return changeStatus(id, PositionStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        UserPositionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

        entity.setStatus(PositionStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), beforeSnapshot);
    }

    /**
     * 变更任职记录状态（启用/停用）并返回更新后的详情。
     *
     * @param id     任职记录 id
     * @param status 目标状态
     * @return 更新后的任职记录详情
     */
    private PositionVO changeStatus(Long id, int status) {
        UserPositionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.POSITION, id,
                positionLogSnapshotSupport.targetName(entity), status == PositionStatus.ENABLED, beforeSnapshot,
                positionLogSnapshotSupport.snapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的任职记录，不存在时抛出业务异常。
     *
     * @param id 任职记录 id
     * @return 任职记录实体
     */
    private UserPositionEntity getExistingEntity(Long id) {
        UserPositionEntity entity = userPositionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), PositionStatus.DELETED)) {
            throw new BusinessException("任职记录不存在");
        }
        return entity;
    }
}
