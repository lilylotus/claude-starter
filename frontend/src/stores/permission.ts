import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { PermissionRow } from '@/types/permission'
import * as permissionApi from '@/api/permission'

// 权限管理 store：维护权限点管理树形展示所需的全量列表状态。权限点管理页面已从分页
// 表格改为按模块分组的两层虚拟树（design.md Decision 1），树形展示天然需要一次性拿到
// 全量数据，不再分页加载，因此这里不再保留 page/pageSize/total 等分页状态。
export const usePermissionStore = defineStore('permission', () => {
  const list = ref<PermissionRow[]>([])
  const listLoading = ref(false)

  // 加载全量权限点列表（调用 GET /api/permissions/list，后端已按 showOrder 升序、
  // id 升序排好序，前端不再重新排序）
  async function loadList() {
    listLoading.value = true
    try {
      list.value = await permissionApi.getPermissionList()
    } finally {
      listLoading.value = false
    }
  }

  // 增/改/启停用/删除之后调用：重新加载全量列表以反映最新状态；树形展示不分页，
  // 不再有"回退到最后一页"那套逻辑
  async function refreshAfterMutation() {
    await loadList()
  }

  return {
    list,
    listLoading,
    loadList,
    refreshAfterMutation,
  }
})
