import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { DictItemRow, DictTypeRow } from '@/types/dict'
import * as dictApi from '@/api/dict'

// 字典管理 store：维护左侧字典类型分页列表、当前选中的字典类型、右侧字典项分页列表。
// 与组织管理的树形结构不同，字典类型之间没有层级关系，左侧就是一个普通分页列表；
// 且未选中任何类型时右侧没有“默认展示的顶级数据”，标题和表格数据是同步的空白态
// （不像组织管理里“标题空白但表格已有顶级组织数据”那样的两个独立状态，见
// design.md 决策“前端左侧字典类型用普通分页列表而非树”）。
export const useDictStore = defineStore('dict', () => {
  // ---- 左侧：字典类型分页列表 ----
  const types = ref<DictTypeRow[]>([])
  const typesLoading = ref(false)
  const typesKeyword = ref('')
  const typesPage = ref(1)
  const typesPageSize = ref(10)
  const typesTotal = ref(0)

  // ---- 右侧：当前选中的字典类型 + 其字典项分页列表 ----
  // 未选中时均为 null / '' / 空数组，右侧面板标题保持空白、不展示任何数据
  const selectedTypeId = ref<number | null>(null)
  const selectedTypeName = ref('')
  const items = ref<DictItemRow[]>([])
  const itemsLoading = ref(false)
  const itemsPage = ref(1)
  const itemsPageSize = ref(10)
  const itemsTotal = ref(0)

  // 加载字典类型分页列表；不传 targetPage 时使用当前 page
  async function fetchTypes(targetPage?: number) {
    if (targetPage !== undefined) typesPage.value = targetPage
    typesLoading.value = true
    try {
      const result = await dictApi.getDictTypePage({
        keyword: typesKeyword.value || undefined,
        page: typesPage.value,
        pageSize: typesPageSize.value,
      })
      types.value = result.records
      typesTotal.value = result.total
      typesPage.value = result.page
      typesPageSize.value = result.pageSize
    } finally {
      typesLoading.value = false
    }
  }

  // 按关键字（名称或编码模糊匹配）搜索字典类型：重置到第一页
  async function searchTypes(keyword: string) {
    typesKeyword.value = keyword
    await fetchTypes(1)
  }

  // 切换左侧分页页码
  function changeTypesPage(targetPage: number) {
    return fetchTypes(targetPage)
  }

  // 加载当前选中字典类型下的字典项分页列表；不传 targetPage 时使用当前 page；
  // 未选中任何字典类型时不发起请求
  async function fetchItems(targetPage?: number) {
    if (selectedTypeId.value === null) return
    if (targetPage !== undefined) itemsPage.value = targetPage
    itemsLoading.value = true
    try {
      const result = await dictApi.getDictItemPage({
        dictTypeId: selectedTypeId.value,
        page: itemsPage.value,
        pageSize: itemsPageSize.value,
      })
      items.value = result.records
      itemsTotal.value = result.total
      itemsPage.value = result.page
      itemsPageSize.value = result.pageSize
    } finally {
      itemsLoading.value = false
    }
  }

  // 切换右侧分页页码
  function changeItemsPage(targetPage: number) {
    return fetchItems(targetPage)
  }

  // 选中左侧某一行字典类型：记录 id/name，重置右侧为第一页并加载其字典项
  async function selectType(row: DictTypeRow) {
    selectedTypeId.value = row.id
    selectedTypeName.value = row.name
    await fetchItems(1)
  }

  // 清空选中状态：右侧面板标题恢复空白，字典项表格清空
  function clearSelection() {
    selectedTypeId.value = null
    selectedTypeName.value = ''
    items.value = []
    itemsTotal.value = 0
  }

  // 字典类型增/改/启停用/删除之后调用：刷新左侧列表（若刷新后当前页超出新的总页数则回退
  // 到最后一页）；若本次变更影响的正是当前右侧选中展示的类型，需联动刷新右侧——
  // 类型仍存在（改名/改状态）则同步更新标题并刷新字典项，类型已被删除（不再出现在
  // 未删除列表中）则右侧恢复为未选中的空白状态
  async function refreshAfterTypeMutation(affectedId?: number) {
    await fetchTypes()
    const lastPage = typesTotal.value === 0 ? 1 : Math.ceil(typesTotal.value / typesPageSize.value)
    if (typesPage.value > lastPage) {
      await fetchTypes(lastPage)
    }
    if (affectedId === undefined || affectedId !== selectedTypeId.value) return
    const stillExists = types.value.find((type) => type.id === affectedId)
    if (stillExists) {
      selectedTypeName.value = stillExists.name
      await fetchItems()
    } else {
      clearSelection()
    }
  }

  // 字典项增/改/启停用/删除之后调用：刷新右侧当前分页；若刷新后当前页超出新的总页数，
  // 则回退到最后一页
  async function refreshAfterItemMutation() {
    await fetchItems()
    const lastPage = itemsTotal.value === 0 ? 1 : Math.ceil(itemsTotal.value / itemsPageSize.value)
    if (itemsPage.value > lastPage) {
      await fetchItems(lastPage)
    }
  }

  return {
    types,
    typesLoading,
    typesKeyword,
    typesPage,
    typesPageSize,
    typesTotal,
    selectedTypeId,
    selectedTypeName,
    items,
    itemsLoading,
    itemsPage,
    itemsPageSize,
    itemsTotal,
    fetchTypes,
    searchTypes,
    changeTypesPage,
    fetchItems,
    changeItemsPage,
    selectType,
    clearSelection,
    refreshAfterTypeMutation,
    refreshAfterItemMutation,
  }
})
