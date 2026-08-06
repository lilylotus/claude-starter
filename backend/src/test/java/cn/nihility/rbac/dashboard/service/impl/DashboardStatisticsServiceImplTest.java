package cn.nihility.rbac.dashboard.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
 * {@link DashboardStatisticsServiceImpl} 的单元测试，重点覆盖四条统计逻辑各自独立、
 * 直接对各自 Mapper 做 {@code selectCount}，互不依赖对方结果。
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
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(configuration, "dashboardStatisticsServiceImplTest");
        assistant.setCurrentNamespace(UserEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, UserEntity.class);
    }

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        dashboardStatisticsService =
                new DashboardStatisticsServiceImpl(orgMapper, userMapper, appMapper, adminMapper);
    }

    /**
     * 查询首页概览统计时，应分别对四个 Mapper 独立做 {@code selectCount}，并把结果原样
     * 组装进返回的 {@link DashboardStatsVO} 对应字段。
     */
    @Test
    void getStats_shouldAggregateFourIndependentCounts() {
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
     * 用户维度统计的查询条件应仅以 {@code status <>} 排除已逻辑删除记录，不叠加任何
     * 组织数据权限范围过滤，确认统计口径与 {@code UserServiceImpl#getPage} 彻底解耦。
     */
    @Test
    void getStats_userQueryConditionShouldOnlyExcludeDeletedStatus() {
        when(orgMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        dashboardStatisticsService.getStats();

        ArgumentCaptor<LambdaQueryWrapper<UserEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(userMapper).selectCount(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("status <>");
    }
}
