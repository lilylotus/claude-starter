package cn.nihility.rbac.userrole.support;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.userrole.constant.UserRoleAttrOperator;
import cn.nihility.rbac.userrole.dto.UserRoleOrgScopeCondition;
import cn.nihility.rbac.userrole.dto.UserRoleUserAttrCondition;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "组织范围 + 用户属性条件匹配用户"组件，职责与
 * {@code cn.nihility.rbac.appaccess.policy.service.impl.PolicyExecutionServiceImpl} 的同名
 * 私有方法群等价但独立实现，供 {@code user-role} 包下批量添加用户角色能力使用；不改造、不
 * 复用 {@code PolicyExecutionServiceImpl}，避免对已上线稳定能力引入回归风险
 * （add-user-role-batch-assignment change design.md Decision 2）。与
 * {@code PolicyExecutionServiceImpl} 的差异：属性条件额外支持
 * {@code tab_metadata_field.bizType=POSITION}（任职记录字段）域，且两类结果集均未配置时
 * 不做"全部启用用户"兜底（调用方须在调用前校验至少配置一类条件）。
 */
@Component
@RequiredArgsConstructor
public class UserMatchConditionResolver {

    /** 列名白名单正则：仅允许字母数字下划线，且不以数字开头，防止 SQL 注入。 */
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** {@code bizType=USER} 元数据字段唯一支持的关联表，双重防御。 */
    private static final String USER_TABLE_NAME = "tab_user";

    /** {@code bizType=POSITION} 元数据字段唯一支持的关联表，双重防御。 */
    private static final String USER_POSITION_TABLE_NAME = "tab_user_position";

    /** 元数据字段数据访问接口，解析用户属性条件关联字段的物理列名与所属域。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 用户数据访问接口，按 {@code bizType=USER} 的属性条件动态匹配命中用户。 */
    private final UserMapper userMapper;

    /** 用户任职记录数据访问接口，按组织范围、{@code bizType=POSITION} 属性条件匹配命中用户。 */
    private final UserPositionMapper userPositionMapper;

    /** 组织子孙展开工具组件。 */
    private final OrgDescendantExpander orgDescendantExpander;

    /**
     * 按组织范围条件、用户属性条件计算命中用户 id 集合：两类结果集之间取交集，未配置的
     * 那一类不参与交集（相当于该维度不收窄）；调用方须保证至少配置一类条件，否则本方法在
     * 两者均未配置时返回空集合，不做"全部启用用户"兜底（design.md Decision 2）。
     *
     * @param orgScopes 组织范围条件列表，可为空
     * @param userAttrs 用户属性条件列表，可为空
     * @return 命中用户 id 集合
     */
    public Set<Long> resolve(List<UserRoleOrgScopeCondition> orgScopes, List<UserRoleUserAttrCondition> userAttrs) {
        Set<Long> orgScopeUserIds = (orgScopes == null || orgScopes.isEmpty()) ? null : matchByOrgScope(orgScopes);
        Set<Long> attrUserIds = (userAttrs == null || userAttrs.isEmpty()) ? null : matchByUserAttrs(userAttrs);
        return intersect(orgScopeUserIds, attrUserIds);
    }

    /**
     * 把组织范围条件展开为最终参与任职记录过滤的组织 id 全集："含子组织"的根节点批量展开
     * 子孙组织，与"不含子组织"的节点直接取并集，供 {@link #matchByOrgScope} 以及调用方
     * （回填命中用户所属组织名称时）复用同一份展开逻辑，避免两处重复实现。
     *
     * @param orgScopes 组织范围条件列表，可为空
     * @return 展开后的组织 id 全集；{@code orgScopes} 为空时返回空集合
     */
    public Set<Long> expandOrgScopeIds(List<UserRoleOrgScopeCondition> orgScopes) {
        if (orgScopes == null || orgScopes.isEmpty()) {
            return new HashSet<>();
        }

        Set<Long> includeChildrenRootIds = new HashSet<>();
        Set<Long> directOnlyOrgIds = new HashSet<>();
        for (UserRoleOrgScopeCondition scope : orgScopes) {
            if (Boolean.TRUE.equals(scope.getIncludeChildren())) {
                includeChildrenRootIds.add(scope.getOrgId());
            } else {
                directOnlyOrgIds.add(scope.getOrgId());
            }
        }

        Set<Long> expandedOrgIds = new HashSet<>(directOnlyOrgIds);
        expandedOrgIds.addAll(orgDescendantExpander.expandWithDescendants(includeChildrenRootIds));
        return expandedOrgIds;
    }

    /**
     * 按组织范围条件匹配命中用户：算法与
     * {@code PolicyExecutionServiceImpl#matchByOrgScope} 完全一致，按展开后的组织 id 全集
     * 匹配 {@code tab_user_position} 未删除任职记录。
     *
     * @param orgScopes 组织范围条件列表，非空
     * @return 命中的用户 id 集合
     */
    private Set<Long> matchByOrgScope(List<UserRoleOrgScopeCondition> orgScopes) {
        Set<Long> expandedOrgIds = expandOrgScopeIds(orgScopes);
        if (expandedOrgIds.isEmpty()) {
            return new HashSet<>();
        }

        List<UserPositionEntity> positions = userPositionMapper.selectList(new LambdaQueryWrapper<UserPositionEntity>()
                .in(UserPositionEntity::getOrgId, expandedOrgIds)
                .ne(UserPositionEntity::getStatus, PositionStatus.DELETED));
        return positions.stream().map(UserPositionEntity::getUserId).collect(Collectors.toSet());
    }

    /**
     * 按用户属性条件匹配命中用户：逐条条件按 {@code metadataFieldId} 解析物理列名并双重
     * 校验，按元数据字段所属域（{@code bizType=USER}/{@code POSITION}）分派到
     * {@link UserMapper#selectIdsByAttrCondition}/{@link UserPositionMapper#selectIdsByAttrCondition}，
     * 多条条件之间（无论是否跨两个域）统一在用户 id 级别取交集。
     *
     * @param userAttrs 用户属性条件列表，非空
     * @return 命中的用户 id 集合
     */
    private Set<Long> matchByUserAttrs(List<UserRoleUserAttrCondition> userAttrs) {
        Set<Long> result = null;
        for (UserRoleUserAttrCondition condition : userAttrs) {
            MetadataFieldEntity field = resolveTrustedField(condition.getMetadataFieldId());
            String operator = condition.getOperator();
            if (!UserRoleAttrOperator.ALL_OPERATORS.contains(operator)) {
                throw new BusinessException("运算符不合法：" + operator);
            }

            List<String> values = UserRoleAttrOperator.IN.equals(operator)
                    ? Arrays.asList(condition.getAttrValue().split(","))
                    : List.of();
            String singleValue = UserRoleAttrOperator.IN.equals(operator) ? null : condition.getAttrValue();

            List<Long> matched = FormFieldBizType.USER.equals(field.getBizType())
                    ? userMapper.selectIdsByAttrCondition(field.getColumnName(), operator, singleValue, values)
                    : userPositionMapper.selectIdsByAttrCondition(field.getColumnName(), operator, singleValue, values);
            Set<Long> matchedSet = new HashSet<>(matched);
            result = result == null ? matchedSet : intersectNonNull(result, matchedSet);
        }
        return result == null ? new HashSet<>() : result;
    }

    /**
     * 解析并双重校验用户属性条件关联的元数据字段：须为启用状态、{@code bizType} 须属于
     * {@code USER}/{@code POSITION} 之一且其 {@code tableName} 与 {@code bizType} 匹配、
     * 列名须通过白名单正则校验，只有全部通过才允许把列名拼入动态 SQL。
     *
     * @param metadataFieldId 元数据字段 id
     * @return 校验通过的元数据字段实体
     */
    private MetadataFieldEntity resolveTrustedField(Long metadataFieldId) {
        MetadataFieldEntity field = metadataFieldMapper.selectById(metadataFieldId);
        if (field == null) {
            throw new BusinessException("属性字段不存在：" + metadataFieldId);
        }
        if (!Objects.equals(field.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]已停用");
        }

        if (FormFieldBizType.USER.equals(field.getBizType())) {
            if (!USER_TABLE_NAME.equals(field.getTableName())) {
                throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于 " + USER_TABLE_NAME);
            }
        } else if (FormFieldBizType.POSITION.equals(field.getBizType())) {
            if (!USER_POSITION_TABLE_NAME.equals(field.getTableName())) {
                throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于 " + USER_POSITION_TABLE_NAME);
            }
        } else {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于用户或任职域");
        }

        String columnName = field.getColumnName();
        if (columnName == null || !COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]列名不合法");
        }
        return field;
    }

    /**
     * 组织范围结果集与用户属性条件结果集取交集：两者都配置时取交集，只配置一类时直接使用
     * 该类结果（{@code null} 表示未配置该类条件）；两者都未配置时返回空集合，不做"全部启用
     * 用户"兜底——本能力要求调用方在调用前校验至少配置一类条件，这个分支正常不会发生
     * （design.md Decision 2，区别于 {@code PolicyExecutionServiceImpl#intersect}）。
     *
     * @param orgScopeUserIds 组织范围匹配结果，{@code null} 表示未配置组织范围
     * @param attrUserIds     用户属性条件匹配结果，{@code null} 表示未配置属性条件
     * @return 最终命中用户 id 集合
     */
    private Set<Long> intersect(Set<Long> orgScopeUserIds, Set<Long> attrUserIds) {
        if (orgScopeUserIds != null && attrUserIds != null) {
            return intersectNonNull(orgScopeUserIds, attrUserIds);
        }
        if (orgScopeUserIds != null) {
            return orgScopeUserIds;
        }
        if (attrUserIds != null) {
            return attrUserIds;
        }
        return new HashSet<>();
    }

    /**
     * 两个非空集合取交集。
     *
     * @param left  左集合
     * @param right 右集合
     * @return 交集
     */
    private Set<Long> intersectNonNull(Set<Long> left, Set<Long> right) {
        Set<Long> result = new HashSet<>(left);
        result.retainAll(right);
        return result;
    }
}
