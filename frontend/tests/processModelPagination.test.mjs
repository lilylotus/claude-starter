import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import { compileScript, parse } from '@vue/compiler-sfc'
import ts from 'typescript'
import * as vue from 'vue'

// 使用项目已有编译器运行真实 SFC 的 setup，替换网络与弹窗边界，无需额外测试依赖。
const filename = new URL('../src/views/workflow/process-model/ProcessModelListView.vue', import.meta.url)
const { descriptor } = parse(readFileSync(filename, 'utf8'))
const componentSource = compileScript(descriptor, { id: 'process-model-pagination-test' }).content

function evaluate(source, imports) {
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  })
  const exports = {}
  runInNewContext(outputText, {
    exports,
    require(id) {
      assert.ok(id in imports, `Unexpected import: ${id}`)
      return imports[id]
    },
  })
  return exports.default ?? exports
}

function createPage() {
  const requests = []
  const operations = []
  const routes = []
  const api = {
    pageProcessModels(params) {
      return new Promise((resolve, reject) => requests.push({ params: { ...params }, resolve, reject }))
    },
    async publishProcessModel(id) { operations.push(['publish', id]); return { version: 2 } },
    async disableProcessModel(id) { operations.push(['disable', id]) },
    async enableProcessModel(id) { operations.push(['enable', id]) },
    async createProcessModel() { return { id: 99 } },
  }
  const component = evaluate(componentSource, {
    vue,
    'vue-router': { useRouter: () => ({ push: async (route) => routes.push(route) }) },
    'element-plus': { ElMessage: { success() {}, error() {} }, ElMessageBox: { confirm: async () => {} } },
    '@/api/workflow': api,
    '@/composables/usePermission': { usePermission: () => ({ hasPermission: () => true }) },
    '@/constants/pagination': { DEFAULT_PAGE_SIZE: 10, PAGE_SIZE_OPTIONS: [10, 20, 50, 100] },
    '@/types/workflow': { PROCESS_MODEL_STATUS_LABEL: {} },
    './VersionHistoryDialog.vue': {},
  })
  return { state: component.setup({}, { expose() {} }), requests, operations, routes }
}

function respond(request, ids, total = 25) {
  request.resolve({ records: ids.map(id => ({ id })), total, ...request.params })
}

const flush = () => new Promise(resolve => setImmediate(resolve))

test('默认请求十条，翻页只展示服务端当页，切换容量回第一页', async () => {
  const { state, requests } = createPage()
  assert.deepEqual(requests[0].params, { page: 1, pageSize: 10 })
  respond(requests[0], Array.from({ length: 10 }, (_, i) => i + 1))
  await flush()
  assert.equal(state.models.value.length, 10)
  assert.equal(state.total.value, 25)

  const next = state.handlePageChange(3)
  assert.deepEqual(requests[1].params, { page: 3, pageSize: 10 })
  respond(requests[1], [21, 22, 23, 24, 25])
  await next
  assert.equal(state.models.value.length, 5)
  assert.equal(state.models.value[0].id, 21)

  const resized = state.handlePageSizeChange(20)
  assert.deepEqual(requests[2].params, { page: 1, pageSize: 20 })
  assert.equal(state.page.value, 1)
  respond(requests[2], Array.from({ length: 20 }, (_, i) => i + 1))
  await resized
  assert.equal(state.models.value.length, 20)
  state.handlePageChange(1)
  assert.equal(requests.length, 3)
})

test('快速翻页时旧成功响应和旧失败都不能覆盖最新列表及总数', async () => {
  const { state, requests } = createPage()
  const second = state.handlePageChange(2)
  const third = state.handlePageChange(3)
  respond(requests[2], [21, 22], 22)
  await third
  respond(requests[0], [1, 2], 25)
  requests[1].reject(new Error('stale request'))
  await second
  await flush()
  assert.equal(state.page.value, 3)
  assert.equal(state.models.value[0].id, 21)
  assert.equal(state.total.value, 22)
  assert.equal(state.loading.value, false)
})

test('旧请求完成不能提前关闭最新请求的加载状态，空列表正常收尾', async () => {
  const { state, requests } = createPage()
  const second = state.handlePageChange(2)
  respond(requests[0], [1])
  await flush()
  assert.equal(state.loading.value, true)
  respond(requests[1], [], 0)
  await second
  assert.equal(state.models.value.length, 0)
  assert.equal(state.total.value, 0)
  assert.equal(state.loading.value, false)
})

test('发布、下线、启用后刷新当前页，创建仍进入设计器', async () => {
  const { state, requests, operations, routes } = createPage()
  respond(requests[0], [1])
  await flush()
  const second = state.handlePageChange(2)
  respond(requests[1], [11])
  await second
  for (const action of ['handlePublish', 'handleDisable', 'handleEnable']) {
    const completed = state[action](11)
    await flush()
    const latest = requests.at(-1)
    assert.deepEqual(latest.params, { page: 2, pageSize: 10 })
    respond(latest, [11])
    await completed
  }
  assert.deepEqual(operations, [['publish', 11], ['disable', 11], ['enable', 11]])
  state.createForm.processCode = 'MODEL_TEST'
  state.createForm.processName = '测试模型'
  await state.handleCreate()
  assert.equal(routes[0].name, 'workflow-designer')
  assert.equal(routes[0].params.id, 99)
})

test('分页 API 传递查询参数，原列表 API 保留数组契约', async () => {
  const calls = []
  const records = [{ id: 1 }, { id: 2 }]
  const api = evaluate(readFileSync(new URL('../src/api/workflow.ts', import.meta.url), 'utf8'), {
    './request': { get: async (...args) => { calls.push(args); return records } },
  })
  await api.pageProcessModels({ page: 2, pageSize: 10 })
  assert.equal(calls[0][0], '/workflow/process-models/page')
  assert.equal(calls[0][1].params.page, 2)
  assert.equal(calls[0][1].params.pageSize, 10)
  assert.equal(await api.listProcessModels(), records)
  assert.equal(calls[1][0], '/workflow/process-models')
  assert.equal(calls[1].length, 1)
})
