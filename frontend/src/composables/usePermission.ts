import { useCurrentUserPermissionStore } from '@/stores/currentUserPermission'

// 可复用的权限判断能力：模板 v-if 与路由守卫脚本逻辑共用同一套判断，
// 避免"菜单一套判断、按钮另一套判断"的分裂实现。
export function usePermission() {
  const store = useCurrentUserPermissionStore()

  // code 为空视为无需权限校验，直接放行
  function hasPermission(code?: string): boolean {
    if (!code) return true
    return store.codes.has(code)
  }

  return { hasPermission }
}
