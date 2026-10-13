package cn.nihility.rbac.abac.support;

import cn.nihility.rbac.abac.constant.AbacAttrOperator;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * ABAC 用户属性条件单用户实时判定组件，职责与 {@code userrole.support.UserMatchConditionResolver}/
 * {@code appaccess.policy.service.impl.PolicyExecutionServiceImpl} 的同类逻辑等价但独立
 * 实现（abac-policy-engine change design.md Decision 3）：那两处是批量计算"哪些用户满足"，
 * 本组件是单用户实时判定"这一个用户是否满足"，场景不同，不复用它们的 Service/校验代码，
 * 但延续同一种"元数据字段 + 列名白名单 + 动态 SQL 下推"安全模式。本次只支持
 * {@code bizType=USER} 域（不支持 {@code POSITION}），因为运行时判定没有"当前任职记录"
 * 这个概念，只有当前登录用户本身。
 */
@Component
@RequiredArgsConstructor
public class AbacUserAttrEvaluator {

    /** 列名白名单正则：仅允许字母数字下划线，且不以数字开头，防止 SQL 注入。 */
    private static final Pattern COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /** {@code bizType=USER} 元数据字段唯一支持的关联表，双重防御。 */
    private static final String USER_TABLE_NAME = "tab_user";

    /** 元数据字段数据访问接口，解析用户属性条件关联字段的物理列名与所属域。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 用户数据访问接口，按 {@code bizType=USER} 的属性条件做单用户实时匹配判定。 */
    private final UserMapper userMapper;

    /**
     * 判断给定用户是否满足单条用户属性条件。
     *
     * @param userId    用户 id
     * @param condition 用户属性条件
     * @return 是否满足
     */
    public boolean matches(Long userId, AbacPolicyUserAttrEntity condition) {
        MetadataFieldEntity field = resolveTrustedField(condition.getMetadataFieldId());
        String operator = condition.getOperator();
        if (!AbacAttrOperator.ALL_OPERATORS.contains(operator)) {
            throw new BusinessException("运算符不合法：" + operator);
        }

        List<String> values = AbacAttrOperator.IN.equals(operator)
                ? Arrays.asList(condition.getAttrValue().split(","))
                : List.of();
        String singleValue = AbacAttrOperator.IN.equals(operator) ? null : condition.getAttrValue();

        int matchedCount = userMapper.countMatchingAttrCondition(userId, field.getColumnName(), operator,
                singleValue, values);
        return matchedCount > 0;
    }

    /**
     * 判断给定用户是否同时满足一条策略下的全部用户属性条件（AND 语义，abac-policy-engine
     * change design.md Decision 3 第 3 点）；条件列表为空时视为该维度恒满足。
     *
     * @param userId     用户 id
     * @param conditions 用户属性条件列表，可为空
     * @return 是否全部满足
     */
    public boolean matchesAll(Long userId, List<AbacPolicyUserAttrEntity> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (AbacPolicyUserAttrEntity condition : conditions) {
            if (!matches(userId, condition)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析并双重校验用户属性条件关联的元数据字段：须为启用状态、{@code bizType=USER}、
     * {@code tableName=tab_user}、列名须通过白名单正则校验，只有全部通过才允许把列名
     * 拼入动态 SQL。
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
        if (!FormFieldBizType.USER.equals(field.getBizType())) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于用户业务域");
        }
        if (!USER_TABLE_NAME.equals(field.getTableName())) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]不属于 " + USER_TABLE_NAME);
        }

        String columnName = field.getColumnName();
        if (columnName == null || !COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new BusinessException("属性字段[" + field.getFieldName() + "]列名不合法");
        }
        return field;
    }
}
