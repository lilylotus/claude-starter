package cn.nihility.rbac.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 首页概览统计视图对象，四个维度均为系统全局总数（排除已逻辑删除记录），不做数据权限
 * 范围（组织范围）过滤，与各业务模块分页列表接口的统计口径互相独立。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "首页概览统计")
public class DashboardStatsVO {

    /** 组织总数（排除已逻辑删除）。 */
    @Schema(description = "组织总数")
    private Long orgCount;

    /** 用户总数（排除已逻辑删除）。 */
    @Schema(description = "用户总数")
    private Long userCount;

    /** 应用总数（排除已逻辑删除）。 */
    @Schema(description = "应用总数")
    private Long appCount;

    /** 管理员总数（排除已逻辑删除）。 */
    @Schema(description = "管理员总数")
    private Long adminCount;
}
