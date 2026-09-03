package cn.nihility.rbac.userrole.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.userrole.dto.UserRoleMatchedUserVO;
import cn.nihility.rbac.userrole.dto.UserRoleOrgScopeCondition;
import cn.nihility.rbac.userrole.dto.UserRoleRuleCreateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleOrgScopeVO;
import cn.nihility.rbac.userrole.dto.UserRoleRulePreviewRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUpdateRequest;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUserAttrRow;
import cn.nihility.rbac.userrole.dto.UserRoleRuleUserAttrVO;
import cn.nihility.rbac.userrole.dto.UserRoleRuleVO;
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
import cn.nihility.rbac.userrole.service.UserRoleRuleService;
import cn.nihility.rbac.userrole.support.UserMatchConditionResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户角色规则业务逻辑实现（design.md Decision 3/3a/4）。
 */
@Service
@RequiredArgsConstructor
public class UserRoleRuleServiceImpl implements UserRoleRuleService {

    /** 用户角色规则数据访问接口。 */
    private final UserRoleRuleMapper userRoleRuleMapper;

    /** 用户角色规则组织范围条件数据访问接口。 */
    private final UserRoleRuleOrgScopeMapper userRoleRuleOrgScopeMapper;

    /** 用户角色规则用户属性条件数据访问接口。 */
    private final UserRoleRuleUserAttrMapper userRoleRuleUserAttrMapper;

    /** 用户角色规则计算结果数据访问接口，用于统计当前命中人数。 */
    private final UserRoleRuleGrantMapper userRoleRuleGrantMapper;

    /** 角色数据访问接口，校验目标角色存在且未删除。 */
    private final RoleMapper roleMapper;

    /** 用户数据访问接口，分页查询预览命中用户基础信息。 */
    private final UserMapper userMapper;

    /** 用户任职记录数据访问接口，回填预览命中用户所属组织名称。 */
    private final UserPositionMapper userPositionMapper;

    /** "组织范围 + 用户属性条件匹配用户"组件，预览接口直接复用。 */
    private final UserMatchConditionResolver conditionResolver;

    /** 用户角色规则执行引擎业务逻辑接口，保存/删除后同步触发执行。 */
    private final UserRoleRuleExecutionService executionService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserRoleRuleVO> listByRoleId(Long roleId) {
        List<UserRoleRuleEntity> rules = userRoleRuleMapper.selectList(
                new LambdaQueryWrapper<UserRoleRuleEntity>().eq(UserRoleRuleEntity::getRoleId, roleId)
                        .orderByAsc(UserRoleRuleEntity::getId));
        return rules.stream().map(this::toSummaryVO).toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UserRoleRuleVO getById(Long id) {
        UserRoleRuleEntity rule = getExistingRule(id);
        return toDetailVO(rule);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<UserRoleMatchedUserVO> preview(UserRoleRulePreviewRequest request) {
        validateConditions(request.getOrgScopes(), request.getUserAttrs());

        Set<Long> hitUserIds = conditionResolver.resolve(request.getOrgScopes(), request.getUserAttrs());
        if (hitUserIds.isEmpty()) {
            return new PageResult<>(List.of(), 0L, request.getPage(), request.getPageSize());
        }

        Page<UserEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        Page<UserEntity> resultPage = userMapper.selectPage(queryPage, new LambdaQueryWrapper<UserEntity>()
                .in(UserEntity::getId, hitUserIds)
                .ne(UserEntity::getStatus, UserStatus.DELETED)
                .orderByAsc(UserEntity::getId));

        Map<Long, String> orgNames = resolveOrgNames(resultPage.getRecords(), request.getOrgScopes());
        List<UserRoleMatchedUserVO> records = resultPage.getRecords().stream()
                .map(user -> UserRoleMatchedUserVO.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .code(user.getCode())
                        .orgName(orgNames.getOrDefault(user.getId(), ""))
                        .build())
                .toList();
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserRoleRuleVO create(UserRoleRuleCreateRequest request) {
        getExistingRole(request.getRoleId());
        validateConditions(request.getOrgScopes(), request.getUserAttrs());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        UserRoleRuleEntity entity = UserRoleRuleEntity.builder()
                .roleId(request.getRoleId())
                .name(request.getName())
                .remark(request.getRemark())
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        userRoleRuleMapper.insert(entity);

        saveOrgScopes(entity.getId(), request.getOrgScopes(), operator, now);
        saveUserAttrs(entity.getId(), request.getUserAttrs(), operator, now);

        executionService.execute(entity.getId(), operator);
        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UserRoleRuleVO update(Long id, UserRoleRuleUpdateRequest request) {
        UserRoleRuleEntity entity = getExistingRule(id);
        validateConditions(request.getOrgScopes(), request.getUserAttrs());

        String operator = resolveOperator();
        LocalDateTime now = LocalDateTime.now();
        entity.setName(request.getName());
        entity.setRemark(request.getRemark());
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        userRoleRuleMapper.updateById(entity);

        userRoleRuleOrgScopeMapper.delete(new LambdaQueryWrapper<UserRoleRuleOrgScopeEntity>()
                .eq(UserRoleRuleOrgScopeEntity::getRuleId, id));
        saveOrgScopes(id, request.getOrgScopes(), operator, now);

        userRoleRuleUserAttrMapper.delete(new LambdaQueryWrapper<UserRoleRuleUserAttrEntity>()
                .eq(UserRoleRuleUserAttrEntity::getRuleId, id));
        saveUserAttrs(id, request.getUserAttrs(), operator, now);

        executionService.execute(id, operator);
        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void delete(Long id) {
        getExistingRule(id);
        String operator = resolveOperator();

        executionService.revokeAll(id, operator);

        userRoleRuleOrgScopeMapper.delete(new LambdaQueryWrapper<UserRoleRuleOrgScopeEntity>()
                .eq(UserRoleRuleOrgScopeEntity::getRuleId, id));
        userRoleRuleUserAttrMapper.delete(new LambdaQueryWrapper<UserRoleRuleUserAttrEntity>()
                .eq(UserRoleRuleUserAttrEntity::getRuleId, id));
        userRoleRuleMapper.deleteById(id);
    }

    /**
     * 查询一条未删除的用户角色规则，不存在时抛出业务异常。
     *
     * @param id 规则 id
     * @return 规则实体
     */
    private UserRoleRuleEntity getExistingRule(Long id) {
        UserRoleRuleEntity entity = userRoleRuleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("用户角色规则不存在");
        }
        return entity;
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
     * 校验组织范围条件、用户属性条件至少配置一类，均为空时拒绝，防止误操作导致全库用户被
     * 批量打标签。
     *
     * @param orgScopes 组织范围条件列表
     * @param userAttrs 用户属性条件列表
     */
    private void validateConditions(List<UserRoleOrgScopeCondition> orgScopes, List<UserRoleUserAttrCondition> userAttrs) {
        boolean hasOrgScope = orgScopes != null && !orgScopes.isEmpty();
        boolean hasUserAttr = userAttrs != null && !userAttrs.isEmpty();
        if (!hasOrgScope && !hasUserAttr) {
            throw new BusinessException("组织范围条件、用户属性条件至少配置一类");
        }
    }

    /**
     * 保存组织范围条件子表记录。
     *
     * @param ruleId    规则 id
     * @param orgScopes 组织范围条件列表，可为空
     * @param operator  操作人
     * @param now       操作时间
     */
    private void saveOrgScopes(Long ruleId, List<UserRoleOrgScopeCondition> orgScopes, String operator, LocalDateTime now) {
        if (orgScopes == null || orgScopes.isEmpty()) {
            return;
        }
        for (UserRoleOrgScopeCondition condition : orgScopes) {
            userRoleRuleOrgScopeMapper.insert(UserRoleRuleOrgScopeEntity.builder()
                    .ruleId(ruleId)
                    .orgId(condition.getOrgId())
                    .includeChildren(condition.getIncludeChildren())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 保存用户属性条件子表记录。
     *
     * @param ruleId    规则 id
     * @param userAttrs 用户属性条件列表，可为空
     * @param operator  操作人
     * @param now       操作时间
     */
    private void saveUserAttrs(Long ruleId, List<UserRoleUserAttrCondition> userAttrs, String operator, LocalDateTime now) {
        if (userAttrs == null || userAttrs.isEmpty()) {
            return;
        }
        for (UserRoleUserAttrCondition condition : userAttrs) {
            userRoleRuleUserAttrMapper.insert(UserRoleRuleUserAttrEntity.builder()
                    .ruleId(ruleId)
                    .metadataFieldId(condition.getMetadataFieldId())
                    .operator(condition.getOperator())
                    .attrValue(condition.getAttrValue())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build());
        }
    }

    /**
     * 组装规则摘要视图对象：仅含基础字段与当前命中人数，不携带组织范围/用户属性条件明细
     * （避免列表接口 N+1）。
     *
     * @param entity 规则实体
     * @return 规则摘要视图对象
     */
    private UserRoleRuleVO toSummaryVO(UserRoleRuleEntity entity) {
        return UserRoleRuleVO.builder()
                .id(entity.getId())
                .roleId(entity.getRoleId())
                .name(entity.getName())
                .remark(entity.getRemark())
                .lastExecTime(entity.getLastExecTime())
                .lastExecBy(entity.getLastExecBy())
                .hitCount(resolveHitCount(entity.getId()))
                .build();
    }

    /**
     * 组装规则详情视图对象：在摘要字段基础上，额外回填组织范围/用户属性条件明细。
     *
     * @param entity 规则实体
     * @return 规则详情视图对象
     */
    private UserRoleRuleVO toDetailVO(UserRoleRuleEntity entity) {
        List<UserRoleRuleOrgScopeVO> orgScopes = userRoleRuleOrgScopeMapper.selectByRuleId(entity.getId());
        List<UserRoleRuleUserAttrRow> attrRows = userRoleRuleUserAttrMapper.selectByRuleId(entity.getId());
        List<UserRoleRuleUserAttrVO> userAttrs = attrRows.stream()
                .map(row -> UserRoleRuleUserAttrVO.builder()
                        .metadataFieldId(row.getMetadataFieldId())
                        .fieldName(row.getFieldName())
                        .fieldCode(row.getFieldCode())
                        .bizType(row.getBizType())
                        .operator(row.getOperator())
                        .values(Arrays.asList(row.getAttrValue().split(",")))
                        .build())
                .toList();

        return UserRoleRuleVO.builder()
                .id(entity.getId())
                .roleId(entity.getRoleId())
                .name(entity.getName())
                .remark(entity.getRemark())
                .lastExecTime(entity.getLastExecTime())
                .lastExecBy(entity.getLastExecBy())
                .hitCount(resolveHitCount(entity.getId()))
                .orgScopes(orgScopes)
                .userAttrs(userAttrs)
                .build();
    }

    /**
     * 统计规则当前命中人数：{@code tab_user_role_rule_grant} 的 {@code (rule_id, user_id)}
     * 唯一约束保证同一规则下每个用户至多一行，直接按 {@code ruleId} 计数等价于
     * {@code COUNT(DISTINCT user_id)}，无需额外去重查询。
     *
     * @param ruleId 规则 id
     * @return 当前命中人数
     */
    private Integer resolveHitCount(Long ruleId) {
        Long count = userRoleRuleGrantMapper.selectCount(new LambdaQueryWrapper<UserRoleRuleGrantEntity>()
                .eq(UserRoleRuleGrantEntity::getRuleId, ruleId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 批量回填预览命中用户所属组织名称，逻辑与首次实现的
     * {@code UserRoleServiceImpl#resolveOrgNames} 完全一致（迁移过来，未变）。
     *
     * @param users     当前页命中用户实体列表
     * @param orgScopes 组织范围条件列表，可为空
     * @return 用户 id 到所属组织名称的映射
     */
    private Map<Long, String> resolveOrgNames(List<UserEntity> users, List<UserRoleOrgScopeCondition> orgScopes) {
        if (users.isEmpty()) {
            return Map.of();
        }

        Set<Long> userIds = users.stream().map(UserEntity::getId).collect(Collectors.toSet());
        Set<Long> orgIds = (orgScopes == null || orgScopes.isEmpty()) ? null : conditionResolver.expandOrgScopeIds(orgScopes);
        List<PositionVO> positions = userPositionMapper.selectRepresentativeOrgNames(userIds, orgIds, PositionStatus.DELETED);

        Map<Long, String> result = new LinkedHashMap<>();
        for (PositionVO position : positions) {
            result.putIfAbsent(position.getUserId(), position.getOrgName());
        }
        return result;
    }

    /**
     * 解析当前登录操作人用户 id 的文本形式。
     *
     * @return 当前登录操作人用户 id 文本
     */
    private String resolveOperator() {
        return Objects.toString(currentOperatorService.resolveUserId(), null);
    }
}
