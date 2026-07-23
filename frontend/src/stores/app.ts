import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { AppRow } from '@/types/app'
import * as appApi from '@/api/app'
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination'

// 应用管理 store：维护分页列表状态。应用列表是不带左侧导航树的扁平分页表格，
// 不需要像任职管理 store 那样区分“是否已选中导航节点”。
export const useAppStore = defineStore('app', () => {
  const list = ref<AppRow[]>([])
  const listLoading = ref(false)

  const page = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const total = ref(0)

  // 加载分页数据；不传 targetPage 时使用当前 page
  async function fetchPage(targetPage?: number) {
    if (targetPage !== undefined) page.value = targetPage
    listLoading.value = true
    try {
      const result = await appApi.getAppPage(page.value, pageSize.value)
      list.value = result.records
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      listLoading.value = false
    }
  }

  // 切换分页页码
  async function changePage(targetPage: number) {
    await fetchPage(targetPage)
  }

  // 切换每页条数：设置新的 pageSize 后重置到第一页重新查询
  async function changePageSize(newSize: number) {
    pageSize.value = newSize
    await fetchPage(1)
  }

  // 增/改/启停用/删除之后调用：刷新当前分页；若刷新后当前页超出新的总页数，
  // 则自动回退到最后一页
  async function refreshAfterMutation() {
    await fetchPage()
    const lastPage = total.value === 0 ? 1 : Math.ceil(total.value / pageSize.value)
    if (page.value > lastPage) {
      await fetchPage(lastPage)
    }
  }

  return {
    list,
    listLoading,
    page,
    pageSize,
    total,
    fetchPage,
    changePage,
    changePageSize,
    refreshAfterMutation,
  }
})
