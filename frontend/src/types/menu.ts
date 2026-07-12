import type { Component } from 'vue'

// 侧边栏子菜单项
export interface MenuChild {
  title: string
  path: string
  // 该页面对应的权限点 key，风格上和后端权限 code 对齐（如 identity:user:view）
  permissionKey: string
}

// 侧边栏一级菜单分组
export interface MenuGroup {
  key: string
  title: string
  icon: Component
  children: MenuChild[]
}
