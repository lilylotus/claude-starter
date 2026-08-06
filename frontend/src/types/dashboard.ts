// 首页概览页相关类型定义，字段命名和后端 DashboardStatsVO DTO 对齐。

// 首页统计卡片数据，来自 GET /api/dashboard/stats：身份总数、接入应用、角色数量、权限点
export interface DashboardStats {
  userCount: number
  appCount: number
  roleCount: number
  permissionCount: number
}
