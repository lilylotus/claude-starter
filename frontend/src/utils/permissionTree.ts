// 权限点"按左侧侧边栏菜单组织的三层虚拟树"共享构造工具。权限点管理页面
// （PermissionManagementView.vue）、角色新增/编辑弹窗内的权限点勾选树
// （RoleManagementView.vue）与角色详情页已分配权限点展示（RoleDetailView.vue）共用同一份
// 构树算法，结构数据源直接复用侧边栏菜单配置 MENU_GROUPS（src/router/menu.ts），保证三处
// 展示与用户在左侧菜单里看到的"一级菜单 → 二级页面"结构一致，且不再维护第二份菜单结构。
import { MENU_GROUPS } from '@/router/menu'
import type { MenuChild, MenuGroup } from '@/types/menu'

// 构树所需的最小字段：归属页面依据 code，叶子节点渲染依据 id/name
export interface PermissionTreeSource {
  id: number
  name: string
  code: string
}

// 虚拟节点层级：menu=侧边栏一级菜单（含末尾"其他"兜底节点），page=二级页面（含"其他"
// 下按模块划分的节点）；叶子节点（真实权限点）不设置 level
export type PermissionTreeNodeLevel = 'menu' | 'page'

// 树节点：虚拟节点（id 为字符串，形如 "menu:identity"、"page:/identity/orgs"、
// "module:PluginManagement"，不对应任何真实权限点，children 非空、count 为其下叶子权限点
// 总数）与叶子节点（真实权限点，id 为数字、children 恒为 undefined）共用同一个类型，与
// el-tree 的 :data/:props 契合。raw 保留叶子节点的原始对象引用，供调用方按需取用渲染/行内
// 操作需要的字段（如 status/showOrder/remark）。虚拟节点 id 均为字符串，角色弹窗提交时
// "只保留数字 id"的过滤逻辑因此天然不会把虚拟节点当作权限点提交
export interface PermissionTreeNode<T extends PermissionTreeSource = PermissionTreeSource> {
  id: string | number
  label: string
  level?: PermissionTreeNodeLevel
  raw?: T
  count?: number
  children?: PermissionTreeNode<T>[]
}

// "其他"兜底一级节点的 id 与名称：侧边栏没有任何对应页面的权限点（如未挂载到侧边栏的
// 插件管理 PluginManagement:plugin:view）归入此节点，保证任何权限点都不会从树中消失
const OTHER_MENU_ID = 'menu:__other__'
const OTHER_MENU_LABEL = '其他'

// 取权限编码冒号分隔的前 n 段
function codePrefix(code: string, segments: number): string {
  return code.split(':').slice(0, segments).join(':')
}

// 页面查找表：完整编码 / 前两段（模块:资源）/ 模块段 → 页面，均只记录侧边栏顺序中第一个
// 命中的页面，构树时按此顺序依次匹配
interface PageLookup {
  byCode: Map<string, MenuChild>
  byResource: Map<string, MenuChild>
  byModule: Map<string, MenuChild>
}

function buildPageLookup(menuGroups: MenuGroup[]): PageLookup {
  const lookup: PageLookup = { byCode: new Map(), byResource: new Map(), byModule: new Map() }
  for (const group of menuGroups) {
    for (const child of group.children) {
      const key = child.permissionKey
      if (!lookup.byCode.has(key)) lookup.byCode.set(key, child)
      const resource = codePrefix(key, 2)
      if (!lookup.byResource.has(resource)) lookup.byResource.set(resource, child)
      const moduleName = codePrefix(key, 1)
      if (!lookup.byModule.has(moduleName)) lookup.byModule.set(moduleName, child)
    }
  }
  return lookup
}

// 按四级规则确定权限点归属的侧边栏页面，命中即停止：① 编码与页面 permissionKey 完全
// 相同；② 编码前两段与页面 permissionKey 前两段相同；③ 编码模块段与页面 permissionKey
// 模块段相同（如 DictManagement:dictItem:add → 字典管理）；均不匹配返回 undefined，由
// 调用方归入"其他"兜底节点
function resolvePage(code: string, lookup: PageLookup): MenuChild | undefined {
  return (
    lookup.byCode.get(code) ??
    lookup.byResource.get(codePrefix(code, 2)) ??
    lookup.byModule.get(codePrefix(code, 1))
  )
}

// 按左侧侧边栏菜单构造三层树：第一层为一级菜单，第二层为二级页面，第三层为归属该页面的
// 权限点叶子节点；一级、二级节点顺序与 menuGroups 一致，叶子保持传入 items 的原始顺序
// （调用方需要的排序——如权限点管理树按 showOrder 升序——应在调用前排好序，本函数只负责
// 归属、不重新排序）。没有任何权限点的页面与一级菜单不输出；侧边栏无对应页面的权限点
// 归入末尾"其他"节点，其下按模块划分二级节点、展示模块中文名。menuGroups 默认取侧边栏
// 配置 MENU_GROUPS，保留参数便于测试或复用
export function buildPermissionTree<T extends PermissionTreeSource>(
  items: T[],
  menuGroups: MenuGroup[] = MENU_GROUPS,
): PermissionTreeNode<T>[] {
  const lookup = buildPageLookup(menuGroups)
  const leavesByPage = new Map<MenuChild, PermissionTreeNode<T>[]>()
  const leavesByOtherModule = new Map<string, PermissionTreeNode<T>[]>()

  for (const item of items) {
    const leaf: PermissionTreeNode<T> = { id: item.id, label: item.name, raw: item }
    const page = resolvePage(item.code, lookup)
    if (page) {
      const leaves = leavesByPage.get(page) ?? []
      leaves.push(leaf)
      leavesByPage.set(page, leaves)
    } else {
      const moduleName = codePrefix(item.code, 1) || item.code
      const leaves = leavesByOtherModule.get(moduleName) ?? []
      leaves.push(leaf)
      leavesByOtherModule.set(moduleName, leaves)
    }
  }

  const tree: PermissionTreeNode<T>[] = []
  for (const group of menuGroups) {
    const pageNodes: PermissionTreeNode<T>[] = []
    for (const child of group.children) {
      const leaves = leavesByPage.get(child)
      if (!leaves || leaves.length === 0) continue
      pageNodes.push({
        id: `page:${child.path}`,
        label: child.title,
        level: 'page',
        count: leaves.length,
        children: leaves,
      })
    }
    if (pageNodes.length === 0) continue
    tree.push({
      id: `menu:${group.key}`,
      label: group.title,
      level: 'menu',
      count: sumCount(pageNodes),
      children: pageNodes,
    })
  }

  if (leavesByOtherModule.size > 0) {
    const moduleNodes: PermissionTreeNode<T>[] = Array.from(leavesByOtherModule, ([moduleName, leaves]) => ({
      id: `module:${moduleName}`,
      label: resolvePermissionModuleLabel(moduleName),
      level: 'page' as const,
      count: leaves.length,
      children: leaves,
    }))
    tree.push({
      id: OTHER_MENU_ID,
      label: OTHER_MENU_LABEL,
      level: 'menu',
      count: sumCount(moduleNodes),
      children: moduleNodes,
    })
  }
  return tree
}

// 汇总一组虚拟节点下的叶子权限点数量
function sumCount<T extends PermissionTreeSource>(nodes: PermissionTreeNode<T>[]): number {
  return nodes.reduce((sum, node) => sum + (node.count ?? 0), 0)
}

// 收集树中全部虚拟节点（一级菜单 + 二级页面）的 id，供"全部展开"一次性展开两层使用
export function collectVirtualNodeIds<T extends PermissionTreeSource>(
  nodes: PermissionTreeNode<T>[],
): Array<string | number> {
  const ids: Array<string | number> = []
  for (const node of nodes) {
    if (!node.level) continue
    ids.push(node.id)
    if (node.children) ids.push(...collectVirtualNodeIds(node.children))
  }
  return ids
}

// 模块编码前缀 -> 中文名，与仓库根目录《权限资源.txt》登记的模块中文名保持一致。用于
// "其他"兜底节点下按模块划分的二级节点名称（侧边栏无对应页面的权限点），三处页面共用
// 这一份映射，避免重复维护容易漂移的对照表
export const PERMISSION_MODULE_LABELS: Record<string, string> = {
  OrgManagement: '组织管理',
  UserManagement: '用户管理',
  PositionManagement: '任职管理',
  UpstreamManagement: '上游数据管理',
  AppManagement: '应用管理',
  RoleManagement: '角色管理',
  PermissionManagement: '权限点管理',
  AdminManagement: '管理员管理',
  AppAccessManagement: '应用访问授权',
  MenuManagement: '菜单管理',
  DictManagement: '字典管理',
  MetadataFieldManagement: '元数据配置',
  FormFieldManagement: '表单管理',
  OperationLogManagement: '操作日志管理',
  LoginLogManagement: '登录日志管理',
  PluginManagement: '插件管理',
  ApprovalManagement: '审批管理',
  Chat: '聊天',
  SensitiveWordManagement: '敏感词管理',
  WorkflowDesign: '流程设计',
  ImageManagement: '图片管理',
  ImageGallery: '图片展示',
}

// 角色管理/权限点管理页面只允许展示中文名，不允许英文模块编码前缀直接暴露给用户；
// 未在 PERMISSION_MODULE_LABELS 中登记的模块名（如新增模块但忘记补充映射）会兜底展示
// 原始英文编码，不报错、不显示空白，但会在开发环境打印告警提示补齐映射，避免线上静默漏配
export function resolvePermissionModuleLabel(moduleName: string): string {
  const label = PERMISSION_MODULE_LABELS[moduleName]
  if (!label && import.meta.env.DEV) {
    console.warn(
      `[permissionTree] 模块编码 "${moduleName}" 未在 PERMISSION_MODULE_LABELS 中登记中文名，` +
        '角色管理/权限点管理页面将直接展示英文编码，请补齐映射。',
    )
  }
  return label ?? moduleName
}
