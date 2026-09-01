<script setup lang="ts">
// 发起单聊弹窗：远程搜索选择目标用户 + 输入首条消息内容，提交后通过 WebSocket
// CHAT_SINGLE 帧发送，服务端首次发送时自动创建单聊会话（无独立 REST 创建接口，
// 见 backend ConversationServiceImpl#getOrCreateSingleConversation）。
import { ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import * as userApi from '@/api/user'
import { useChatStore } from '@/stores/chat'

const emit = defineEmits<{
  created: [conversationId: number]
}>()

const visible = defineModel<boolean>({ required: true })

const chatStore = useChatStore()

interface UserOption {
  id: number
  name: string
  code: string
}

const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = ref<{ userId: number | null; content: string }>({ userId: null, content: '' })

const rules: FormRules = {
  userId: [{ required: true, message: '请选择聊天对象', trigger: 'change' }],
  content: [{ required: true, message: '请输入首条消息内容', trigger: 'blur' }],
}

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const result = await userApi.getUserPage({ name: query, pageSize: 20 })
    // 当前用户自己不能作为单聊对象（后端也会拒绝"向自己发送单聊消息"），
    // 但前端没有一个可靠的"自己是哪一行"的判断依据（用户搜索结果不携带这个标记），
    // 交由后端兜底校验失败提示，此处不做客户端过滤
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, code: user.code }))
  } finally {
    userSearchLoading.value = false
  }
}

function reset() {
  form.value = { userId: null, content: '' }
  userOptions.value = []
  formRef.value?.clearValidate()
}

function close() {
  visible.value = false
  reset()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || form.value.userId === null) return

  submitting.value = true
  try {
    const conversationId = await chatStore.startNewSingleChat(form.value.userId, form.value.content)
    await chatStore.loadConversations()
    await chatStore.selectConversation(conversationId)
    emit('created', conversationId)
    close()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起单聊" width="480px" @close="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="聊天对象" prop="userId">
        <el-select
          v-model="form.userId"
          filterable
          remote
          reserve-keyword
          placeholder="输入姓名搜索用户"
          :remote-method="remoteSearchUsers"
          :loading="userSearchLoading"
          style="width: 100%"
        >
          <el-option
            v-for="opt in userOptions"
            :key="opt.id"
            :label="`${opt.name}（${opt.code}）`"
            :value="opt.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="首条消息" prop="content">
        <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入要发送的第一句话" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">发送</el-button>
    </template>
  </el-dialog>
</template>
