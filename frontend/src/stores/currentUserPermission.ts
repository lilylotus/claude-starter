import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'

// 当前登录用户拥有的权限编码集合：只在内存中持有，不写入 localStorage
// （见 openspec/changes/permission-driven-ui-visibility design.md Decision 3），
// 每次应用启动/登录都强制重新拉取，避免复用本地缓存的陈旧权限。
export const useCurrentUserPermissionStore = defineStore('currentUserPermission', () => {
  const codes = ref<Set<string>>(new Set())
  const loaded = ref(false)

  // 拉取当前用户的权限编码集合，写入 codes 并标记 loaded
  async function loadCodes() {
    const result = await authApi.getMyPermissions()
    codes.value = new Set(result.codes)
    loaded.value = true
  }

  // 退出登录时清空，避免残留上一个用户的权限编码
  function reset() {
    codes.value = new Set()
    loaded.value = false
  }

  return { codes, loaded, loadCodes, reset }
})
