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
  '/identity/users': '管理组织内的用户账号：创建、禁用、重置密码，以及查看每个用户当前拥有的角色。',
  '/application/list': '登记接入本系统的应用，每个应用拥有独立的权限点集合和访问密钥。',
  '/application/secret': '查看和轮换应用的访问密钥，密钥仅在生成时完整显示一次。',
  '/permission/roles': '定义角色并为角色勾选权限点，用户通过被赋予角色间接获得权限——这是 RBAC 的核心。',
  '/permission/points': '维护最细粒度的权限点（如 identity:user:edit），供角色勾选和接口鉴权引用。',
  '/system/menus': '配置侧边栏菜单与其绑定的权限点，控制不同角色登录后能看到哪些入口。',
  '/system/logs': '记录关键操作的审计日志：谁在什么时候对什么资源做了什么变更。',
}

// 已经落地真实业务组件的路由，按 path 覆盖默认的占位组件
const implementedComponents: Record<string, () => Promise<unknown>> = {
  '/identity/orgs': () => import('@/views/identity/org/OrgManagementView.vue'),
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
