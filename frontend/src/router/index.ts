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
  '/system/dicts': () => import('@/views/system/dict/DictManagementView.vue'),
  '/system/menus': () => import('@/views/system/menu/MenuManagementView.vue'),
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
