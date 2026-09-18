<script setup lang="ts">
// 安全码校验弹窗：展示"我的安全码"与"对方安全码"（对身份公钥做 Blake2b 摘要后分组
// 十六进制展示），供双方通过电话/当面等带外渠道核对是否完全一致，防御密钥目录被篡改
// 导致的中间人攻击（chat-end-to-end-encryption change design.md Decision 4，
// tasks.md 7.1）。指纹计算全部在本地完成，弹窗打开时才异步算一次"我的安全码"
// （身份公钥本身不敏感，但没必要在组件挂载阶段就抢跑）。
import { ref, watch } from 'vue'
import { useChatStore } from '@/stores/chat'

const props = defineProps<{
  counterpartUserId: number | null
  counterpartName: string
}>()

const visible = defineModel<boolean>({ required: true })

const chatStore = useChatStore()
const myFingerprint = ref('')

// 弹窗打开时才现算一次"我的安全码"；身份私钥全程不出 store（computeMyFingerprint 内部
// 只用公钥部分算 Blake2b 摘要），本组件不直接持有任何密钥字节
watch(visible, async (value) => {
  if (!value) return
  myFingerprint.value = (await chatStore.computeMyFingerprint()) ?? ''
})

function counterpartFingerprint(): string {
  if (props.counterpartUserId === null) return ''
  return chatStore.counterpartFingerprints[props.counterpartUserId] ?? ''
}
</script>

<template>
  <el-dialog v-model="visible" title="查看安全码" width="440px">
    <p class="security-code__hint">
      通过电话、当面或其他你已验证过身份的渠道，和「{{ counterpartName || '对方' }}」核对下面两组安全码是否完全一致。
      一致即可确认双方的加密会话未被冒充或窃听；如果不一致，请立即停止在本会话中发送敏感信息。
    </p>
    <div class="security-code__block">
      <div class="security-code__label">我的安全码</div>
      <div class="security-code__value">{{ myFingerprint || '计算中…' }}</div>
    </div>
    <div class="security-code__block">
      <div class="security-code__label">对方安全码</div>
      <div class="security-code__value">{{ counterpartFingerprint() || '尚未获取到对方公钥' }}</div>
    </div>
    <template #footer>
      <el-button type="primary" @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.security-code__hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.security-code__block {
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
}

.security-code__label {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 4px;
}

.security-code__value {
  font-family: var(--font-mono, monospace);
  font-size: 14px;
  letter-spacing: 0.04em;
  color: var(--color-ink);
  word-break: break-all;
}
</style>
