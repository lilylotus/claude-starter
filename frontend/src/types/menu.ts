import type { Component } from 'vue'

// 侧边栏子菜单项
export interface MenuChild {
  title: string
  path: string
  // 该页面对应的权限点 code，须与后端 tab_permission.code / 权限资源.txt 保持一致（如 UserManagement:user:view）
  permissionKey: string
}

// 侧边栏一级菜单分组
export interface MenuGroup {
  key: string
  title: string
  icon: Component
  children: MenuChild[]
}
