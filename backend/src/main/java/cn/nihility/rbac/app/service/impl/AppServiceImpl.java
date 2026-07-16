package cn.nihility.rbac.app.service.impl;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.dto.AppUpdateRequest;
import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.mapstruct.AppConvert;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 应用管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppServiceImpl implements AppService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 应用数据访问接口。 */
    private final AppMapper appMapper;

    /** 用户数据访问接口，仅用于回填负责人姓名。 */
    private final UserMapper userMapper;

    /** 组织数据访问接口，仅用于回填所属组织名称。 */
    private final OrgMapper orgMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppVO> getPage(Integer page, Integer pageSize) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<AppEntity>()
                .ne(AppEntity::getStatus, AppStatus.DELETED)
                .orderByDesc(AppEntity::getShowOrder)
                .orderByAsc(AppEntity::getId);

        Page<AppEntity> queryPage = new Page<>(page, pageSize);
        Page<AppEntity> resultPage = appMapper.selectPage(queryPage, wrapper);
        List<AppVO> records = toVOListWithNames(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO getById(Long id) {
        AppEntity entity = getExistingEntity(id);
        return toVOListWithNames(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO create(AppCreateRequest request) {
        checkCodeUnique(request.getCode(), null);

        AppEntity entity = AppConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(AppStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        appMapper.insert(entity);

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO update(Long id, AppUpdateRequest request) {
        AppEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);

        AppConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO enable(Long id) {
        return changeStatus(id, AppStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVO disable(Long id) {
        return changeStatus(id, AppStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AppEntity entity = getExistingEntity(id);
        entity.setStatus(AppStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);
    }

    /**
     * 变更应用状态（启用/停用）并返回更新后的详情。
     *
     * @param id     应用 id
     * @param status 目标状态
     * @return 更新后的应用详情
     */
    private AppVO changeStatus(Long id, int status) {
        AppEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        appMapper.updateById(entity);
        return getById(id);
    }

    /**
     * 校验应用编码在未删除的应用中是否唯一。
     *
     * @param code      待校验的应用编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<AppEntity>()
                .eq(AppEntity::getCode, code)
                .ne(AppEntity::getStatus, AppStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AppEntity::getId, excludeId);
        }
        Long count = appMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("应用编码[" + code + "]已存在");
        }
    }

    /**
     * 查询一个未被逻辑删除的应用，不存在时抛出业务异常。
     *
     * @param id 应用 id
     * @return 应用实体
     */
    private AppEntity getExistingEntity(Long id) {
        AppEntity entity = appMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), AppStatus.DELETED)) {
            throw new BusinessException("应用不存在");
        }
        return entity;
    }

    /**
     * 把应用实体列表转换为视图对象列表，并批量解析负责人姓名与所属组织名称，
     * 避免逐条查询用户表/组织表。
     *
     * @param entities 应用实体列表
     * @return 应用视图对象列表
     */
    private List<AppVO> toVOListWithNames(List<AppEntity> entities) {
        List<AppVO> result = AppConvert.INSTANCE.toVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        List<Long> ownerIds = entities.stream().map(AppEntity::getOwnerId).distinct().toList();
        Map<Long, String> ownerNameMap;
        if (ownerIds.isEmpty()) {
            ownerNameMap = Map.of();
        } else {
            List<UserEntity> owners = userMapper.selectList(
                    new LambdaQueryWrapper<UserEntity>().in(UserEntity::getId, ownerIds));
            ownerNameMap = owners.stream()
                    .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName, (left, right) -> left));
        }

        List<Long> orgIds = entities.stream().map(AppEntity::getOrgId).distinct().toList();
        Map<Long, String> orgNameMap;
        if (orgIds.isEmpty()) {
            orgNameMap = Map.of();
        } else {
            List<OrgEntity> orgs = orgMapper.selectList(
                    new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, orgIds));
            orgNameMap = orgs.stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));
        }

        for (AppVO vo : result) {
            vo.setOwnerName(ownerNameMap.get(vo.getOwnerId()));
            vo.setOrgName(orgNameMap.get(vo.getOrgId()));
        }
        return result;
    }
}
