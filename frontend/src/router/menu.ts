import { UserFilled, Grid, Lock, Setting } from '@element-plus/icons-vue'
import type { MenuGroup } from '@/types/menu'

// 侧边栏的四个一级菜单及其子菜单，供 Sidebar 渲染，也是 router 子路由的数据来源。
export const MENU_GROUPS: MenuGroup[] = [
  {
    key: 'identity',
    title: '身份管理',
    icon: UserFilled,
    children: [
      { title: '组织管理', path: '/identity/orgs', permissionKey: 'identity:org:view' },
      { title: '用户管理', path: '/identity/users', permissionKey: 'identity:user:view' },
    ],
  },
  {
    key: 'application',
    title: '应用管理',
    icon: Grid,
    children: [
      { title: '应用列表', path: '/application/list', permissionKey: 'application:app:view' },
      { title: '应用密钥', path: '/application/secret', permissionKey: 'application:secret:view' },
    ],
  },
  {
    key: 'permission',
    title: '权限管理',
    icon: Lock,
    children: [
      { title: '角色管理', path: '/permission/roles', permissionKey: 'permission:role:view' },
      { title: '权限点管理', path: '/permission/points', permissionKey: 'permission:point:view' },
    ],
  },
  {
    key: 'system',
    title: '系统管理',
    icon: Setting,
    children: [
      { title: '菜单管理', path: '/system/menus', permissionKey: 'system:menu:view' },
      { title: '字典管理', path: '/system/dicts', permissionKey: 'system:dict:view' },
      { title: '操作日志', path: '/system/logs', permissionKey: 'system:log:view' },
    ],
  },
]
