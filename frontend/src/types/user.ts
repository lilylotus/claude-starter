// 用户管理相关类型定义，字段命名和后端 UserVO / UserPositionVO / UserCreateRequest /
// UserUpdateRequest / UserPositionRequest DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const USER_STATUS_ENABLED = 2000
export const USER_STATUS_DISABLED = 3000

// 性别常量：0 = 未知，1 = 男，2 = 女，对应后端 UserGender 常量类。
export const USER_GENDER_UNKNOWN = 0
export const USER_GENDER_MALE = 1
export const USER_GENDER_FEMALE = 2

export const USER_GENDER_OPTIONS: Array<{ value: number; label: string }> = [
  { value: USER_GENDER_UNKNOWN, label: '未知' },
  { value: USER_GENDER_MALE, label: '男' },
  { value: USER_GENDER_FEMALE, label: '女' },
]

// 用户名下的一条任职记录，来自 GET /api/users/{id} 详情接口内嵌的 positions 数组
// （分页列表接口不填充该字段，避免 N+1）
export interface UserPositionRow {
  id: number
  userId: number
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
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 用户列表行/详情数据，来自 GET /api/users、GET /api/users/{id}、POST /api/users、
// PUT /api/users/{id} 等接口的返回值；分页列表接口返回时 positions 恒为空数组
export interface UserRow {
  id: number
  name: string
  code: string
  gender: number
  mobile: string
  idCard: string
  showOrder: number
  remark: string
  // ext1~ext10：可开放配置的扩展字段，是否展示/可编辑由"表单字段定义"（bizType=USER）驱动，
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
  positions: UserPositionRow[]
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 详情弹窗展示的数据来源，字段与 UserRow 一致（详情接口会填充 positions）
export type UserDetail = UserRow

// 新增/编辑弹窗内，任职信息子表单一行的数据结构；id 为空表示新增行，
// 非空表示编辑既有任职记录（提交时按 id 回传给后端做增量 diff）。
// 额外声明字符串索引签名，使 bizType=POSITION 的动态字段（ext1~ext10）能以
// item.columnName 为 key 动态读写，与 useDynamicFormFields 在其余三个业务页面
// 表单里的用法保持一致
export interface UserPositionFormItem {
  id?: number
  orgId: number | null
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
  [key: string]: unknown
}

// 新增/编辑用户的请求体，positions 为该用户任职记录的完整列表
export interface UserFormRequest {
  name: string
  code: string
  gender: number
  mobile: string
  idCard: string
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
  positions: UserPositionFormItem[]
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
