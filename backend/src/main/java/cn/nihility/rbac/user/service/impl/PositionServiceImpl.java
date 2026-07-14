package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.PositionConvert;
import cn.nihility.rbac.user.service.PositionService;
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

    /** 用户数据访问接口，仅用于回填任职记录的所属用户姓名。 */
    private final UserMapper userMapper;

    /** 组织数据访问接口，仅用于回填任职记录的所属组织名称。 */
    private final OrgMapper orgMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize) {
        if (orgId == null) {
            throw new BusinessException("所属组织不能为空");
        }

        LambdaQueryWrapper<UserPositionEntity> wrapper = new LambdaQueryWrapper<UserPositionEntity>()
                .eq(UserPositionEntity::getOrgId, orgId)
                .ne(UserPositionEntity::getStatus, PositionStatus.DELETED)
                .orderByDesc(UserPositionEntity::getShowOrder)
                .orderByAsc(UserPositionEntity::getId);

        Page<UserPositionEntity> queryPage = new Page<>(page, pageSize);
        Page<UserPositionEntity> resultPage = userPositionMapper.selectPage(queryPage, wrapper);
        List<PositionVO> records = toVOListWithNames(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO getById(Long id) {
        UserPositionEntity entity = getExistingEntity(id);
        return toVOListWithNames(List.of(entity)).get(0);
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

    /**
     * 把任职记录实体列表转换为视图对象列表，并批量解析所属用户姓名与所属组织名称，
     * 避免逐条查询用户表/组织表。
     *
     * @param entities 任职记录实体列表
     * @return 任职记录视图对象列表
     */
    private List<PositionVO> toVOListWithNames(List<UserPositionEntity> entities) {
        List<PositionVO> result = PositionConvert.INSTANCE.toVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        List<Long> userIds = entities.stream().map(UserPositionEntity::getUserId).distinct().toList();
        Map<Long, String> userNameMap;
        if (userIds.isEmpty()) {
            userNameMap = Map.of();
        } else {
            List<UserEntity> users = userMapper.selectList(
                    new LambdaQueryWrapper<UserEntity>().in(UserEntity::getId, userIds));
            userNameMap = users.stream()
                    .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName, (left, right) -> left));
        }

        List<Long> orgIds = entities.stream().map(UserPositionEntity::getOrgId).distinct().toList();
        Map<Long, String> orgNameMap;
        if (orgIds.isEmpty()) {
            orgNameMap = Map.of();
        } else {
            List<OrgEntity> orgs = orgMapper.selectList(
                    new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, orgIds));
            orgNameMap = orgs.stream()
                    .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));
        }

        for (PositionVO vo : result) {
            vo.setUserName(userNameMap.get(vo.getUserId()));
            vo.setOrgName(orgNameMap.get(vo.getOrgId()));
        }
        return result;
    }
}
