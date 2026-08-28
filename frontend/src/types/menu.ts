import type { Component } from 'vue'

// 侧边栏子菜单项
export interface MenuChild {
  title: string
  path: string
  // 该页面对应的权限点 code，须与后端 tab_permission.code / 权限资源.txt 保持一致（如 UserManagement:user:view）；
  // 自助类页面（见 selfService）仍然要填写一个格式合法的三段式编码，供请求头 menu 使用，
  // 只是不参与侧边栏"是否展示该菜单项"的过滤判断
  permissionKey: string
  // 标记该菜单项是否为"自助类"页面：不论当前用户的权限编码集合是否包含 permissionKey，
  // 侧边栏都展示该菜单项、路由守卫也不因缺少该权限点而拦截导航——参照
  // IdentityAuthFilter 对"修改密码"等自助接口豁免权限点校验的既有做法（见 router/index.ts
  // 里 change-password 路由的处理注释）。目前仅"我的申请"（任何登录用户查看/撤回自己提交
  // 的审批申请，不应受审批相关权限点约束）使用该标记
  selfService?: boolean
}

// 侧边栏一级菜单分组
export interface MenuGroup {
  key: string
  title: string
  icon: Component
  children: MenuChild[]
}
