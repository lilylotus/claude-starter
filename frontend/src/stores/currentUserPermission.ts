import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'

// 当前登录用户拥有的权限编码集合：只在内存中持有，不写入 localStorage
// （见 openspec/changes/permission-driven-ui-visibility design.md Decision 3），
// 每次应用启动/登录都强制重新拉取，避免复用本地缓存的陈旧权限。
export const useCurrentUserPermissionStore = defineStore('currentUserPermission', () => {
  const codes = ref<Set<string>>(new Set())
  const loaded = ref(false)
  // 当前用户的管辖组织范围是否受限，随权限编码一并加载（见 org-scope-write-guard change
  // design.md Decision 6）；受限时组织管理"上级组织"等选择器需要收紧可选范围，
  // 加载完成前保守地当作"受限"处理，避免加载完成前的一瞬间把不该出现的选项展示出来
  const orgScopeRestricted = ref(true)

  // 单飞：路由守卫可能在权限编码加载完成前被并发触发多次（连续快速点击不同菜单项），
  // 这里去重，避免同时打出多个 /auth/permissions 请求
  let loadingPromise: Promise<void> | null = null

  // 拉取当前用户的权限编码集合，写入 codes 并标记 loaded
  function loadCodes(): Promise<void> {
    if (!loadingPromise) {
      loadingPromise = authApi
        .getMyPermissions()
        .then((result) => {
          codes.value = new Set(result.codes)
          orgScopeRestricted.value = result.orgScopeRestricted
          loaded.value = true
        })
        .finally(() => {
          loadingPromise = null
        })
    }
    return loadingPromise
  }

  // 退出登录时清空，避免残留上一个用户的权限编码
  function reset() {
    codes.value = new Set()
    orgScopeRestricted.value = true
    loaded.value = false
  }

  return { codes, loaded, orgScopeRestricted, loadCodes, reset }
})
