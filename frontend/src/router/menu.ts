import { UserFilled, Grid, Lock, Setting, Document } from '@element-plus/icons-vue'
import type { MenuGroup } from '@/types/menu'

// 侧边栏的四个一级菜单及其子菜单，供 Sidebar 渲染，也是 router 子路由的数据来源。
export const MENU_GROUPS: MenuGroup[] = [
  {
    key: 'identity',
    title: '身份管理',
    icon: UserFilled,
    children: [
      { title: '组织管理', path: '/identity/orgs', permissionKey: 'OrgManagement:org:view' },
      { title: '用户管理', path: '/identity/users', permissionKey: 'UserManagement:user:view' },
      { title: '任职管理', path: '/identity/positions', permissionKey: 'PositionManagement:position:view' },
    ],
  },
  {
    key: 'application',
    title: '应用管理',
    icon: Grid,
    children: [
      { title: '应用管理', path: '/application/list', permissionKey: 'AppManagement:app:view' },
      { title: '应用密钥', path: '/application/secret', permissionKey: 'AppManagement:secret:view' },
    ],
  },
  {
    key: 'permission',
    title: '权限管理',
    icon: Lock,
    children: [
      { title: '角色管理', path: '/permission/roles', permissionKey: 'RoleManagement:role:view' },
      { title: '权限点管理', path: '/permission/points', permissionKey: 'PermissionManagement:permission:view' },
      { title: '管理员管理', path: '/permission/admins', permissionKey: 'AdminManagement:admin:view' },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    icon: Setting,
    children: [
      { title: '菜单管理', path: '/system/menus', permissionKey: 'MenuManagement:menu:view' },
      { title: '字典管理', path: '/system/dicts', permissionKey: 'DictManagement:dictType:view' },
      { title: '元数据配置', path: '/system/metadata-fields', permissionKey: 'MetadataFieldManagement:metadataField:view' },
      { title: '表单管理', path: '/system/form-fields', permissionKey: 'FormFieldManagement:formField:view' },
    ],
  },
  {
    key: 'log',
    title: '日志管理',
    icon: Document,
    children: [
      { title: '操作日志', path: '/log/operation-logs', permissionKey: 'OperationLogManagement:log:view' },
      { title: '登录日志', path: '/log/login-logs', permissionKey: 'LoginLogManagement:loginLog:view' },
    ],
  },
]

// 按当前用户权限编码集合过滤菜单：无权限的二级菜单项被过滤掉；一级分组下所有
// 二级菜单都被过滤掉时，该分组自然从结果数组里消失，不需要额外的"是否整组隐藏"分支。
export function filterMenuGroups(
  groups: MenuGroup[],
  hasPermission: (code?: string) => boolean,
): MenuGroup[] {
  return groups
    .map((group) => ({
      ...group,
      children: group.children.filter((child) => hasPermission(child.permissionKey)),
    }))
    .filter((group) => group.children.length > 0)
}
