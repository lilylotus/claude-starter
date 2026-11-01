import request from './request'
import type { PageResult, VideoPageQueryParams, VideoPlayTicketVO, VideoVO } from '@/types/video'

// 视频管理接口封装，组件不直接调用 axios。
// 后端契约见 cn.nihility.rbac.video.controller.VideoController。

// 允许上传的视频后缀与单个文件大小上限，与后端 VideoServiceImpl 白名单、
// rbac.video.max-file-size 默认值保持一致，前端预校验只为尽早提示，最终以后端校验为准
export const VIDEO_ACCEPT_SUFFIXES = ['.mp4', '.webm', '.ogg', '.ogv'] as const
export const VIDEO_MAX_FILE_SIZE = 2 * 1024 * 1024 * 1024

// 上传视频：multipart/form-data，字段名固定为 file。大文件上传耗时长，单独关闭 axios
// 默认 10 秒超时（timeout: 0），并通过 onProgress 回调上传进度百分比
export function uploadVideo(file: File, onProgress?: (percent: number) => void): Promise<VideoVO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/videos/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    onUploadProgress: (event) => {
      if (onProgress && event.total) {
        onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)))
      }
    },
  })
}

// 视频管理分页列表：keyword、startTime/endTime（ISO LocalDateTime 字符串）均可选；
// page/pageSize 不传时后端分别默认 1、10
export function listVideos(params: VideoPageQueryParams): Promise<PageResult<VideoVO>> {
  return request.get('/videos', { params })
}

// 单条逻辑删除
export function deleteVideo(id: number): Promise<void> {
  return request.delete(`/videos/${id}`)
}

// 批量逻辑删除，返回实际删除数量；无效/已删除的 id 会被自动跳过
export function batchDeleteVideos(ids: number[]): Promise<number> {
  return request.delete('/videos/batch', { data: { ids } })
}

// 换取短期播放凭证：浏览器原生 <video src> 无法携带 identity-token/menu 请求头，必须先经
// axios（带请求头）换取凭证，再用 buildVideoStreamUrl 拼出流式播放地址
export function createVideoPlayTicket(id: number): Promise<VideoPlayTicketVO> {
  return request.post(`/videos/${id}/play-ticket`)
}

// 流式播放地址：后端按凭证鉴权并支持 HTTP Range，可直接作为 <video src> 边下边播、拖动进度
export function buildVideoStreamUrl(id: number, ticket: string): string {
  return `/api/videos/${id}/stream?ticket=${encodeURIComponent(ticket)}`
}
