package cn.nihility.rbac.admin.service.impl;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.dto.AdminAppendRoleCandidateVO;
import cn.nihility.rbac.admin.dto.AdminBatchPromoteByRolePreviewVO;
import cn.nihility.rbac.admin.dto.AdminBatchPromoteByRoleResult;
import cn.nihility.rbac.admin.dto.AdminBatchPromoteCandidateVO;
import cn.nihility.rbac.admin.dto.AdminBatchPromoteSkippedVO;
import cn.nihility.rbac.admin.dto.AdminCreateRequest;
import cn.nihility.rbac.admin.dto.AdminOrgScopeRequest;
import cn.nihility.rbac.admin.dto.AdminOrgScopeVO;
import cn.nihility.rbac.admin.dto.AdminRoleVO;
import cn.nihility.rbac.admin.dto.AdminUpdateRequest;
import cn.nihility.rbac.admin.dto.AdminVO;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.entity.AdminRoleEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.admin.mapper.AdminRoleMapper;
import cn.nihility.rbac.admin.mapstruct.AdminConvert;
import cn.nihility.rbac.admin.service.AdminService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleGrantMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理员管理业务逻辑实现。角色关联（{@code tab_admin_role}）与组织管辖范围
 * （{@code tab_admin_org_scope}）在每次创建/更新时采用"先删后插"的整体同步策略，
 * 不做按行 diff（两张关联表都是纯关联/配置行，没有独立业务详情需要跨版本保留）。
 * {@link #previewBatchPromoteByRole}/{@link #batchPromoteByRole} 是例外：以
 * {@code user-role-assignment} 能力维护的规则执行结果表 {@code tab_user_role_rule_grant}
 * 为匹配来源，对"将补充角色"分组只做追加式的单条插入，不走整体替换语义，避免误删既有角色/
 * 组织管辖范围；"将新建管理员"分组创建的管理员记录会打上 {@code autoCreatedRoleId} 标记，
 * 供角色被规则收回时联动停用（add-user-role-batch-assignment change design.md
 * Decision 5/7，二次设计版本）。
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    /** 管理员数据访问接口。 */
    private final AdminMapper adminMapper;

    /** 管理员角色关联数据访问接口。 */
    private final AdminRoleMapper adminRoleMapper;

    /** 管理员组织管辖范围数据访问接口。 */
    private final AdminOrgScopeMapper adminOrgScopeMapper;

    /** 用户数据访问接口，仅用于回填关联用户姓名。 */
    private final UserMapper userMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段（{@code createBy}/{@code updateBy}）展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /** 角色数据访问接口，校验按角色批量设置管理员时目标角色存在且未删除。 */
    private final RoleMapper roleMapper;

    /** 用户角色规则计算结果数据访问接口，按角色批量设置管理员的候选用户匹配来源
     *  （add-user-role-batch-assignment change design.md Decision 5，二次设计版本：数据
     *  来源从已废弃的 {@code tab_user_role} 切换为规则执行结果表）。 */
    private final UserRoleRuleGrantMapper userRoleRuleGrantMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AdminVO> getPage(Integer page, Integer pageSize) {
        IPage<AdminVO> resultPage = adminMapper.selectAdminPage(new Page<>(page, pageSize), AdminStatus.DELETED);
        backfillDisplayNames(resultPage.getRecords());
        return PageResult.of(resultPage.getRecords(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO getById(Long id) {
        return getExistingVO(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO create(AdminCreateRequest request) {
        checkCodeUnique(request.getCode(), null);
        checkUserIdUnique(request.getUserId(), null);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        AdminEntity entity = AdminConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(AdminStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        adminMapper.insert(entity);

        syncRoles(entity.getId(), request.getRoleIds(), operator);
        syncOrgScopes(entity.getId(), request.getOrgScopes(), operator);

        operationLogRecorder.recordCreate(OperationLogResourceType.ADMIN, entity.getId(), entity.getName(),
                toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO update(Long id, AdminUpdateRequest request) {
        AdminEntity entity = getExistingEntity(id);
        checkCodeUnique(request.getCode(), id);
        checkUserIdUnique(request.getUserId(), id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        AdminConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);

        syncRoles(id, request.getRoleIds(), operator);
        syncOrgScopes(id, request.getOrgScopes(), operator);

        operationLogRecorder.recordUpdate(OperationLogResourceType.ADMIN, id, entity.getName(),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO enable(Long id) {
        return changeStatus(id, AdminStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminVO disable(Long id) {
        return changeStatus(id, AdminStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AdminEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(AdminStatus.DELETED);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.ADMIN, id, entity.getName(), beforeSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminBatchPromoteByRolePreviewVO previewBatchPromoteByRole(Long roleId) {
        getExistingRole(roleId);
        Set<Long> candidateUserIds = resolveEnabledCandidateUserIds(roleId);
        if (candidateUserIds.isEmpty()) {
            return AdminBatchPromoteByRolePreviewVO.builder()
                    .newAdmins(List.of()).newAdminCount(0)
                    .appendRoleAdmins(List.of()).appendRoleCount(0)
                    .build();
        }

        Map<Long, UserEntity> userById = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getId, candidateUserIds))
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));

        List<AdminEntity> existingAdmins = adminMapper.selectList(new LambdaQueryWrapper<AdminEntity>()
                .in(AdminEntity::getUserId, candidateUserIds)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED));
        Map<Long, AdminEntity> adminByUserId = existingAdmins.stream()
                .collect(Collectors.toMap(AdminEntity::getUserId, Function.identity()));

        Set<Long> existingAdminIds = existingAdmins.stream().map(AdminEntity::getId).collect(Collectors.toSet());
        Set<Long> adminIdsAlreadyHavingRole = existingAdminIds.isEmpty() ? Set.of()
                : adminRoleMapper.selectList(new LambdaQueryWrapper<AdminRoleEntity>()
                        .in(AdminRoleEntity::getAdminId, existingAdminIds)
                        .eq(AdminRoleEntity::getRoleId, roleId))
                    .stream().map(AdminRoleEntity::getAdminId).collect(Collectors.toSet());

        List<AdminBatchPromoteCandidateVO> newAdmins = new ArrayList<>();
        List<AdminAppendRoleCandidateVO> appendRoleAdmins = new ArrayList<>();
        for (Long userId : candidateUserIds) {
            UserEntity user = userById.get(userId);
            if (user == null) {
                continue;
            }
            AdminEntity admin = adminByUserId.get(userId);
            if (admin == null) {
                newAdmins.add(AdminBatchPromoteCandidateVO.builder()
                        .userId(userId).userName(user.getName()).userCode(user.getCode()).build());
            } else if (!adminIdsAlreadyHavingRole.contains(admin.getId())) {
                appendRoleAdmins.add(AdminAppendRoleCandidateVO.builder()
                        .adminId(admin.getId()).adminName(admin.getName()).adminCode(admin.getCode())
                        .userId(userId).userName(user.getName()).build());
            }
        }

        return AdminBatchPromoteByRolePreviewVO.builder()
                .newAdmins(newAdmins).newAdminCount(newAdmins.size())
                .appendRoleAdmins(appendRoleAdmins).appendRoleCount(appendRoleAdmins.size())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public AdminBatchPromoteByRoleResult batchPromoteByRole(Long roleId, List<AdminOrgScopeRequest> orgScopes) {
        getExistingRole(roleId);
        AdminBatchPromoteByRolePreviewVO preview = previewBatchPromoteByRole(roleId);
        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);

        List<AdminBatchPromoteSkippedVO> skipped = new ArrayList<>();
        int newAdminCount = 0;
        for (AdminBatchPromoteCandidateVO candidate : preview.getNewAdmins()) {
            if (isCodeOccupiedByAnotherAdmin(candidate.getUserCode())) {
                skipped.add(AdminBatchPromoteSkippedVO.builder()
                        .userId(candidate.getUserId())
                        .userName(candidate.getUserName())
                        .userCode(candidate.getUserCode())
                        .reason("管理员编码[" + candidate.getUserCode() + "]已被占用")
                        .build());
                continue;
            }

            LocalDateTime now = LocalDateTime.now();
            AdminEntity entity = AdminEntity.builder()
                    .name(candidate.getUserName())
                    .code(candidate.getUserCode())
                    .userId(candidate.getUserId())
                    .autoCreatedRoleId(roleId)
                    .showOrder(0)
                    .remark(null)
                    .status(AdminStatus.ENABLED)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            adminMapper.insert(entity);
            syncRoles(entity.getId(), List.of(roleId), operator);
            syncOrgScopes(entity.getId(), orgScopes, operator);
            newAdminCount++;
        }

        int appendRoleCount = 0;
        for (AdminAppendRoleCandidateVO candidate : preview.getAppendRoleAdmins()) {
            appendRoleIfMissing(candidate.getAdminId(), roleId, operator);
            appendRoleCount++;
        }

        return AdminBatchPromoteByRoleResult.builder()
                .newAdminCount(newAdminCount)
                .appendRoleCount(appendRoleCount)
                .skipped(skipped)
                .build();
    }

    /**
     * 查询一个未被逻辑删除的角色，不存在时抛出业务异常。
     *
     * @param roleId 角色 id
     * @return 角色实体
     */
    private RoleEntity getExistingRole(Long roleId) {
        RoleEntity entity = roleMapper.selectById(roleId);
        if (entity == null || Objects.equals(entity.getStatus(), RoleStatus.DELETED)) {
            throw new BusinessException("角色不存在");
        }
        return entity;
    }

    /**
     * 查询当前持有目标角色、且状态启用的候选用户 id 集合：先按 {@code tab_user_role_rule_grant}
     * 查出持有该角色的用户 id（去重），再按 {@code tab_user.status = ENABLED} 收窄。
     *
     * @param roleId 目标角色 id
     * @return 候选用户 id 集合
     */
    private Set<Long> resolveEnabledCandidateUserIds(Long roleId) {
        List<Long> userIds = userRoleRuleGrantMapper.selectUserIdsByRoleId(roleId);
        if (userIds.isEmpty()) {
            return Set.of();
        }

        List<UserEntity> enabledUsers = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                .in(UserEntity::getId, userIds)
                .eq(UserEntity::getStatus, UserStatus.ENABLED));
        return enabledUsers.stream().map(UserEntity::getId).collect(Collectors.toSet());
    }

    /**
     * 判断给定编码是否已被某个未删除的管理员占用（不排除任何自身 id，仅用于批量创建新管理员
     * 前的冲突检查，冲突场景下跳过该用户、不创建，见 {@link #batchPromoteByRole}）。
     *
     * @param code 待校验的管理员编码
     * @return 是否已被占用
     */
    private boolean isCodeOccupiedByAnotherAdmin(String code) {
        Long count = adminMapper.selectCount(new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getCode, code)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED));
        return count != null && count > 0;
    }

    /**
     * 仅向既有管理员追加一条角色关联（如果尚未持有该角色），不经过"编辑管理员"的整体替换
     * 语义，不触碰该管理员的其他字段、其他已有角色、已有管辖组织范围。
     *
     * @param adminId  既有管理员 id
     * @param roleId   待追加的角色 id
     * @param operator 操作人用户 id 文本
     */
    private void appendRoleIfMissing(Long adminId, Long roleId, String operator) {
        Long count = adminRoleMapper.selectCount(new LambdaQueryWrapper<AdminRoleEntity>()
                .eq(AdminRoleEntity::getAdminId, adminId)
                .eq(AdminRoleEntity::getRoleId, roleId));
        if (count != null && count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        adminRoleMapper.insert(AdminRoleEntity.builder()
                .adminId(adminId)
                .roleId(roleId)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build());
    }

    /**
     * 变更管理员状态（启用/停用）并返回更新后的详情，不影响角色关联与组织管辖范围。
     *
     * @param id     管理员 id
     * @param status 目标状态
     * @return 更新后的管理员详情
     */
    private AdminVO changeStatus(Long id, int status) {
        AdminEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        adminMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.ADMIN, id, entity.getName(),
                status == AdminStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的管理员，不存在时抛出业务异常。
     *
     * @param id 管理员 id
     * @return 管理员实体
     */
    private AdminEntity getExistingEntity(Long id) {
        AdminEntity entity = adminMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), AdminStatus.DELETED)) {
            throw new BusinessException("管理员不存在");
        }
        return entity;
    }

    /**
     * 查询一个未被逻辑删除的管理员详情，含关联用户姓名、全部角色、全部管辖组织范围，
     * 不存在时抛出业务异常。
     *
     * @param id 管理员 id
     * @return 管理员详情视图对象
     */
    private AdminVO getExistingVO(Long id) {
        AdminVO vo = adminMapper.selectAdminDetail(id, AdminStatus.DELETED);
        if (vo == null) {
            throw new BusinessException("管理员不存在");
        }
        vo.setRoles(adminRoleMapper.selectRolesByAdminId(id));
        vo.setOrgScopes(adminOrgScopeMapper.selectOrgScopesByAdminId(id));
        backfillDisplayNames(List.of(vo));
        return vo;
    }

    /**
     * 校验管理员编码在未删除的管理员中是否唯一。
     *
     * @param code      待校验的管理员编码
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<AdminEntity> wrapper = new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getCode, code)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AdminEntity::getId, excludeId);
        }
        Long count = adminMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("管理员编码[" + code + "]已存在");
        }
    }

    /**
     * 校验关联用户 id 在未删除的管理员中是否唯一（同一用户最多关联一个未删除的管理员身份）。
     *
     * @param userId    待校验的关联用户 id
     * @param excludeId 更新场景下需要排除的自身 id，创建场景传 null
     */
    private void checkUserIdUnique(Long userId, Long excludeId) {
        LambdaQueryWrapper<AdminEntity> wrapper = new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getUserId, userId)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED);
        if (excludeId != null) {
            wrapper.ne(AdminEntity::getId, excludeId);
        }
        Long count = adminMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("用户[" + userId + "]已关联其他管理员");
        }
    }

    /**
     * 把请求中的角色 id 列表与管理员当前的角色关联做整体同步：先物理删除该管理员名下
     * 全部既有角色关联行，再按请求列表整批插入，不做按行 diff。
     *
     * @param adminId  管理员 id
     * @param roleIds  请求中的角色 id 列表，可为空（视为清空全部既有角色关联）
     * @param operator 操作人用户 id 文本，由调用方在方法内只解析一次后传入，避免重复解析
     */
    private void syncRoles(Long adminId, List<Long> roleIds, String operator) {
        adminRoleMapper.delete(new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getAdminId, adminId));

        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long roleId : roleIds) {
            AdminRoleEntity entity = AdminRoleEntity.builder()
                    .adminId(adminId)
                    .roleId(roleId)
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            adminRoleMapper.insert(entity);
        }
    }

    /**
     * 把请求中的管辖组织范围列表与管理员当前的组织管辖范围做整体同步：先物理删除该管理员
     * 名下全部既有管辖组织范围行，再按请求列表整批插入，不做按行 diff。
     *
     * @param adminId   管理员 id
     * @param orgScopes 请求中的管辖组织范围列表，可为空（视为清空全部既有管辖组织范围）
     * @param operator  操作人用户 id 文本，由调用方在方法内只解析一次后传入，避免重复解析
     */
    private void syncOrgScopes(Long adminId, List<AdminOrgScopeRequest> orgScopes, String operator) {
        adminOrgScopeMapper.delete(new LambdaQueryWrapper<AdminOrgScopeEntity>()
                .eq(AdminOrgScopeEntity::getAdminId, adminId));

        if (orgScopes == null || orgScopes.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (AdminOrgScopeRequest request : orgScopes) {
            AdminOrgScopeEntity entity = AdminOrgScopeEntity.builder()
                    .adminId(adminId)
                    .orgId(request.getOrgId())
                    .includeChildren(request.getIncludeChildren())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            adminOrgScopeMapper.insert(entity);
        }
    }

    /**
     * 批量把管理员详情/列表视图对象的 {@code createBy}/{@code updateBy} 字段（此时仍是
     * {@link cn.nihility.rbac.admin.mapper.AdminMapper} 的 XML 查询直接落入的用户 id 文本，
     * 而非经由 {@link AdminConvert} 转换得到）就地覆盖为人可读展示名。
     *
     * @param voList 待回填的管理员详情/列表视图对象列表
     */
    private void backfillDisplayNames(List<AdminVO> voList) {
        Set<String> auditUserIdTexts = voList.stream()
                .flatMap(vo -> Stream.of(vo.getCreateBy(), vo.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (AdminVO vo : voList) {
            vo.setCreateBy(resolveDisplayName(vo.getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(vo.getUpdateBy(), displayNames));
        }
    }

    /**
     * 把审计字段原始存储的用户 id 文本解析为人可读展示名，查不到时兜底为"未知用户"，
     * 避免直接把不可读的 id 数字暴露给前端。
     *
     * @param userIdText   审计字段原始存储的用户 id 文本
     * @param displayNames 批量解析得到的用户 id 文本到展示名的映射
     * @return 人可读展示名
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }

    /**
     * 构造管理员实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；
     * 关联用户姓名需按 {@code userId} 回查一次，管辖角色/管辖组织范围各按
     * {@code adminId} 查询当前关联行，并汇总成一个可读字符串字段，不逐行 diff。
     *
     * @param entity 管理员实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(AdminEntity entity) {
        UserEntity user = entity.getUserId() != null ? userMapper.selectById(entity.getUserId()) : null;
        List<AdminRoleVO> roles = adminRoleMapper.selectRolesByAdminId(entity.getId());
        List<AdminOrgScopeVO> orgScopes = adminOrgScopeMapper.selectOrgScopesByAdminId(entity.getId());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("管理员名称", entity.getName());
        snapshot.put("管理员编码", entity.getCode());
        snapshot.put("关联用户", user != null ? user.getName() : null);
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));
        snapshot.put("管辖角色", joinRoleNames(roles));
        snapshot.put("管辖组织范围", joinOrgScopeNames(orgScopes));
        return snapshot;
    }

    /**
     * 把管辖角色列表汇总成一个以顿号分隔的可读字符串。
     *
     * @param roles 管辖角色列表
     * @return 汇总字符串，列表为空时返回 {@code null}
     */
    private String joinRoleNames(List<AdminRoleVO> roles) {
        if (roles == null || roles.isEmpty()) {
            return null;
        }
        return roles.stream().map(AdminRoleVO::getRoleName).collect(Collectors.joining("、"));
    }

    /**
     * 把管辖组织范围列表汇总成一个以顿号分隔的可读字符串，包含递归子组织的条目
     * 附加"(含子组织)"后缀。
     *
     * @param orgScopes 管辖组织范围列表
     * @return 汇总字符串，列表为空时返回 {@code null}
     */
    private String joinOrgScopeNames(List<AdminOrgScopeVO> orgScopes) {
        if (orgScopes == null || orgScopes.isEmpty()) {
            return null;
        }
        return orgScopes.stream()
                .map(scope -> scope.getOrgName() + (Boolean.TRUE.equals(scope.getIncludeChildren()) ? "(含子组织)" : ""))
                .collect(Collectors.joining("、"));
    }

    /**
     * 把管理员状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, AdminStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, AdminStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
