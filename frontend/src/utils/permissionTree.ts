// 权限点"按编码模块分组的两层虚拟树"共享构造工具。角色管理弹窗里的权限点勾选控件
// （RoleManagementView.vue）与权限点管理页面（PermissionManagementView.vue）的树形展示
// 用的是同一份分组算法，抽到这里避免逻辑重复维护两份、后续调整分组规则要改两处。

// 分组算法所需的最小字段：分组依据 code 的第一段（模块名），叶子节点渲染依据 id/name
export interface PermissionTreeSource {
  id: number
  name: string
  code: string
}

// 树节点：分组虚拟节点（id 形如 "group:模块名"，不对应任何真实权限点，children 非空、
// count 为该模块下权限点数量）与叶子节点（真实权限点，id 为数字、children 恒为
// undefined）共用同一个类型，与 el-tree 的 :data/:props 契合。raw 保留叶子节点的原始
// 对象引用，供调用方按需取用分组算法本身用不到、但渲染/行内操作需要的字段（如
// status/showOrder/remark）——角色管理的勾选树只用得上 id/label，权限点管理树则需要
// 通过 raw 取更多字段，用泛型统一两种场景，不必为后者单独定义一套节点类型。
export interface PermissionTreeNode<T extends PermissionTreeSource = PermissionTreeSource> {
  id: string | number
  label: string
  raw?: T
  count?: number
  children?: PermissionTreeNode<T>[]
}

// 按权限点编码冒号分隔的第一段（模块）分组构造两层树：第一层是分组虚拟节点，第二层是
// 该模块下的权限点叶子节点，保持传入 items 的原始顺序（调用方需要的排序——如权限点
// 管理树按 showOrder 升序——应在调用前对 items 排好序，本函数只负责分组、不重新排序）。
// resolveGroupLabel 可选：不传时分组节点 label 就是原始模块名；目前 RoleManagementView.vue
// 与 PermissionManagementView.vue 都传入本文件导出的 resolvePermissionModuleLabel 展示中文
// 模块名，保留可选参数是为了不强制所有调用方都依赖这份映射，把"模块名到中文名"的映射
// 留给调用方按需选用，不把这份业务性质的映射硬编码进这个通用分组工具本身的分组逻辑里
export function buildPermissionTree<T extends PermissionTreeSource>(
  items: T[],
  resolveGroupLabel?: (moduleName: string) => string,
): PermissionTreeNode<T>[] {
  const groups = new Map<string, PermissionTreeNode<T>>()
  for (const item of items) {
    const moduleName = item.code.split(':')[0] || item.code
    let group = groups.get(moduleName)
    if (!group) {
      const label = resolveGroupLabel ? resolveGroupLabel(moduleName) : moduleName
      group = { id: `group:${moduleName}`, label, count: 0, children: [] }
      groups.set(moduleName, group)
    }
    group.children!.push({ id: item.id, label: item.name, raw: item })
    group.count = (group.count ?? 0) + 1
  }
  return Array.from(groups.values())
}

// 模块编码前缀 -> 中文名，与仓库根目录《权限资源.txt》登记的模块中文名保持一致。权限点
// 管理页面（PermissionManagementView.vue）与角色管理弹窗内的权限点勾选树
// （RoleManagementView.vue）共用这一份映射，避免重复维护两处容易漂移的对照表
export const PERMISSION_MODULE_LABELS: Record<string, string> = {
  OrgManagement: '组织管理',
  UserManagement: '用户管理',
  PositionManagement: '任职管理',
  AppManagement: '应用管理',
  RoleManagement: '角色管理',
  PermissionManagement: '权限点管理',
  AdminManagement: '管理员管理',
  MenuManagement: '菜单管理',
  DictManagement: '字典管理',
  MetadataFieldManagement: '元数据配置',
  FormFieldManagement: '表单管理',
  OperationLogManagement: '操作日志管理',
}

// 未在 PERMISSION_MODULE_LABELS 中登记的模块名（如后续新增模块但还没来得及补充映射）
// 兜底展示原始模块名，不报错、不显示空白；作为 buildPermissionTree 的默认
// resolveGroupLabel 实现供各调用方直接传入，不必各自维护一份同样逻辑的解析函数
export function resolvePermissionModuleLabel(moduleName: string): string {
  return PERMISSION_MODULE_LABELS[moduleName] ?? moduleName
}
