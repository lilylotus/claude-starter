package cn.nihility.rbac.dashboard.service;

import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;

/**
 * 首页概览统计业务逻辑接口，独立于各业务模块的分页列表接口，自行定义统计口径。
 */
public interface DashboardStatisticsService {

    /**
     * 查询用户/应用/角色/权限点四个维度的系统全局总数（排除已逻辑删除记录，不做数据
     * 权限范围过滤）。
     *
     * @return 首页概览统计结果
     */
    DashboardStatsVO getStats();
}
