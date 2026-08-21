package cn.nihility.rbac.appaccess.policy.service.impl;

import cn.nihility.rbac.appaccess.policy.constant.PolicyAttrOperator;
import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyGrantEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyOrgScopeEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyTargetAppEntity;
import cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyGrantMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyOrgScopeMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyTargetAppMapper;
import cn.nihility.rbac.appaccess.policy.mapper.PolicyUserAttrMapper;
import cn.nihility.rbac.appaccess.policy.service.PolicyExecutionService;
import cn.nihility.rbac.appaccess.policy.service.PolicyService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.org.support.OrgDescendantExpander;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 策略规则手动执行业务逻辑实现（design.md Decision 4）。
 */
@Service
@RequiredArgsConstructor
public class PolicyExecutionServiceImpl implements PolicyExecutionService {

    /** 列名白名单正则：仅允许字母数字下划线，且不以数字开头（design.md Risks 缓解措施）。 */
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** 用户属性条件唯一支持的关联表，双重防御（design.md Decision 1）。 */
    private static final String USER_ATTR_TABLE_NAME = "tab_user";

    /** 策略规则数据访问接口。 */
    private final PolicyMapper policyMapper;

    /** 策略组织范围条件数据访问接口。 */
    private final PolicyOrgScopeMapper policyOrgScopeMapper;

    /** 策略用户属性条件数据访问接口。 */
    private final PolicyUserAttrMapper policyUserAttrMapper;

    /** 策略目标应用数据访问接口。 */
    private final PolicyTargetAppMapper policyTargetAppMapper;

    /** 策略计算结果数据访问接口。 */
    private final PolicyGrantMapper policyGrantMapper;

    /** 元数据字段数据访问接口，解析用户属性条件关联字段的物理列名。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 用户任职记录数据访问接口，按展开后的组织范围匹配命中用户。 */
    private final UserPositionMapper userPositionMapper;

    /** 用户数据访问接口，按用户属性条件动态匹配命中用户。 */
    private final UserMapper userMapper;

    /** 组织子孙展开工具组件。 */
    private final OrgDescendantExpander orgDescendantExpander;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 策略规则业务逻辑接口，执行完成后复用其详情组装逻辑返回最新视图对象。 */
    private final PolicyService policyService;

    /**
     * {@inheritDoc}
     *
     * <p>请求控制是运行时校验，不参与本方法的批量身份计算：本方法既不读取也不校验策略的
     * 浏览器/IP 白名单配置（{@code tab_app_access_policy_browser_rule}/
     * {@code tab_app_access_policy_ip_rule}），配置了请求控制的策略与未配置的策略在
     * "执行"这一步产生完全一致的命中结果（app-access-request-control change design.md
     * Decision 2、tasks.md 4.1）；请求控制只在最终生效权限判定时读取，见
     * {@code AppAccessEffectivePermissionService#isAuthorized(Long, Long, String, String)}。
     */
    @Override
    @Transactional
    public PolicyVO execute(Long policyId) {
        return execute(policyId, Objects.toString(currentOperatorService.resolveUserId(), null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public PolicyVO execute(Long policyId, String operator) {
        PolicyEntity policy = policyMapper.selectById(policyId);
        if (policy == null) {
            throw new BusinessException("策略规则不存在");
        }

        List<PolicyOrgScopeEntity> orgScopes = policyOrgScopeMapper.selectList(
                new LambdaQueryWrapper<PolicyOrgScopeEntity>().eq(PolicyOrgScopeEntity::getPolicyId, policyId));
        List<PolicyUserAttrEntity> userAttrs = policyUserAttrMapper.selectList(
                new LambdaQueryWrapper<PolicyUserAttrEntity>().eq(PolicyUserAttrEntity::getPolicyId, policyId));
        List<PolicyTargetAppEntity> targetApps = policyTargetAppMapper.selectList(
                new LambdaQueryWrapper<PolicyTargetAppEntity>().eq(PolicyTargetAppEntity::getPolicyId, policyId));

        Set<Long> orgScopeUserIds = orgScopes.isEmpty() ? null : matchByOrgScope(orgScopes);
        Set<Long> attrUserIds = userAttrs.isEmpty() ? null : matchByUserAttrs(userAttrs);
        Set<Long> hitUserIds = intersect(orgScopeUserIds, attrUserIds);

        List<PolicyGrantEntity> newGrants = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long userId : hitUserIds) {
            for (PolicyTargetAppEntity targetApp : targetApps) {
                newGrants.add(PolicyGrantEntity.builder()
                        .policyId(policyId)
                        .userId(userId)
                        .appId(targetApp.getAppId())
                        .createBy(operator)
                        .createTime(now)
                        .updateBy(operator)
                        .updateTime(now)
                        .build());
            }
        }

        policyGrantMapper.delete(new LambdaQueryWrapper<PolicyGrantEntity>()
                .eq(PolicyGrantEntity::getPolicyId, policyId));
        for (PolicyGrantEntity grant : newGrants) {
            policyGrantMapper.insert(grant);
        }

        policy.setLastExecTime(now);
        policy.setLastExecBy(operator);
        policy.setUpdateBy(operator);
        policy.setUpdateTime(now);
        policyMapper.updateById(policy);

        return policyService.detail(policyId);
    }

    /**
     * 按组织范围条件匹配命中用户：{@code includeChildren=true} 的行批量展开子孙组织后
     * 合并查询，{@code includeChildren=false} 的行只按自身组织 id 查询，最终按
     * {@code tab_user_position} 未删除任职记录匹配去重用户 id（design.md Decision 4）。
     *
     * @param orgScopes 组织范围条件列表，非空
     * @return 命中的用户 id 集合
     */
    private Set<Long> matchByOrgScope(List<PolicyOrgScopeEntity> orgScopes) {
        Set<Long> includeChildrenRootIds = new HashSet<>();
        Set<Long> directOnlyOrgIds = new HashSet<>();
        for (PolicyOrgScopeEntity scope : orgScopes) {
            if (Boolean.TRUE.equals(scope.getIncludeChildren())) {
                includeChildrenRootIds.add(scope.getOrgId());
            } else {
                directOnlyOrgIds.add(scope.getOrgId());
            }
        }

        Set<Long> expandedOrgIds = new HashSet<>(directOnlyOrgIds);
        expandedOrgIds.addAll(orgDescendantExpander.expandWithDescendants(includeChildrenRootIds));
        if (expandedOrgIds.isEmpty()) {
            return new HashSet<>();
        }

        // 不使用 LambdaQueryWrapper#select 限定返回列：单元测试（纯 Mockito，不起 Spring
        // 上下文）中该重载在个别 MyBatis-Plus 版本下会触发 lambda 列缓存初始化异常
        // （AbstractLambdaWrapper#tryInitCache），改为整行查询后在 Java 侧只取 userId，
        // 行为等价，只是多传输了几列，规模有限可忽略。
        List<UserPositionEntity> positions = userPositionMapper.selectList(
                new LambdaQueryWrapper<UserPositionEntity>()
                        .in(UserPositionEntity::getOrgId, expandedOrgIds)
                        .ne(UserPositionEntity::getStatus, PositionStatus.DELETED));
        return positions.stream().map(UserPositionEntity::getUserId).collect(Collectors.toSet());
    }

    /**
     * 按用户属性条件匹配命中用户：逐条条件按 {@code metadataFieldId} 解析物理列名并双重
     * 校验（{@code table_name='tab_user'} 且列名仅含字母数字下划线），多条条件之间取交集
     * （design.md Decision 1/4）。
     *
     * @param userAttrs 用户属性条件列表，非空
     * @return 命中的用户 id 集合
     */
    private Set<Long> matchByUserAttrs(List<PolicyUserAttrEntity> userAttrs) {
        Set<Long> result = null;
        for (PolicyUserAttrEntity attr : userAttrs) {
            String columnName = resolveTrustedColumnName(attr.getMetadataFieldId());
            List<String> values = PolicyAttrOperator.IN.equals(attr.getOperator())
                    ? Arrays.asList(attr.getAttrValue().split(","))
                    : List.of();
            String singleValue = PolicyAttrOperator.IN.equals(attr.getOperator()) ? null : attr.getAttrValue();

            List<Long> matched = userMapper.selectIdsByAttrCondition(columnName, attr.getOperator(), singleValue, values);
            Set<Long> matchedSet = new HashSet<>(matched);
            result = result == null ? matchedSet : intersectNonNull(result, matchedSet);
        }
        return result == null ? new HashSet<>() : result;
    }

    /**
     * 解析并双重校验用户属性条件关联的元数据字段物理列名，只有通过白名单校验的列名才允许
     * 拼入动态 SQL（design.md Risks 缓解措施），执行阶段现查，不信任任何调用方直接传入的
     * 列名字符串。
     *
     * @param metadataFieldId 元数据字段 id
     * @return 校验通过的物理列名
     */
    private String resolveTrustedColumnName(Long metadataFieldId) {
        MetadataFieldEntity field = metadataFieldMapper.selectById(metadataFieldId);
        if (field == null) {
            throw new BusinessException("属性字段不存在：" + metadataFieldId);
        }
        if (!USER_ATTR_TABLE_NAME.equals(field.getTableName())) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于 " + USER_ATTR_TABLE_NAME);
        }
        String columnName = field.getColumnName();
        if (columnName == null || !COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]列名不合法");
        }
        return columnName;
    }

    /**
     * 组织范围结果集与用户属性条件结果集取交集：两者都配置时取交集，只配置一类时直接使用
     * 该类结果（{@code null} 表示未配置该类条件，见调用方 design.md Decision 4）；两者都
     * 未配置时（此时策略必然配置了请求控制条件，否则无法通过保存校验），命中集合为系统内
     * 全部启用状态用户，不要求存在任职记录——此时策略本就没有组织维度的圈定意图，纯粹依赖
     * 请求控制收窄，语义上是"完全不做身份限定"，与组织范围匹配路径要求"存在未删除任职记录"
     * 是有意的不一致（close-sso-log-and-policy-gaps change design.md Decision 3）。
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
        return allEnabledUserIds();
    }

    /**
     * 查询系统内全部启用状态用户的 id，供组织范围与用户属性条件均未配置的策略执行时使用。
     *
     * @return 全部启用状态用户 id 集合
     */
    private Set<Long> allEnabledUserIds() {
        List<UserEntity> enabledUsers = userMapper.selectList(
                new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getStatus, UserStatus.ENABLED));
        return enabledUsers.stream().map(UserEntity::getId).collect(Collectors.toSet());
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
