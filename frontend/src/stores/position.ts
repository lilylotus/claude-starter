import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { OrgTreeNode } from '@/types/org'
import type { PositionRow } from '@/types/position'
import * as orgApi from '@/api/org'
import * as positionApi from '@/api/position'
import { DEFAULT_PAGE_SIZE } from '@/constants/pagination'

// 任职管理 store：维护左侧组织树（懒加载，仅用于导航选择组织，复用组织管理树接口）、
// 当前选中的组织 id、右侧任职记录分页列表状态。
// 与 org store 不同，任职记录和组织之间没有父子递归关系，不存在“顶级组织默认聚合
// 查询”的语义：未选中任何左侧树节点之前，右侧列表保持空，也不发起任何查询请求；
// 任职记录的增删改也不会影响组织树本身，因此不需要 org store 那样的局部刷新逻辑。
export const usePositionStore = defineStore('position', () => {
  // ---- 左侧组织树（懒加载，纯导航用途） ----
  const navTreeTopLevel = ref<OrgTreeNode[]>([])

  // 加载指定父级下的直属子组织，供 el-tree 的 load 回调使用；parentId = 0 时为顶级
  async function loadNavTreeChildren(parentId: number): Promise<OrgTreeNode[]> {
    return orgApi.getOrgTreeChildren(parentId)
  }

  // ---- 右侧任职记录分页列表 ----

  // 当前选中的左侧树组织 id；为 null 表示尚未选中任何组织，此时不发起查询
  const selectedOrgId = ref<number | null>(null)
  const list = ref<PositionRow[]>([])
  const listLoading = ref(false)

  const page = ref(1)
  const pageSize = ref(DEFAULT_PAGE_SIZE)
  const total = ref(0)

  // 加载当前选中组织下的任职记录分页数据；不传 targetPage 时使用当前 page；
  // 未选中任何组织（selectedOrgId 为 null）时不发起请求
  async function fetchPage(targetPage?: number) {
    if (selectedOrgId.value === null) return
    if (targetPage !== undefined) page.value = targetPage
    listLoading.value = true
    try {
      const result = await positionApi.getPositionPage(selectedOrgId.value, page.value, pageSize.value)
      list.value = result.records
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      listLoading.value = false
    }
  }

  // 选中左侧树节点：记录 selectedOrgId，重置为第一页并加载其任职记录
  async function selectNode(orgId: number) {
    selectedOrgId.value = orgId
    await fetchPage(1)
  }

  // 切换分页页码：保持当前 selectedOrgId 不变
  async function changePage(targetPage: number) {
    await fetchPage(targetPage)
  }

  // 切换每页条数：设置新的 pageSize 后重置到第一页重新查询，保持当前 selectedOrgId 不变
  async function changePageSize(newSize: number) {
    pageSize.value = newSize
    await fetchPage(1)
  }

  // 增/改/启停用/删除之后调用：刷新当前分页；若刷新后当前页超出新的总页数，
  // 则自动回退到最后一页。任职记录的增删改不影响左侧组织树，无需联动刷新。
  async function refreshAfterMutation() {
    await fetchPage()
    const lastPage = total.value === 0 ? 1 : Math.ceil(total.value / pageSize.value)
    if (page.value > lastPage) {
      await fetchPage(lastPage)
    }
  }

  return {
    navTreeTopLevel,
    loadNavTreeChildren,
    selectedOrgId,
    list,
    listLoading,
    page,
    pageSize,
    total,
    fetchPage,
    selectNode,
    changePage,
    changePageSize,
    refreshAfterMutation,
  }
})
