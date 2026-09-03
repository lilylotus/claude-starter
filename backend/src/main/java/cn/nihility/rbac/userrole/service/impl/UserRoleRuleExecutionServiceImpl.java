package cn.nihility.rbac.userrole.service.impl;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.userrole.dto.UserRoleOrgScopeCondition;
import cn.nihility.rbac.userrole.dto.UserRoleUserAttrCondition;
import cn.nihility.rbac.userrole.entity.UserRoleRuleEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleGrantEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleOrgScopeEntity;
import cn.nihility.rbac.userrole.entity.UserRoleRuleUserAttrEntity;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleGrantMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleOrgScopeMapper;
import cn.nihility.rbac.userrole.mapper.UserRoleRuleUserAttrMapper;
import cn.nihility.rbac.userrole.service.UserRoleRuleExecutionService;
import cn.nihility.rbac.userrole.support.UserMatchConditionResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户角色规则执行引擎业务逻辑实现（design.md Decision 3/7）。
 */
@Service
@RequiredArgsConstructor
public class UserRoleRuleExecutionServiceImpl implements UserRoleRuleExecutionService {

    /** 用户角色规则数据访问接口。 */
    private final UserRoleRuleMapper userRoleRuleMapper;

    /** 用户角色规则组织范围条件数据访问接口。 */
    private final UserRoleRuleOrgScopeMapper userRoleRuleOrgScopeMapper;

    /** 用户角色规则用户属性条件数据访问接口。 */
    private final UserRoleRuleUserAttrMapper userRoleRuleUserAttrMapper;

    /** 用户角色规则计算结果数据访问接口。 */
    private final UserRoleRuleGrantMapper userRoleRuleGrantMapper;

    /** "组织范围 + 用户属性条件匹配用户"组件。 */
    private final UserMatchConditionResolver conditionResolver;

    /** 管理员数据访问接口，跨模块注入，用于"角色收回联动停用自动创建的管理员"检查
     *  （design.md Decision 7；跨模块直接注入其他模块 Mapper 是本仓库既有约定）。 */
    private final AdminMapper adminMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void execute(Long ruleId, String operator) {
        UserRoleRuleEntity rule = getExistingRule(ruleId);

        List<UserRoleOrgScopeCondition> orgScopes = loadOrgScopeConditions(ruleId);
        List<UserRoleUserAttrCondition> userAttrs = loadUserAttrConditions(ruleId);
        Set<Long> matched = conditionResolver.resolve(orgScopes, userAttrs);

        Set<Long> previouslyGrantedUserIds = loadGrantedUserIds(ruleId);

        Set<Long> toAdd = new HashSet<>(matched);
        toAdd.removeAll(previouslyGrantedUserIds);
        Set<Long> toRemove = new HashSet<>(previouslyGrantedUserIds);
        toRemove.removeAll(matched);

        insertGrants(ruleId, rule.getRoleId(), toAdd, operator);
        processRemovals(ruleId, rule.getRoleId(), toRemove, operator);

        LocalDateTime now = LocalDateTime.now();
        rule.setLastExecTime(now);
        rule.setLastExecBy(operator);
        rule.setUpdateBy(operator);
        rule.setUpdateTime(now);
        userRoleRuleMapper.updateById(rule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void revokeAll(Long ruleId, String operator) {
        UserRoleRuleEntity rule = userRoleRuleMapper.selectById(ruleId);
        if (rule == null) {
            return;
        }
        Set<Long> grantedUserIds = loadGrantedUserIds(ruleId);
        processRemovals(ruleId, rule.getRoleId(), grantedUserIds, operator);
    }

    /**
     * 查询一条用户角色规则，不存在时抛出业务异常。
     *
     * @param ruleId 规则 id
     * @return 规则实体
     */
    private UserRoleRuleEntity getExistingRule(Long ruleId) {
        UserRoleRuleEntity rule = userRoleRuleMapper.selectById(ruleId);
        if (rule == null) {
            throw new BusinessException("用户角色规则不存在：" + ruleId);
        }
        return rule;
    }

    /**
     * 加载规则的组织范围条件，转换为 {@code UserMatchConditionResolver} 的输入类型。
     *
     * @param ruleId 规则 id
     * @return 组织范围条件列表
     */
    private List<UserRoleOrgScopeCondition> loadOrgScopeConditions(Long ruleId) {
        List<UserRoleRuleOrgScopeEntity> entities = userRoleRuleOrgScopeMapper.selectList(
                new LambdaQueryWrapper<UserRoleRuleOrgScopeEntity>().eq(UserRoleRuleOrgScopeEntity::getRuleId, ruleId));
        return entities.stream().map(entity -> {
            UserRoleOrgScopeCondition condition = new UserRoleOrgScopeCondition();
            condition.setOrgId(entity.getOrgId());
            condition.setIncludeChildren(entity.getIncludeChildren());
            return condition;
        }).toList();
    }

    /**
     * 加载规则的用户属性条件，转换为 {@code UserMatchConditionResolver} 的输入类型。
     *
     * @param ruleId 规则 id
     * @return 用户属性条件列表
     */
    private List<UserRoleUserAttrCondition> loadUserAttrConditions(Long ruleId) {
        List<UserRoleRuleUserAttrEntity> entities = userRoleRuleUserAttrMapper.selectList(
                new LambdaQueryWrapper<UserRoleRuleUserAttrEntity>().eq(UserRoleRuleUserAttrEntity::getRuleId, ruleId));
        return entities.stream().map(entity -> {
            UserRoleUserAttrCondition condition = new UserRoleUserAttrCondition();
            condition.setMetadataFieldId(entity.getMetadataFieldId());
            condition.setOperator(entity.getOperator());
            condition.setAttrValue(entity.getAttrValue());
            return condition;
        }).toList();
    }

    /**
     * 查询规则既有执行结果对应的用户 id 集合。
     *
     * @param ruleId 规则 id
     * @return 既有执行结果的用户 id 集合
     */
    private Set<Long> loadGrantedUserIds(Long ruleId) {
        List<UserRoleRuleGrantEntity> grants = userRoleRuleGrantMapper.selectList(
                new LambdaQueryWrapper<UserRoleRuleGrantEntity>().eq(UserRoleRuleGrantEntity::getRuleId, ruleId));
        return grants.stream().map(UserRoleRuleGrantEntity::getUserId).collect(Collectors.toSet());
    }

    /**
     * 批量插入新增命中的规则计算结果，使用单次批量 SQL。
     *
     * @param ruleId   规则 id
     * @param roleId   角色 id
     * @param toAdd    新增命中的用户 id 集合
     * @param operator 操作人
     */
    private void insertGrants(Long ruleId, Long roleId, Set<Long> toAdd, String operator) {
        if (toAdd.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<UserRoleRuleGrantEntity> newGrants = toAdd.stream()
                .map(userId -> UserRoleRuleGrantEntity.builder()
                        .ruleId(ruleId)
                        .userId(userId)
                        .roleId(roleId)
                        .createBy(operator)
                        .createTime(now)
                        .updateBy(operator)
                        .updateTime(now)
                        .build())
                .toList();
        userRoleRuleGrantMapper.insertBatch(newGrants);
    }

    /**
     * 处理不再命中的用户：删除该规则对这些用户的执行结果，并对每个用户检查该用户该角色是否
     * 还有其他规则命中，没有的话触发"角色收回联动停用自动创建的管理员"检查。
     *
     * @param ruleId       规则 id
     * @param roleId       角色 id
     * @param toRemove     不再命中（待收回）的用户 id 集合
     * @param operator     操作人
     */
    private void processRemovals(Long ruleId, Long roleId, Set<Long> toRemove, String operator) {
        if (toRemove.isEmpty()) {
            return;
        }
        userRoleRuleGrantMapper.delete(new LambdaQueryWrapper<UserRoleRuleGrantEntity>()
                .eq(UserRoleRuleGrantEntity::getRuleId, ruleId)
                .in(UserRoleRuleGrantEntity::getUserId, toRemove));
        for (Long userId : toRemove) {
            revokeAdminIfNoLongerGranted(userId, roleId, operator);
        }
    }

    /**
     * 确认某用户某角色已不再被任何规则命中后（本方法内部再次查询确认，因为一个用户的同一
     * 角色可能被多条规则同时命中），触发"角色收回联动停用自动创建的管理员"检查。
     *
     * @param userId   用户 id
     * @param roleId   角色 id
     * @param operator 操作人
     */
    private void revokeAdminIfNoLongerGranted(Long userId, Long roleId, String operator) {
        Long remaining = userRoleRuleGrantMapper.selectCount(new LambdaQueryWrapper<UserRoleRuleGrantEntity>()
                .eq(UserRoleRuleGrantEntity::getUserId, userId)
                .eq(UserRoleRuleGrantEntity::getRoleId, roleId));
        if (remaining != null && remaining > 0) {
            return;
        }
        revokeAdminIfAutoCreatedFor(userId, roleId, operator);
    }

    /**
     * 查询该用户是否存在一条 {@code autoCreatedRoleId} 等于本次收回角色的未删除管理员记录，
     * 若存在则将其状态置为停用，不清空 {@code autoCreatedRoleId}、不物理删除、不级联处理其
     * 角色关联/组织管辖范围（design.md Decision 7）。人工创建的管理员、或仅"补充角色"获得
     * 该角色的管理员（{@code autoCreatedRoleId} 为空）不受影响。
     *
     * @param userId   用户 id
     * @param roleId   被收回的角色 id
     * @param operator 触发本次停用的操作人
     */
    private void revokeAdminIfAutoCreatedFor(Long userId, Long roleId, String operator) {
        List<AdminEntity> admins = adminMapper.selectList(new LambdaQueryWrapper<AdminEntity>()
                .eq(AdminEntity::getUserId, userId)
                .eq(AdminEntity::getAutoCreatedRoleId, roleId)
                .ne(AdminEntity::getStatus, AdminStatus.DELETED));
        if (admins.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (AdminEntity admin : admins) {
            admin.setStatus(AdminStatus.DISABLED);
            admin.setUpdateBy(operator);
            admin.setUpdateTime(now);
            adminMapper.updateById(admin);
        }
    }
}
