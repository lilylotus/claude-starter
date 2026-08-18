import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MENU_GROUPS } from './menu'
import { useAuthStore } from '@/stores/auth'
import { useCurrentUserPermissionStore } from '@/stores/currentUserPermission'
import { usePermission } from '@/composables/usePermission'

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
  '/log/operation-logs': '记录关键操作的审计日志：谁在什么时候对什么资源做了什么变更。',
  '/log/login-logs': '记录每一次登录尝试的审计日志：谁在什么时候、用什么设备尝试登录，是否成功。',
}

// 已经落地真实业务组件的路由，按 path 覆盖默认的占位组件
const implementedComponents: Record<string, () => Promise<unknown>> = {
  '/identity/orgs': () => import('@/views/identity/org/OrgManagementView.vue'),
  '/identity/users': () => import('@/views/identity/user/UserManagementView.vue'),
  '/identity/positions': () => import('@/views/identity/position/PositionManagementView.vue'),
  '/identity/upstream': () => import('@/views/identity/upstream/UpstreamSourceListView.vue'),
  '/application/list': () => import('@/views/application/app/AppManagementView.vue'),
  '/permission/roles': () => import('@/views/permission/role/RoleManagementView.vue'),
  '/permission/points': () => import('@/views/permission/permission/PermissionManagementView.vue'),
  '/permission/admins': () => import('@/views/permission/admin/AdminManagementView.vue'),
  '/system/dicts': () => import('@/views/system/dict/DictManagementView.vue'),
  '/system/menus': () => import('@/views/system/menu/MenuManagementView.vue'),
  '/system/metadata-fields': () => import('@/views/system/metadatafields/MetadataFieldListView.vue'),
  '/system/form-fields': () => import('@/views/system/formfields/FormFieldListView.vue'),
  '/log/operation-logs': () => import('@/views/system/log/OperationLogManagementView.vue'),
  '/log/login-logs': () => import('@/views/system/log/LoginLogManagementView.vue'),
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
    meta: { title: '组织详情', requiresAuth: true, permissionKey: 'OrgManagement:org:detail' },
  },
  {
    path: 'identity/users/:id',
    name: 'identity-users-detail',
    component: () => import('@/views/identity/user/UserDetailView.vue'),
    meta: { title: '用户详情', requiresAuth: true, permissionKey: 'UserManagement:user:detail' },
  },
  {
    path: 'identity/positions/:id',
    name: 'identity-positions-detail',
    component: () => import('@/views/identity/position/PositionDetailView.vue'),
    meta: { title: '任职详情', requiresAuth: true, permissionKey: 'PositionManagement:position:detail' },
  },
  {
    path: 'application/list/:id',
    name: 'application-list-detail',
    component: () => import('@/views/application/app/AppDetailView.vue'),
    meta: { title: '应用详情', requiresAuth: true, permissionKey: 'AppManagement:app:detail' },
  },
  {
    path: 'application/list/:id/config',
    name: 'application-list-config',
    component: () => import('@/views/application/app/AppConfigView.vue'),
    meta: { title: '应用配置', requiresAuth: true, permissionKey: 'AppManagement:app:config' },
  },
  {
    path: 'identity/upstream/:id/config',
    name: 'identity-upstream-config',
    component: () => import('@/views/identity/upstream/UpstreamSourceConfigView.vue'),
    meta: { title: '上游数据源配置', requiresAuth: true, permissionKey: 'UpstreamManagement:source:config' },
  },
  {
    path: 'permission/roles/:id',
    name: 'permission-roles-detail',
    component: () => import('@/views/permission/role/RoleDetailView.vue'),
    meta: { title: '角色详情', requiresAuth: true, permissionKey: 'RoleManagement:role:detail' },
  },
  {
    path: 'permission/points/:id',
    name: 'permission-points-detail',
    component: () => import('@/views/permission/permission/PermissionDetailView.vue'),
    meta: { title: '权限点详情', requiresAuth: true, permissionKey: 'PermissionManagement:permission:detail' },
  },
  {
    path: 'permission/admins/:id',
    name: 'permission-admins-detail',
    component: () => import('@/views/permission/admin/AdminDetailView.vue'),
    meta: { title: '管理员详情', requiresAuth: true, permissionKey: 'AdminManagement:admin:detail' },
  },
  {
    path: 'system/menus/:id',
    name: 'system-menus-detail',
    component: () => import('@/views/system/menu/MenuDetailView.vue'),
    meta: { title: '菜单详情', requiresAuth: true, permissionKey: 'MenuManagement:menu:detail' },
  },
  {
    path: 'system/dicts/type/:id',
    name: 'system-dicts-type-detail',
    component: () => import('@/views/system/dict/DictTypeDetailView.vue'),
    meta: { title: '字典类型详情', requiresAuth: true, permissionKey: 'DictManagement:dictType:detail' },
  },
  {
    path: 'system/dicts/item/:id',
    name: 'system-dicts-item-detail',
    component: () => import('@/views/system/dict/DictItemDetailView.vue'),
    meta: { title: '字典项详情', requiresAuth: true, permissionKey: 'DictManagement:dictItem:detail' },
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
      // SSO 专用登录页：外部应用做 CAS/OAuth2 单点登录、用户未登录时跳转来的独立
      // 登录页，完全独立于管理端登录，不挂在 AppLayout 下（不是嵌套路由），
      // meta 不设 requiresAuth（不能被下面的 router.beforeEach 守卫拦截重定向到
      // 管理端登录页）
      path: '/sso/login',
      name: 'sso-login',
      component: () => import('@/views/sso/SsoLoginView.vue'),
      meta: { title: '单点登录' },
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

router.beforeEach(async (to) => {
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

  // 3. 已登录、非首登待改密状态，但当前用户的权限编码集合尚未加载过（内存状态为空——
  //    刷新页面/直接地址栏输入 URL 都会走到这里），先拉取一次，供下一步权限校验、
  //    以及侧边栏菜单过滤使用
  const currentUserPermissionStore = useCurrentUserPermissionStore()
  if (authStore.isLoggedIn && !authStore.firstLogin && !currentUserPermissionStore.loaded) {
    try {
      await currentUserPermissionStore.loadCodes()
    } catch {
      // 加载失败通常是因为 access-key/refresh-key 均已失效（比如标签页闲置很久后才点
      // 菜单）：axios 响应拦截器内部已经调用 redirectToLogin() 清空登录态并另外发起了
      // 一次跳转登录页的导航。这里必须放弃本次导航而不是让异常继续往外抛——
      // el-menu 的 router.push(route).then(...) 没有写 .catch()（Element Plus
      // menu.mjs 源码如此），异常逃逸出去会变成一个未处理的 Promise rejection，并且
      // 和 redirectToLogin() 发起的登录页跳转互相竞争，表现为页面状态被整个刷新重置
      return false
    }
  }

  // 4. 目标路由声明了 permissionKey，但当前用户不拥有该权限编码：拦截导航，
  //    不允许仅凭"入口不展示"这一层防线被绕过（直接改地址栏 URL 访问）。
  //    change-password 的 permissionKey 只是给请求头用的格式占位（未登记为真实权限点，
  //    权限编码集合里永远不会有它），豁免本项校验，否则首登用户会在 /dashboard 与
  //    /change-password 之间被无限重定向
  const { hasPermission } = usePermission()
  if (to.name !== 'change-password' && to.meta.permissionKey && !hasPermission(to.meta.permissionKey)) {
    ElMessage.warning('没有权限访问该页面')
    return { path: '/dashboard' }
  }

  // 5. 已登录（且非首登待改密，上面已处理）访问登录页，直接跳概览页
  if (to.name === 'login' && authStore.isLoggedIn) {
    return { path: '/dashboard' }
  }

  return true
})

export default router
