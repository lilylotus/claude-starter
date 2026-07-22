package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.DynamicFieldValidator;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
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
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
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

    /**
     * {@code bizType=USER} 下允许被动态字段唯一性校验拼进 {@code ${column}} 的列名
     * 白名单，取自 {@code tab_metadata_field} 目录里 USER 的原有可配置列 +
     * {@code ext1}..{@code ext10}（design.md Decision 3/8）。身份证号（{@code idCard}）
     * 的唯一性校验已从此前的硬编码 {@code checkIdCardUnique} 迁移到这条数据驱动管线，
     * 由默认字段定义（{@code isUnique=true}）驱动，管理员可在"表单管理"页面调整。
     */
    private static final Set<String> ALLOWED_DYNAMIC_COLUMNS = Set.of(
            "name", "code", "mobile", "id_card", "show_order", "remark",
            "ext1", "ext2", "ext3", "ext4", "ext5", "ext6", "ext7", "ext8", "ext9", "ext10");

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 用户任职记录数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 组织数据访问接口，仅用于回填任职记录的所属组织名称。 */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 表单字段定义业务逻辑接口，用于驱动非锁定字段的必填/正则/唯一性校验。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，用于内嵌任职
     * 子表单（{@code positions[]}）中每一项的动态字段校验，与独立任职管理入口
     * （{@code PositionServiceImpl}）共用同一份校验逻辑。
     */
    private final PositionDynamicFieldSupport positionDynamicFieldSupport;

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
        validateDynamicFields(request, true, null);
        validatePositionsDynamicFields(request.getPositions());

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
        validateDynamicFields(request, false, id);
        validatePositionsDynamicFields(request.getPositions());
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
     * 对非锁定（{@code locked=false}）且适用于当前场景的字段定义执行必填、正则、
     * 唯一性校验（design.md Decision 9）。{@code name}/{@code code} 属于承重字段，
     * 已被 {@link FormFieldDefinitionService#listActiveByBizType} 排除，不受本方法
     * 影响，继续依赖上面既有的 {@code checkCodeUnique}/Bean Validation；身份证号的
     * 唯一性校验由本方法驱动（默认字段定义 {@code isUnique=true}），不再单独硬编码。
     *
     * @param request   创建或更新请求，按 {@code fieldCode} 反射读取字段值
     * @param creating  是否为新增场景
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 {@code null}
     */
    private void validateDynamicFields(Object request, boolean creating, Long excludeId) {
        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.USER);
        DynamicFieldValidator.validate(definitions, request, creating, (column, value) -> {
            if (!ALLOWED_DYNAMIC_COLUMNS.contains(column)) {
                throw new BusinessException("非法的动态字段列名：" + column);
            }
            return userMapper.countByColumnValue(column, value, excludeId);
        });
    }

    /**
     * 对内嵌任职子表单中的每一项任职记录执行 {@code bizType=POSITION} 的动态字段
     * 必填/正则/唯一性校验，复用独立任职管理入口共用的 {@link PositionDynamicFieldSupport}，
     * 避免重复实现一套校验逻辑。每一项是否为"新增"场景按其自身是否携带 {@code id} 判断，
     * 与 {@link #syncPositions} 的按行 diff 逻辑保持一致。
     *
     * @param positions 内嵌任职子表单提交的任职记录请求列表，可为空
     */
    private void validatePositionsDynamicFields(List<UserPositionRequest> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (UserPositionRequest position : positions) {
            boolean creating = position.getId() == null;
            positionDynamicFieldSupport.validate(position, creating, position.getId());
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
     * 构造用户实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；末尾
     * 追加当前启用的 {@code ext1}..{@code ext10} 扩展字段（key 使用字段定义的展示名）。
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

        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.USER);
        FormFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, definitions, extValues(entity));
        return snapshot;
    }

    /**
     * 把用户实体的 {@code ext1}..{@code ext10} 逐一收集为列名到当前值的映射，
     * 供 {@link FormFieldSnapshotSupport#appendExtFieldSnapshot} 使用。
     *
     * @param entity 用户实体
     * @return {@code ext1}..{@code ext10} 列名到当前值的映射
     */
    private Map<String, String> extValues(UserEntity entity) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ext1", entity.getExt1());
        values.put("ext2", entity.getExt2());
        values.put("ext3", entity.getExt3());
        values.put("ext4", entity.getExt4());
        values.put("ext5", entity.getExt5());
        values.put("ext6", entity.getExt6());
        values.put("ext7", entity.getExt7());
        values.put("ext8", entity.getExt8());
        values.put("ext9", entity.getExt9());
        values.put("ext10", entity.getExt10());
        return values;
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
