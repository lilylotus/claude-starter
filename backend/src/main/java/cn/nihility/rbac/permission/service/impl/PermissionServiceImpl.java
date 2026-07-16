package cn.nihility.rbac.permission.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.permission.constant.PermissionStatus;
import cn.nihility.rbac.permission.dto.PermissionCreateRequest;
import cn.nihility.rbac.permission.dto.PermissionUpdateRequest;
import cn.nihility.rbac.permission.dto.PermissionVO;
import cn.nihility.rbac.permission.entity.PermissionEntity;
import cn.nihility.rbac.permission.mapper.PermissionMapper;
import cn.nihility.rbac.permission.mapstruct.PermissionConvert;
import cn.nihility.rbac.permission.service.PermissionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 权限管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 权限点数据访问接口。 */
    private final PermissionMapper permissionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PermissionVO> getPage(Integer page, Integer pageSize) {
        LambdaQueryWrapper<PermissionEntity> wrapper = new LambdaQueryWrapper<PermissionEntity>()
                .ne(PermissionEntity::getStatus, PermissionStatus.DELETED)
                .orderByDesc(PermissionEntity::getShowOrder)
                .orderByAsc(PermissionEntity::getId);

        Page<PermissionEntity> queryPage = new Page<>(page, pageSize);
        Page<PermissionEntity> resultPage = permissionMapper.selectPage(queryPage, wrapper);
        List<PermissionVO> records = PermissionConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO getById(Long id) {
        PermissionEntity entity = getExistingEntity(id);
        return PermissionConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO create(PermissionCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        PermissionEntity entity = PermissionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PermissionStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        permissionMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO update(Long id, PermissionUpdateRequest request) {
        PermissionEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);

        PermissionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO enable(Long id) {
        return changeStatus(id, PermissionStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionVO disable(Long id) {
        return changeStatus(id, PermissionStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        PermissionEntity entity = getExistingEntity(id);
        entity.setStatus(PermissionStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);
    }

    /**
     * 变更权限点状态（启用/停用）并返回更新后的详情。
     *
     * @param id     权限点 id
     * @param status 目标状态
     * @return 更新后的权限点详情
     */
    private PermissionVO changeStatus(Long id, int status) {
        PermissionEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        permissionMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的权限点，不存在时抛出业务异常。
     *
     * @param id 权限点 id
     * @return 权限点实体
     */
    private PermissionEntity getExistingEntity(Long id) {
        PermissionEntity entity = permissionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), PermissionStatus.DELETED)) {
            throw new BusinessException("权限点不存在");
        }
        return entity;
    }

    /**
     * 校验权限编码在未删除的权限点中是否唯一。
     *
     * @param code      待校验的权限编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<PermissionEntity> wrapper = new LambdaQueryWrapper<PermissionEntity>()
                .eq(PermissionEntity::getCode, code)
                .ne(PermissionEntity::getStatus, PermissionStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(PermissionEntity::getId, excludeId);
        }
        Long count = permissionMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("权限编码[" + code + "]已存在");
        }
    }
}
