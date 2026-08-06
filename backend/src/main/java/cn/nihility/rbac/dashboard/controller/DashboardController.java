package cn.nihility.rbac.dashboard.controller;

import cn.nihility.rbac.dashboard.dto.DashboardStatsVO;
import cn.nihility.rbac.dashboard.service.DashboardRecentOperationService;
import cn.nihility.rbac.dashboard.service.DashboardStatisticsService;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页概览统计接口，对所有已登录用户开放，不与任何业务模块的查看权限点绑定（归入
 * {@code IdentityAuthFilter} 的自助操作白名单），只返回聚合计数，不返回任何具体业务记录。
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "首页概览", description = "首页统计卡片数据接口，只读")
public class DashboardController {

    /** 首页概览统计业务逻辑接口。 */
    private final DashboardStatisticsService dashboardStatisticsService;

    /** 首页概览"当前用户最近操作"业务逻辑接口。 */
    private final DashboardRecentOperationService dashboardRecentOperationService;

    /**
     * 查询组织/用户/应用/管理员四个维度的真实总数：当前登录账号未配置管辖组织范围时为
     * 系统全局总数，配置了管辖组织范围时收紧为范围内的总数。
     *
     * @return 首页概览统计结果
     */
    @Operation(summary = "查询首页概览统计",
            description = "返回组织/用户/应用/管理员四个维度排除已逻辑删除记录后的真实总数，"
                    + "按当前登录账号的管辖组织范围收紧（未配置管辖范围时为系统全局总数），"
                    + "口径与组织树/应用列表/用户列表等业务列表页保持一致")
    @GetMapping("/api/dashboard/stats")
    public DashboardStatsVO stats() {
        return dashboardStatisticsService.getStats();
    }

    /**
     * 查询当前登录账号自己最近的操作记录，固定最多 4 条，只返回当前用户自己的记录，
     * 不做"操作日志管理"权限点校验，不需要账号拥有 {@code OperationLogManagement:log:view}
     * 权限即可查看自己的最近操作。
     *
     * @return 当前用户最近操作记录列表
     */
    @Operation(summary = "查询当前用户最近操作",
            description = "只返回当前登录账号自己最近的操作记录（固定最多 4 条），不做权限点校验，"
                    + "全系统范围的操作审计请使用「操作日志」管理页面")
    @GetMapping("/api/dashboard/recent-operations")
    public List<OperationLogVO> recentOperations() {
        return dashboardRecentOperationService.getRecentOperations();
    }
}
