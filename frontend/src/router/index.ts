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
  '/system/metadata-fields': () => import('@/views/system/metadatafields/MetadataFieldListView.vue'),
  '/system/form-fields': () => import('@/views/system/formfields/FormFieldListView.vue'),
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
    meta: { title: '组织详情', requiresAuth: true, permissionKey: 'identity:org:detail' },
  },
  {
    path: 'identity/users/:id',
    name: 'identity-users-detail',
    component: () => import('@/views/identity/user/UserDetailView.vue'),
    meta: { title: '用户详情', requiresAuth: true, permissionKey: 'identity:user:detail' },
  },
  {
    path: 'identity/positions/:id',
    name: 'identity-positions-detail',
    component: () => import('@/views/identity/position/PositionDetailView.vue'),
    meta: { title: '任职详情', requiresAuth: true, permissionKey: 'identity:position:detail' },
  },
  {
    path: 'application/list/:id',
    name: 'application-list-detail',
    component: () => import('@/views/application/app/AppDetailView.vue'),
    meta: { title: '应用详情', requiresAuth: true, permissionKey: 'application:app:detail' },
  },
  {
    path: 'permission/roles/:id',
    name: 'permission-roles-detail',
    component: () => import('@/views/permission/role/RoleDetailView.vue'),
    meta: { title: '角色详情', requiresAuth: true, permissionKey: 'permission:role:detail' },
  },
  {
    path: 'permission/points/:id',
    name: 'permission-points-detail',
    component: () => import('@/views/permission/permission/PermissionDetailView.vue'),
    meta: { title: '权限点详情', requiresAuth: true, permissionKey: 'permission:point:detail' },
  },
  {
    path: 'permission/admins/:id',
    name: 'permission-admins-detail',
    component: () => import('@/views/permission/admin/AdminDetailView.vue'),
    meta: { title: '管理员详情', requiresAuth: true, permissionKey: 'permission:admin:detail' },
  },
  {
    path: 'system/menus/:id',
    name: 'system-menus-detail',
    component: () => import('@/views/system/menu/MenuDetailView.vue'),
    meta: { title: '菜单详情', requiresAuth: true, permissionKey: 'system:menu:detail' },
  },
  {
    path: 'system/dicts/type/:id',
    name: 'system-dicts-type-detail',
    component: () => import('@/views/system/dict/DictTypeDetailView.vue'),
    meta: { title: '字典类型详情', requiresAuth: true, permissionKey: 'system:dict:typeDetail' },
  },
  {
    path: 'system/dicts/item/:id',
    name: 'system-dicts-item-detail',
    component: () => import('@/views/system/dict/DictItemDetailView.vue'),
    meta: { title: '字典项详情', requiresAuth: true, permissionKey: 'system:dict:itemDetail' },
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
      // 首登强制改密页面：不挂在 menu.ts 侧边栏菜单下（不是主动导航入口，是登录后
      // firstLogin=true 时被动跳转进入），因此不出现在侧边栏，也不在权限资源.txt 里
      // 登记为一个独立的可访问菜单资源；但 POST /api/auth/password 本身仍属于"业务接口"，
      // 不在后端 menu 请求头校验的白名单（登录/刷新/公钥获取）内，仍需要携带一个格式合法
      // 的三段式 menu 头（后端本次只做格式校验，不做真实权限匹配，见 design.md Non-Goals），
      // 所以这里仍给出一个 permissionKey，只是不作为侧边栏可点菜单对外注册
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/auth/ChangePasswordView.vue'),
      meta: { title: '修改密码', requiresAuth: true, permissionKey: 'Auth:password:change' },
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

  // 1. 目标路由需要登录，但本地判定未登录（access-key/refresh-key 都已过期或压根没有）
  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // 2. 已登录且处于首登待改密状态，除改密页面本身外一律拦截并重定向到改密页面
  //    （包括已登录再次访问 /login 的情况，此时应该去改密页而不是概览页）
  if (authStore.isLoggedIn && authStore.firstLogin && to.name !== 'change-password') {
    return { name: 'change-password' }
  }

  // 3. 已登录（且非首登待改密，上面已处理）访问登录页，直接跳概览页
  if (to.name === 'login' && authStore.isLoggedIn) {
    return { path: '/dashboard' }
  }

  return true
})

export default router
