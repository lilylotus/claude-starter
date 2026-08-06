// 首页概览页相关类型定义，字段命名和后端 DashboardStatsVO DTO 对齐。

// 首页统计卡片数据，来自 GET /api/dashboard/stats：组织总数、身份总数、接入应用、管理员总数
export interface DashboardStats {
  orgCount: number
  userCount: number
  appCount: number
  adminCount: number
}
