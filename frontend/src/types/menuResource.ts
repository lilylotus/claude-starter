// 菜单管理（资源）相关类型定义，字段命名和后端 MenuTreeNodeVO / MenuVO DTO 对齐。
// 命名为 menuResource 而非 menu，是为了避开已存在的 @/types/menu（侧边栏 MenuGroup 类型）。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const MENU_STATUS_ENABLED = 2000
export const MENU_STATUS_DISABLED = 3000

// 资源类型常量：1 = 菜单，2 = 按钮，3 = API；三者互斥，固定类型，不接入通用字典管理模块。
export const MENU_RESOURCE_TYPE_MENU = 1
export const MENU_RESOURCE_TYPE_BUTTON = 2
export const MENU_RESOURCE_TYPE_API = 3

export const MENU_RESOURCE_TYPE_OPTIONS = [
  { label: '菜单', value: MENU_RESOURCE_TYPE_MENU },
  { label: '按钮', value: MENU_RESOURCE_TYPE_BUTTON },
  { label: 'API', value: MENU_RESOURCE_TYPE_API },
]

export const MENU_RESOURCE_TYPE_LABELS: Record<number, string> = {
  [MENU_RESOURCE_TYPE_MENU]: '菜单',
  [MENU_RESOURCE_TYPE_BUTTON]: '按钮',
  [MENU_RESOURCE_TYPE_API]: 'API',
}

// 左侧资源树节点，来自 GET /api/menus/tree
export interface MenuResourceTreeNode {
  id: number
  name: string
  code: string
  parentId: number
  resourceType: number
  status: number
  showOrder: number
  children: MenuResourceTreeNode[]
}

// 右侧表格行数据，来自 GET /api/menus/children、GET /api/menus/{id}
export interface MenuResourceRow {
  id: number
  name: string
  code: string
  parentId: number
  parentName: string
  resourceType: number
  status: number
  showOrder: number
  remark: string
  createBy: string
  createTime: string
  updateBy: string
  updateTime: string
}

// 新增/编辑资源的请求体
export interface MenuResourceFormRequest {
  name: string
  code: string
  parentId: number
  resourceType: number
  showOrder: number
  remark: string
}

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
