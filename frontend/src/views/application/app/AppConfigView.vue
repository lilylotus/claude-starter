<script setup lang="ts">
// 应用配置独立页面：管理该应用对外的 AppId/AccessKey/SecretKey 凭证、签名算法、
// 数据同步范围开关。风格参照 AppDetailView.vue（独立路由、独立拉取数据、左上角"返回"按钮），
// 区别在于这里是可编辑的配置页而不是只读详情页；三个分区用 el-tabs 切换展示，
// 参照 FormFieldListView.vue 的外层 tabs 用法。
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CopyDocument, Delete, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as appApi from '@/api/app'
import type { AppConfigVO, SignAlgorithm, SyncMode } from '@/types/app'
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

// 同步配置卡片：四个数据域开关 + 基础同步配置项（同步方式、通知回调地址）的本地可编辑副本；
// notifyParams 不直接用 Record 编辑（模板里动态 key 不便双向绑定），拆成一个 { key, value }
// 行数组，保存前再收敛回 Record<string, string>
const syncForm = ref({
  syncOrgEnabled: false,
  syncUserEnabled: false,
  syncAppEnabled: false,
  syncDictEnabled: false,
  syncMode: 'PULL' as SyncMode,
  notifyUrl: '',
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
    syncOrgEnabled: data.syncOrgEnabled,
    syncUserEnabled: data.syncUserEnabled,
    syncAppEnabled: data.syncAppEnabled,
    syncDictEnabled: data.syncDictEnabled,
    syncMode: data.syncMode,
    notifyUrl: data.notifyUrl,
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

          <h4 class="app-config__sync-group-title">数据范围</h4>
          <el-form-item label="组织数据">
            <el-switch v-model="syncForm.syncOrgEnabled" />
          </el-form-item>
          <el-form-item label="用户数据">
            <el-switch v-model="syncForm.syncUserEnabled" />
          </el-form-item>
          <el-form-item label="应用数据">
            <el-switch v-model="syncForm.syncAppEnabled" />
          </el-form-item>
          <el-form-item label="字典数据">
            <el-switch v-model="syncForm.syncDictEnabled" />
          </el-form-item>

          <el-form-item v-if="hasPermission('AppManagement:app:config:editSync')">
            <el-button type="primary" :loading="savingSync" @click="saveSyncConfig">保存</el-button>
          </el-form-item>
        </el-form>
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
</style>
