import { computed, reactive, ref } from 'vue'
import type { FormRules } from 'element-plus'
import { fetchFormFieldRenderSchema } from '@/api/formField'
import {
  FORM_FIELD_CONTROL_TYPE_DATE,
  FORM_FIELD_CONTROL_TYPE_DICT,
  FORM_FIELD_CONTROL_TYPE_MULTI_DICT,
  FORM_FIELD_CONTROL_TYPE_NUMBER,
  type FormFieldBizType,
  type FormFieldRenderItem,
} from '@/types/formField'

// 按显示序号降序排列（值越大越靠前），和后端 render-schema 接口的排序约定一致
function sortBySchemaOrder(items: FormFieldRenderItem[]): FormFieldRenderItem[] {
  return [...items].sort((a, b) => b.showOrder - a.showOrder)
}

// render-schema 接口返回的 columnName 是元数据字段配置里登记的数据库字段列名
// （下划线分隔，如 id_card、show_order），而组织/用户/任职/应用四个模块的
// Create/Update/VO DTO 序列化成 JSON 时用的是驼峰属性名（idCard、showOrder），
// MyBatis-Plus 的驼峰↔下划线映射只发生在 Java 对象与数据库列之间，不影响 DTO 的
// JSON 字段名。ext1~ext10 本身不含下划线，转换后保持不变。这里统一转换一次，
// 下游（本文件与四个业务页面）一律用转换后的驼峰 key 去读写行数据/表单模型，
// 不需要再各自处理下划线转换
function toCamelCase(columnName: string): string {
  return columnName.replace(/_([a-z0-9])/g, (_, char: string) => char.toUpperCase())
}

/**
 * 组织/用户/任职/应用四个业务页面接入"表单字段定义"动态渲染的组合式函数。
 *
 * 拉取指定业务对象类型（bizType）下全部启用状态的渲染元数据，派生出：
 * - 列表列配置（listColumns）：showInList=true 的定义
 * - 新增/编辑表单项配置（createFields/editFields）：分别按 showInCreate/showInEdit 过滤
 * - 对应的 el-form 校验规则（createRules/editRules）：isRequired → required 规则，
 *   validateRegex 非空 → pattern 规则；isUnique 不做前端异步校验，交给提交时后端返回
 *   的业务错误统一提示
 *
 * 每项渲染元数据都以 columnName（绑定的元数据字段列名，即实际的实体/DTO 属性名）
 * 作为动态表单/表格数据的绑定 key，fieldCode 只是该字段定义自身的标识，不用于取值。
 *
 * 返回值用 reactive() 包一层，使 refs/computed 在使用方的 <script setup> 与模板里
 * 都能像 Pinia store 一样直接用点号访问、无需手写 .value，和本项目现有 store 的
 * 使用习惯保持一致。
 */
export function useDynamicFormFields(bizType: FormFieldBizType) {
  const schema = ref<FormFieldRenderItem[]>([])
  const loading = ref(false)

  // 拉取指定业务对象类型的渲染元数据，页面挂载时调用一次即可
  async function fetchSchema() {
    loading.value = true
    try {
      const items = await fetchFormFieldRenderSchema(bizType)
      // 统一把 columnName 转换成驼峰形式，见 toCamelCase 注释
      schema.value = items.map((item) => ({ ...item, columnName: toCamelCase(item.columnName) }))
    } finally {
      loading.value = false
    }
  }

  const listColumns = computed(() => sortBySchemaOrder(schema.value.filter((item) => item.showInList)))
  const createFields = computed(() => sortBySchemaOrder(schema.value.filter((item) => item.showInCreate)))
  const editFields = computed(() => sortBySchemaOrder(schema.value.filter((item) => item.showInEdit)))

  // 由一批字段渲染元数据生成对应的 el-form 校验规则
  function buildRules(fields: FormFieldRenderItem[]): FormRules {
    const rules: FormRules = {}
    for (const item of fields) {
      const itemRules: Exclude<FormRules[string], undefined> = []
      if (item.isRequired) {
        if (item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT) {
          // 多选字典下拉：值是数组，必填校验数组非空（至少选择一项）
          itemRules.push({
            type: 'array',
            required: true,
            min: 1,
            message: `请选择${item.fieldName}`,
            trigger: 'change',
          })
        } else {
          const isDict = item.controlType === FORM_FIELD_CONTROL_TYPE_DICT
          const isDate = item.controlType === FORM_FIELD_CONTROL_TYPE_DATE
          itemRules.push({
            required: true,
            message: isDict || isDate ? `请选择${item.fieldName}` : `请输入${item.fieldName}`,
            trigger: isDict || isDate ? 'change' : 'blur',
          })
        }
      }
      if (item.validateRegex) {
        try {
          const pattern = new RegExp(item.validateRegex)
          itemRules.push({ pattern, message: `${item.fieldName}格式不正确`, trigger: 'blur' })
        } catch {
          // 后端存的是 Java 与 JavaScript 都应兼容的正则字符串，个别配置在浏览器 JS 引擎里
          // 可能仍然非法；此时跳过前端正则校验，不影响必填等其余规则，提交仍由后端兜底校验
        }
      }
      if (itemRules.length > 0) {
        rules[item.columnName] = itemRules
      }
    }
    return rules
  }

  const createRules = computed(() => buildRules(createFields.value))
  const editRules = computed(() => buildRules(editFields.value))

  // 构建/回填动态表单模型：以 columnName 为 key。source 提供时（编辑场景）从中取值回填，
  // 取不到时按控件类型给出空白默认值（数字框为 0，日期为 null，多选字典下拉为 []，
  // 其余为空字符串，新增场景）。
  // source 的形参类型放宽为 object，调用方可直接传具体的行/详情类型（如 OrgRow），
  // 不必先手动转换成带字符串索引签名的类型
  function buildFormModel(fields: FormFieldRenderItem[], source?: object | null): Record<string, unknown> {
    const sourceRecord = source as Record<string, unknown> | null | undefined
    const model: Record<string, unknown> = {}
    for (const item of fields) {
      const sourceValue = sourceRecord ? sourceRecord[item.columnName] : undefined
      if (item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT) {
        // 后端扩展列（ext1~ext10）是 String 类型，多选值以逗号分隔字符串承载，
        // 回填到 el-select multiple 的 v-model 前需还原为数组；空字符串/null/undefined
        // 还原为空数组；防御性兼容极个别已经是数组的来源（如本地未提交过的默认值）
        if (Array.isArray(sourceValue)) {
          model[item.columnName] = sourceValue
        } else if (typeof sourceValue === 'string' && sourceValue.length > 0) {
          model[item.columnName] = sourceValue.split(',')
        } else {
          model[item.columnName] = []
        }
      } else if (sourceValue !== undefined && sourceValue !== null) {
        model[item.columnName] = sourceValue
      } else if (item.controlType === FORM_FIELD_CONTROL_TYPE_NUMBER) {
        model[item.columnName] = 0
      } else if (item.controlType === FORM_FIELD_CONTROL_TYPE_DATE) {
        // el-date-picker 的空值语义是 null，不是空字符串
        model[item.columnName] = null
      } else {
        model[item.columnName] = ''
      }
    }
    return model
  }

  // 字典下拉字段：把提交/展示用的编码值转换为可读标签，找不到匹配项时回退展示原始值
  function dictOptionLabel(item: FormFieldRenderItem, value: unknown): string {
    if (value === undefined || value === null || value === '') return ''
    return item.dictOptions.find((opt) => opt.value === value)?.label ?? String(value)
  }

  // 多选字典下拉字段：把提交/展示用的编码值转换为可读标签，用"、"拼接；单个值找不到
  // 匹配项时回退展示该值原始字符串，与 dictOptionLabel() 的兜底行为保持一致。
  // value 兼容两种入参形态：已经是数组（如经过 buildFormModel 回填的表单值），或者
  // 后端原始的逗号分隔字符串（如列表/详情页未经 buildFormModel 直接读取的行数据）
  function dictOptionLabels(item: FormFieldRenderItem, value: unknown): string {
    let values: unknown[]
    if (Array.isArray(value)) {
      values = value
    } else if (typeof value === 'string') {
      values = value.length > 0 ? value.split(',') : []
    } else {
      return ''
    }
    if (values.length === 0) return ''
    return values
      .map((v) => item.dictOptions.find((opt) => opt.value === v)?.label ?? String(v))
      .join('、')
  }

  // 提交前的模型序列化：后端 ORG/USER/POSITION/APP 的 ext1~ext10 扩展列都是 String
  // 类型，MULTI_DICT 控件的 v-model 却是数组，直接把表单模型展开进请求体会触发
  // HttpMessageNotReadableException（无法把 JSON 数组反序列化成 String）。这里对
  // fields 中所有 MULTI_DICT 字段的值 join(',') 成字符串后再返回，供四个业务页面
  // 的 submitForm() 在构建请求 payload 前调用，其余字段原样透传
  function buildSubmitModel(
    fields: FormFieldRenderItem[],
    model: Record<string, unknown>,
  ): Record<string, unknown> {
    const result: Record<string, unknown> = { ...model }
    for (const item of fields) {
      if (item.controlType === FORM_FIELD_CONTROL_TYPE_MULTI_DICT) {
        const value = result[item.columnName]
        result[item.columnName] = Array.isArray(value) ? value.join(',') : (value ?? '')
      }
    }
    return result
  }

  return reactive({
    schema,
    loading,
    fetchSchema,
    listColumns,
    createFields,
    editFields,
    createRules,
    editRules,
    buildFormModel,
    dictOptionLabel,
    dictOptionLabels,
    buildSubmitModel,
  })
}
