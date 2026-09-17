import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import { compileScript, parse } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'
import ts from 'typescript'
import * as vue from 'vue'

// 编译真实模板与 setup，通过轻量控件替身验证条件编辑入口和事件，不新增测试依赖。
function evaluate(source, imports = {}) {
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

const readSource = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')
const formField = evaluate(readSource('../src/types/formField.ts'))
const workflow = evaluate(readSource('../src/types/workflow.ts'), { './formField': formField })
const { descriptor } = parse(readSource('../src/views/workflow/designer/panels/NodePropertyPanel.vue'))
const component = evaluate(compileScript(descriptor, {
  id: 'condition-branch-editor-test',
  inlineTemplate: true,
}).content, {
  vue,
  '@/types/workflow': workflow,
  '@/types/formField': formField,
  '@/api/user': {},
})

function createEditor(readonly = false) {
  const edge = { id: 'edge-1', source: 'condition-1', target: 'approval-1', data: { condition: null } }
  const updates = []
  async function render() {
    const controls = []
    const app = vue.createSSRApp(component, {
      node: { id: 'condition-1', type: 'condition', data: {} },
      outgoingEdges: [edge],
      nodeLabel: () => '人工审批',
      conditionFieldOptions: [
        { bizType: 'ORG', fieldCode: 'amount', fieldName: '金额', controlType: 2, dictOptions: [] },
        { bizType: 'USER', fieldCode: 'name', fieldName: '姓名', controlType: 1, dictOptions: [] },
      ],
      roleOptions: [],
      readonly,
      onUpdateEdgeCondition(payload) {
        updates.push(payload)
        edge.data.condition = payload.condition
      },
    })
    for (const name of ['ElForm', 'ElFormItem', 'ElInput', 'ElButton', 'ElSelect', 'ElOptionGroup',
      'ElOption', 'ElInputNumber', 'ElDatePicker', 'ElCheckbox']) {
      app.component(name, vue.defineComponent({
        inheritAttrs: false,
        setup(_, { attrs, slots }) {
          return () => {
            controls.push({ name, ...attrs })
            return vue.h('div', slots.default?.())
          }
        },
      }))
    }
    const html = await renderToString(app)
    return { html, controls, field: controls.find((control) => control.clearable !== undefined) }
  }
  return { edge, updates, render }
}

test('新建无条件边可以选择条件字段、设置比较值，再清空恢复手动默认分支', async () => {
  const editor = createEditor()
  let view = await editor.render()
  assert.ok(view.field, '新边也必须显示字段选择器')
  assert.match(view.html, /默认分支/)
  assert.equal(view.controls.filter((control) => control.name === 'ElSelect').length, 1)
  view.field['onUpdate:modelValue']('ORG::amount')
  assert.equal(editor.edge.data.condition.field, 'amount')
  assert.equal(editor.edge.data.condition.operator, 'EQ')
  view = await editor.render()
  const valueControl = view.controls.find((control) => control.name === 'ElInputNumber')
  valueControl['onUpdate:modelValue'](100)
  assert.equal(editor.edge.data.condition.value, 100)
  view.field['onUpdate:modelValue']('')
  assert.equal(editor.edge.data.condition, null)
  view = await editor.render()
  assert.equal(view.controls.filter((control) => control.name === 'ElSelect').length, 1)
  assert.match(view.html, /默认分支/)
  assert.ok(editor.updates.every((update) => update.edgeId === editor.edge.id))
})

test('从数字条件切换到文本字段时清空值并重置不适用的比较符', async () => {
  const editor = createEditor()
  editor.edge.data.condition = { fieldBizType: 'ORG', field: 'amount', operator: 'GT', value: 100 }
  const view = await editor.render()
  view.field['onUpdate:modelValue']('USER::name')
  assert.equal(editor.edge.data.condition.fieldBizType, 'USER')
  assert.equal(editor.edge.data.condition.field, 'name')
  assert.equal(editor.edge.data.condition.operator, 'EQ')
  assert.equal(editor.edge.data.condition.value, null)
})

test('只读模式禁用字段、比较符和比较值，隐藏分支增删操作', async () => {
  const editor = createEditor(true)
  let view = await editor.render()
  assert.equal(view.field.disabled, true)
  editor.edge.data.condition = { fieldBizType: 'ORG', field: 'amount', operator: 'GT', value: 100 }
  view = await editor.render()
  const conditionControls = view.controls.filter((control) => ['ElSelect', 'ElInputNumber'].includes(control.name))
  assert.equal(conditionControls.length, 3)
  assert.ok(conditionControls.every((control) => control.disabled))
  assert.equal(view.controls.filter((control) => control.name === 'ElButton').length, 0)
  assert.equal(editor.updates.length, 0)
})
