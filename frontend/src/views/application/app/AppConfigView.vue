<script setup lang="ts">
// 应用配置独立页面：管理该应用对外的 AppId/AccessKey/SecretKey 凭证、签名算法、
// 数据同步范围开关。风格参照 AppDetailView.vue（独立路由、独立拉取数据、左上角"返回"按钮），
// 区别在于这里是可编辑的配置页而不是只读详情页；"基础信息"/"同步配置"两个分区用 el-tabs
// 切换展示，参照 FormFieldListView.vue 的外层 tabs 用法。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as appApi from '@/api/app'
import * as metadataFieldApi from '@/api/metadataField'
import * as orgApi from '@/api/org'
import { DEFAULT_PAGE_SIZE, PAGE_SIZE_OPTIONS } from '@/constants/pagination'
import {
  AUTH_PROTOCOL_OPTIONS,
  NOTIFY_STATUS_OPTIONS,
  NOTIFY_STATUS_SUCCESS,
  SYNC_DOMAIN_FIELD_MAPPING_DOMAINS,
  SYNC_DOMAIN_OPTIONS,
  SYNC_DOMAIN_ORG_SCOPE_DOMAINS,
  TRANSFORM_TYPE_OPTIONS,
  type AppAuthConfigVO,
  type AppConfigVO,
  type AppNotifyRecordRow,
  type AppPullRecordRow,
  type AppUserinfoFieldMappingSaveRequest,
  type AppUserinfoFieldMappingVO,
  type AuthProtocol,
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

// 三个一级分区（基础信息/同步配置/认证管理）用 el-tabs 切换展示，而不是纵向堆叠；原
// “接口配置” tab（只有签名算法一项）已合并进“同步配置”tab 的“基础同步配置”表单；
// “通知日志”“拉取日志”不再是一级 tab，改为“同步配置”内部子级 tab（见 syncSectionTab）
const activeTab = ref<'basic' | 'sync' | 'auth'>('basic')

// “同步配置”一级 tab 内部的子级 tabs：基础同步配置/数据范围/通知日志/拉取日志，
// 互斥展示，默认展示“基础同步配置”（见 openspec/changes/app-config-sync-subtabs）
const syncSectionTab = ref<'basicSync' | 'domainScope' | 'notifyLog' | 'pullLog'>('basicSync')

// 切到“通知日志”“拉取日志”子 tab 时按需触发首次加载，不在页面 onMounted 里一起拉取
function handleSyncSectionTabChange() {
  if (syncSectionTab.value === 'notifyLog') ensureNotifyLogLoaded()
  else if (syncSectionTab.value === 'pullLog') ensurePullLogLoaded()
}

// 签名算法的本地可编辑副本，与 config.signAlgorithm 分离，未点“保存”前不影响已加载的
// 展示态；模板位置在“同步配置”tab 的“基础同步配置”表单里，紧跟“签名校验”开关之后，
// 仅当该开关启用时才展示（见 openspec/changes/app-config-page-ux-refine）
const signAlgorithmForm = ref<SignAlgorithm>('SHA256')

// 同步配置卡片：基础同步配置项（同步方式、通知回调地址、是否需要签名/验签校验、签名算法）
// 的本地可编辑副本；数据范围（组织/用户/任职/应用/角色/字典六个数据域各自的启用开关+分页
// 大小+字段映射）改由下方独立的“数据范围”区块管理，不再是这个表单的一部分（见
// openspec/changes/app-sync-field-mapping、app-sync-notify-pull-api）。
// notifyParams 不直接用 Record 编辑（模板里动态 key 不便双向绑定），拆成一个 { key, value }
// 行数组，保存前再收敛回 Record<string, string>
const syncForm = ref({
  syncMode: 'PULL' as SyncMode,
  notifyUrl: '',
  needSign: false,
  // 同步总开关：整个应用一份，默认开启；关闭后不再产生新的变更记录/通知，拉取接口
  // 返回空结果，历史记录不清空（见 openspec/changes/app-sync-master-switch）
  syncMasterEnabled: true,
})
const notifyParamRows = ref<{ key: string; value: string }[]>([])
// 签名算法与基础同步配置合并为一次保存点击（两次既有接口调用），共用一个 loading
const savingBasicSync = ref(false)

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
    syncMasterEnabled: data.syncMasterEnabled,
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
  fetchAuthConfig()
  fetchUserinfoFieldMappings()
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

// 基础同步配置 + 签名算法合并保存：先做前端校验，通过后并发提交两个既有接口（接口契约
// 不变，语义独立——一个改签名算法，一个改同步方式/通知配置/签名校验开关）。两个请求各自
// 在后端独立读-改-写同一条 AppConfigEntity，并发执行时任一响应都可能读到另一个请求提交前
// 的旧值（例如签名算法接口的响应里 syncMode 还是旧值），不能直接拿其中一个响应回显；
// 两个请求都成功后重新 fetchConfig() 拉取合并后的最终状态再回显，避免界面被过期响应覆盖
async function saveBasicSyncConfig() {
  const validationError = validateNotifyUrl()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingBasicSync.value = true
  try {
    await Promise.all([
      appApi.updateAppSignAlgorithm(appId.value, { signAlgorithm: signAlgorithmForm.value }),
      appApi.updateAppSyncConfig(appId.value, {
        ...syncForm.value,
        notifyParams: notifyParamRowsToRecord(),
      }),
    ])
    await fetchConfig()
    ElMessage.success('保存成功')
  } finally {
    savingBasicSync.value = false
  }
}

// ---- 同步配置：数据范围（左侧纵向 tabs，组织/用户/任职/应用/角色/字典 6 个数据域） ----

// 支持字段级同步映射的数据域（组织/用户/任职/应用/角色），字典不展示字段映射表格
const fieldMappingSupportedDomains = SYNC_DOMAIN_FIELD_MAPPING_DOMAINS

// 当前激活的数据域子 tab（左侧一级 tab）
const syncDomainTab = ref<SyncDomain>('ORG')

// 每个数据域面板内“是否启用/同步范围/字段映射”二级 tab 的激活状态，按数据域独立记忆，
// 不做跨数据域联动（切到某数据域时二级 tab 一律回到默认值“是否启用”），避免切到不支持
// “同步范围”/“字段映射”的数据域时选中值悬空
type DomainSubTabName = 'enable' | 'orgScope' | 'fieldMapping'
const domainSubTab = reactive<Record<SyncDomain, DomainSubTabName>>({
  ORG: 'enable',
  USER: 'enable',
  POSITION: 'enable',
  APP: 'enable',
  ROLE: 'enable',
  DICT: 'enable',
})

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
    // 应用字段名称/编码默认预填为源字段的名称/编码，多数场景下两者相同，用户仍可编辑覆盖
    appFieldName: field.fieldName,
    appFieldCode: field.fieldCode,
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

// ---- 认证管理：单点登录协议配置（CAS/OAuth2.0），仅做协议接入前置配置维护，不含协议
// 运行时鉴权逻辑（见 openspec/changes/app-auth-protocol-config），前端顶部有 el-alert 提示 ----

const authConfigLoading = ref(false)
const savingAuthConfig = ref(false)
const authProtocol = ref<AuthProtocol>('NONE')
// 回跳地址 ANT 匹配规则用"单值行数组 + 增删按钮"编辑，与 notifyParamRows 的
// key-value 两列不同，这里每行只有一个 ANT 表达式字符串；CAS/OAuth2.0 及未来新增协议
// 共用同一份 servicePatternRows，语义为"当前生效协议下允许的回跳地址匹配列表"
const servicePatternRows = ref<string[]>([])
// 登出通知回调地址：随协议类型/匹配列表一并读写，不区分协议类型都可填写，留空表示不通知
const logoutNotifyUrl = ref('')
// 6 个只读协议接口地址（路径部分），由后端按当前应用的 AppId 计算返回
const authUrls = reactive({
  casLoginUrl: '',
  casServiceValidateUrl: '',
  casLogoutUrl: '',
  oauthAuthorizeUrl: '',
  oauthTokenUrl: '',
  oauthUserInfoUrl: '',
})

// OAuth2 授权接口参数说明，纯静态文案，不依赖接口返回
const OAUTH_AUTHORIZE_PARAMS = [
  { name: 'response_type', required: '必选', desc: '授权类型，固定值 "code"' },
  { name: 'client_id', required: '必选', desc: '应用 ID（AppId）' },
  { name: 'redirect_uri', required: '可选', desc: '重定向 URI' },
  { name: 'scope', required: '可选', desc: '申请的权限范围' },
  { name: 'state', required: '可选', desc: '客户端当前状态，认证服务器原样返回' },
]

function applyAuthConfig(data: AppAuthConfigVO) {
  authProtocol.value = data.authProtocol
  servicePatternRows.value = [...data.servicePatterns]
  logoutNotifyUrl.value = data.logoutNotifyUrl ?? ''
  authUrls.casLoginUrl = data.casLoginUrl
  authUrls.casServiceValidateUrl = data.casServiceValidateUrl
  authUrls.casLogoutUrl = data.casLogoutUrl
  authUrls.oauthAuthorizeUrl = data.oauthAuthorizeUrl
  authUrls.oauthTokenUrl = data.oauthTokenUrl
  authUrls.oauthUserInfoUrl = data.oauthUserInfoUrl
}

async function fetchAuthConfig() {
  authConfigLoading.value = true
  try {
    const data = await appApi.getAppAuthConfig(appId.value)
    applyAuthConfig(data)
  } finally {
    authConfigLoading.value = false
  }
}

function addServicePatternRow() {
  servicePatternRows.value.push('')
}

function removeServicePatternRow(index: number) {
  servicePatternRows.value.splice(index, 1)
}

// 登出通知回调地址允许留空，非空时必须是 http/https 开头的合法 URL；与 validateNotifyUrl
// 校验逻辑一致（复用同步配置“接口地址”的写法），仅去掉“必填”这一条
function validateLogoutNotifyUrl(): string {
  const url = logoutNotifyUrl.value.trim()
  if (!url) return ''
  try {
    const parsed = new URL(url)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return '登出通知回调地址格式不正确，必须是 http/https 开头的合法 URL'
    }
  } catch {
    return '登出通知回调地址格式不正确，必须是 http/https 开头的合法 URL'
  }
  return ''
}

// 前端做一次快速校验（协议为 CAS/OAuth2.0 时至少一条非空规则、登出通知回调地址格式），
// 避免明显不合法的输入还要走一次网络请求才被后端拒绝；后端仍会做同样的校验兜底
function validateAuthConfig(): string {
  if (
    (authProtocol.value === 'CAS' || authProtocol.value === 'OAUTH2') &&
    servicePatternRows.value.every((row) => !row.trim())
  ) {
    return '协议类型为 CAS/OAuth2.0 时，回跳地址匹配列表至少需要一条规则'
  }
  return validateLogoutNotifyUrl()
}

async function saveAuthConfig() {
  const validationError = validateAuthConfig()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingAuthConfig.value = true
  try {
    const data = await appApi.updateAppAuthConfig(appId.value, {
      authProtocol: authProtocol.value,
      servicePatterns: servicePatternRows.value.map((row) => row.trim()).filter(Boolean),
      logoutNotifyUrl: logoutNotifyUrl.value.trim(),
    })
    applyAuthConfig(data)
    ElMessage.success('保存成功')
  } finally {
    savingAuthConfig.value = false
  }
}

// ---- 认证管理：用户信息响应字段映射（CAS 票据验证/OAuth2 userinfo 接口共用，每个应用
// 一份），交互模式照抄上面“同步配置”标签页的“字段映射”表格：顶部“新增字段”下拉选择本地
// 字段插入一行、应用字段名称/编码可编辑、转换方式下拉+条件展示固定值/脚本输入框、增删行、
// 保存。本地字段目录复用 metadataFieldOptionsCache.USER 这份缓存（与“同步字段映射”表格
// 的用户域共用同一份元数据字段查询结果），并在下拉最前面插入一个固定的“用户ID”伪字段选项
// （metadataFieldId 传 null，见 openspec/changes/add-sso-userinfo-field-mapping/design.md
// Decision 2）----

interface UserinfoFieldMappingRow {
  id: number | null
  metadataFieldId: number | null
  fieldName: string
  fieldCode: string
  appFieldName: string
  appFieldCode: string
  transformType: TransformType
  transformValue: string
}

const userinfoFieldMappingRows = ref<UserinfoFieldMappingRow[]>([])
const userinfoFieldMappingLoading = ref(false)
const savingUserinfoFieldMapping = ref(false)
// “新增字段”下拉框当前选中值：undefined 表示未选中（清空态），null 表示选中了“用户ID”
// 伪字段，两者需要能区分，因此不能都用 undefined 表达
const pendingUserinfoFieldId = ref<number | null | undefined>(undefined)

// 本地字段目录：与“同步字段映射”表格的用户域共用同一份缓存（metadataFieldOptionsCache.USER），
// 避免重复请求；哪个标签页先加载都会把结果写入这份共享缓存
const userinfoMetadataFieldOptions = computed<MetadataField[]>(() => metadataFieldOptionsCache.USER ?? [])

const userinfoUsedFieldIds = computed(() => new Set(userinfoFieldMappingRows.value.map((row) => row.metadataFieldId)))
// “用户ID”伪字段是否已经在表格里出现过（metadataFieldId 为 null 的那一行）
const userinfoPseudoFieldAvailable = computed(() => !userinfoUsedFieldIds.value.has(null))
// “新增字段”下拉框可选的真实元数据字段：状态启用、且尚未出现在表格里
const addableUserinfoMetadataFieldOptions = computed(() =>
  userinfoMetadataFieldOptions.value.filter(
    (field) => field.status === METADATA_FIELD_STATUS_ENABLED && !userinfoUsedFieldIds.value.has(field.id),
  ),
)

function toUserinfoFieldMappingRow(vo: AppUserinfoFieldMappingVO): UserinfoFieldMappingRow {
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

// 按需加载用户域元数据字段目录（若“同步配置”标签页已加载过则直接复用，不重复请求）
async function ensureUserinfoMetadataFieldOptionsLoaded() {
  if (metadataFieldOptionsCache.USER) return
  const fieldPage = await metadataFieldApi.getMetadataFieldPageForSyncDomain('USER')
  metadataFieldOptionsCache.USER = fieldPage.records
}

async function fetchUserinfoFieldMappings() {
  userinfoFieldMappingLoading.value = true
  try {
    const [mappings] = await Promise.all([
      appApi.getAppUserinfoFieldMappings(appId.value),
      ensureUserinfoMetadataFieldOptionsLoaded(),
    ])
    userinfoFieldMappingRows.value = mappings.map(toUserinfoFieldMappingRow)
  } finally {
    userinfoFieldMappingLoading.value = false
  }
}

function handleAddUserinfoField(metadataFieldId: number | null | undefined) {
  if (metadataFieldId === undefined) return
  if (metadataFieldId === null) {
    userinfoFieldMappingRows.value.push({
      id: null,
      metadataFieldId: null,
      fieldName: '用户ID',
      fieldCode: 'id',
      appFieldName: '用户ID',
      appFieldCode: 'id',
      transformType: 'NO_TRANSFORM',
      transformValue: '',
    })
  } else {
    const field = userinfoMetadataFieldOptions.value.find((item) => item.id === metadataFieldId)
    if (!field) return
    userinfoFieldMappingRows.value.push({
      id: null,
      metadataFieldId: field.id,
      fieldName: field.fieldName,
      fieldCode: field.fieldCode,
      // 应用字段名称/编码默认预填为源字段的名称/编码，多数场景下两者相同，用户仍可编辑覆盖
      appFieldName: field.fieldName,
      appFieldCode: field.fieldCode,
      transformType: 'NO_TRANSFORM',
      transformValue: '',
    })
  }
  pendingUserinfoFieldId.value = undefined
}

function removeUserinfoFieldMappingRow(index: number) {
  userinfoFieldMappingRows.value.splice(index, 1)
}

// appFieldCode 标识符正则，对齐后端 AppUserinfoFieldMappingSaveRequest 的
// @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_-]*$")
const APP_FIELD_CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_-]*$/

function validateUserinfoFieldMappingRows(rows: UserinfoFieldMappingRow[]): string {
  const seenCodes = new Set<string>()
  for (const row of rows) {
    if (!row.appFieldName.trim()) return '应用字段名称不能为空'
    const code = row.appFieldCode.trim()
    if (!code) return '应用字段编码不能为空'
    if (!APP_FIELD_CODE_PATTERN.test(code)) {
      return '应用字段编码格式不正确，须以字母开头，只能包含字母、数字、下划线、短横线'
    }
    if (seenCodes.has(code)) return `应用字段编码"${code}"重复，请修改后再保存`
    seenCodes.add(code)
    if (row.transformType === 'FIXED_VALUE' && !row.transformValue.trim()) {
      return '转换方式为固定值时，取值不能为空'
    }
    if (row.transformType === 'SCRIPT' && !row.transformValue.trim()) {
      return '转换方式为转换脚本时，脚本内容不能为空'
    }
  }
  return ''
}

async function saveUserinfoFieldMappings() {
  const rows = userinfoFieldMappingRows.value
  const validationError = validateUserinfoFieldMappingRows(rows)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingUserinfoFieldMapping.value = true
  try {
    const payload: AppUserinfoFieldMappingSaveRequest[] = rows.map((row) => ({
      metadataFieldId: row.metadataFieldId,
      appFieldName: row.appFieldName.trim(),
      appFieldCode: row.appFieldCode.trim(),
      transformType: row.transformType,
      transformValue: row.transformType === 'NO_TRANSFORM' ? null : row.transformValue,
    }))
    const data = await appApi.replaceAppUserinfoFieldMappings(appId.value, payload)
    userinfoFieldMappingRows.value = data.map(toUserinfoFieldMappingRow)
    ElMessage.success('保存成功')
  } finally {
    savingUserinfoFieldMapping.value = false
  }
}

// ---- 通知日志：数据类型/bizId/状态/HTTP 状态码/回调地址/错误摘要/时间，历史记录部分列
// 可能为空（见 openspec/changes/add-app-sync-notify-pull-logs），首次激活标签页才加载，
// 交互模式参照 OperationLogManagementView.vue（过滤表单 + 分页表格） ----

const notifyLogLoaded = ref(false)
const notifyLogLoading = ref(false)
const notifyLogList = ref<AppNotifyRecordRow[]>([])
const notifyLogFilters = reactive({
  notifyStatus: undefined as number | undefined,
})
// datetimerange 选择器绑定值：未选择时为 null
const notifyLogDateRange = ref<[string, string] | null>(null)
const notifyLogPage = ref(1)
const notifyLogPageSize = ref(DEFAULT_PAGE_SIZE)
const notifyLogTotal = ref(0)

async function fetchNotifyLogList() {
  notifyLogLoading.value = true
  try {
    const result = await appApi.getAppNotifyRecordPage(appId.value, {
      notifyStatus: notifyLogFilters.notifyStatus,
      startTime: notifyLogDateRange.value?.[0],
      endTime: notifyLogDateRange.value?.[1],
      page: notifyLogPage.value,
      pageSize: notifyLogPageSize.value,
    })
    notifyLogList.value = result.records
    notifyLogTotal.value = result.total
    notifyLogPage.value = result.page
    notifyLogPageSize.value = result.pageSize
  } finally {
    notifyLogLoading.value = false
  }
}

function ensureNotifyLogLoaded() {
  if (notifyLogLoaded.value) return
  notifyLogLoaded.value = true
  fetchNotifyLogList()
}

function handleNotifyLogSearch() {
  notifyLogPage.value = 1
  fetchNotifyLogList()
}

function handleNotifyLogReset() {
  notifyLogFilters.notifyStatus = undefined
  notifyLogDateRange.value = null
  notifyLogPage.value = 1
  fetchNotifyLogList()
}

function handleNotifyLogPageChange(targetPage: number) {
  notifyLogPage.value = targetPage
  fetchNotifyLogList()
}

function handleNotifyLogSizeChange(newSize: number) {
  notifyLogPageSize.value = newSize
  notifyLogPage.value = 1
  fetchNotifyLogList()
}

// 取值为空（null/undefined/空字符串）时统一展示为空占位符“-”
function displayOrDash(value: string | number | null | undefined): string | number {
  return value === null || value === undefined || value === '' ? '-' : value
}

// ---- 拉取日志：拉取方式/数据类型/请求摘要/返回条数/时间，dataType 按序列号拉取未传时
// 可能为空，首次激活标签页才加载 ----

const pullLogLoaded = ref(false)
const pullLogLoading = ref(false)
const pullLogList = ref<AppPullRecordRow[]>([])
// datetimerange 选择器绑定值：未选择时为 null
const pullLogDateRange = ref<[string, string] | null>(null)
const pullLogPage = ref(1)
const pullLogPageSize = ref(DEFAULT_PAGE_SIZE)
const pullLogTotal = ref(0)

async function fetchPullLogList() {
  pullLogLoading.value = true
  try {
    const result = await appApi.getAppPullRecordPage(appId.value, {
      startTime: pullLogDateRange.value?.[0],
      endTime: pullLogDateRange.value?.[1],
      page: pullLogPage.value,
      pageSize: pullLogPageSize.value,
    })
    pullLogList.value = result.records
    pullLogTotal.value = result.total
    pullLogPage.value = result.page
    pullLogPageSize.value = result.pageSize
  } finally {
    pullLogLoading.value = false
  }
}

function ensurePullLogLoaded() {
  if (pullLogLoaded.value) return
  pullLogLoaded.value = true
  fetchPullLogList()
}

function handlePullLogSearch() {
  pullLogPage.value = 1
  fetchPullLogList()
}

function handlePullLogReset() {
  pullLogDateRange.value = null
  pullLogPage.value = 1
  fetchPullLogList()
}

function handlePullLogPageChange(targetPage: number) {
  pullLogPage.value = targetPage
  fetchPullLogList()
}

function handlePullLogSizeChange(newSize: number) {
  pullLogPageSize.value = newSize
  pullLogPage.value = 1
  fetchPullLogList()
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

      <el-tab-pane label="同步配置" name="sync">
        <el-tabs
          v-model="syncSectionTab"
          class="app-config__sync-section-tabs"
          @tab-change="handleSyncSectionTabChange"
        >
          <el-tab-pane label="基础同步配置" name="basicSync">
            <el-form label-width="110px">
              <el-form-item label="同步总开关">
                <el-switch v-model="syncForm.syncMasterEnabled" />
                <div class="app-config__form-item-hint">
                  关闭后该应用不再产生新的数据变更记录、不再收到通知、拉取接口返回空结果；
                  已产生的历史记录不会被清空，重新打开后可继续访问。
                </div>
              </el-form-item>
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
              <el-form-item label="签名校验">
                <el-switch v-model="syncForm.needSign" />
              </el-form-item>
              <el-form-item v-if="syncForm.needSign" label="签名算法">
                <el-radio-group v-model="signAlgorithmForm">
                  <el-radio value="SHA256">SHA-256</el-radio>
                  <el-radio value="SM3">国密 SM3</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item v-if="hasPermission('AppManagement:app:config:editSync')">
                <el-button type="primary" :loading="savingBasicSync" @click="saveBasicSyncConfig">保存</el-button>
              </el-form-item>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="数据范围" name="domainScope">
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
                  <el-tabs v-model="domainSubTab[option.value]" class="app-config__domain-sub-tabs">
                    <el-tab-pane label="是否启用" name="enable">
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
                    </el-tab-pane>

                    <el-tab-pane
                      v-if="orgScopeSupportedDomains.includes(option.value)"
                      label="同步范围"
                      name="orgScope"
                    >
                      <div v-loading="orgScopeLoading" class="app-config__org-scope">
                        <el-radio-group v-model="orgScopeState[option.value].mode">
                          <el-radio value="ALL">全部数据</el-radio>
                          <el-radio value="SCOPED">指定组织范围</el-radio>
                        </el-radio-group>

                        <div
                          v-if="orgScopeState[option.value].mode === 'SCOPED'"
                          class="app-config__org-scope-section"
                        >
                          <div class="app-config__org-scope-section__header">
                            <el-button link type="primary" @click="addOrgScopeRow(option.value)">
                              + 添加组织
                            </el-button>
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

                        <div
                          v-if="hasPermission('AppManagement:app:config:editSync')"
                          class="app-config__org-scope-save"
                        >
                          <el-button type="primary" :loading="savingOrgScope" @click="saveOrgScope(option.value)">
                            保存同步范围
                          </el-button>
                        </div>
                      </div>
                    </el-tab-pane>

                    <el-tab-pane
                      v-if="fieldMappingSupportedDomains.includes(option.value)"
                      label="字段映射"
                      name="fieldMapping"
                    >
                      <div class="app-config__field-mapping-toolbar">
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

                      <div
                        v-if="hasPermission('AppManagement:app:config:editSync')"
                        class="app-config__field-mapping-save"
                      >
                        <el-button type="primary" :loading="savingFieldMapping" @click="saveFieldMappings">
                          保存字段映射
                        </el-button>
                      </div>
                    </el-tab-pane>
                  </el-tabs>
                </div>
              </el-tab-pane>
            </el-tabs>
          </el-tab-pane>

          <el-tab-pane label="通知日志" name="notifyLog">
            <el-form class="app-config__log-filter-form" inline @submit.prevent>
              <el-form-item label="状态">
                <el-select
                  v-model="notifyLogFilters.notifyStatus"
                  placeholder="全部状态"
                  clearable
                  style="width: 140px"
                >
                  <el-option
                    v-for="opt in NOTIFY_STATUS_OPTIONS"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="通知时间">
                <el-date-picker
                  v-model="notifyLogDateRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleNotifyLogSearch">查询</el-button>
                <el-button @click="handleNotifyLogReset">重置</el-button>
              </el-form-item>
            </el-form>

            <el-table v-loading="notifyLogLoading" :data="notifyLogList" empty-text="暂无通知日志">
              <el-table-column label="数据类型" width="100">
                <template #default="{ row }">{{ displayOrDash((row as AppNotifyRecordRow).dataType) }}</template>
              </el-table-column>
              <el-table-column label="bizId" width="100">
                <template #default="{ row }">{{ displayOrDash((row as AppNotifyRecordRow).bizId) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag
                    :type="(row as AppNotifyRecordRow).notifyStatus === NOTIFY_STATUS_SUCCESS ? 'success' : 'danger'"
                  >
                    {{ (row as AppNotifyRecordRow).notifyStatus === NOTIFY_STATUS_SUCCESS ? '成功' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="HTTP 状态码" width="110">
                <template #default="{ row }">{{ displayOrDash((row as AppNotifyRecordRow).httpStatus) }}</template>
              </el-table-column>
              <el-table-column label="回调地址" min-width="200" show-overflow-tooltip>
                <template #default="{ row }">{{ displayOrDash((row as AppNotifyRecordRow).notifyUrl) }}</template>
              </el-table-column>
              <el-table-column label="错误摘要" min-width="180" show-overflow-tooltip>
                <template #default="{ row }">{{ displayOrDash((row as AppNotifyRecordRow).errorMsg) }}</template>
              </el-table-column>
              <el-table-column prop="createTime" label="时间" width="170" />
            </el-table>

            <el-pagination
              class="app-config__log-pagination"
              background
              layout="sizes, prev, pager, next, total"
              :page-sizes="[...PAGE_SIZE_OPTIONS]"
              :current-page="notifyLogPage"
              :page-size="notifyLogPageSize"
              :total="notifyLogTotal"
              @current-change="handleNotifyLogPageChange"
              @size-change="handleNotifyLogSizeChange"
            />
          </el-tab-pane>

          <el-tab-pane label="拉取日志" name="pullLog">
            <el-form class="app-config__log-filter-form" inline @submit.prevent>
              <el-form-item label="拉取时间">
                <el-date-picker
                  v-model="pullLogDateRange"
                  type="datetimerange"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handlePullLogSearch">查询</el-button>
                <el-button @click="handlePullLogReset">重置</el-button>
              </el-form-item>
            </el-form>

            <el-table v-loading="pullLogLoading" :data="pullLogList" empty-text="暂无拉取日志">
              <el-table-column label="数据类型" width="120">
                <template #default="{ row }">{{ displayOrDash((row as AppPullRecordRow).dataType) }}</template>
              </el-table-column>
              <el-table-column label="请求摘要" min-width="260" show-overflow-tooltip prop="requestSummary" />
              <el-table-column label="返回条数" prop="resultCount" width="100" />
              <el-table-column prop="createTime" label="时间" width="170" />
            </el-table>

            <el-pagination
              class="app-config__log-pagination"
              background
              layout="sizes, prev, pager, next, total"
              :page-sizes="[...PAGE_SIZE_OPTIONS]"
              :current-page="pullLogPage"
              :page-size="pullLogPageSize"
              :total="pullLogTotal"
              @current-change="handlePullLogPageChange"
              @size-change="handlePullLogSizeChange"
            />
          </el-tab-pane>
        </el-tabs>
      </el-tab-pane>

      <el-tab-pane label="认证管理" name="auth">
        <div v-loading="authConfigLoading">
          <el-alert
            class="app-config__auth-alert"
            type="info"
            :closable="false"
            show-icon
            title="当前仅支持协议配置维护，协议运行时接口尚未开放"
          />

          <el-form label-width="150px">
            <el-form-item label="单点登录协议">
              <el-select v-model="authProtocol" style="width: 200px">
                <el-option v-for="option in AUTH_PROTOCOL_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>

            <el-form-item label="登出通知回调地址">
              <el-input
                v-model="logoutNotifyUrl"
                style="width: 420px"
                placeholder="可选，单点登出时通知该地址，如 https://partner.example.com/sso/logout-notify"
              />
            </el-form-item>

            <el-form-item label="回跳地址匹配列表">
              <div class="app-config__param-rows">
                <div v-for="(_, index) in servicePatternRows" :key="index" class="app-config__auth-pattern-row">
                  <el-input
                    v-model="servicePatternRows[index]"
                    placeholder="ANT 表达式，如 https://partner.example.com/**"
                  />
                  <el-button link :icon="Delete" type="danger" @click="removeServicePatternRow(index)" />
                </div>
                <el-button link :icon="Plus" @click="addServicePatternRow">添加匹配规则</el-button>
              </div>
            </el-form-item>

            <template v-if="authProtocol === 'CAS'">
              <el-form-item v-if="hasPermission('AppManagement:app:config:editAuth')">
                <el-button type="primary" :loading="savingAuthConfig" @click="saveAuthConfig">保存</el-button>
              </el-form-item>

              <h4 class="app-config__sync-group-title">CAS 协议接口</h4>
              <div class="app-config__row">
                <span class="app-config__label">单点登录接口</span>
                <span class="app-config__value">{{ authUrls.casLoginUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.casLoginUrl)">复制</el-button>
              </div>
              <div class="app-config__row">
                <span class="app-config__label">票据验证接口</span>
                <span class="app-config__value">{{ authUrls.casServiceValidateUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.casServiceValidateUrl)">复制</el-button>
              </div>
              <div class="app-config__row">
                <span class="app-config__label">单点登出接口</span>
                <span class="app-config__value">{{ authUrls.casLogoutUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.casLogoutUrl)">复制</el-button>
              </div>
            </template>

            <template v-else-if="authProtocol === 'OAUTH2'">
              <el-form-item v-if="hasPermission('AppManagement:app:config:editAuth')">
                <el-button type="primary" :loading="savingAuthConfig" @click="saveAuthConfig">保存</el-button>
              </el-form-item>

              <h4 class="app-config__sync-group-title">OAuth2.0 协议接口</h4>
              <div class="app-config__row">
                <span class="app-config__label">授权接口</span>
                <span class="app-config__value">{{ authUrls.oauthAuthorizeUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.oauthAuthorizeUrl)">复制</el-button>
              </div>
              <el-table :data="OAUTH_AUTHORIZE_PARAMS" border size="small" class="app-config__auth-param-table">
                <el-table-column label="参数名" prop="name" width="140" />
                <el-table-column label="是否必选" prop="required" width="90" />
                <el-table-column label="说明" prop="desc" />
              </el-table>
              <div class="app-config__row">
                <span class="app-config__label">Access Token 接口</span>
                <span class="app-config__value">{{ authUrls.oauthTokenUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.oauthTokenUrl)">复制</el-button>
              </div>
              <div class="app-config__row">
                <span class="app-config__label">用户信息接口</span>
                <span class="app-config__value">{{ authUrls.oauthUserInfoUrl }}</span>
                <el-button link :icon="CopyDocument" @click="copyText(authUrls.oauthUserInfoUrl)">复制</el-button>
              </div>
            </template>

            <el-form-item v-else>
              <el-button
                v-if="hasPermission('AppManagement:app:config:editAuth')"
                type="primary"
                :loading="savingAuthConfig"
                @click="saveAuthConfig"
              >
                保存
              </el-button>
            </el-form-item>
          </el-form>

          <h4 class="app-config__sync-group-title">用户信息响应字段映射</h4>
          <p class="app-config__field-mapping-hint">
            配置 CAS 票据验证 / OAuth2 用户信息接口返回给应用的字段（协议固定字段 cas:user /
            sub 不受此配置影响，无需在此重复配置）
          </p>
          <div class="app-config__field-mapping-toolbar">
            <el-select
              v-model="pendingUserinfoFieldId"
              placeholder="选择字段新增映射"
              filterable
              clearable
              class="app-config__field-mapping-select"
              @change="handleAddUserinfoField"
            >
              <el-option v-if="userinfoPseudoFieldAvailable" label="用户ID" :value="null as unknown as number" />
              <el-option
                v-for="field in addableUserinfoMetadataFieldOptions"
                :key="field.id"
                :label="`${field.fieldName}（${field.fieldCode}）`"
                :value="field.id"
              />
            </el-select>
          </div>

          <el-table
            v-loading="userinfoFieldMappingLoading"
            :data="userinfoFieldMappingRows"
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
                <el-button link :icon="Delete" type="danger" @click="removeUserinfoFieldMappingRow($index)" />
              </template>
            </el-table-column>
          </el-table>

          <div v-if="hasPermission('AppManagement:app:config:editAuth')" class="app-config__field-mapping-save">
            <el-button type="primary" :loading="savingUserinfoFieldMapping" @click="saveUserinfoFieldMappings">
              保存字段映射
            </el-button>
          </div>
        </div>
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

// 表单项内的补充说明小字，如"同步总开关"旁的效果说明，和 .el-form-item__error 的行高
// 视觉呼应但不占用校验错误的展示位
.app-config__form-item-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-secondary);
}

// “同步配置”一级 tab 内部的子级 tabs：基础同步配置/数据范围/通知日志/拉取日志，顶部横排，
// 写法与下面数据域面板内“是否启用/同步范围/字段映射”二级 tabs（.app-config__domain-sub-tabs）
// 保持一致，收紧和一级 tab 内容区的间距
.app-config__sync-section-tabs {
  margin-top: 4px;

  :deep(.el-tabs__header) {
    margin-bottom: 16px;
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

// 数据域面板内“是否启用/同步范围/字段映射”二级 tabs：顶部横排，和外层左侧纵向的数据域
// tab 区分开，靠一条实线分隔线收紧和一级 tab 内容区的间距
.app-config__domain-sub-tabs {
  margin-top: 4px;

  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

// 同步范围：单选“全部数据/指定组织范围” + 指定时的组织行列表，视觉上直接复用
// AdminManagementView.vue“管辖组织范围”子表单的链式连接语言（虚线+圆点），
// 与本页“字段映射”区块并列作为同一个数据域 tab 内的独立保存分区
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
  grid-template-columns: minmax(0, 260px) auto;
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
  justify-content: flex-end;
  margin: 4px 0 12px;
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

.app-config__field-mapping-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin: -8px 0 12px;
}

// 认证管理：顶部提示条 + 匹配规则行编辑（单值，区别于同步配置通知参数的 key-value 两列）
.app-config__auth-alert {
  margin-bottom: 16px;
}

.app-config__auth-pattern-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.app-config__auth-param-table {
  width: 100%;
  margin: 8px 0 4px;
}

// 通知日志/拉取日志：过滤表单 + 分页，交互模式与 OperationLogManagementView.vue 保持一致
.app-config__log-filter-form {
  margin-bottom: 8px;

  :deep(.el-form-item) {
    margin-bottom: 12px;
  }
}

.app-config__log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
