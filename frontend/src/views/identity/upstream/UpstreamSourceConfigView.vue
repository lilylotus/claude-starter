<script setup lang="ts">
// 上游数据源独立配置页：参照 AppConfigView.vue 的整体结构（返回按钮+标题、el-tabs 分区、
// v-loading）。五个分区：基础信息/连接配置/调度配置/数据范围/同步记录。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as upstreamSourceApi from '@/api/upstreamSource'
import * as metadataFieldApi from '@/api/metadataField'
import { TRANSFORM_TYPE_OPTIONS, type TransformType } from '@/types/app'
import {
  UPSTREAM_API_METHOD_OPTIONS,
  UPSTREAM_DATA_TYPE_OPTIONS,
  UPSTREAM_INTERVAL_UNIT_OPTIONS,
  UPSTREAM_POSITION_ORG_CODE_FIELD,
  UPSTREAM_POSITION_USER_IDENTIFIER_FIELD,
  UPSTREAM_SCHEDULE_TYPE_OPTIONS,
  UPSTREAM_SYNC_STATUS_TAG_TYPE,
  UPSTREAM_SYNC_TYPE_OPTIONS,
  UPSTREAM_TRIGGER_TYPE_LABELS,
  UPSTREAM_SYNC_STATUS_LABELS,
  type UpstreamApiMethod,
  type UpstreamDataType,
  type UpstreamFieldMappingVO,
  type UpstreamIntervalUnit,
  type UpstreamScheduleType,
  type UpstreamSourceVO,
  type UpstreamSyncRecordVO,
  type UpstreamSyncType,
} from '@/types/upstreamSource'
import { METADATA_FIELD_STATUS_ENABLED, type MetadataField, type MetadataFieldBizType } from '@/types/metadataField'
import { usePermission } from '@/composables/usePermission'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const sourceId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const source = ref<UpstreamSourceVO | null>(null)

const activeTab = ref<'basic' | 'connection' | 'schedule' | 'domains' | 'records'>('basic')

function goBack() {
  router.push({ name: 'identity-upstream' })
}

// ---- 基础信息 ----

const basicForm = reactive<{ name: string; syncType: UpstreamSyncType }>({ name: '', syncType: 'API' })
const savingBasic = ref(false)

async function saveBasicInfo() {
  if (!basicForm.name.trim()) {
    ElMessage.error('请输入数据源名称')
    return
  }
  savingBasic.value = true
  try {
    const data = await upstreamSourceApi.updateUpstreamSourceBasicInfo(sourceId.value, {
      name: basicForm.name.trim(),
      syncType: basicForm.syncType,
    })
    applySource(data)
    ElMessage.success('保存成功')
  } finally {
    savingBasic.value = false
  }
}

// ---- 连接配置 ----

const headerRows = ref<{ key: string; value: string }[]>([])
const connectionForm = reactive({ dbJdbcUrl: '', dbUsername: '', dbPassword: '' })
const savingConnection = ref(false)

function addHeaderRow() {
  headerRows.value.push({ key: '', value: '' })
}

function removeHeaderRow(index: number) {
  headerRows.value.splice(index, 1)
}

function initConnectionForm(data: UpstreamSourceVO) {
  headerRows.value = data.apiAuthHeaderKeys.map((key) => ({ key, value: '' }))
  connectionForm.dbJdbcUrl = data.dbJdbcUrl ?? ''
  connectionForm.dbUsername = data.dbUsername ?? ''
  connectionForm.dbPassword = ''
}

function validateConnectionForm(): string {
  if (basicForm.syncType === 'DB_TABLE') {
    if (!connectionForm.dbJdbcUrl.trim()) return '请输入 JDBC 连接地址'
    if (!connectionForm.dbJdbcUrl.trim().startsWith('jdbc:mysql://')) {
      return 'JDBC 连接地址格式不正确，必须是 jdbc:mysql:// 开头'
    }
    if (!connectionForm.dbUsername.trim()) return '请输入数据库用户名'
  }
  return ''
}

async function saveConnectionConfig() {
  const validationError = validateConnectionForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingConnection.value = true
  try {
    const apiAuthHeaders: Record<string, string> = {}
    for (const row of headerRows.value) {
      if (row.key.trim()) apiAuthHeaders[row.key.trim()] = row.value
    }
    const data = await upstreamSourceApi.updateUpstreamConnectionConfig(sourceId.value, {
      apiAuthHeaders,
      dbJdbcUrl: connectionForm.dbJdbcUrl.trim(),
      dbUsername: connectionForm.dbUsername.trim(),
      dbPassword: connectionForm.dbPassword,
    })
    applySource(data)
    ElMessage.success('保存成功')
  } finally {
    savingConnection.value = false
  }
}

// ---- 调度配置 ----

const scheduleForm = reactive<{
  scheduleType: UpstreamScheduleType
  intervalUnit: UpstreamIntervalUnit | ''
  intervalValue: number | null
  fixedTime: string
}>({ scheduleType: 'INTERVAL', intervalUnit: 'MINUTE', intervalValue: 30, fixedTime: '' })
const savingSchedule = ref(false)
const manualSyncing = ref(false)

function initScheduleForm(data: UpstreamSourceVO) {
  scheduleForm.scheduleType = data.scheduleType
  scheduleForm.intervalUnit = data.intervalUnit || 'MINUTE'
  scheduleForm.intervalValue = data.intervalValue ?? 30
  scheduleForm.fixedTime = data.fixedTime || ''
}

function validateScheduleForm(): string {
  if (scheduleForm.scheduleType === 'INTERVAL') {
    if (!scheduleForm.intervalUnit) return '请选择间隔单位'
    if (!scheduleForm.intervalValue || scheduleForm.intervalValue <= 0) return '间隔取值必须是正整数'
  } else {
    if (!scheduleForm.fixedTime || !/^([01]\d|2[0-3]):[0-5]\d$/.test(scheduleForm.fixedTime)) {
      return '固定时间点格式不正确，应为 HH:mm'
    }
  }
  return ''
}

async function saveScheduleConfig() {
  const validationError = validateScheduleForm()
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingSchedule.value = true
  try {
    const data = await upstreamSourceApi.updateUpstreamScheduleConfig(sourceId.value, {
      scheduleType: scheduleForm.scheduleType,
      intervalUnit: scheduleForm.scheduleType === 'INTERVAL' ? scheduleForm.intervalUnit : '',
      intervalValue: scheduleForm.scheduleType === 'INTERVAL' ? scheduleForm.intervalValue : null,
      fixedTime: scheduleForm.scheduleType === 'FIXED_TIME' ? scheduleForm.fixedTime : '',
    })
    applySource(data)
    ElMessage.success('保存成功')
  } finally {
    savingSchedule.value = false
  }
}

async function handleManualSync() {
  manualSyncing.value = true
  try {
    await upstreamSourceApi.manualSyncUpstreamSource(sourceId.value)
    ElMessage.success('已触发，请稍后在同步记录里查看结果')
  } finally {
    manualSyncing.value = false
  }
}

// ---- 数据范围：左侧组织/用户/任职纵向 tab + 右侧“是否启用”/“字段映射”二级 tab ----

const domainTab = ref<UpstreamDataType>('ORG')

type DomainSubTabName = 'enable' | 'fieldMapping'
const domainSubTab = reactive<Record<UpstreamDataType, DomainSubTabName>>({
  ORG: 'enable',
  USER: 'enable',
  POSITION: 'enable',
})

interface DomainForm {
  enabled: boolean
  apiUrl: string
  apiMethod: UpstreamApiMethod
  dbSql: string
}

const domainConfigs = reactive<Record<UpstreamDataType, DomainForm>>({
  ORG: { enabled: false, apiUrl: '', apiMethod: 'GET', dbSql: '' },
  USER: { enabled: false, apiUrl: '', apiMethod: 'GET', dbSql: '' },
  POSITION: { enabled: false, apiUrl: '', apiMethod: 'GET', dbSql: '' },
})
const domainConfigLoading = ref(false)
const savingDomainConfig = ref(false)

async function fetchDomainConfigs() {
  domainConfigLoading.value = true
  try {
    const rows = await upstreamSourceApi.listUpstreamDomainConfigs(sourceId.value)
    for (const row of rows) {
      domainConfigs[row.dataType] = {
        enabled: row.enabled,
        apiUrl: row.apiUrl ?? '',
        apiMethod: (row.apiMethod || 'GET') as UpstreamApiMethod,
        dbSql: row.dbSql ?? '',
      }
    }
  } finally {
    domainConfigLoading.value = false
  }
}

function validateDomainForm(dataType: UpstreamDataType): string {
  const form = domainConfigs[dataType]
  if (!form.enabled) return ''
  if (basicForm.syncType === 'API') {
    if (!form.apiUrl.trim()) return '请输入请求地址'
  } else {
    const sql = form.dbSql.trim()
    if (!sql) return '请输入查询 SQL'
    if (!/^(select|with)/i.test(sql)) return '查询 SQL 只支持以 SELECT 或 WITH 开头的只读查询'
  }
  return ''
}

async function saveDomainConfig(dataType: UpstreamDataType) {
  const validationError = validateDomainForm(dataType)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingDomainConfig.value = true
  try {
    const form = domainConfigs[dataType]
    const data = await upstreamSourceApi.updateUpstreamDomainConfig(sourceId.value, dataType, {
      enabled: form.enabled,
      apiUrl: form.apiUrl.trim(),
      apiMethod: form.apiMethod,
      dbSql: form.dbSql,
    })
    domainConfigs[dataType] = {
      enabled: data.enabled,
      apiUrl: data.apiUrl ?? '',
      apiMethod: (data.apiMethod || 'GET') as UpstreamApiMethod,
      dbSql: data.dbSql ?? '',
    }
    ElMessage.success('保存成功')
  } finally {
    savingDomainConfig.value = false
  }
}

// ---- 数据范围：字段映射 ----

interface FieldMappingRow {
  id?: number
  metadataFieldId: number
  fieldName: string
  fieldCode: string
  upstreamFieldName: string
  upstreamFieldCode: string
  isPrimaryKey: boolean
  transformType: TransformType
  transformValue: string
}

const fieldMappingRowsCache = reactive<Partial<Record<UpstreamDataType, FieldMappingRow[]>>>({})
const metadataFieldOptionsCache = reactive<Partial<Record<UpstreamDataType, MetadataField[]>>>({})
const fieldMappingLoading = ref(false)
const savingFieldMapping = ref(false)
const pendingFieldId = ref<number | null>(null)

const currentFieldMappingRows = computed<FieldMappingRow[]>(() => fieldMappingRowsCache[domainTab.value] ?? [])
const currentMetadataFieldOptions = computed<MetadataField[]>(() => metadataFieldOptionsCache[domainTab.value] ?? [])

const addableMetadataFieldOptions = computed(() => {
  const usedIds = new Set(currentFieldMappingRows.value.map((row) => row.metadataFieldId))
  return currentMetadataFieldOptions.value.filter(
    (field) => field.status === METADATA_FIELD_STATUS_ENABLED && !usedIds.has(field.id),
  )
})

function toFieldMappingRow(vo: UpstreamFieldMappingVO): FieldMappingRow {
  return {
    id: vo.id,
    metadataFieldId: vo.metadataFieldId,
    fieldName: vo.fieldName,
    fieldCode: vo.fieldCode,
    upstreamFieldName: vo.upstreamFieldName,
    upstreamFieldCode: vo.upstreamFieldCode,
    isPrimaryKey: vo.isPrimaryKey,
    transformType: vo.transformType,
    transformValue: vo.transformValue ?? '',
  }
}

async function ensureFieldMappingLoaded(dataType: UpstreamDataType) {
  if (fieldMappingRowsCache[dataType] && metadataFieldOptionsCache[dataType]) return
  fieldMappingLoading.value = true
  try {
    const [mappings, fieldPage] = await Promise.all([
      upstreamSourceApi.listUpstreamFieldMappings(sourceId.value, dataType),
      metadataFieldApi.getMetadataFieldPageForSyncDomain(dataType as MetadataFieldBizType),
    ])
    fieldMappingRowsCache[dataType] = mappings.map(toFieldMappingRow)
    metadataFieldOptionsCache[dataType] = fieldPage.records
  } finally {
    fieldMappingLoading.value = false
  }
}

function handleDomainTabChange() {
  ensureFieldMappingLoaded(domainTab.value)
}

function handleAddField(metadataFieldId: number | null) {
  if (!metadataFieldId) return
  const dataType = domainTab.value
  const field = currentMetadataFieldOptions.value.find((item) => item.id === metadataFieldId)
  if (!field) return
  const rows = fieldMappingRowsCache[dataType] ?? (fieldMappingRowsCache[dataType] = [])
  rows.push({
    metadataFieldId: field.id,
    fieldName: field.fieldName,
    fieldCode: field.fieldCode,
    // 上游字段名称/编码不做默认预填：上游 schema 未知，且通常与系统字段名称不同，
    // 预填反而会误导管理员以为不用改（design.md Decision 5）
    upstreamFieldName: '',
    upstreamFieldCode: '',
    // 新增行默认不勾选主键标识，需管理员手动确认
    isPrimaryKey: false,
    transformType: 'NO_TRANSFORM',
    transformValue: '',
  })
  pendingFieldId.value = null
}

function removeFieldMappingRow(index: number) {
  fieldMappingRowsCache[domainTab.value]?.splice(index, 1)
}

function validateFieldMappingRows(rows: FieldMappingRow[]): string {
  for (const row of rows) {
    if (!row.upstreamFieldName.trim()) return '上游字段名称不能为空'
    if (!row.upstreamFieldCode.trim()) return '上游字段编码不能为空'
    if (row.transformType === 'FIXED_VALUE' && !row.transformValue.trim()) {
      return '转换方式为固定值时，取值不能为空'
    }
    if (row.transformType === 'SCRIPT' && !row.transformValue.trim()) {
      return '转换方式为转换脚本时，脚本内容不能为空'
    }
  }
  if (rows.length > 0 && !rows.some((row) => row.isPrimaryKey)) {
    return '请至少标记一个字段作为主键标识，用于判断新增或更新'
  }
  return ''
}

async function saveFieldMappings() {
  const dataType = domainTab.value
  const rows = fieldMappingRowsCache[dataType] ?? []
  const validationError = validateFieldMappingRows(rows)
  if (validationError) {
    ElMessage.error(validationError)
    return
  }
  savingFieldMapping.value = true
  try {
    const payload = rows.map((row) => ({
      upstreamFieldName: row.upstreamFieldName.trim(),
      upstreamFieldCode: row.upstreamFieldCode.trim(),
      metadataFieldId: row.metadataFieldId,
      isPrimaryKey: row.isPrimaryKey,
      transformType: row.transformType,
      transformValue: row.transformType === 'NO_TRANSFORM' ? null : row.transformValue,
    }))
    const data = await upstreamSourceApi.replaceUpstreamFieldMappings(sourceId.value, dataType, payload)
    fieldMappingRowsCache[dataType] = data.map(toFieldMappingRow)
    ElMessage.success('保存成功')
  } finally {
    savingFieldMapping.value = false
  }
}

// ---- 同步记录 ----

const syncRecords = ref<UpstreamSyncRecordVO[]>([])
const syncRecordsLoading = ref(false)

async function fetchSyncRecords() {
  syncRecordsLoading.value = true
  try {
    syncRecords.value = await upstreamSourceApi.listUpstreamSyncRecords(sourceId.value)
  } finally {
    syncRecordsLoading.value = false
  }
}

function dataTypeLabel(dataType: UpstreamDataType) {
  return UPSTREAM_DATA_TYPE_OPTIONS.find((item) => item.value === dataType)?.label ?? dataType
}

// ---- 加载入口 ----

function applySource(data: UpstreamSourceVO) {
  source.value = data
  basicForm.name = data.name
  basicForm.syncType = data.syncType
  initConnectionForm(data)
  initScheduleForm(data)
}

async function fetchSource() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await upstreamSourceApi.getUpstreamSource(sourceId.value)
    applySource(data)
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加载上游数据源失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSource()
  fetchDomainConfigs()
  ensureFieldMappingLoaded(domainTab.value)
  fetchSyncRecords()
})
</script>

<template>
  <div class="upstream-config">
    <header class="upstream-config__header">
      <el-button link :icon="ArrowLeft" class="upstream-config__back" @click="goBack">返回</el-button>
      <h2 class="upstream-config__title">上游数据源配置</h2>
    </header>

    <el-alert
      v-if="loadError"
      class="upstream-config__error"
      type="error"
      :title="loadError"
      show-icon
      :closable="false"
    />

    <el-tabs v-else v-model="activeTab" class="upstream-config__tabs" v-loading="loading">
      <el-tab-pane label="基础信息" name="basic">
        <el-form label-width="110px">
          <el-form-item label="名称">
            <el-input v-model="basicForm.name" placeholder="请输入数据源名称" maxlength="128" />
          </el-form-item>
          <el-form-item label="同步方式">
            <el-radio-group v-model="basicForm.syncType">
              <el-radio v-for="item in UPSTREAM_SYNC_TYPE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="hasPermission('UpstreamManagement:source:edit')">
            <el-button type="primary" :loading="savingBasic" @click="saveBasicInfo">保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="连接配置" name="connection">
        <el-form label-width="140px">
          <template v-if="basicForm.syncType === 'API'">
            <el-form-item label="自定义请求头">
              <div class="upstream-config__header-rows">
                <el-alert
                  type="info"
                  :closable="false"
                  show-icon
                  title="出于安全考虑，已保存的请求头取值不会回显；保存为整体替换语义，未在此重新填写的 key 会被清除"
                />
                <div v-for="(row, index) in headerRows" :key="index" class="upstream-config__header-row">
                  <el-input v-model="row.key" placeholder="请求头名称，如 Authorization" />
                  <el-input v-model="row.value" placeholder="请求头取值" />
                  <el-button link :icon="Delete" type="danger" @click="removeHeaderRow(index)" />
                </div>
                <el-button link :icon="Plus" @click="addHeaderRow">添加请求头</el-button>
              </div>
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="JDBC 连接地址">
              <el-input v-model="connectionForm.dbJdbcUrl" placeholder="jdbc:mysql://host:3306/database" />
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="connectionForm.dbUsername" placeholder="请输入数据库连接用户名" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input
                v-model="connectionForm.dbPassword"
                type="password"
                show-password
                placeholder="留空表示不修改已保存的密码"
              />
            </el-form-item>
          </template>
          <el-form-item v-if="hasPermission('UpstreamManagement:source:config:edit')">
            <el-button type="primary" :loading="savingConnection" @click="saveConnectionConfig">保存</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="调度配置" name="schedule">
        <el-form label-width="110px">
          <el-form-item label="调度方式">
            <el-radio-group v-model="scheduleForm.scheduleType">
              <el-radio v-for="item in UPSTREAM_SCHEDULE_TYPE_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
          <template v-if="scheduleForm.scheduleType === 'INTERVAL'">
            <el-form-item label="间隔单位">
              <el-radio-group v-model="scheduleForm.intervalUnit">
                <el-radio v-for="item in UPSTREAM_INTERVAL_UNIT_OPTIONS" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="间隔取值">
              <el-input-number v-model="scheduleForm.intervalValue" :min="1" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="每日固定时间点">
              <el-time-picker
                v-model="scheduleForm.fixedTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="请选择时间"
              />
            </el-form-item>
          </template>
          <el-form-item>
            <el-button
              v-if="hasPermission('UpstreamManagement:source:config:edit')"
              type="primary"
              :loading="savingSchedule"
              @click="saveScheduleConfig"
            >
              保存
            </el-button>
            <el-button
              v-if="hasPermission('UpstreamManagement:source:manualSync')"
              :loading="manualSyncing"
              @click="handleManualSync"
            >
              立即同步一次
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="数据范围" name="domains">
        <el-tabs v-model="domainTab" tab-position="left" class="upstream-config__domain-tabs" @tab-change="handleDomainTabChange">
          <el-tab-pane v-for="option in UPSTREAM_DATA_TYPE_OPTIONS" :key="option.value" :label="option.label" :name="option.value">
            <div v-loading="domainConfigLoading" class="upstream-config__domain-panel">
              <el-tabs v-model="domainSubTab[option.value]" class="upstream-config__domain-sub-tabs">
                <el-tab-pane label="是否启用" name="enable">
                  <el-alert
                    v-if="option.value === 'POSITION'"
                    class="upstream-config__pseudo-field-alert"
                    type="info"
                    :closable="false"
                    show-icon
                    :title="`任职数据域的取数结果须包含两个固定编码列「${UPSTREAM_POSITION_USER_IDENTIFIER_FIELD}」（人员标识，按用户 code/mobile/idCard 任一匹配）与「${UPSTREAM_POSITION_ORG_CODE_FIELD}」（组织编码，按组织 code 匹配），用于确定任职记录归属；这两列不在下方字段映射中配置`"
                  />
                  <el-form label-width="110px" class="upstream-config__domain-form">
                    <el-form-item label="是否启用">
                      <el-switch v-model="domainConfigs[option.value].enabled" />
                    </el-form-item>
                    <template v-if="basicForm.syncType === 'API'">
                      <el-form-item label="请求地址">
                        <el-input v-model="domainConfigs[option.value].apiUrl" placeholder="请输入该数据域的请求地址" />
                      </el-form-item>
                      <el-form-item label="请求方式">
                        <el-radio-group v-model="domainConfigs[option.value].apiMethod">
                          <el-radio v-for="item in UPSTREAM_API_METHOD_OPTIONS" :key="item.value" :value="item.value">
                            {{ item.label }}
                          </el-radio>
                        </el-radio-group>
                      </el-form-item>
                    </template>
                    <template v-else>
                      <el-form-item label="查询 SQL">
                        <el-input
                          v-model="domainConfigs[option.value].dbSql"
                          type="textarea"
                          :rows="4"
                          placeholder="请输入只读查询 SQL（SELECT/WITH 开头），列别名对应上游字段编码"
                        />
                      </el-form-item>
                    </template>
                    <el-form-item v-if="hasPermission('UpstreamManagement:source:config:edit')">
                      <el-button type="primary" :loading="savingDomainConfig" @click="saveDomainConfig(option.value)">
                        保存
                      </el-button>
                    </el-form-item>
                  </el-form>
                </el-tab-pane>

                <el-tab-pane label="字段映射" name="fieldMapping">
                  <div class="upstream-config__field-mapping-toolbar">
                    <el-select
                      v-model="pendingFieldId"
                      placeholder="选择系统字段新增映射"
                      filterable
                      clearable
                      class="upstream-config__field-mapping-select"
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
                    class="upstream-config__field-mapping-table"
                  >
                    <el-table-column label="上游字段名称" min-width="140">
                      <template #default="{ row }">
                        <el-input v-model="row.upstreamFieldName" placeholder="请输入上游字段名称" />
                      </template>
                    </el-table-column>
                    <el-table-column label="上游字段编码" min-width="140">
                      <template #default="{ row }">
                        <el-input v-model="row.upstreamFieldCode" placeholder="请输入上游字段编码" />
                      </template>
                    </el-table-column>
                    <el-table-column label="系统字段名称" prop="fieldName" width="130" />
                    <el-table-column label="系统字段编码" prop="fieldCode" width="130" />
                    <el-table-column label="主键标识" width="90" align="center">
                      <template #default="{ row }">
                        <el-checkbox v-model="row.isPrimaryKey" />
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
                          placeholder="请输入把上游取值转换为系统取值的 JavaScript 转换脚本"
                        />
                        <span v-else class="upstream-config__field-mapping-disabled">-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="70" fixed="right">
                      <template #default="{ $index }">
                        <el-button link :icon="Delete" type="danger" @click="removeFieldMappingRow($index)" />
                      </template>
                    </el-table-column>
                  </el-table>

                  <div
                    v-if="hasPermission('UpstreamManagement:source:config:edit')"
                    class="upstream-config__field-mapping-save"
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

      <el-tab-pane label="同步记录" name="records">
        <el-table v-loading="syncRecordsLoading" :data="syncRecords" empty-text="暂无同步执行记录">
          <el-table-column label="数据域" width="90">
            <template #default="{ row }">{{ dataTypeLabel((row as UpstreamSyncRecordVO).dataType) }}</template>
          </el-table-column>
          <el-table-column label="触发方式" width="100">
            <template #default="{ row }">
              {{ UPSTREAM_TRIGGER_TYPE_LABELS[(row as UpstreamSyncRecordVO).triggerType] }}
            </template>
          </el-table-column>
          <el-table-column prop="startTime" label="开始时间" min-width="160" />
          <el-table-column label="结束时间" min-width="160">
            <template #default="{ row }">{{ (row as UpstreamSyncRecordVO).endTime ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="UPSTREAM_SYNC_STATUS_TAG_TYPE[(row as UpstreamSyncRecordVO).status]">
                {{ UPSTREAM_SYNC_STATUS_LABELS[(row as UpstreamSyncRecordVO).status] }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalCount" label="处理总数" width="90" />
          <el-table-column prop="successCount" label="成功数" width="80" />
          <el-table-column prop="failCount" label="失败数" width="80" />
          <el-table-column label="失败摘要" min-width="220">
            <template #default="{ row }">{{ (row as UpstreamSyncRecordVO).failSummary ?? '-' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped lang="scss">
.upstream-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.upstream-config__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.upstream-config__back {
  font-size: 14px;
}

.upstream-config__title {
  font-size: 16px;
  color: var(--color-ink);
  margin: 0;
}

.upstream-config__error {
  margin-bottom: 4px;
}

.upstream-config__tabs {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);

  :deep(.el-tabs__header) {
    margin-bottom: 16px;
  }
}

.upstream-config__header-rows {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.upstream-config__header-row {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-input {
    max-width: 240px;
  }
}

// 数据范围：左侧纵向 tabs（组织/用户/任职），呼应仓库整体的“链式连接”视觉语言
.upstream-config__domain-tabs {
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  padding: 4px 16px 16px;

  :deep(.el-tabs__nav-wrap)::after {
    display: none;
  }
}

.upstream-config__domain-panel {
  min-height: 120px;
}

.upstream-config__domain-sub-tabs {
  margin-top: 4px;

  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.upstream-config__pseudo-field-alert {
  margin-bottom: 12px;
}

.upstream-config__domain-form {
  max-width: 640px;
}

.upstream-config__field-mapping-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin: 4px 0 12px;
}

.upstream-config__field-mapping-select {
  width: 280px;
}

.upstream-config__field-mapping-table {
  width: 100%;
}

.upstream-config__field-mapping-disabled {
  color: var(--color-text-tertiary);
}

.upstream-config__field-mapping-save {
  margin-top: 12px;
}
</style>
