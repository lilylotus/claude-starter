package cn.nihility.rbac.dashboard.service.impl;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.dashboard.service.DashboardStatisticsService;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 首页概览统计业务逻辑实现。四条统计逻辑分别直接对各自的 Mapper 做 {@code selectCount}，
 * 互相独立，且不调用、不依赖 {@code UserServiceImpl#getPage}/{@code AppServiceImpl#getPage}
 * 等业务列表方法——那些方法叠加了组织数据权限范围过滤等业务列表专用逻辑，本次统计要的是
 * 系统全局总数，不做组织范围过滤（dashboard-real-data change design.md Decision 1/3）。
 */
@Service
@RequiredArgsConstructor
public class DashboardStatisticsServiceImpl implements DashboardStatisticsService {

    /** 组织数据访问接口。 */
    private final OrgMapper orgMapper;

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 应用数据访问接口。 */
    private final AppMapper appMapper;

    /** 管理员数据访问接口。 */
    private final AdminMapper adminMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public DashboardStatsVO getStats() {
        Long orgCount = orgMapper.selectCount(
                new LambdaQueryWrapper<OrgEntity>().ne(OrgEntity::getStatus, OrgStatus.DELETED));
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().ne(UserEntity::getStatus, UserStatus.DELETED));
        Long appCount = appMapper.selectCount(
                new LambdaQueryWrapper<AppEntity>().ne(AppEntity::getStatus, AppStatus.DELETED));
        Long adminCount = adminMapper.selectCount(
                new LambdaQueryWrapper<AdminEntity>().ne(AdminEntity::getStatus, AdminStatus.DELETED));

        return DashboardStatsVO.builder()
                .orgCount(orgCount)
                .userCount(userCount)
                .appCount(appCount)
                .adminCount(adminCount)
                .build();
    }
}
