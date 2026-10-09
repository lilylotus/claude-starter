// 图片管理/图片展示相关类型定义，字段命名和后端 ImageVO / PageResult 对齐
// （cn.nihility.rbac.image 模块，openspec/changes/add-image-gallery）。

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

// 图片记录展示字段，对应后端 ImageVO（不包含磁盘保存文件名 storedFileName）
export interface ImageVO {
  id: number
  originalFileName: string
  fileSize: number
  imageWidth: number
  imageHeight: number
  createTime: string
}

// 图片管理分页列表查询参数，对应 GET /api/images，均为可选
export interface ImagePageQueryParams {
  page?: number
  pageSize?: number
  keyword?: string
  startTime?: string
  endTime?: string
}

// 图片展示 feed 分页查询参数，对应 GET /api/images/feed，不支持筛选参数
export interface ImageFeedQueryParams {
  page?: number
  pageSize?: number
}

// 批量上传中单个文件的处理结果，对应后端 ImageBatchUploadResultVO：一个文件失败不影响
// 其它文件，success 为 false 时 image 为 undefined、message 携带失败原因
export interface ImageBatchUploadResultVO {
  originalFileName: string
  success: boolean
  image?: ImageVO
  message?: string
}
