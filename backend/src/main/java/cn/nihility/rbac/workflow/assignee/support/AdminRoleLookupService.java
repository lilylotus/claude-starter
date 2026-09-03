package cn.nihility.rbac.workflow.assignee.support;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.entity.AdminOrgScopeEntity;
import cn.nihility.rbac.admin.entity.AdminRoleEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.admin.mapper.AdminOrgScopeMapper;
import cn.nihility.rbac.admin.mapper.AdminRoleMapper;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 管理员角色/组织管辖范围查询辅助组件，供多个基于角色/组织负责人的
 * {@link cn.nihility.rbac.workflow.assignee.AssigneeResolver} 实现与任务候选组越权校验复用，
 * 避免重复编写"角色编码 -&gt; 持有该角色的启用管理员 -&gt; 关联用户 id"这段查询逻辑。
 * <p>
 * {@code tab_admin_org_scope} 本是为"管理员管辖范围"设计的表，这里被复用为"组织负责人"
 * 数据源：管辖范围覆盖目标组织（{@code org_id} 命中，或 {@code include_children=1} 且目标
 * 组织在其子树）、且持有指定角色编码、状态启用的管理员，即视为该组织的负责人
 * （workflow-approval-engine change design.md Decision 5 / Risks）。
 */
@Component
@RequiredArgsConstructor
public class AdminRoleLookupService {

    /** 角色数据访问接口。 */
    private final RoleMapper roleMapper;

    /** 管理员数据访问接口。 */
    private final AdminMapper adminMapper;

    /** 管理员角色关联数据访问接口。 */
    private final AdminRoleMapper adminRoleMapper;

    /** 管理员组织管辖范围数据访问接口。 */
    private final AdminOrgScopeMapper adminOrgScopeMapper;

    /** 组织数据访问接口。 */
    private final OrgMapper orgMapper;

    /**
     * 查询持有指定角色编码、状态启用的全部管理员关联用户 id。
     *
     * @param roleCode 角色编码
     * @return 用户 id 集合，角色不存在或无人持有时返回空集合
     */
    public Set<Long> findUserIdsByRoleCode(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return Set.of();
        }
        Set<Long> adminIds = findAdminIdsByRoleCode(roleCode);
        if (adminIds.isEmpty()) {
            return Set.of();
        }
        return enabledAdminUserIds(adminIds);
    }

    /**
     * 判断指定用户是否以启用状态的管理员身份持有指定角色。
     *
     * @param userId   用户 id
     * @param roleCode 角色编码
     * @return 是否持有
     */
    public boolean userHasRoleCode(Long userId, String roleCode) {
        if (userId == null || !StringUtils.hasText(roleCode)) {
            return false;
        }
        List<AdminEntity> admins = adminMapper.selectList(new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getUserId, userId)
                .eq(AdminEntity::getStatus, AdminStatus.ENABLED));
        if (admins.isEmpty()) {
            return false;
        }
        Set<Long> adminIds = admins.stream().map(AdminEntity::getId).collect(Collectors.toSet());
        Set<Long> roleAdminIds = findAdminIdsByRoleCode(roleCode);
        return roleAdminIds.stream().anyMatch(adminIds::contains);
    }

    /**
     * 查询管辖范围覆盖目标组织、且持有指定角色编码、状态启用的管理员关联用户 id
     * （即目标组织的"负责人"）。
     *
     * @param targetOrgId 目标组织 id
     * @param roleCode    要求持有的管理员角色编码
     * @return 用户 id 集合，无法解析出负责人时返回空集合
     */
    public Set<Long> findOrgLeaderUserIds(Long targetOrgId, String roleCode) {
        if (targetOrgId == null || !StringUtils.hasText(roleCode)) {
            return Set.of();
        }
        Set<Long> roleAdminIds = findAdminIdsByRoleCode(roleCode);
        if (roleAdminIds.isEmpty()) {
            return Set.of();
        }
        OrgEntity org = orgMapper.selectById(targetOrgId);
        if (org == null || !StringUtils.hasText(org.getOrgPath())) {
            return Set.of();
        }
        Set<String> ancestorIdTexts = Arrays.stream(org.getOrgPath().split("/"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        List<AdminOrgScopeEntity> scopes = adminOrgScopeMapper.selectList(
                new LambdaQueryWrapper<AdminOrgScopeEntity>().in(AdminOrgScopeEntity::getAdminId, roleAdminIds));
        Set<Long> matchedAdminIds = new HashSet<>();
        for (AdminOrgScopeEntity scope : scopes) {
            boolean directHit = targetOrgId.equals(scope.getOrgId());
            boolean recursiveHit = Boolean.TRUE.equals(scope.getIncludeChildren())
                    && ancestorIdTexts.contains(String.valueOf(scope.getOrgId()));
            if (directHit || recursiveHit) {
                matchedAdminIds.add(scope.getAdminId());
            }
        }
        if (matchedAdminIds.isEmpty()) {
            return Set.of();
        }
        return enabledAdminUserIds(matchedAdminIds);
    }

    /**
     * 从目标组织的父级路径起，沿组织路径向上逐级查找第一个能解析出负责人的上级组织。
     *
     * @param orgId    起始组织 id（通常为发起人所属组织），本方法从其父级开始查找，不含自身
     * @param roleCode 要求持有的管理员角色编码
     * @return 用户 id 集合，全部上级组织均无法解析出负责人时返回空集合
     */
    public Set<Long> findParentOrgLeaderUserIds(Long orgId, String roleCode) {
        if (orgId == null || !StringUtils.hasText(roleCode)) {
            return Set.of();
        }
        OrgEntity org = orgMapper.selectById(orgId);
        if (org == null || !StringUtils.hasText(org.getOrgParentPath())) {
            return Set.of();
        }
        List<String> ancestorIdTexts = Arrays.stream(org.getOrgParentPath().split("/"))
                .filter(StringUtils::hasText)
                .toList();
        for (int i = ancestorIdTexts.size() - 1; i >= 0; i--) {
            Long ancestorOrgId = Long.valueOf(ancestorIdTexts.get(i));
            Set<Long> result = findOrgLeaderUserIds(ancestorOrgId, roleCode);
            if (!result.isEmpty()) {
                return result;
            }
        }
        return Set.of();
    }

    /**
     * 查询持有指定角色编码的全部管理员 id。
     */
    private Set<Long> findAdminIdsByRoleCode(String roleCode) {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, roleCode)
                .ne(RoleEntity::getStatus, RoleStatus.DELETED));
        if (role == null) {
            return Set.of();
        }
        List<AdminRoleEntity> adminRoles = adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRoleEntity>().eq(AdminRoleEntity::getRoleId, role.getId()));
        return adminRoles.stream().map(AdminRoleEntity::getAdminId).collect(Collectors.toSet());
    }

    /**
     * 按管理员 id 集合查询状态启用的管理员关联用户 id。
     */
    private Set<Long> enabledAdminUserIds(Set<Long> adminIds) {
        List<AdminEntity> admins = adminMapper.selectList(new LambdaQueryWrapper<AdminEntity>()
                .in(AdminEntity::getId, adminIds)
                .eq(AdminEntity::getStatus, AdminStatus.ENABLED));
        return admins.stream().map(AdminEntity::getUserId).collect(Collectors.toSet());
    }
}
