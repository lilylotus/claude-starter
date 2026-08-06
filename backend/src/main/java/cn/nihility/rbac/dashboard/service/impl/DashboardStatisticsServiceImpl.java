package cn.nihility.rbac.dashboard.service.impl;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.dashboard.service.DashboardStatisticsService;
import cn.nihility.rbac.permission.constant.PermissionStatus;
import cn.nihility.rbac.permission.entity.PermissionEntity;
import cn.nihility.rbac.permission.mapper.PermissionMapper;
import cn.nihility.rbac.role.constant.RoleStatus;
import cn.nihility.rbac.role.entity.RoleEntity;
import cn.nihility.rbac.role.mapper.RoleMapper;
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

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /** 应用数据访问接口。 */
    private final AppMapper appMapper;

    /** 角色数据访问接口。 */
    private final RoleMapper roleMapper;

    /** 权限点数据访问接口。 */
    private final PermissionMapper permissionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public DashboardStatsVO getStats() {
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<UserEntity>().ne(UserEntity::getStatus, UserStatus.DELETED));
        Long appCount = appMapper.selectCount(
                new LambdaQueryWrapper<AppEntity>().ne(AppEntity::getStatus, AppStatus.DELETED));
        Long roleCount = roleMapper.selectCount(
                new LambdaQueryWrapper<RoleEntity>().ne(RoleEntity::getStatus, RoleStatus.DELETED));
        Long permissionCount = permissionMapper.selectCount(
                new LambdaQueryWrapper<PermissionEntity>().ne(PermissionEntity::getStatus, PermissionStatus.DELETED));

        return DashboardStatsVO.builder()
                .userCount(userCount)
                .appCount(appCount)
                .roleCount(roleCount)
                .permissionCount(permissionCount)
                .build();
    }
}
