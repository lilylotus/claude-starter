package cn.nihility.rbac.workflow.assignee.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link AdminRoleLookupService} 单元测试：覆盖组织负责人管辖范围命中（直接命中/
 * 递归子孙命中）与沿父级路径向上查找（spec.md "发起人部门负责人解析"/"发起人部门无负责人时
 * 向上级部门查找" Scenario）。
 */
@ExtendWith(MockitoExtension.class)
class AdminRoleLookupServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private AdminMapper adminMapper;

    @Mock
    private AdminRoleMapper adminRoleMapper;

    @Mock
    private AdminOrgScopeMapper adminOrgScopeMapper;

    @Mock
    private OrgMapper orgMapper;

    private AdminRoleLookupService service;

    /** 初始化 MyBatis-Plus Lambda 列缓存，涉及的多个实体均需预热。 */
    @BeforeAll
    static void primeLambdaColumnCache() {
        prime(RoleEntity.class);
        prime(AdminEntity.class);
        prime(AdminRoleEntity.class);
        prime(AdminOrgScopeEntity.class);
        prime(OrgEntity.class);
    }

    private static void prime(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "adminRoleLookupTest");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    void setUp() {
        service = new AdminRoleLookupService(roleMapper, adminMapper, adminRoleMapper, adminOrgScopeMapper, orgMapper);
    }

    /** 角色不存在时应返回空集合。 */
    @Test
    void findUserIdsByRoleCode_shouldReturnEmptyWhenRoleMissing() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThat(service.findUserIdsByRoleCode("SECURITY_ADMIN")).isEmpty();
    }

    /** 持有该角色、状态启用的管理员关联用户应被解析出来。 */
    @Test
    void findUserIdsByRoleCode_shouldReturnEnabledAdminUsers() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(9L).code("SECURITY_ADMIN").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(9L).build()));
        when(adminMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminEntity.builder().id(1L).userId(101L).status(AdminStatus.ENABLED).build()));

        assertThat(service.findUserIdsByRoleCode("SECURITY_ADMIN")).containsExactly(101L);
    }

    /** 组织负责人：管辖范围直接命中目标组织（不递归）。 */
    @Test
    void findOrgLeaderUserIds_shouldMatchDirectScope() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(1L).code("DEPT_LEADER").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(1L).build()));
        when(orgMapper.selectById(10L)).thenReturn(OrgEntity.builder().id(10L).orgPath("1/5/10").build());
        when(adminOrgScopeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminOrgScopeEntity.builder().adminId(1L).orgId(10L).includeChildren(false).build()));
        when(adminMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminEntity.builder().id(1L).userId(201L).status(AdminStatus.ENABLED).build()));

        assertThat(service.findOrgLeaderUserIds(10L, "DEPT_LEADER")).containsExactly(201L);
    }

    /** 组织负责人：管辖范围为上级组织且 includeChildren=true 时递归命中子孙组织。 */
    @Test
    void findOrgLeaderUserIds_shouldMatchRecursiveScope() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(1L).code("DEPT_LEADER").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(1L).build()));
        when(orgMapper.selectById(10L)).thenReturn(OrgEntity.builder().id(10L).orgPath("1/5/10").build());
        when(adminOrgScopeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminOrgScopeEntity.builder().adminId(1L).orgId(5L).includeChildren(true).build()));
        when(adminMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminEntity.builder().id(1L).userId(202L).status(AdminStatus.ENABLED).build()));

        assertThat(service.findOrgLeaderUserIds(10L, "DEPT_LEADER")).containsExactly(202L);
    }

    /** 组织负责人：管辖范围为上级组织但 includeChildren=false 时不命中子孙组织。 */
    @Test
    void findOrgLeaderUserIds_shouldNotMatchWhenNotRecursive() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(1L).code("DEPT_LEADER").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(1L).build()));
        when(orgMapper.selectById(10L)).thenReturn(OrgEntity.builder().id(10L).orgPath("1/5/10").build());
        when(adminOrgScopeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminOrgScopeEntity.builder().adminId(1L).orgId(5L).includeChildren(false).build()));

        assertThat(service.findOrgLeaderUserIds(10L, "DEPT_LEADER")).isEmpty();
    }

    /** 沿父级路径向上查找：本级无负责人，上一级有负责人。 */
    @Test
    void findParentOrgLeaderUserIds_shouldWalkUpUntilResolved() {
        setUp();
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(1L).code("DEPT_LEADER").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(1L).build()));
        when(orgMapper.selectById(10L)).thenReturn(OrgEntity.builder().id(10L).orgParentPath("1/5").build());
        // 最近的上级（5）没有负责人，需要继续向上到（1）。
        when(orgMapper.selectById(5L)).thenReturn(OrgEntity.builder().id(5L).orgPath("1/5").build());
        when(orgMapper.selectById(1L)).thenReturn(OrgEntity.builder().id(1L).orgPath("1").build());
        when(adminOrgScopeMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminOrgScopeEntity.builder().adminId(1L).orgId(1L).includeChildren(false).build()));
        when(adminMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminEntity.builder().id(1L).userId(303L).status(AdminStatus.ENABLED).build()));

        Set<Long> result = service.findParentOrgLeaderUserIds(10L, "DEPT_LEADER");

        assertThat(result).containsExactly(303L);
    }

    /** 组织不存在父级路径时返回空集合。 */
    @Test
    void findParentOrgLeaderUserIds_shouldReturnEmptyWhenNoParent() {
        setUp();
        when(orgMapper.selectById(10L)).thenReturn(OrgEntity.builder().id(10L).orgParentPath(null).build());

        assertThat(service.findParentOrgLeaderUserIds(10L, "DEPT_LEADER")).isEmpty();
    }

    /** 判断用户是否持有指定角色。 */
    @Test
    void userHasRoleCode_shouldReturnTrueWhenAdminHoldsRole() {
        setUp();
        when(adminMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminEntity.builder().id(1L).userId(101L).status(AdminStatus.ENABLED).build()));
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(RoleEntity.builder().id(9L).code("SECURITY_ADMIN").status(RoleStatus.ENABLED).build());
        when(adminRoleMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(AdminRoleEntity.builder().adminId(1L).roleId(9L).build()));

        assertThat(service.userHasRoleCode(101L, "SECURITY_ADMIN")).isTrue();
    }

    /** 用户不是任何启用管理员身份时应返回 false。 */
    @Test
    void userHasRoleCode_shouldReturnFalseWhenNotAdmin() {
        setUp();
        when(adminMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        assertThat(service.userHasRoleCode(101L, "SECURITY_ADMIN")).isFalse();
    }
}
