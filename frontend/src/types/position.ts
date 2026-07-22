// 任职管理相关类型定义，字段命名和后端 PositionVO / PositionCreateRequest /
// PositionUpdateRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const POSITION_STATUS_ENABLED = 2000
export const POSITION_STATUS_DISABLED = 3000

// 任职记录列表行/详情数据，来自 GET /api/positions、GET /api/positions/{id}、
// POST /api/positions、PUT /api/positions/{id} 等接口的返回值
export interface PositionRow {
  id: number
  userId: number
  userName: string
  orgId: number
  orgName: string
  positionType: string
  positionAddress: string
  positionPhone: string
  showOrder: number
  remark: string
  // ext1~ext10：可开放配置的扩展字段，是否展示/可编辑由"表单字段定义"（bizType=POSITION）驱动，
  // 未被任何启用字段定义绑定时恒为空
  ext1?: string
  ext2?: string
  ext3?: string
  ext4?: string
  ext5?: string
  ext6?: string
  ext7?: string
  ext8?: string
  ext9?: string
  ext10?: string
  status: number
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 编辑任职记录的请求体：不含 userId——后端更新接口不支持修改任职记录所属的用户
export interface PositionFormRequest {
  orgId: number
  positionType: string
  positionAddress: string
  positionPhone: string
  showOrder: number
  remark: string
  ext1?: string
  ext2?: string
  ext3?: string
  ext4?: string
  ext5?: string
  ext6?: string
  ext7?: string
  ext8?: string
  ext9?: string
  ext10?: string
}

// 新增任职记录的请求体：在编辑表单字段基础上附加所属用户 id，须为已存在的用户；
// 一旦创建，所属用户不可再修改（换人 = 删除重建）
export interface PositionCreateRequest extends PositionFormRequest {
  userId: number
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
