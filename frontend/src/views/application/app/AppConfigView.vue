<script setup lang="ts">
// 应用配置独立页面：管理该应用对外的 AppId/AccessKey/SecretKey 凭证、签名算法、
// 数据同步范围开关。风格参照 AppDetailView.vue（独立路由、独立拉取数据、左上角"返回"按钮），
// 区别在于这里是可编辑的配置页而不是只读详情页；三个分区用 el-tabs 切换展示，
// 参照 FormFieldListView.vue 的外层 tabs 用法。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as appApi from '@/api/app'
import * as metadataFieldApi from '@/api/metadataField'
import * as orgApi from '@/api/org'
import {
  SYNC_DOMAIN_FIELD_MAPPING_DOMAINS,
  SYNC_DOMAIN_OPTIONS,
  SYNC_DOMAIN_ORG_SCOPE_DOMAINS,
  TRANSFORM_TYPE_OPTIONS,
  type AppConfigVO,
  type AppSyncDomainConfigVO,
  type AppSyncFieldMappingVO,
  type AppSyncOrgScopeFormItem,
  type SignAlgorithm,
  type SyncDomain,
  type SyncMode,
  type TransformType,
} from '@/types/app'
import { METADATA_FIELD_STATUS_ENABLED, type MetadataField, type MetadataFieldBizType } from '@/types/metadataField'
import type { OrgTreeNode } from '@/types/org'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const appId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const config = ref<AppConfigVO | null>(null)

// 三个分区（基础信息/接口配置/同步配置）用 el-tabs 切换展示，而不是纵向堆叠
const activeTab = ref<'basic' | 'signature' | 'sync'>('basic')

// 接口配置卡片：签名算法的本地可编辑副本，与 config.signAlgorithm 分离，
// 未点“保存”前不影响已加载的展示态
const signAlgorithmForm = ref<SignAlgorithm>('SHA256')
const savingSignAlgorithm = ref(false)

// 同步配置卡片：基础同步配置项（同步方式、通知回调地址、是否需要签名/验签校验）的本地可
// 编辑副本；数据范围（组织/用户/任职/应用/角色/字典六个数据域各自的启用开关+分页大小+
// 字段映射）改由下方独立的"数据范围"区块管理，不再是这个表单的一部分（见
// openspec/changes/app-sync-field-mapping、app-sync-notify-pull-api）。
// notifyParams 不直接用 Record 编辑（模板里动态 key 不便双向绑定），拆成一个 { key, value }
// 行数组，保存前再收敛回 Record<string, string>
const syncForm = ref({
  syncMode: 'PULL' as SyncMode,
  notifyUrl: '',
  needSign: false,
})
const notifyParamRows = ref<{ key: string; value: string }[]>([])
const savingSync = ref(false)

function addNotifyParamRow() {
  notifyParamRows.value.push({ key: '', value: '' })
}

function removeNotifyParamRow(index: number) {
  notifyParamRows.value.splice(index, 1)
}

// Record<string, string> -> 行数组，供加载时回填表单
function notifyParamsToRows(params: Record<string, string>): { key: string; value: string }[] {
  return Object.entries(params).map(([key, value]) => ({ key, value }))
}

// 行数组 -> Record<string, string>，提交前收敛；忽略 key 为空的行（用户加了空行又没填）
function notifyParamRowsToRecord(): Record<string, string> {
  const result: Record<string, string> = {}
  for (const row of notifyParamRows.value) {
    if (row.key.trim()) result[row.key.trim()] = row.value
  }
  return result
}

function applyConfig(data: AppConfigVO) {
  config.value = data
  signAlgorithmForm.value = data.signAlgorithm
  syncForm.value = {
    syncMode: data.syncMode,
    notifyUrl: data.notifyUrl,
    needSign: data.needSign,
  }
  notifyParamRows.value = notifyParamsToRows(data.notifyParams)
}

async function fetchConfig() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await appApi.getAppConfig(appId.value)
    applyConfig(data)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载应用配置失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchConfig()
  fetchDomainConfigs()
  fetchOrgTree()
  ensureFieldMappingLoaded(syncDomainTab.value)
  ensureOrgScopeLoaded(syncDomainTab.value)
})

function goBack() {
  router.push({ name: 'application-list' })
}

// ---- 复制到剪贴板：Clipboard API 在非安全上下文（如 http）下可能不可用，
// 静默降级即可，不打断操作 ----
async function copyText(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败，请手动选择复制')
  }
}

// ---- 重置 SecretKey：破坏性操作，二次确认后调用重置接口，明文仅在弹窗内短暂展示，
// 弹窗关闭即丢弃，不写入任何长期状态（不进 config、不进 store、不进 localStorage） ----

const secretRevealVisible = ref(false)
const revealedSecretKey = ref('')
const resettingSecret = ref(false)

async function handleResetSecretKey() {
  await ElMessageBox.confirm(
    '重置后旧的 SecretKey 将立即失效，任何仍在使用旧密钥签名的外部系统都会鉴权失败，且此操作不可撤销。确定要重置吗？',
    '重置 SecretKey 确认',
    {
      type: 'warning',
      confirmButtonText: '重置',
      cancelButtonText: '取消',
    },
  )
  resettingSecret.value = true
  try {
    const result = await appApi.resetAppSecretKey(appId.value)
    revealedSecretKey.value = result.secretKey
    secretRevealVisible.value = true
  } finally {
    resettingSecret.value = false
  }
}

// 弹窗关闭（无论是点“我已保存”还是点右上角关闭）都清空本地持有的明文，
// 保持“唯一的明文暴露入口是重置接口的这一次响应”这条不变式
function closeSecretReveal() {
  secretRevealVisible.value = false
  revealedSecretKey.value = ''
}

// ---- 接口配置：签名算法 ----

async function saveSignAlgorithm() {
  savingSignAlgorithm.value = true
  try {
    const data = await appApi.updateAppSignAlgorithm(appId.value, { signAlgorithm: signAlgorithmForm.value })
    applyConfig(data)
    ElMessage.success('保存成功')
  } finally {
    savingSignAlgorithm.value = false
  }
}

// ---- 同步配置 ----

// 同步方式为“通知”时，接口地址必填且必须是 http/https 开头的合法 URL；这里做一次前端快速
// 校验，避免明显不合法的输入还要走一次网络请求才被后端拒绝（后端仍会做同样的校验兜底）
function validateNotifyUrl(): string {
  if (syncForm.value.syncMode !== 'NOTIFY') return ''
  const url = syncForm.value.notifyUrl.trim()
  if (!url) return '同步方式为通知时，接口地址不能为空'
  try {
    const parsed = new URL(url)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return '接口地址格式不正确，必须是 http/https 开头的合法 URL'
    }
  } catch {
    return '接口地址格式不正确，必须是 http/https 开头的合法 URL'
  }
  return ''
}

async function saveSyncConfig() {
  const validationError = validateNotifyUrl()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingSync.value = true
  try {
    const data = await appApi.updateAppSyncConfig(appId.value, {
      ...syncForm.value,
      notifyParams: notifyParamRowsToRecord(),
    })
    applyConfig(data)
    ElMessage.success('保存成功')
  } finally {
    savingSync.value = false
  }
}

// ---- 同步配置：数据范围（左侧纵向 tabs，组织/用户/任职/应用/角色/字典 6 个数据域） ----

// 支持字段级同步映射的数据域（组织/用户/任职/应用/角色），字典不展示字段映射表格
const fieldMappingSupportedDomains = SYNC_DOMAIN_FIELD_MAPPING_DOMAINS

// 当前激活的数据域子 tab
const syncDomainTab = ref<SyncDomain>('ORG')

// 6 个数据域的启用开关+拉取分页大小，一次性拉取缓存在本地，切换子 tab 不重新请求；
// 编辑态直接绑定这份缓存本身（这个区块和同步配置其余区块一样是"编辑完点保存"节奏，
// 没有单独的取消态需要区分本地副本与已加载展示态）
const domainConfigs = reactive<Record<SyncDomain, { syncEnabled: boolean; pageSize: number }>>({
  ORG: { syncEnabled: false, pageSize: 20 },
  USER: { syncEnabled: false, pageSize: 20 },
  POSITION: { syncEnabled: false, pageSize: 20 },
  APP: { syncEnabled: false, pageSize: 20 },
  ROLE: { syncEnabled: false, pageSize: 20 },
  DICT: { syncEnabled: false, pageSize: 20 },
})
const domainConfigLoading = ref(false)
const savingDomainConfig = ref(false)

// 字段映射表格行的本地可编辑结构：新增未保存的行没有 id（整体替换语义下不需要，
// 保存时按当前顺序全量提交）
interface FieldMappingRow {
  id?: number
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string
}

// 按数据域缓存字段映射行、可选源字段目录，切换子 tab 时按需请求一次；保存成功后用响应
// 刷新对应数据域的缓存
const fieldMappingRowsCache = reactive<Partial<Record<SyncDomain, FieldMappingRow[]>>>({})
const metadataFieldOptionsCache = reactive<Partial<Record<SyncDomain, MetadataField[]>>>({})
const fieldMappingLoading = ref(false)
const savingFieldMapping = ref(false)
// “新增字段”下拉框当前选中值，选中后立即插入一行并重置为空
const pendingFieldId = ref<number | null>(null)

const currentFieldMappingRows = computed<FieldMappingRow[]>(() => fieldMappingRowsCache[syncDomainTab.value] ?? [])
const currentMetadataFieldOptions = computed<MetadataField[]>(
  () => metadataFieldOptionsCache[syncDomainTab.value] ?? [],
)

// “新增字段”下拉框可选项：当前数据域下状态为启用、且尚未出现在表格里的元数据字段
const addableMetadataFieldOptions = computed(() => {
  const usedIds = new Set(currentFieldMappingRows.value.map((row) => row.metadataFieldId))
  return currentMetadataFieldOptions.value.filter(
    (field) => field.status === METADATA_FIELD_STATUS_ENABLED && !usedIds.has(field.id),
  )
})

function applyDomainConfigs(rows: AppSyncDomainConfigVO[]) {
  for (const row of rows) {
    domainConfigs[row.syncDomain] = { syncEnabled: row.syncEnabled, pageSize: row.pageSize }
  }
}

async function fetchDomainConfigs() {
  domainConfigLoading.value = true
  try {
    const rows = await appApi.listAppSyncDomainConfigs(appId.value)
    applyDomainConfigs(rows)
  } finally {
    domainConfigLoading.value = false
  }
}

async function saveDomainConfig(domain: SyncDomain) {
  savingDomainConfig.value = true
  try {
    const form = domainConfigs[domain]
    const data = await appApi.updateAppSyncDomainConfig(appId.value, domain, {
      syncEnabled: form.syncEnabled,
      pageSize: form.pageSize,
    })
    domainConfigs[domain] = { syncEnabled: data.syncEnabled, pageSize: data.pageSize }
    ElMessage.success('保存成功')
  } finally {
    savingDomainConfig.value = false
  }
}

// ---- 同步配置：同步范围（组织/用户/任职三个数据域，全部数据/指定组织范围二选一）----

// 支持"同步范围"配置的数据域（组织/用户/任职），应用/角色/字典不展示该区块
const orgScopeSupportedDomains = SYNC_DOMAIN_ORG_SCOPE_DOMAINS

// 组织树（弹窗/表单里的组织选择器数据源），一次性加载全量，供 6 个数据域共用
const orgTree = ref<OrgTreeNode[]>([])

async function fetchOrgTree() {
  orgTree.value = await orgApi.getOrgTree()
}

type OrgScopeMode = 'ALL' | 'SCOPED'

// 单个数据域的同步范围本地可编辑状态：mode 是纯前端概念（不随请求提交），由加载时的
// 行数量推导（空=全部数据），rows 是行编辑态，即便 mode 切回“全部数据”也保留在内存里，
// 方便用户切换单选反复横跳时不丢失已经填好的行
const orgScopeState = reactive<Record<SyncDomain, { mode: OrgScopeMode; rows: AppSyncOrgScopeFormItem[] }>>({
  ORG: { mode: 'ALL', rows: [] },
  USER: { mode: 'ALL', rows: [] },
  POSITION: { mode: 'ALL', rows: [] },
  APP: { mode: 'ALL', rows: [] },
  ROLE: { mode: 'ALL', rows: [] },
  DICT: { mode: 'ALL', rows: [] },
})
// 已加载过的数据域集合，切子 tab 时只按需请求一次，与字段映射的按需加载策略一致
const orgScopeLoadedDomains = reactive<Partial<Record<SyncDomain, boolean>>>({})
const orgScopeLoading = ref(false)
const savingOrgScope = ref(false)

function blankOrgScopeRow(): AppSyncOrgScopeFormItem {
  return { orgId: null, includeChildren: false }
}

function addOrgScopeRow(domain: SyncDomain) {
  orgScopeState[domain].rows.push(blankOrgScopeRow())
}

function removeOrgScopeRow(domain: SyncDomain, index: number) {
  orgScopeState[domain].rows.splice(index, 1)
}

// 切到某个数据域子 tab 时，按需加载该数据域当前的同步范围（仅组织/用户/任职三个数据域）
async function ensureOrgScopeLoaded(domain: SyncDomain) {
  if (!orgScopeSupportedDomains.includes(domain)) return
  if (orgScopeLoadedDomains[domain]) return
  orgScopeLoading.value = true
  try {
    const rows = await appApi.listAppSyncOrgScope(appId.value, domain)
    orgScopeState[domain].mode = rows.length === 0 ? 'ALL' : 'SCOPED'
    orgScopeState[domain].rows = rows.map((row) => ({ orgId: row.orgId, includeChildren: row.includeChildren }))
    orgScopeLoadedDomains[domain] = true
  } finally {
    orgScopeLoading.value = false
  }
}

function validateOrgScopeRows(state: { mode: OrgScopeMode; rows: AppSyncOrgScopeFormItem[] }): string {
  if (state.mode !== 'SCOPED') return ''
  if (state.rows.length === 0) return '已选择“指定组织范围”，请至少添加一个组织'
  if (state.rows.some((row) => row.orgId === null)) return '存在未选择组织的行，请补全或删除'
  return ''
}

async function saveOrgScope(domain: SyncDomain) {
  const state = orgScopeState[domain]
  const validationError = validateOrgScopeRows(state)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingOrgScope.value = true
  try {
    const payload =
      state.mode === 'ALL'
        ? []
        : state.rows.map((row) => ({ orgId: row.orgId as number, includeChildren: row.includeChildren }))
    const data = await appApi.replaceAppSyncOrgScope(appId.value, domain, payload)
    state.mode = data.length === 0 ? 'ALL' : 'SCOPED'
    state.rows = data.map((row) => ({ orgId: row.orgId, includeChildren: row.includeChildren }))
    ElMessage.success('保存成功')
  } finally {
    savingOrgScope.value = false
  }
}

function toFieldMappingRow(vo: AppSyncFieldMappingVO): FieldMappingRow {
  return {
    id: vo.id,
    metadataFieldId: vo.metadataFieldId,
    fieldName: vo.fieldName,
    fieldCode: vo.fieldCode,
    appFieldName: vo.appFieldName,
    appFieldCode: vo.appFieldCode,
    transformType: vo.transformType,
    transformValue: vo.transformValue ?? '',
  }
}

// 切到某个数据域子 tab 时，按需加载该数据域的字段映射列表 + 可选源字段目录（各自仅加载一次，
// 不含字典——字典不支持字段级配置，见 fieldMappingSupportedDomains）
async function ensureFieldMappingLoaded(domain: SyncDomain) {
  if (!fieldMappingSupportedDomains.includes(domain)) return
  if (fieldMappingRowsCache[domain] && metadataFieldOptionsCache[domain]) return
  fieldMappingLoading.value = true
  try {
    const [mappings, fieldPage] = await Promise.all([
      appApi.listAppSyncFieldMappings(appId.value, domain),
      metadataFieldApi.getMetadataFieldPageForSyncDomain(domain as MetadataFieldBizType),
    ])
    fieldMappingRowsCache[domain] = mappings.map(toFieldMappingRow)
    metadataFieldOptionsCache[domain] = fieldPage.records
  } finally {
    fieldMappingLoading.value = false
  }
}

function handleDomainTabChange() {
  ensureFieldMappingLoaded(syncDomainTab.value)
  ensureOrgScopeLoaded(syncDomainTab.value)
}

function handleAddField(metadataFieldId: number | null) {
  if (!metadataFieldId) return
  const domain = syncDomainTab.value
  const field = currentMetadataFieldOptions.value.find((item) => item.id === metadataFieldId)
  if (!field) return
  const rows = fieldMappingRowsCache[domain] ?? (fieldMappingRowsCache[domain] = [])
  rows.push({
    metadataFieldId: field.id,
    fieldName: field.fieldName,
    fieldCode: field.fieldCode,
    appFieldName: '',
    appFieldCode: '',
    transformType: 'NO_TRANSFORM',
    transformValue: '',
  })
  pendingFieldId.value = null
}

function removeFieldMappingRow(index: number) {
  fieldMappingRowsCache[syncDomainTab.value]?.splice(index, 1)
}

function validateFieldMappingRows(rows: FieldMappingRow[]): string {
  for (const row of rows) {
    if (!row.appFieldName.trim()) return '应用字段名称不能为空'
    if (!row.appFieldCode.trim()) return '应用字段编码不能为空'
    if (row.transformType === 'FIXED_VALUE' && !row.transformValue.trim()) {
      return '转换方式为固定值时，取值不能为空'
    }
    if (row.transformType === 'SCRIPT' && !row.transformValue.trim()) {
      return '转换方式为转换脚本时，脚本内容不能为空'
    }
  }
  return ''
}

async function saveFieldMappings() {
  const domain = syncDomainTab.value
  const rows = fieldMappingRowsCache[domain] ?? []
  const validationError = validateFieldMappingRows(rows)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingFieldMapping.value = true
  try {
    const payload = rows.map((row) => ({
      metadataFieldId: row.metadataFieldId,
      appFieldName: row.appFieldName.trim(),
      appFieldCode: row.appFieldCode.trim(),
      transformType: row.transformType,
      transformValue: row.transformType === 'NO_TRANSFORM' ? null : row.transformValue,
    }))
    const data = await appApi.replaceAppSyncFieldMappings(appId.value, domain, payload)
    fieldMappingRowsCache[domain] = data.map(toFieldMappingRow)
    ElMessage.success('保存成功')
  } finally {
    savingFieldMapping.value = false
  }
}
</script>

<template>
  <div class="app-config">
    <header class="app-config__header">
      <el-button link :icon="ArrowLeft" class="app-config__back" @click="goBack">返回</el-button>
      <h2 class="app-config__title">应用配置</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="app-config__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <el-tabs v-else v-model="activeTab" class="app-config__tabs" v-loading="loading">
      <el-tab-pane label="基础信息" name="basic">
        <div class="app-config__row">
          <span class="app-config__label">AppId</span>
          <span class="app-config__value">{{ config?.appId }}</span>
          <el-button link :icon="CopyDocument" @click="copyText(config?.appId ?? '')">复制</el-button>
        </div>
        <div class="app-config__row">
          <span class="app-config__label">AccessKey</span>
          <span class="app-config__value">{{ config?.accessKey }}</span>
          <el-button link :icon="CopyDocument" @click="copyText(config?.accessKey ?? '')">复制</el-button>
        </div>
        <div class="app-config__row">
          <span class="app-config__label">SecretKey</span>
          <span class="app-config__value app-config__value--masked">••••••••（已设置，重置后可查看新密钥）</span>
          <el-button
            v-if="hasPermission('AppManagement:app:config:resetSecret')"
            type="danger"
            plain
            :loading="resettingSecret"
            @click="handleResetSecretKey"
          >
            重置 SecretKey
          </el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="接口配置" name="signature">
        <el-form label-width="90px">
          <el-form-item label="签名算法">
            <el-radio-group v-model="signAlgorithmForm">
              <el-radio value="SHA256">SHA-256</el-radio>
              <el-radio value="SM3">国密 SM3</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="hasPermission('AppManagement:app:config:editSignAlgorithm')">
            <el-button type="primary" :loading="savingSignAlgorithm" @click="saveSignAlgorithm">保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="同步配置" name="sync">
        <el-form label-width="110px">
          <h4 class="app-config__sync-group-title">基础同步配置</h4>
          <el-form-item label="同步方式">
            <el-radio-group v-model="syncForm.syncMode">
              <el-radio value="NOTIFY">通知</el-radio>
              <el-radio value="PULL">拉取</el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="syncForm.syncMode === 'NOTIFY'">
            <el-form-item label="接口地址">
              <el-input
                v-model="syncForm.notifyUrl"
                placeholder="请输入通知回调接口地址，如 https://partner.example.com/callback"
              />
            </el-form-item>
            <el-form-item label="参数配置">
              <div class="app-config__param-rows">
                <div v-for="(row, index) in notifyParamRows" :key="index" class="app-config__param-row">
                  <el-input v-model="row.key" placeholder="参数名" />
                  <el-input v-model="row.value" placeholder="参数值" />
                  <el-button link :icon="Delete" type="danger" @click="removeNotifyParamRow(index)" />
                </div>
                <el-button link :icon="Plus" @click="addNotifyParamRow">添加参数</el-button>
              </div>
            </el-form-item>
          </template>
          <el-form-item label="需要签名/验签校验">
            <el-switch v-model="syncForm.needSign" />
          </el-form-item>

          <el-form-item v-if="hasPermission('AppManagement:app:config:editSync')">
            <el-button type="primary" :loading="savingSync" @click="saveSyncConfig">保存</el-button>
          </el-form-item>
        </el-form>

        <h4 class="app-config__sync-group-title">数据范围</h4>
        <el-tabs
          v-model="syncDomainTab"
          tab-position="left"
          class="app-config__domain-tabs"
          @tab-change="handleDomainTabChange"
        >
          <el-tab-pane
            v-for="option in SYNC_DOMAIN_OPTIONS"
            :key="option.value"
            :label="option.label"
            :name="option.value"
          >
            <div v-loading="domainConfigLoading" class="app-config__domain-panel">
              <el-form label-width="110px">
                <el-form-item label="是否启用">
                  <el-switch v-model="domainConfigs[option.value].syncEnabled" />
                </el-form-item>
                <el-form-item label="拉取分页大小">
                  <el-input-number v-model="domainConfigs[option.value].pageSize" :min="1" />
                </el-form-item>
                <el-form-item v-if="hasPermission('AppManagement:app:config:editSync')">
                  <el-button
                    type="primary"
                    :loading="savingDomainConfig"
                    @click="saveDomainConfig(option.value)"
                  >
                    保存
                  </el-button>
                </el-form-item>
              </el-form>

              <template v-if="orgScopeSupportedDomains.includes(option.value)">
                <h5 class="app-config__org-scope-title">同步范围</h5>
                <div v-loading="orgScopeLoading" class="app-config__org-scope">
                  <el-radio-group v-model="orgScopeState[option.value].mode">
                    <el-radio value="ALL">全部数据</el-radio>
                    <el-radio value="SCOPED">指定组织范围</el-radio>
                  </el-radio-group>

                  <div v-if="orgScopeState[option.value].mode === 'SCOPED'" class="app-config__org-scope-section">
                    <div class="app-config__org-scope-section__header">
                      <el-button link type="primary" @click="addOrgScopeRow(option.value)">+ 添加组织</el-button>
                    </div>

                    <p v-if="orgScopeState[option.value].rows.length === 0" class="app-config__org-scope-empty">
                      暂无指定组织，请添加至少一个组织
                    </p>

                    <div v-else class="app-config__org-scope-list">
                      <div
                        v-for="(scope, index) in orgScopeState[option.value].rows"
                        :key="index"
                        class="app-config__org-scope-row"
                      >
                        <div class="app-config__org-scope-row__fields">
                          <el-tree-select
                            v-model="scope.orgId"
                            :data="orgTree"
                            :props="{ label: 'name', children: 'children' }"
                            node-key="id"
                            check-strictly
                            placeholder="请选择组织"
                            style="width: 100%"
                          />
                          <el-checkbox v-model="scope.includeChildren">含子组织</el-checkbox>
                        </div>
                        <el-button
                          link
                          type="danger"
                          class="app-config__org-scope-row__remove"
                          @click="removeOrgScopeRow(option.value, index)"
                        >
                          删除
                        </el-button>
                      </div>
                    </div>
                  </div>

                  <div v-if="hasPermission('AppManagement:app:config:editSync')" class="app-config__org-scope-save">
                    <el-button type="primary" :loading="savingOrgScope" @click="saveOrgScope(option.value)">
                      保存同步范围
                    </el-button>
                  </div>
                </div>
              </template>

              <template v-if="fieldMappingSupportedDomains.includes(option.value)">
                <div class="app-config__field-mapping-toolbar">
                  <span class="app-config__field-mapping-title">字段映射</span>
                  <el-select
                    v-model="pendingFieldId"
                    placeholder="选择字段新增映射"
                    filterable
                    clearable
                    class="app-config__field-mapping-select"
                    @change="handleAddField"
                  >
                    <el-option
                      v-for="field in addableMetadataFieldOptions"
                      :key="field.id"
                      :label="`${field.fieldName}（${field.fieldCode}）`"
                      :value="field.id"
                    />
                  </el-select>
                </div>

                <el-table
                  v-loading="fieldMappingLoading"
                  :data="currentFieldMappingRows"
                  border
                  size="small"
                  class="app-config__field-mapping-table"
                >
                  <el-table-column label="字段名称" prop="fieldName" width="130" />
                  <el-table-column label="字段编码" prop="fieldCode" width="130" />
                  <el-table-column label="应用字段名称" min-width="140">
                    <template #default="{ row }">
                      <el-input v-model="row.appFieldName" placeholder="请输入应用侧字段名称" />
                    </template>
                  </el-table-column>
                  <el-table-column label="应用字段编码" min-width="140">
                    <template #default="{ row }">
                      <el-input v-model="row.appFieldCode" placeholder="请输入应用侧字段编码" />
                    </template>
                  </el-table-column>
                  <el-table-column label="转换方式" width="130">
                    <template #default="{ row }">
                      <el-select v-model="row.transformType">
                        <el-option
                          v-for="item in TRANSFORM_TYPE_OPTIONS"
                          :key="item.value"
                          :label="item.label"
                          :value="item.value"
                        />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column label="转换取值" min-width="220">
                    <template #default="{ row }">
                      <el-input
                        v-if="row.transformType === 'FIXED_VALUE'"
                        v-model="row.transformValue"
                        placeholder="请输入固定值"
                      />
                      <el-input
                        v-else-if="row.transformType === 'SCRIPT'"
                        v-model="row.transformValue"
                        type="textarea"
                        :rows="3"
                        placeholder="请输入 JavaScript 转换脚本"
                      />
                      <span v-else class="app-config__field-mapping-disabled">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="70" fixed="right">
                    <template #default="{ $index }">
                      <el-button link :icon="Delete" type="danger" @click="removeFieldMappingRow($index)" />
                    </template>
                  </el-table-column>
                </el-table>

                <div v-if="hasPermission('AppManagement:app:config:editSync')" class="app-config__field-mapping-save">
                  <el-button type="primary" :loading="savingFieldMapping" @click="saveFieldMappings">
                    保存字段映射
                  </el-button>
                </div>
              </template>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="secretRevealVisible"
      title="新的 SecretKey"
      width="520px"
      :close-on-click-modal="false"
      @close="closeSecretReveal"
    >
      <el-alert type="warning" :closable="false" show-icon title="请立即复制保存，关闭后将不再显示这个密钥" />
      <div class="app-config__secret-reveal">
        <span class="app-config__secret-value">{{ revealedSecretKey }}</span>
        <el-button link :icon="CopyDocument" @click="copyText(revealedSecretKey)">复制</el-button>
      </div>
      <template #footer>
        <el-button type="primary" @click="closeSecretReveal">我已保存，关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.app-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.app-config__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.app-config__back {
  font-size: 14px;
}

.app-config__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.app-config__error {
  margin-bottom: 4px;
}

.app-config__tabs {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);

  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.app-config__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border);

  &:last-child {
    border-bottom: none;
  }
}

.app-config__label {
  width: 90px;
  flex-shrink: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.app-config__value {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-text);
  word-break: break-all;
}

.app-config__value--masked {
  font-family: var(--font-body);
  color: var(--color-text-tertiary);
}

.app-config__secret-reveal {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 12px;
  background: var(--color-primary-softer);
  border-radius: var(--radius-sm);
}

.app-config__secret-value {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--color-ink);
  word-break: break-all;
}

.app-config__sync-group-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0 0 12px;

  &:not(:first-child) {
    margin-top: 8px;
    padding-top: 16px;
    border-top: 1px dashed var(--color-border);
  }
}

.app-config__param-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.app-config__param-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-input {
    max-width: 220px;
  }
}

// 数据范围：左侧纵向 tabs（组织/用户/任职/应用/角色/字典），呼应仓库整体的“链式连接”视觉语言，
// 用一条虚线把每个子 tab 的内容和外层"数据范围"标题隔开
.app-config__domain-tabs {
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  padding: 4px 16px 16px;

  :deep(.el-tabs__nav-wrap)::after {
    display: none;
  }
}

.app-config__domain-panel {
  min-height: 120px;
}

// 同步范围：单选“全部数据/指定组织范围” + 指定时的组织行列表，视觉上直接复用
// AdminManagementView.vue“管辖组织范围”子表单的链式连接语言（虚线+圆点），
// 与本页“字段映射”区块并列作为同一个数据域 tab 内的独立保存分区
.app-config__org-scope-title {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 4px 0 12px;
}

.app-config__org-scope {
  margin-bottom: 8px;
}

.app-config__org-scope-section {
  margin-top: 12px;
}

.app-config__org-scope-section__header {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.app-config__org-scope-empty {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin: 0 0 8px;
}

.app-config__org-scope-list {
  position: relative;
  padding-left: 16px;
  border-left: 1px dashed var(--chain-line-color);
}

.app-config__org-scope-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px dashed var(--color-border);

  &:last-child {
    border-bottom: none;
  }
}

.app-config__org-scope-row::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 12px;
  width: var(--chain-dot-size-sm);
  height: var(--chain-dot-size-sm);
  border-radius: 50%;
  background: var(--chain-line-color-active);
}

.app-config__org-scope-row__fields {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  column-gap: 12px;
}

.app-config__org-scope-row__remove {
  flex-shrink: 0;
}

.app-config__org-scope-save {
  margin-top: 12px;
}

.app-config__field-mapping-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 4px 0 12px;
}

.app-config__field-mapping-title {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.app-config__field-mapping-select {
  width: 260px;
}

.app-config__field-mapping-table {
  width: 100%;
}

.app-config__field-mapping-disabled {
  color: var(--color-text-tertiary);
}

.app-config__field-mapping-save {
  margin-top: 12px;
}
</style>
