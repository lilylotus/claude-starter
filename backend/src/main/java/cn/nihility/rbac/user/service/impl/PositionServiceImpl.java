package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.PositionConvert;
import cn.nihility.rbac.user.service.PositionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Objects;
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

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize) {
        if (orgId == null) {
            throw new BusinessException("所属组织不能为空");
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
        UserPositionEntity entity = PositionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PositionStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        userPositionMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO update(Long id, PositionUpdateRequest request) {
        UserPositionEntity entity = getExistingEntity(id);

        PositionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

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
        entity.setStatus(PositionStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);
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
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);
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
