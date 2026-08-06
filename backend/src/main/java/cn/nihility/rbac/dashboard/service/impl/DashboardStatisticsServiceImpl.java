package cn.nihility.rbac.dashboard.service.impl;

import cn.nihility.rbac.admin.constant.AdminStatus;
import cn.nihility.rbac.admin.entity.AdminEntity;
import cn.nihility.rbac.admin.mapper.AdminMapper;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.dashboard.service.DashboardStatisticsService;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 首页概览统计业务逻辑实现。先解析一次当前登录用户的管辖组织范围，未配置管辖范围
 * （{@code resolveAllowedOrgIds} 返回空 {@code Optional}）时四条统计逻辑直接对各自的
 * Mapper 做全局 {@code selectCount}，与业务列表页解耦；配置了管辖范围时四条统计逻辑
 * 分别追加组织范围过滤，口径与组织树/应用列表/用户列表等业务列表页保持一致
 * （scope-dashboard-stats-by-admin-org change design.md Decision 1，反转了
 * dashboard-real-data change design.md Decision 3 "口径独立于数据权限范围"的取舍）。
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

    /** 管辖组织范围解析业务逻辑接口，用于按当前登录用户的管辖组织范围收紧统计口径。 */
    private final OrgScopeService orgScopeService;

    /**
     * {@inheritDoc}
     */
    @Override
    public DashboardStatsVO getStats() {
        Optional<Set<Long>> allowedOrgIds = orgScopeService.resolveAllowedOrgIds(CurrentUserContext.getUserId());

        Long orgCount;
        Long userCount;
        Long appCount;
        Long adminCount;
        if (allowedOrgIds.isEmpty()) {
            orgCount = orgMapper.selectCount(
                    new LambdaQueryWrapper<OrgEntity>().ne(OrgEntity::getStatus, OrgStatus.DELETED));
            userCount = userMapper.selectCount(
                    new LambdaQueryWrapper<UserEntity>().ne(UserEntity::getStatus, UserStatus.DELETED));
            appCount = appMapper.selectCount(
                    new LambdaQueryWrapper<AppEntity>().ne(AppEntity::getStatus, AppStatus.DELETED));
            adminCount = adminMapper.selectCount(
                    new LambdaQueryWrapper<AdminEntity>().ne(AdminEntity::getStatus, AdminStatus.DELETED));
        } else {
            // allowed 为空集合时用恒不匹配的哨兵条件（id = -1）兜底，而不是依赖 MyBatis-Plus
            // 对空集合 .in() 的默认行为，与 AppServiceImpl#getPage 的既有写法保持一致
            // （scope-dashboard-stats-by-admin-org change design.md Decision 3）。
            Set<Long> allowed = allowedOrgIds.get();
            LambdaQueryWrapper<OrgEntity> orgWrapper =
                    new LambdaQueryWrapper<OrgEntity>().ne(OrgEntity::getStatus, OrgStatus.DELETED);
            LambdaQueryWrapper<AppEntity> appWrapper =
                    new LambdaQueryWrapper<AppEntity>().ne(AppEntity::getStatus, AppStatus.DELETED);
            if (allowed.isEmpty()) {
                orgWrapper.eq(OrgEntity::getId, -1L);
                appWrapper.eq(AppEntity::getOrgId, -1L);
            } else {
                orgWrapper.in(OrgEntity::getId, allowed);
                appWrapper.in(AppEntity::getOrgId, allowed);
            }
            orgCount = orgMapper.selectCount(orgWrapper);
            appCount = appMapper.selectCount(appWrapper);
            userCount = (long) userMapper.countUsersInScope(allowed, UserStatus.DELETED, PositionStatus.DELETED);
            adminCount = (long) adminMapper.countAdminsInScope(allowed, AdminStatus.DELETED, PositionStatus.DELETED);
        }

        return DashboardStatsVO.builder()
                .orgCount(orgCount)
                .userCount(userCount)
                .appCount(appCount)
                .adminCount(adminCount)
                .build();
    }
}
