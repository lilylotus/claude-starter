package cn.nihility.rbac.userrole.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserMatchConditionResolver} 的单元测试，覆盖组织范围命中、属性条件命中 USER 域、
 * 属性条件命中 POSITION 域、多条件取交集、引用停用/域外字段时拒绝等分支
 * （add-user-role-batch-assignment change tasks.md 2.6）。
 */
@ExtendWith(MockitoExtension.class)
class UserMatchConditionResolverTest {

    /** 被测组件的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测组件的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测组件的用户任职记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测组件的组织子孙展开工具依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgDescendantExpander orgDescendantExpander;

    /** 被测组件实例。 */
    private UserMatchConditionResolver resolver;

    /**
     * 每个用例执行前重新构造被测组件。
     */
    @BeforeEach
    void setUp() {
        resolver = new UserMatchConditionResolver(metadataFieldMapper, userMapper, userPositionMapper,
                orgDescendantExpander);
    }

    /**
     * 仅配置组织范围条件（含子组织）时，应展开子孙组织后按任职记录匹配命中用户。
     */
    @Test
    void resolve_shouldMatchByOrgScope_whenOnlyOrgScopeConfigured() {
        UserRoleOrgScopeCondition condition = new UserRoleOrgScopeCondition();
        condition.setOrgId(10L);
        condition.setIncludeChildren(true);
        when(orgDescendantExpander.expandWithDescendants(Set.of(10L))).thenReturn(Set.of(10L, 11L));
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(11L).status(PositionStatus.ENABLED).build()));

        Set<Long> result = resolver.resolve(List.of(condition), List.of());

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    /**
     * 仅配置用户属性条件（{@code bizType=USER}）时，应按解析出的物理列名匹配用户。
     */
    @Test
    void resolve_shouldMatchByUserAttr_whenUserDomainFieldConfigured() {
        UserRoleUserAttrCondition condition = new UserRoleUserAttrCondition();
        condition.setMetadataFieldId(1L);
        condition.setOperator(UserRoleAttrOperator.EQ);
        condition.setAttrValue("male");
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField(FormFieldBizType.USER, "tab_user", "gender"));
        when(userMapper.selectIdsByAttrCondition("gender", UserRoleAttrOperator.EQ, "male", List.of()))
                .thenReturn(List.of(1L, 2L));

        Set<Long> result = resolver.resolve(List.of(), List.of(condition));

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    /**
     * 仅配置用户属性条件（{@code bizType=POSITION}）时，应分派到任职记录属性匹配。
     */
    @Test
    void resolve_shouldMatchByUserAttr_whenPositionDomainFieldConfigured() {
        UserRoleUserAttrCondition condition = new UserRoleUserAttrCondition();
        condition.setMetadataFieldId(2L);
        condition.setOperator(UserRoleAttrOperator.EQ);
        condition.setAttrValue("minister");
        when(metadataFieldMapper.selectById(2L))
                .thenReturn(buildField(FormFieldBizType.POSITION, "tab_user_position", "position_type"));
        when(userPositionMapper.selectIdsByAttrCondition("position_type", UserRoleAttrOperator.EQ, "minister", List.of()))
                .thenReturn(List.of(3L, 4L));

        Set<Long> result = resolver.resolve(List.of(), List.of(condition));

        assertThat(result).containsExactlyInAnyOrder(3L, 4L);
    }

    /**
     * 同时配置组织范围条件与用户属性条件（跨 USER/POSITION 两个域）时，命中结果应为各类
     * 结果集的交集。
     */
    @Test
    void resolve_shouldIntersect_whenOrgScopeAndUserAttrBothConfigured() {
        UserRoleOrgScopeCondition orgScope = new UserRoleOrgScopeCondition();
        orgScope.setOrgId(10L);
        orgScope.setIncludeChildren(false);
        when(orgDescendantExpander.expandWithDescendants(Set.of())).thenReturn(Set.of());
        when(userPositionMapper.selectList(any())).thenReturn(List.of(
                UserPositionEntity.builder().userId(1L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(2L).orgId(10L).status(PositionStatus.ENABLED).build(),
                UserPositionEntity.builder().userId(3L).orgId(10L).status(PositionStatus.ENABLED).build()));

        UserRoleUserAttrCondition genderCondition = new UserRoleUserAttrCondition();
        genderCondition.setMetadataFieldId(1L);
        genderCondition.setOperator(UserRoleAttrOperator.EQ);
        genderCondition.setAttrValue("male");
        UserRoleUserAttrCondition positionCondition = new UserRoleUserAttrCondition();
        positionCondition.setMetadataFieldId(2L);
        positionCondition.setOperator(UserRoleAttrOperator.EQ);
        positionCondition.setAttrValue("minister");
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField(FormFieldBizType.USER, "tab_user", "gender"));
        when(metadataFieldMapper.selectById(2L))
                .thenReturn(buildField(FormFieldBizType.POSITION, "tab_user_position", "position_type"));
        when(userMapper.selectIdsByAttrCondition("gender", UserRoleAttrOperator.EQ, "male", List.of()))
                .thenReturn(List.of(2L, 3L, 4L));
        when(userPositionMapper.selectIdsByAttrCondition("position_type", UserRoleAttrOperator.EQ, "minister", List.of()))
                .thenReturn(List.of(2L, 3L));

        Set<Long> result = resolver.resolve(List.of(orgScope), List.of(genderCondition, positionCondition));

        assertThat(result).containsExactlyInAnyOrder(2L, 3L);
    }

    /**
     * 引用停用状态的元数据字段时，应拒绝本次请求。
     */
    @Test
    void resolve_shouldReject_whenFieldDisabled() {
        UserRoleUserAttrCondition condition = new UserRoleUserAttrCondition();
        condition.setMetadataFieldId(1L);
        condition.setOperator(UserRoleAttrOperator.EQ);
        condition.setAttrValue("male");
        MetadataFieldEntity field = buildField(FormFieldBizType.USER, "tab_user", "gender");
        field.setStatus(MetadataFieldStatus.DISABLED);
        when(metadataFieldMapper.selectById(1L)).thenReturn(field);

        assertThatThrownBy(() -> resolver.resolve(List.of(), List.of(condition)))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 引用 {@code bizType} 既不是 {@code USER} 也不是 {@code POSITION} 的元数据字段时，
     * 应拒绝本次请求。
     */
    @Test
    void resolve_shouldReject_whenFieldBizTypeOutOfRange() {
        UserRoleUserAttrCondition condition = new UserRoleUserAttrCondition();
        condition.setMetadataFieldId(1L);
        condition.setOperator(UserRoleAttrOperator.EQ);
        condition.setAttrValue("A");
        when(metadataFieldMapper.selectById(1L)).thenReturn(buildField(FormFieldBizType.ORG, "tab_org", "code"));

        assertThatThrownBy(() -> resolver.resolve(List.of(), List.of(condition)))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 组织范围条件、用户属性条件均未配置时，作为防御措施应返回空集合，不做"全部启用用户"
     * 兜底（调用方须在调用前校验至少配置一类条件，本组件本身不承担该校验职责）。
     */
    @Test
    void resolve_shouldReturnEmptySet_whenBothUnconfigured() {
        Set<Long> result = resolver.resolve(List.of(), List.of());

        assertThat(result).isEmpty();
    }

    /**
     * 构造一个测试用的元数据字段实体。
     *
     * @param bizType    业务对象类型
     * @param tableName  所属表名
     * @param columnName 列名
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildField(String bizType, String tableName, String columnName) {
        return MetadataFieldEntity.builder()
                .id(1L)
                .bizType(bizType)
                .tableName(tableName)
                .columnName(columnName)
                .columnType("VARCHAR(64)")
                .fieldCode(columnName)
                .fieldName(columnName)
                .status(MetadataFieldStatus.ENABLED)
                .build();
    }
}
