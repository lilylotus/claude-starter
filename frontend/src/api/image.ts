import request from './request'
import type {
  ImageBatchUploadResultVO,
  ImageFeedQueryParams,
  ImagePageQueryParams,
  ImageVO,
  PageResult,
} from '@/types/image'

// 图片管理/图片展示接口封装，组件不直接调用 axios。
// 后端契约见 cn.nihility.rbac.image.controller.ImageController。

// 上传图片：multipart/form-data，字段名固定为 file，仅支持 jpg/jpeg/png/gif 且内容
// 可被解析为合法图片，成功后返回新增记录
export function uploadImage(file: File): Promise<ImageVO> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/images/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 批量上传图片：一次请求携带多个文件（字段名固定为 files），一个文件校验失败不影响其它
// 文件，返回每个文件各自的成功/失败结果，顺序与传入的文件顺序一致
export function uploadImagesBatch(files: File[]): Promise<ImageBatchUploadResultVO[]> {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return request.post('/images/upload/batch', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 图片管理分页列表：keyword（文件名关键字）、startTime/endTime（创建时间区间，ISO
// LocalDateTime 字符串）均可选，可组合使用；page/pageSize 不传时后端分别默认 1、10
export function listImages(params: ImagePageQueryParams): Promise<PageResult<ImageVO>> {
  return request.get('/images', { params })
}

// 图片展示 feed 分页：不支持筛选参数，page/pageSize 不传时后端分别默认 1、20
export function feedImages(params: ImageFeedQueryParams): Promise<PageResult<ImageVO>> {
  return request.get('/images/feed', { params })
}

// 单条逻辑删除
export function deleteImage(id: number): Promise<void> {
  return request.delete(`/images/${id}`)
}

// 批量逻辑删除，返回实际删除数量；无效/已删除的 id 会被自动跳过，不需要前端处理失败分支
export function batchDeleteImages(ids: number[]): Promise<number> {
  return request.delete('/images/batch', { data: { ids } })
}

// 原图二进制内容：不能直接把 `/images/{id}/raw` 当 URL 给 <img>/el-image 的 src 用——
// IdentityAuthFilter 对除白名单外的所有接口都要求 identity-token/menu 请求头，这两个头只
// 由本文件引入的 axios 实例的请求拦截器注入，浏览器原生发出的 <img> 请求不会带上，直接用
// URL 会被拦截返回 JSON 错误体，图片渲染失败。必须经过 axios（带上请求头）以
// responseType: 'blob' 取回二进制内容，再由调用方用 URL.createObjectURL 生成本地
// blob URL 使用，参见 components/AuthorizedImage.vue。
export function fetchImageRaw(id: number): Promise<Blob> {
  return request.get(`/images/${id}/raw`, { responseType: 'blob' })
}
