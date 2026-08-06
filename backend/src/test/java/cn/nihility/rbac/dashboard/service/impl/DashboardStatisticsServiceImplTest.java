package cn.nihility.rbac.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.Optional;
import java.util.Set;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link DashboardStatisticsServiceImpl} 的单元测试，覆盖"未配置管辖组织范围时展示
 * 系统全局总数"与"配置了管辖组织范围时四个维度按范围收紧"两条路径
 * （scope-dashboard-stats-by-admin-org change design.md）。
 */
@ExtendWith(MockitoExtension.class)
class DashboardStatisticsServiceImplTest {

    /** 被测服务的组织数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测服务的应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppMapper appMapper;

    /** 被测服务的管理员数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AdminMapper adminMapper;

    /** 被测服务的管辖组织范围解析依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgScopeService orgScopeService;

    /** 被测服务实例。 */
    private DashboardStatisticsServiceImpl dashboardStatisticsService;

    /**
     * MyBatis-Plus 的 {@code LambdaQueryWrapper} 需要先有实体的 {@code TableInfo} 缓存才能在
     * 脱离 Spring 容器的纯单元测试里调用 {@code getSqlSegment()} 做条件断言（正常启动时该
     * 缓存由 Spring Boot 扫描 Mapper 时自动建立），这里手动触发一次初始化，仅供本测试类使用。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        primeEntity(configuration, UserEntity.class);
        primeEntity(configuration, OrgEntity.class);
        primeEntity(configuration, AppEntity.class);
    }

    /**
     * 每个实体各自用一个新的 {@code MapperBuilderAssistant}：{@code setCurrentNamespace}
     * 一旦设置就不允许在同一个 assistant 上改成另一个命名空间，多个实体不能共用同一个
     * assistant 实例。
     */
    private static void primeEntity(Configuration configuration, Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "dashboardStatisticsServiceImplTest");
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        dashboardStatisticsService =
                new DashboardStatisticsServiceImpl(orgMapper, userMapper, appMapper, adminMapper, orgScopeService);
    }

    /**
     * 未配置管辖组织范围（{@code resolveAllowedOrgIds} 返回空 {@code Optional}）时，应分别对
     * 四个 Mapper 独立做全局 {@code selectCount}，并把结果原样组装进返回的
     * {@link DashboardStatsVO} 对应字段，不追加任何组织范围过滤。
     */
    @Test
    void getStats_shouldAggregateFourIndependentCountsWhenUnrestricted() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(11L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(22L);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(33L);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(44L);

        DashboardStatsVO result = dashboardStatisticsService.getStats();

        assertThat(result.getOrgCount()).isEqualTo(11L);
        assertThat(result.getUserCount()).isEqualTo(22L);
        assertThat(result.getAppCount()).isEqualTo(33L);
        assertThat(result.getAdminCount()).isEqualTo(44L);
        verify(orgMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(userMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(appMapper).selectCount(any(LambdaQueryWrapper.class));
        verify(adminMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    /**
     * 未配置管辖组织范围时，用户维度统计的查询条件应仅以 {@code status <>} 排除已逻辑
     * 删除记录，不叠加任何组织数据权限范围过滤，确认统计口径与 {@code UserServiceImpl#getPage}
     * 彻底解耦。
     */
    @Test
    void getStats_userQueryConditionShouldOnlyExcludeDeletedStatusWhenUnrestricted() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.empty());
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        dashboardStatisticsService.getStats();

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("status <>");
    }

    /**
     * 配置了管辖组织范围（非空 {@code Optional}）时，组织/应用维度应在既有 Mapper 上追加
     * "落在管辖范围内"的过滤条件，用户/管理员维度应改为调用按管辖范围计数的专用方法，
     * 且四个 Mapper 都不再调用未受限时使用的 {@code selectCount}。
     */
    @Test
    void getStats_shouldScopeAllFourDimensionsWhenRestricted() {
        Set<Long> allowedOrgIds = Set.of(100L, 200L);
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(allowedOrgIds));
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(userMapper.countUsersInScope(eq(allowedOrgIds), anyInt(), anyInt())).thenReturn(5);
        when(adminMapper.countAdminsInScope(eq(allowedOrgIds), anyInt(), anyInt())).thenReturn(1);

        DashboardStatsVO result = dashboardStatisticsService.getStats();

        assertThat(result.getOrgCount()).isEqualTo(2L);
        assertThat(result.getAppCount()).isEqualTo(3L);
        assertThat(result.getUserCount()).isEqualTo(5L);
        assertThat(result.getAdminCount()).isEqualTo(1L);

        ArgumentCaptor<LambdaQueryWrapper<OrgEntity>> orgCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orgMapper).selectCount(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getSqlSegment()).contains("id IN");

        ArgumentCaptor<LambdaQueryWrapper<AppEntity>> appCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(appMapper).selectCount(appCaptor.capture());
        assertThat(appCaptor.getValue().getSqlSegment()).contains("orgId IN");

        verify(userMapper).countUsersInScope(eq(allowedOrgIds), anyInt(), anyInt());
        verify(adminMapper).countAdminsInScope(eq(allowedOrgIds), anyInt(), anyInt());
    }

    /**
     * 配置了管辖组织范围但解析结果是空集合（理论边界情况）时，组织/应用维度应使用恒不
     * 匹配的哨兵条件（{@code id = -1}）兜底，而不是让空集合 {@code .in()} 决定行为。
     */
    @Test
    void getStats_shouldUseSentinelConditionWhenAllowedOrgIdsEmpty() {
        when(orgScopeService.resolveAllowedOrgIds(any())).thenReturn(Optional.of(Set.of()));
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.countUsersInScope(any(), anyInt(), anyInt())).thenReturn(0);
        when(adminMapper.countAdminsInScope(any(), anyInt(), anyInt())).thenReturn(0);

        dashboardStatisticsService.getStats();

        ArgumentCaptor<LambdaQueryWrapper<OrgEntity>> orgCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orgMapper).selectCount(orgCaptor.capture());
        assertThat(orgCaptor.getValue().getSqlSegment()).contains("id =");
    }
}
