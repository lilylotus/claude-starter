import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserRow } from '@/types/user'
import * as userApi from '@/api/user'
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination'

// 用户管理 store：维护分页列表、姓名/手机号/身份证号三个搜索条件、loading 状态。
// 用户管理是单一分页表格（无左右联动结构），比组织/字典管理的 store 更简单。
export const useUserStore = defineStore('user', () => {
  const list = ref<UserRow[]>([])
  const loading = ref(false)

  const page = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const total = ref(0)

  // 三个独立可选的搜索条件，组合时为“与”关系，与后端 name/mobile/idCard 三个参数对齐
  const searchName = ref('')
  const searchMobile = ref('')
  const searchIdCard = ref('')

  // 加载分页列表；不传 targetPage 时使用当前 page
  async function fetchPage(targetPage?: number) {
    if (targetPage !== undefined) page.value = targetPage
    loading.value = true
    try {
      const result = await userApi.getUserPage({
        name: searchName.value || undefined,
        mobile: searchMobile.value || undefined,
        idCard: searchIdCard.value || undefined,
        page: page.value,
        pageSize: pageSize.value,
      })
      list.value = result.records
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      loading.value = false
    }
  }

  // 使用三个搜索条件重新查询，重置到第一页
  async function search(conditions: { name: string; mobile: string; idCard: string }) {
    searchName.value = conditions.name
    searchMobile.value = conditions.mobile
    searchIdCard.value = conditions.idCard
    await fetchPage(1)
  }

  // 切换分页页码
  function changePage(targetPage: number) {
    return fetchPage(targetPage)
  }

  // 切换每页条数：设置新的 pageSize 后重置到第一页重新查询
  function changePageSize(newSize: number) {
    pageSize.value = newSize
    return fetchPage(1)
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
    loading,
    page,
    pageSize,
    total,
    searchName,
    searchMobile,
    searchIdCard,
    fetchPage,
    search,
    changePage,
    changePageSize,
    refreshAfterMutation,
  }
})
