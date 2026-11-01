// 视频管理相关类型定义，字段命名和后端 VideoVO / VideoPlayTicketVO 对齐
// （cn.nihility.rbac.video 模块，openspec/changes/add-video-management）。

export type { PageResult } from './image'

// 视频记录展示字段，对应后端 VideoVO（不包含磁盘保存文件名 storedFileName）
export interface VideoVO {
  id: number
  originalFileName: string
  fileSize: number
  contentType: string
  createBy: string | null
  createTime: string
}

// 视频管理分页列表查询参数，对应 GET /api/videos，均为可选
export interface VideoPageQueryParams {
  page?: number
  pageSize?: number
  keyword?: string
  startTime?: string
  endTime?: string
}

// 视频播放凭证，对应后端 VideoPlayTicketVO：ticket 拼接到流式播放地址上作为 <video src>
export interface VideoPlayTicketVO {
  ticket: string
  expiresInSeconds: number
}
