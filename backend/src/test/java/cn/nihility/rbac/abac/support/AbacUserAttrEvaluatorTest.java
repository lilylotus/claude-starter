package cn.nihility.rbac.abac.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.abac.constant.AbacAttrOperator;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AbacUserAttrEvaluator} 的单元测试，覆盖 {@code EQ}/{@code NE}/{@code IN} 三种
 * 运算符对实际存在/不存在的用户数据的判断结果，以及引用停用/域外元数据字段时拒绝的分支
 * （abac-policy-engine change tasks.md 3.1）。
 */
@ExtendWith(MockitoExtension.class)
class AbacUserAttrEvaluatorTest {

    /** 被测组件的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测组件的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测组件实例。 */
    private AbacUserAttrEvaluator evaluator;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        evaluator = new AbacUserAttrEvaluator(metadataFieldMapper, userMapper);
    }

    /**
     * {@code EQ} 运算符命中实际存在的用户数据时，应判定为满足。
     */
    @Test
    void matches_shouldReturnTrue_whenEqOperatorMatchesExistingUser() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.EQ, "财务部", List.of()))
                .thenReturn(1);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.EQ, "财务部"));

        assertThat(result).isTrue();
    }

    /**
     * {@code EQ} 运算符未命中（用户数据不存在/不满足）时，应判定为不满足。
     */
    @Test
    void matches_shouldReturnFalse_whenEqOperatorDoesNotMatch() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.EQ, "财务部", List.of()))
                .thenReturn(0);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.EQ, "财务部"));

        assertThat(result).isFalse();
    }

    /**
     * {@code NE} 运算符命中实际存在的用户数据时，应判定为满足。
     */
    @Test
    void matches_shouldReturnTrue_whenNeOperatorMatchesExistingUser() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.NE, "财务部", List.of()))
                .thenReturn(1);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.NE, "财务部"));

        assertThat(result).isTrue();
    }

    /**
     * {@code NE} 运算符未命中时，应判定为不满足。
     */
    @Test
    void matches_shouldReturnFalse_whenNeOperatorDoesNotMatch() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.NE, "财务部", List.of()))
                .thenReturn(0);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.NE, "财务部"));

        assertThat(result).isFalse();
    }

    /**
     * {@code IN} 运算符命中实际存在的用户数据时，应判定为满足，比较值按逗号拆分为多个值
     * 传递给数据访问层。
     */
    @Test
    void matches_shouldReturnTrue_whenInOperatorMatchesExistingUser() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.IN, null,
                List.of("财务部", "研发部"))).thenReturn(1);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.IN, "财务部,研发部"));

        assertThat(result).isTrue();
    }

    /**
     * {@code IN} 运算符未命中时，应判定为不满足。
     */
    @Test
    void matches_shouldReturnFalse_whenInOperatorDoesNotMatch() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.IN, null,
                List.of("财务部", "研发部"))).thenReturn(0);

        boolean result = evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.IN, "财务部,研发部"));

        assertThat(result).isFalse();
    }

    /**
     * 多条属性条件同时满足时，{@link AbacUserAttrEvaluator#matchesAll} 应判定为满足
     * （AND 语义）。
     */
    @Test
    void matchesAll_shouldReturnTrue_whenAllConditionsSatisfied() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(metadataFieldMapper.selectById(2L)).thenReturn(buildField("level"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.NE, "财务部", List.of()))
                .thenReturn(1);
        when(userMapper.countMatchingAttrCondition(10L, "level", AbacAttrOperator.EQ, "P5", List.of()))
                .thenReturn(1);

        boolean result = evaluator.matchesAll(10L,
                List.of(buildCondition(1L, AbacAttrOperator.NE, "财务部"), buildCondition(2L, AbacAttrOperator.EQ, "P5")));

        assertThat(result).isTrue();
    }

    /**
     * 多条属性条件中有一条不满足时，{@link AbacUserAttrEvaluator#matchesAll} 应判定为不
     * 满足（AND 语义，spec.md"多条属性条件要求同时满足" Scenario）。
     */
    @Test
    void matchesAll_shouldReturnFalse_whenAnyConditionUnsatisfied() {
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField("dept"));
        when(metadataFieldMapper.selectById(2L)).thenReturn(buildField("level"));
        when(userMapper.countMatchingAttrCondition(10L, "dept", AbacAttrOperator.NE, "财务部", List.of()))
                .thenReturn(1);
        when(userMapper.countMatchingAttrCondition(10L, "level", AbacAttrOperator.EQ, "P5", List.of()))
                .thenReturn(0);

        boolean result = evaluator.matchesAll(10L,
                List.of(buildCondition(1L, AbacAttrOperator.NE, "财务部"), buildCondition(2L, AbacAttrOperator.EQ, "P5")));

        assertThat(result).isFalse();
    }

    /**
     * 条件列表为空时，{@link AbacUserAttrEvaluator#matchesAll} 应视为该维度恒满足。
     */
    @Test
    void matchesAll_shouldReturnTrue_whenConditionsEmpty() {
        boolean result = evaluator.matchesAll(10L, List.of());

        assertThat(result).isTrue();
    }

    /**
     * 引用停用状态的元数据字段时，应拒绝本次判定。
     */
    @Test
    void matches_shouldReject_whenFieldDisabled() {
        MetadataFieldEntity field = buildField("dept");
        field.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(field);

        assertThatThrownBy(() -> evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.EQ, "财务部")))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 引用 {@code bizType} 不是 {@code USER} 的元数据字段时，应拒绝本次判定。
     */
    @Test
    void matches_shouldReject_whenFieldBizTypeNotUser() {
        MetadataFieldEntity field = MetadataFieldEntity.builder()
                .id(1L)
                .bizType(FormFieldBizType.ORG)
                .tableName("tab_org")
                .columnName("code")
                .columnType("VARCHAR(64)")
                .fieldCode("code")
                .fieldName("组织编码")
                .status(MetadataFieldStatus.ENABLED)
                .build();
        when(metadataFieldMapper.selectById(1L)).thenReturn(field);

        assertThatThrownBy(() -> evaluator.matches(10L, buildCondition(1L, AbacAttrOperator.EQ, "A")))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 构造一条用户属性条件实体。
     *
     * @param metadataFieldId 元数据字段 id
     * @param operator        运算符
     * @param attrValue       比较值落库原文
     * @return 用户属性条件实体
     */
    private AbacPolicyUserAttrEntity buildCondition(Long metadataFieldId, String operator, String attrValue) {
        return AbacPolicyUserAttrEntity.builder()
                .metadataFieldId(metadataFieldId)
                .operator(operator)
                .attrValue(attrValue)
                .build();
    }

    /**
     * 构造一个测试用的 {@code biz_type=USER} 元数据字段实体。
     *
     * @param columnName 列名
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildField(String columnName) {
        return MetadataFieldEntity.builder()
                .id(1L)
                .bizType(FormFieldBizType.USER)
                .tableName("tab_user")
                .columnName(columnName)
                .columnType("VARCHAR(64)")
                .fieldCode(columnName)
                .fieldName(columnName)
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
