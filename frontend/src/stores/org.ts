import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { OrgRow, OrgTreeNode } from '@/types/org'
import * as orgApi from '@/api/org'

// 组织管理 store：维护左侧组织树、当前选中节点、右侧子节点表格数据（含分页状态）。
export const useOrgStore = defineStore('org', () => {
  const tree = ref<OrgTreeNode[]>([])
  const treeLoading = ref(false)

  // 当前选中的左侧树节点 id；未选中时为 null，此时标题保持空白，
  // 但右侧表格仍可能已经展示了顶级组织（parentId = 0）的默认分页数据——
  // “标题”依赖 selectedId，“表格数据源（当前查询用的 parentId）”依赖 currentParentId，
  // 这是两个故意独立的状态，不要把它们耦合在一起（详见 design.md 决策 3）。
  const selectedId = ref<number | null>(null)
  // 当前选中节点的名称，随 selectNode 一起写入，直接来自左侧树点击事件自带的数据，
  // 供右侧面板标题使用，避免为了拿一个名字去依赖全量树（orgStore.tree）
  const selectedName = ref<string | null>(null)
  // 当前用于查询右侧表格的 parentId；默认 0（顶级组织），与 selectedId 是否为 null 无关
  const currentParentId = ref(0)
  const children = ref<OrgRow[]>([])
  const childrenLoading = ref(false)

  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)

  // ---- 左侧导航树（懒加载） ----
  // 与上面的 tree（弹窗“上级组织”选择器用，全量一次性加载）是两套独立状态，
  // 不要混用：懒加载树稀疏、按需展开；tree 稠密、需要支持 prune 自身/子孙节点。
  // 顶级（parentId = 0）节点列表单独维护成一个响应式数组，绑定给 el-tree 的 :data；
  // 初次挂载时不需要主动填充它——el-tree 在 lazy 模式下，store 初始化时会自动对根节点
  // 调用一次 load（即下面的 loadNavTreeChildren(0)）来渲染顶级节点，与是否绑定 :data
  // 无关；navTreeTopLevel 只在“顶级层面发生增删改后需要局部刷新”时才被写入（见
  // refreshNavTreeBranch），避免初次挂载时对 parentId=0 重复请求两次。
  // 更深层级的子节点由 el-tree 内部懒加载 store 维护，此处不重复镜像。
  const navTreeTopLevel = ref<OrgTreeNode[]>([])
  const navTreeLoading = ref(false)

  // 加载左侧组织树
  async function fetchTree() {
    treeLoading.value = true
    try {
      tree.value = await orgApi.getOrgTree()
    } finally {
      treeLoading.value = false
    }
  }

  // 加载指定父级下的直属子组织，供 el-tree 的 load 回调使用；parentId = 0 时为顶级
  async function loadNavTreeChildren(parentId: number): Promise<OrgTreeNode[]> {
    return orgApi.getOrgTreeChildren(parentId)
  }

  // 加载指定节点的直接子节点分页数据，填充右侧表格；不传 targetPage 时使用当前 page
  async function fetchChildren(parentId: number, targetPage?: number) {
    currentParentId.value = parentId
    if (targetPage !== undefined) {
      page.value = targetPage
    }
    childrenLoading.value = true
    try {
      const result = await orgApi.getOrgChildren(parentId, page.value, pageSize.value)
      children.value = result.records
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      childrenLoading.value = false
    }
  }

  // 增删改成功后，刷新左侧导航树中受影响父节点（parentId）下的子节点，供组件调用
  // el-tree 实例的 updateKeyChildren(parentId, freshChildren) 做局部刷新；
  // 特殊地，parentId === 0（顶级）不是树上任何真实节点的 node-key，updateKeyChildren
  // 对它不生效，这里改为直接刷新 navTreeTopLevel 并重新整体赋值给 el-tree 的 :data
  // （只有顶级这一层会跟着变化，是本次改动在“用户在顶级层面增删改”这个较少见场景下
  // 的一个可接受折衷，见 design.md 决策 3）
  async function refreshNavTreeBranch(parentId: number): Promise<OrgTreeNode[]> {
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

  // 清空选中状态，标题恢复空白；表格仍展示顶级组织的默认列表（不清空 children）
  function clearSelection() {
    selectedId.value = null
    selectedName.value = null
  }

  // 任意增删改操作之后调用：刷新当前分页的子表格；若刷新后当前页超出新的总页数，则回退到最后一页。
  // 弹窗用的全量树（tree）不在这里主动刷新——下次打开新增/编辑弹窗时会重新拉取，天然是最新数据
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
