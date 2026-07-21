import { createRouter, createWebHistory } from 'vue-router'
import { MENU_GROUPS } from './menu'
import { useAuthStore } from '@/stores/auth'

declare module 'vue-router' {
  interface RouteMeta {
    title: string
    description?: string
    permissionKey?: string
    requiresAuth?: boolean
  }
}

const stubDescriptions: Record<string, string> = {
  '/application/secret': '查看和轮换应用的访问密钥，密钥仅在生成时完整显示一次。',
  '/system/logs': '记录关键操作的审计日志：谁在什么时候对什么资源做了什么变更。',
}

// 已经落地真实业务组件的路由，按 path 覆盖默认的占位组件
const implementedComponents: Record<string, () => Promise<unknown>> = {
  '/identity/orgs': () => import('@/views/identity/org/OrgManagementView.vue'),
  '/identity/users': () => import('@/views/identity/user/UserManagementView.vue'),
  '/identity/positions': () => import('@/views/identity/position/PositionManagementView.vue'),
  '/application/list': () => import('@/views/application/app/AppManagementView.vue'),
  '/permission/roles': () => import('@/views/permission/role/RoleManagementView.vue'),
  '/permission/points': () => import('@/views/permission/permission/PermissionManagementView.vue'),
  '/permission/admins': () => import('@/views/permission/admin/AdminManagementView.vue'),
  '/system/dicts': () => import('@/views/system/dict/DictManagementView.vue'),
  '/system/menus': () => import('@/views/system/menu/MenuManagementView.vue'),
  '/system/logs': () => import('@/views/system/log/OperationLogManagementView.vue'),
}

const menuRoutes = MENU_GROUPS.flatMap((group) =>
  group.children.map((child) => ({
    path: child.path.slice(1),
    name: child.path.slice(1).replace('/', '-'),
    component: implementedComponents[child.path] ?? (() => import('@/views/PlaceholderView.vue')),
    meta: {
      title: child.title,
      description: stubDescriptions[child.path],
      permissionKey: child.permissionKey,
    },
  })),
)

// 9 个业务模块（字典拆成类型/项两个）的"详情"路由：不加入 menu.ts，因此不出现在侧边栏，
// 只能通过列表页"详情"按钮跳转或直接访问带 id 的 URL 进入。尚未由本轮完成的模块先指向
// PlaceholderView 占位，待各自的详情组件落地后再替换 component，避免这个共享文件被
// 多个 agent 反复冲突修改。命名规则沿用菜单路由：path.slice(1).replace(/\//g, '-')。
const detailRoutes = [
  {
    path: 'identity/orgs/:id',
    name: 'identity-orgs-detail',
    component: () => import('@/views/identity/org/OrgDetailView.vue'),
    meta: { title: '组织详情', requiresAuth: true },
  },
  {
    path: 'identity/users/:id',
    name: 'identity-users-detail',
    component: () => import('@/views/identity/user/UserDetailView.vue'),
    meta: { title: '用户详情', requiresAuth: true },
  },
  {
    path: 'identity/positions/:id',
    name: 'identity-positions-detail',
    component: () => import('@/views/identity/position/PositionDetailView.vue'),
    meta: { title: '任职详情', requiresAuth: true },
  },
  {
    path: 'application/list/:id',
    name: 'application-list-detail',
    component: () => import('@/views/application/app/AppDetailView.vue'),
    meta: { title: '应用详情', requiresAuth: true },
  },
  {
    path: 'permission/roles/:id',
    name: 'permission-roles-detail',
    component: () => import('@/views/permission/role/RoleDetailView.vue'),
    meta: { title: '角色详情', requiresAuth: true },
  },
  {
    path: 'permission/points/:id',
    name: 'permission-points-detail',
    component: () => import('@/views/permission/permission/PermissionDetailView.vue'),
    meta: { title: '权限点详情', requiresAuth: true },
  },
  {
    path: 'permission/admins/:id',
    name: 'permission-admins-detail',
    component: () => import('@/views/permission/admin/AdminDetailView.vue'),
    meta: { title: '管理员详情', requiresAuth: true },
  },
  {
    path: 'system/menus/:id',
    name: 'system-menus-detail',
    component: () => import('@/views/system/menu/MenuDetailView.vue'),
    meta: { title: '菜单详情', requiresAuth: true },
  },
  {
    path: 'system/dicts/type/:id',
    name: 'system-dicts-type-detail',
    component: () => import('@/views/system/dict/DictTypeDetailView.vue'),
    meta: { title: '字典类型详情', requiresAuth: true },
  },
  {
    path: 'system/dicts/item/:id',
    name: 'system-dicts-item-detail',
    component: () => import('@/views/system/dict/DictItemDetailView.vue'),
    meta: { title: '字典项详情', requiresAuth: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/login/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: () => import('@/layout/AppLayout.vue'),
      redirect: '/dashboard',
      meta: { requiresAuth: true, title: '首页' },
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '概览' },
        },
        ...menuRoutes,
        ...detailRoutes,
      ],
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && authStore.isLoggedIn) {
    return { path: '/dashboard' }
  }
  return true
})

export default router
