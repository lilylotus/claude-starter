import { UserFilled, Grid, Lock, Setting, Document, Checked, ChatDotRound } from '@element-plus/icons-vue'
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
      { title: '上游数据管理', path: '/identity/upstream', permissionKey: 'UpstreamManagement:source:view' },
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
      { title: '应用访问授权', path: '/permission/app-access', permissionKey: 'AppAccessManagement:appAccess:view' },
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
      // 聊天敏感词库后台管理，登记在"系统管理"分组下，与后端 V15__create_chat_tables.sql
      // 权限点登记位置（parent_id 挂在 system 分组下）保持一致
      { title: '敏感词管理', path: '/system/sensitive-words', permissionKey: 'SensitiveWordManagement:sensitiveWord:view' },
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
  {
    key: 'approval',
    title: '审批管理',
    icon: Checked,
    children: [
      // 自助操作：任何登录用户都能查看/撤回自己提交的申请，不受审批相关权限点约束
      // （selfService=true，见 types/menu.ts 注释；permissionKey 仍填写真实登记的
      // ApprovalManagement:request:view，供请求头 menu 使用）
      {
        title: '我的申请',
        path: '/approval/mine',
        permissionKey: 'ApprovalManagement:request:view',
        selfService: true,
      },
      { title: '待我审批', path: '/approval/pending', permissionKey: 'ApprovalManagement:request:approve' },
      { title: '审批设置', path: '/approval/settings', permissionKey: 'ApprovalManagement:switch:view' },
    ],
  },
  {
    // 聊天独立一级导航分组，与后端 V15__create_chat_tables.sql 新增的"聊天"侧边栏分组
    // （code=chat）一一对应；本阶段只有"聊天"这一个页面，其下会话列表/消息收发/群聊
    // 创建与成员管理都在同一个页面内完成，不拆分成多个子菜单项
    key: 'chat',
    title: '聊天',
    icon: ChatDotRound,
    children: [{ title: '聊天', path: '/chat', permissionKey: 'Chat:conversation:view' }],
  },
]

// 按当前用户权限编码集合过滤菜单：无权限的二级菜单项被过滤掉（自助类页面例外，恒展示，
// 见 types/menu.ts selfService 注释）；一级分组下所有二级菜单都被过滤掉时，该分组自然从
// 结果数组里消失，不需要额外的"是否整组隐藏"分支。
export function filterMenuGroups(
  groups: MenuGroup[],
  hasPermission: (code?: string) => boolean,
): MenuGroup[] {
  return groups
    .map((group) => ({
      ...group,
      children: group.children.filter((child) => child.selfService || hasPermission(child.permissionKey)),
    }))
    .filter((group) => group.children.length > 0)
}
