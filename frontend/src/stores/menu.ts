import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MenuResourceRow, MenuResourceTreeNode } from '@/types/menuResource'
import * as menuApi from '@/api/menu'

// 菜单管理（资源）store：维护左侧资源树、当前选中节点、右侧子节点表格数据（含分页状态）。
// 结构照抄 stores/org.ts，两套“左树右表懒加载 + 按需加载全量树”状态互不影响，见该文件注释。
export const useMenuStore = defineStore('menu', () => {
  const tree = ref<MenuResourceTreeNode[]>([])
  const treeLoading = ref(false)

  const selectedId = ref<number | null>(null)
  const selectedName = ref<string | null>(null)
  const currentParentId = ref(0)
  const children = ref<MenuResourceRow[]>([])
  const childrenLoading = ref(false)

  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)

  const navTreeTopLevel = ref<MenuResourceTreeNode[]>([])
  const navTreeLoading = ref(false)

  // 加载全量资源树，供弹窗“上级资源”选择器使用
  async function fetchTree() {
    treeLoading.value = true
    try {
      tree.value = await menuApi.getMenuTree()
    } finally {
      treeLoading.value = false
    }
  }

  // 加载指定父级下的直属子资源，供 el-tree 的 load 回调使用；parentId = 0 时为顶级
  async function loadNavTreeChildren(parentId: number): Promise<MenuResourceTreeNode[]> {
    return menuApi.getMenuTreeChildren(parentId)
  }

  // 加载指定节点的直接子节点分页数据，填充右侧表格；不传 targetPage 时使用当前 page
  async function fetchChildren(parentId: number, targetPage?: number) {
    currentParentId.value = parentId
    if (targetPage !== undefined) {
      page.value = targetPage
    }
    childrenLoading.value = true
    try {
      const result = await menuApi.getMenuChildren(parentId, page.value, pageSize.value)
      children.value = result.records
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      childrenLoading.value = false
    }
  }

  // 增删改成功后，刷新左侧导航树中受影响父节点（parentId）下的子节点
  async function refreshNavTreeBranch(parentId: number): Promise<MenuResourceTreeNode[]> {
    if (parentId === 0) navTreeLoading.value = true
    try {
      const fresh = await loadNavTreeChildren(parentId)
      if (parentId === 0) {
        navTreeTopLevel.value = fresh
      }
      return fresh
    } finally {
      if (parentId === 0) navTreeLoading.value = false
    }
  }

  // 切换页码：保持当前 parentId 不变，不影响标题
  async function changePage(targetPage: number) {
    await fetchChildren(currentParentId.value, targetPage)
  }

  // 选中左侧树节点：记录 selectedId/selectedName，重置为第一页并加载其直接子节点
  async function selectNode(id: number, name: string) {
    selectedId.value = id
    selectedName.value = name
    await fetchChildren(id, 1)
  }

  // 清空选中状态，标题恢复空白；表格仍展示顶级资源的默认列表（不清空 children）
  function clearSelection() {
    selectedId.value = null
    selectedName.value = null
  }

  // 任意增删改操作之后调用：刷新当前分页的子表格；若刷新后当前页超出新的总页数，则回退到最后一页
  async function refreshAfterMutation() {
    await fetchChildren(currentParentId.value)
    const lastPage = total.value === 0 ? 1 : Math.ceil(total.value / pageSize.value)
    if (page.value > lastPage) {
      await fetchChildren(currentParentId.value, lastPage)
    }
  }

  return {
    tree,
    treeLoading,
    selectedId,
    selectedName,
    currentParentId,
    children,
    childrenLoading,
    page,
    pageSize,
    total,
    navTreeTopLevel,
    navTreeLoading,
    fetchTree,
    fetchChildren,
    changePage,
    selectNode,
    clearSelection,
    refreshAfterMutation,
    loadNavTreeChildren,
    refreshNavTreeBranch,
  }
})
