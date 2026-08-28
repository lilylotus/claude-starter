package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.dict.dto.DictItemOptionVO;
import cn.nihility.rbac.dict.service.DictItemService;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.DynamicFieldValidator;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.app.sync.constant.SyncDomain;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.sync.event.DomainChangeEvent;
import cn.nihility.rbac.sync.event.DomainEventPublisher;
import cn.nihility.rbac.user.constant.PositionStatus;
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
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.user.service.UserService;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import cn.nihility.rbac.user.service.support.PositionLogSnapshotSupport;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 用户业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

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

    /** 性别字段绑定的字典类型编码，用于操作日志快照把存储的字典编码解析为标签。 */
    private static final String GENDER_DICT_TYPE_CODE = "gender";

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

    /** 操作日志扩展字段快照填充组件，负责把字典类扩展字段的存储编码解析为标签。 */
    private final FormFieldSnapshotSupport formFieldSnapshotSupport;

    /** 字典项业务逻辑接口，用于把性别字段存储的字典编码解析为标签。 */
    private final DictItemService dictItemService;

    /**
     * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，用于内嵌任职
     * 子表单（{@code positions[]}）中每一项的动态字段校验，与独立任职管理入口
     * （{@code PositionServiceImpl}）共用同一份校验逻辑。
     */
    private final PositionDynamicFieldSupport positionDynamicFieldSupport;

    /**
     * 任职记录操作日志的被操作对象名称快照与字段快照共享组件，与独立任职管理入口
     * （{@code PositionServiceImpl}）共用同一份快照逻辑，供 {@link #syncPositions} 记录内嵌
     * 任职子表单产生的新增/编辑/删除操作日志时使用。
     */
    private final PositionLogSnapshotSupport positionLogSnapshotSupport;

    /**
     * 密码业务逻辑接口（{@code auth} 模块暴露），用于新增用户时联动创建默认密码记录、
     * 管理员重置密码；本模块只依赖该接口做依赖注入调用，不反向被 {@code auth} 依赖。
     */
    private final PasswordService passwordService;

    /**
     * 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围过滤用户列表
     * （user-org-scope-data-permission change design.md Decision 1）。
     */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * 数据变更事件发布抽象，用户数据新增/编辑/启用/停用/删除写操作成功后紧邻
     * {@code operationLogRecorder} 调用之后发布一次同步事件
     * （app-sync-notify-pull-api change design.md Decision 5）。
     */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * {@inheritDoc}
     * <p>
     * 受限时（管辖范围非空）追加"存在至少一条未删除、所属组织落在管辖范围内的任职记录"
     * 过滤条件，跨表条件写在 {@link UserMapper#selectUserPage} 对应的 XML 里，不在 Java
     * 端用 {@link LambdaQueryWrapper} 拼接（design.md Decision 2/4）。{@code selectUserPage}
     * 直接把 {@code tab_user} 整行映射到 {@link UserVO}，{@code createBy}/{@code updateBy}
     * 是登录用户 id 的原始字符串，需要就地批量回填为展示名。
     */
    @Override
    public PageResult<UserVO> getPage(String name, String mobile, String idCard, Integer page, Integer pageSize) {
        Optional<Set<Long>> allowedOrgIdsOptional = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());
        Set<Long> allowedOrgIds = allowedOrgIdsOptional.orElse(null);

        Page<UserVO> queryPage = new Page<>(page, pageSize);
        IPage<UserVO> resultPage = userMapper.selectUserPage(queryPage, name, mobile, idCard, allowedOrgIds,
                UserStatus.DELETED, PositionStatus.DELETED);
        List<UserVO> records = resultPage.getRecords();
        fillAuditDisplayNames(records);
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

        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(
                Stream.of(entity.getCreateBy(), entity.getUpdateBy()).collect(Collectors.toSet()));
        vo.setCreateBy(resolveDisplayName(entity.getCreateBy(), displayNames));
        vo.setUpdateBy(resolveDisplayName(entity.getUpdateBy(), displayNames));
        return vo;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 创建用户与创建默认密码记录（{@link PasswordService#createDefaultPassword}）需在同一
     * 事务内完成，因此整个方法标注 {@link Transactional}。
     */
    @Override
    @Transactional
    public UserVO create(UserCreateRequest request) {
        checkCodeUnique(request.getCode(), null);
        validateDynamicFields(request, true, null);
        validatePositionsDynamicFields(request.getPositions());

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        UserEntity entity = UserConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(UserStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        userMapper.insert(entity);

        passwordService.createDefaultPassword(entity.getId());

        syncPositions(entity.getId(), request.getPositions(), operator);

        operationLogRecorder.recordCreate(OperationLogResourceType.USER, entity.getId(), entity.getName(),
                toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(entity.getId())
                .operationType(OperationType.CREATE)
                .operator(entity.getCreateBy())
                .occurredAt(LocalDateTime.now())
                .build());

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

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        UserConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        syncPositions(id, request.getPositions(), operator);

        operationLogRecorder.recordUpdate(OperationLogResourceType.USER, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(id)
                .operationType(OperationType.UPDATE)
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());

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
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.USER, id, entity.getName(), beforeSnapshot);
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(id)
                .operationType(OperationType.DELETE)
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetPassword(Long id) {
        getExistingEntity(id);
        passwordService.resetToDefault(id);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 不接入 {@link OrgScopeService}，与 {@link #getPage} 现状保持一致
     * （master-data-excel-export change design.md Decision 2）。{@code toVOList} 会
     * ignore {@code createBy}/{@code updateBy}（entity 落库内容是登录用户 id 的字符串），
     * 需要另行批量回填为展示名，复用 {@link #fillAuditDisplayNames}。
     */
    @Override
    public List<UserVO> listAllForExport() {
        List<UserEntity> entities = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .ne(UserEntity::getStatus, UserStatus.DELETED)
                .orderByAsc(UserEntity::getId));

        List<UserVO> records = UserConvert.INSTANCE.toVOList(entities);
        for (int i = 0; i < entities.size(); i++) {
            records.get(i).setCreateBy(entities.get(i).getCreateBy());
            records.get(i).setUpdateBy(entities.get(i).getUpdateBy());
        }
        fillAuditDisplayNames(records);
        return records;
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
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.USER, id, entity.getName(),
                status == UserStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        domainEventPublisher.publish(DomainChangeEvent.builder()
                .dataType(SyncDomain.USER)
                .bizId(id)
                .operationType(status == UserStatus.ENABLED ? OperationType.ENABLE : OperationType.DISABLE)
                .operator(entity.getUpdateBy())
                .occurredAt(LocalDateTime.now())
                .build());
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
     * 把任职记录实体列表转换为视图对象列表，并批量解析所属组织名称、审计字段展示名，
     * 避免逐条查询组织表/用户表。
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

        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (int i = 0; i < entities.size(); i++) {
            UserPositionVO vo = result.get(i);
            vo.setOrgName(orgNameMap.get(vo.getOrgId()));
            vo.setCreateBy(resolveDisplayName(entities.get(i).getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(entities.get(i).getUpdateBy(), displayNames));
        }
        return result;
    }

    /**
     * 批量把一批用户视图对象自身携带的 {@code createBy}/{@code updateBy}（{@code selectUserPage}
     * 直接由 SQL 映射得到，原始内容是登录用户 id 的字符串）就地回填为"姓名（账号编码）"
     * 展示名，避免逐条查询造成 N+1。
     *
     * @param voList 用户视图对象列表，原地修改
     */
    private void fillAuditDisplayNames(List<UserVO> voList) {
        if (voList.isEmpty()) {
            return;
        }
        Set<String> auditUserIdTexts = voList.stream()
                .flatMap(vo -> Stream.of(vo.getCreateBy(), vo.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);
        for (UserVO vo : voList) {
            vo.setCreateBy(resolveDisplayName(vo.getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(vo.getUpdateBy(), displayNames));
        }
    }

    /**
     * 把审计字段原始值（登录用户 id 的字符串）解析为展示名；空白值回退空字符串，
     * 查不到对应用户（如账号已被物理清理）时回退"未知用户"文案。
     *
     * @param userIdText   审计字段原始值
     * @param displayNames 批量解析得到的"用户 id 文本 -> 展示名"映射
     * @return 展示名
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }

    /**
     * 把请求中的任职记录列表与用户当前未被逻辑删除的既有任职记录做增量 diff：携带
     * {@code id} 且属于当前用户未被逻辑删除的既有记录的按行更新（保留原创建审计信息，
     * 刷新更新审计信息，不修改其 {@code status}）；不携带 {@code id} 的作为新记录插入，
     * {@code status} 显式置为 {@link PositionStatus#ENABLED}；当前用户未被逻辑删除的既有
     * 记录中未出现在本次请求列表中的物理删除；请求携带不属于当前用户的任职记录 id 时拒绝
     * 整个更新。已被逻辑删除的既有记录不参与本次 diff。新增、更新、物理删除三个分支各自追加
     * 一次 {@code POSITION} 资源操作日志记录（字段快照口径与独立任职管理入口一致，见
     * {@link PositionLogSnapshotSupport}），使内嵌子表单产生的任职记录变更也能在该记录自己的
     * 详情页操作历史中查看。
     *
     * @param userId    用户 id
     * @param positions 请求中的任职记录列表，可为空（视为清空全部既有任职记录）
     * @param operator  操作人用户 id 的字符串，由调用方在方法内只解析一次后传入，避免重复解析
     */
    private void syncPositions(Long userId, List<UserPositionRequest> positions, String operator) {
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
                Map<String, Object> beforeSnapshot = positionLogSnapshotSupport.snapshot(entity);

                UserConvert.INSTANCE.updatePositionEntity(request, entity);
                entity.setUpdateBy(operator);
                entity.setUpdateTime(now);
                userPositionMapper.updateById(entity);
                keptIds.add(entity.getId());

                operationLogRecorder.recordUpdate(OperationLogResourceType.POSITION, entity.getId(),
                        positionLogSnapshotSupport.targetName(entity), beforeSnapshot,
                        positionLogSnapshotSupport.snapshot(entity));
            } else {
                UserPositionEntity entity = UserConvert.INSTANCE.toPositionEntity(request);
                entity.setUserId(userId);
                entity.setStatus(PositionStatus.ENABLED);
                entity.setCreateBy(operator);
                entity.setCreateTime(now);
                entity.setUpdateBy(operator);
                entity.setUpdateTime(now);
                userPositionMapper.insert(entity);

                operationLogRecorder.recordCreate(OperationLogResourceType.POSITION, entity.getId(),
                        positionLogSnapshotSupport.targetName(entity), positionLogSnapshotSupport.snapshot(entity));
            }
        }

        List<Long> idsToDelete = existingList.stream()
                .map(UserPositionEntity::getId)
                .filter(existingId -> !keptIds.contains(existingId))
                .toList();
        if (!idsToDelete.isEmpty()) {
            for (Long deleteId : idsToDelete) {
                UserPositionEntity entity = existingById.get(deleteId);
                operationLogRecorder.recordDelete(OperationLogResourceType.POSITION, deleteId,
                        positionLogSnapshotSupport.targetName(entity), positionLogSnapshotSupport.snapshot(entity));
            }
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
        formFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, definitions, extValues(entity));
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

    /**
     * 把性别字段存储的字典编码转换为标签，供操作日志快照使用；找不到对应字典项
     * （如字典项已停用/删除）时回退展示原始编码。
     *
     * @param genderCode 性别字典编码
     * @return 性别标签
     */
    private String genderLabel(String genderCode) {
        if (genderCode == null || genderCode.isBlank()) {
            return genderCode;
        }
        return dictItemService.getEnabledOptions(GENDER_DICT_TYPE_CODE).stream()
                .filter(option -> Objects.equals(option.getCode(), genderCode))
                .map(DictItemOptionVO::getLabel)
                .findFirst()
                .orElse(genderCode);
    }
}
