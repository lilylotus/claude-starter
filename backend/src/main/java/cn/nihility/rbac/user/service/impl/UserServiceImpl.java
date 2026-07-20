package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserGender;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.UserCreateRequest;
import cn.nihility.rbac.user.dto.UserPositionRequest;
import cn.nihility.rbac.user.dto.UserPositionVO;
import cn.nihility.rbac.user.dto.UserUpdateRequest;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.UserConvert;
import cn.nihility.rbac.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
import org.springframework.util.StringUtils;

/**
 * 用户业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 用户任职记录数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 组织数据访问接口，仅用于回填任职记录的所属组织名称。 */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<UserVO> getPage(String name, String mobile, String idCard, Integer page, Integer pageSize) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .ne(UserEntity::getStatus, UserStatus.DELETED)
                .like(StringUtils.hasText(name), UserEntity::getName, name)
                .like(StringUtils.hasText(mobile), UserEntity::getMobile, mobile)
                .like(StringUtils.hasText(idCard), UserEntity::getIdCard, idCard)
                .orderByDesc(UserEntity::getShowOrder)
                .orderByAsc(UserEntity::getId);

        Page<UserEntity> queryPage = new Page<>(page, pageSize);
        Page<UserEntity> resultPage = userMapper.selectPage(queryPage, wrapper);
        List<UserVO> records = UserConvert.INSTANCE.toVOList(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserVO getById(Long id) {
        UserEntity entity = getExistingEntity(id);
        UserVO vo = UserConvert.INSTANCE.toVO(entity);
        vo.setPositions(listPositionsWithOrgName(id));
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserVO create(UserCreateRequest request) {
        checkCodeUnique(request.getCode(), null);
        checkIdCardUnique(request.getIdCard(), null);

        UserEntity entity = UserConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(UserStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        userMapper.insert(entity);

        syncPositions(entity.getId(), request.getPositions());

        operationLogRecorder.recordCreate(OperationLogResourceType.USER, entity.getId(), entity.getName(),
                toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserVO update(Long id, UserUpdateRequest request) {
        UserEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        checkIdCardUnique(request.getIdCard(), id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        UserConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        syncPositions(id, request.getPositions());

        operationLogRecorder.recordUpdate(OperationLogResourceType.USER, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserVO enable(Long id) {
        return changeStatus(id, UserStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserVO disable(Long id) {
        return changeStatus(id, UserStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        UserEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(UserStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.USER, id, entity.getName(), beforeSnapshot);
    }

    /**
     * 变更用户状态（启用/停用）并返回更新后的详情。
     *
     * @param id     用户 id
     * @param status 目标状态
     * @return 更新后的用户详情
     */
    private UserVO changeStatus(Long id, int status) {
        UserEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.USER, id, entity.getName(),
                status == UserStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的用户，不存在时抛出业务异常。
     *
     * @param id 用户 id
     * @return 用户实体
     */
    private UserEntity getExistingEntity(Long id) {
        UserEntity entity = userMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), UserStatus.DELETED)) {
            throw new BusinessException("用户不存在");
        }
        return entity;
    }

    /**
     * 校验用户编号在未删除的用户中是否唯一。
     *
     * @param code      待校验的用户编号
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getCode, code)
                .ne(UserEntity::getStatus, UserStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("用户编号[" + code + "]已存在");
        }
    }

    /**
     * 校验身份证号（若提供）在未删除的用户中是否唯一。
     *
     * @param idCard    待校验的身份证号，为空时不做校验
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkIdCardUnique(String idCard, Long excludeId) {
        if (!StringUtils.hasText(idCard)) {
            return;
        }
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getIdCard, idCard)
                .ne(UserEntity::getStatus, UserStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(UserEntity::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("身份证号[" + idCard + "]已存在");
        }
    }

    /**
     * 按用户 id 查询其全部未被逻辑删除的任职记录（按显示序号降序、id 升序排列），
     * 并批量回填组织名称。
     *
     * @param userId 用户 id
     * @return 任职记录视图对象列表
     */
    private List<UserPositionVO> listPositionsWithOrgName(Long userId) {
        List<UserPositionEntity> entities = userPositionMapper.selectList(new LambdaQueryWrapper<UserPositionEntity>()
                .eq(UserPositionEntity::getUserId, userId)
                .ne(UserPositionEntity::getStatus, PositionStatus.DELETED)
                .orderByDesc(UserPositionEntity::getShowOrder)
                .orderByAsc(UserPositionEntity::getId));
        return toPositionVOListWithOrgName(entities);
    }

    /**
     * 把任职记录实体列表转换为视图对象列表，并批量解析所属组织名称，避免逐条查询组织表。
     *
     * @param entities 任职记录实体列表
     * @return 任职记录视图对象列表
     */
    private List<UserPositionVO> toPositionVOListWithOrgName(List<UserPositionEntity> entities) {
        List<UserPositionVO> result = UserConvert.INSTANCE.toPositionVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        List<Long> orgIds = entities.stream().map(UserPositionEntity::getOrgId).distinct().toList();
        List<OrgEntity> orgs = orgMapper.selectList(new LambdaQueryWrapper<OrgEntity>().in(OrgEntity::getId, orgIds));
        Map<Long, String> orgNameMap = orgs.stream()
                .collect(Collectors.toMap(OrgEntity::getId, OrgEntity::getName, (left, right) -> left));

        for (UserPositionVO vo : result) {
            vo.setOrgName(orgNameMap.get(vo.getOrgId()));
        }
        return result;
    }

    /**
     * 把请求中的任职记录列表与用户当前未被逻辑删除的既有任职记录做增量 diff：携带
     * {@code id} 且属于当前用户未被逻辑删除的既有记录的按行更新（保留原创建审计信息，
     * 刷新更新审计信息，不修改其 {@code status}）；不携带 {@code id} 的作为新记录插入，
     * {@code status} 显式置为 {@link PositionStatus#ENABLED}；当前用户未被逻辑删除的既有
     * 记录中未出现在本次请求列表中的物理删除；请求携带不属于当前用户的任职记录 id 时拒绝
     * 整个更新。已被逻辑删除的既有记录不参与本次 diff。任职记录本身的操作日志由独立的
     * 任职管理入口（{@code PositionServiceImpl}）记录，这里不重复记录。
     *
     * @param userId    用户 id
     * @param positions 请求中的任职记录列表，可为空（视为清空全部既有任职记录）
     */
    private void syncPositions(Long userId, List<UserPositionRequest> positions) {
        List<UserPositionRequest> requests = positions != null ? positions : List.of();

        List<UserPositionEntity> existingList = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPositionEntity>()
                        .eq(UserPositionEntity::getUserId, userId)
                        .ne(UserPositionEntity::getStatus, PositionStatus.DELETED));
        Map<Long, UserPositionEntity> existingById = existingList.stream()
                .collect(Collectors.toMap(UserPositionEntity::getId, entity -> entity));

        for (UserPositionRequest request : requests) {
            if (request.getId() != null && !existingById.containsKey(request.getId())) {
                throw new BusinessException("任职记录[" + request.getId() + "]不属于当前用户，无法更新");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Set<Long> keptIds = new HashSet<>();
        for (UserPositionRequest request : requests) {
            if (request.getId() != null) {
                UserPositionEntity entity = existingById.get(request.getId());
                UserConvert.INSTANCE.updatePositionEntity(request, entity);
                entity.setUpdateBy(DEFAULT_OPERATOR);
                entity.setUpdateTime(now);
                userPositionMapper.updateById(entity);
                keptIds.add(entity.getId());
            } else {
                UserPositionEntity entity = UserConvert.INSTANCE.toPositionEntity(request);
                entity.setUserId(userId);
                entity.setStatus(PositionStatus.ENABLED);
                entity.setCreateBy(DEFAULT_OPERATOR);
                entity.setCreateTime(now);
                entity.setUpdateBy(DEFAULT_OPERATOR);
                entity.setUpdateTime(now);
                userPositionMapper.insert(entity);
            }
        }

        List<Long> idsToDelete = existingList.stream()
                .map(UserPositionEntity::getId)
                .filter(existingId -> !keptIds.contains(existingId))
                .toList();
        if (!idsToDelete.isEmpty()) {
            userPositionMapper.deleteByIds(idsToDelete);
        }
    }

    /**
     * 构造用户实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值。
     *
     * @param entity 用户实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(UserEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("姓名", entity.getName());
        snapshot.put("编码", entity.getCode());
        snapshot.put("性别", genderLabel(entity.getGender()));
        snapshot.put("手机号", entity.getMobile());
        snapshot.put("身份证号", entity.getIdCard());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        return snapshot;
    }

    /**
     * 把性别码值转换为中文文案，供操作日志快照使用。
     *
     * @param gender 性别码值
     * @return 中文文案
     */
    private String genderLabel(Integer gender) {
        if (Objects.equals(gender, UserGender.MALE)) {
            return "男";
        }
        if (Objects.equals(gender, UserGender.FEMALE)) {
            return "女";
        }
        return "未知";
    }

    /**
     * 把用户状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, UserStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, UserStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
