// 组织管理相关类型定义，字段命名和后端 OrgTreeNode / OrgRow DTO 对齐。

// 状态常量：2000 = 启用，3000 = 停用（-1000 为逻辑删除，后端接口已过滤，前端不会拿到）。
export const ORG_STATUS_ENABLED = 2000
export const ORG_STATUS_DISABLED = 3000

// 左侧组织树节点，来自 GET /api/orgs/tree
export interface OrgTreeNode {
  id: number
  name: string
  code: string
  parentId: number
  // 上级组织编码，纯只读派生字段（由后端根据 parentId 自动回填），顶级组织为空
  parentCode?: string
  status: number
  showOrder: number
  children: OrgTreeNode[]
}

// 右侧表格行数据，来自 GET /api/orgs/children、GET /api/orgs/{id}
export interface OrgRow {
  id: number
  name: string
  code: string
  parentId: number
  parentName: string
  // 上级组织编码，纯只读派生字段（由后端根据 parentId 自动回填），顶级组织为空
  parentCode?: string
  status: number
  showOrder: number
  remark: string
  // ext1~ext10：可开放配置的扩展字段，是否展示/可编辑由"表单字段定义"（bizType=ORG）驱动，
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

// 新增/编辑组织的请求体
export interface OrgFormRequest {
  name: string
  code: string
  parentId: number
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

// 通用分页响应结构，字段命名和后端 cn.nihility.rbac.common.PageResult 对齐
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}
