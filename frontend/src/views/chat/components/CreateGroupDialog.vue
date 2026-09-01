<script setup lang="ts">
// 创建群聊弹窗：填写群名称 + 远程搜索多选初始成员，提交后调用 POST
// /api/chat/conversations/group；当前登录用户自动成为群主并计入初始成员，
// 不需要在 memberUserIds 里重复携带自己。
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

const form = ref<{ name: string; memberUserIds: number[] }>({ name: '', memberUserIds: [] })

const rules: FormRules = {
  name: [{ required: true, message: '请输入群聊名称', trigger: 'blur' }],
  memberUserIds: [{ required: true, type: 'array', min: 1, message: '请至少选择一名初始成员', trigger: 'change' }],
}

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const result = await userApi.getUserPage({ name: query, pageSize: 20 })
    userOptions.value = result.records.map((user) => ({ id: user.id, name: user.name, code: user.code }))
  } finally {
    userSearchLoading.value = false
  }
}

function reset() {
  form.value = { name: '', memberUserIds: [] }
  userOptions.value = []
  formRef.value?.clearValidate()
}

function close() {
  visible.value = false
  reset()
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const created = await chatStore.createGroup(form.value.name, form.value.memberUserIds)
    await chatStore.selectConversation(created.id)
    emit('created', created.id)
    close()
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起群聊" width="480px" @close="reset">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="群聊名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入群聊名称" maxlength="128" />
      </el-form-item>
      <el-form-item label="初始成员" prop="memberUserIds">
        <el-select
          v-model="form.memberUserIds"
          multiple
          filterable
          remote
          reserve-keyword
          placeholder="输入姓名搜索用户，可多选"
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
    </el-form>
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>
