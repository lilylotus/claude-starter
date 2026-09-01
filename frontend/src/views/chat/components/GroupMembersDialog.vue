<script setup lang="ts">
// 群成员管理弹窗：查看成员列表、添加成员、移除成员/退出群聊（tasks.md 6.7）。
// 三个操作合并受 Chat:conversation:manageMember 权限点控制（与后端
// V15__create_chat_tables.sql 权限点登记的"群成员管理"备注一致）；移除他人成员
// 额外要求当前用户是该群群主（后端 ConversationServiceImpl#removeMember 同样校验），
// 前端仅做按钮可见性层面的提前拦截，真正的权限判断以后端为准。
import { computed, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import * as userApi from '@/api/user'
import { useChatStore } from '@/stores/chat'
import { usePermission } from '@/composables/usePermission'
import { CONVERSATION_MEMBER_ROLE_OWNER, type ConversationMemberVO } from '@/types/chat'

const props = defineProps<{
  conversationId: number | null
}>()

const visible = defineModel<boolean>({ required: true })

const chatStore = useChatStore()
const { hasPermission } = usePermission()
const canManageMember = computed(() => hasPermission('Chat:conversation:manageMember'))

const members = computed<ConversationMemberVO[]>(() =>
  props.conversationId !== null ? (chatStore.membersByConversation[props.conversationId] ?? []) : [],
)

const isOwner = computed(() =>
  members.value.some(
    (member) => member.userId === chatStore.currentUserId && member.role === CONVERSATION_MEMBER_ROLE_OWNER,
  ),
)

watch(
  () => [visible.value, props.conversationId] as const,
  async ([isVisible, conversationId]) => {
    if (isVisible && conversationId !== null) {
      await chatStore.refreshMembers(conversationId)
    }
  },
)

// ---- 添加成员 ----

interface UserOption {
  id: number
  name: string
  code: string
}

const addingVisible = ref(false)
const addingUserIds = ref<number[]>([])
const userOptions = ref<UserOption[]>([])
const userSearchLoading = ref(false)
const addingSubmitting = ref(false)

async function remoteSearchUsers(query: string) {
  if (!query) {
    userOptions.value = []
    return
  }
  userSearchLoading.value = true
  try {
    const result = await userApi.getUserPage({ name: query, pageSize: 20 })
    const existingIds = new Set(members.value.map((member) => member.userId))
    userOptions.value = result.records
      .filter((user) => !existingIds.has(user.id))
      .map((user) => ({ id: user.id, name: user.name, code: user.code }))
  } finally {
    userSearchLoading.value = false
  }
}

function openAddDialog() {
  addingUserIds.value = []
  userOptions.value = []
  addingVisible.value = true
}

async function submitAdd() {
  if (props.conversationId === null || addingUserIds.value.length === 0) return
  addingSubmitting.value = true
  try {
    await chatStore.addMembers(props.conversationId, addingUserIds.value)
    addingVisible.value = false
  } finally {
    addingSubmitting.value = false
  }
}

// ---- 移除成员 / 退出群聊 ----

async function handleRemove(member: ConversationMemberVO) {
  if (props.conversationId === null) return
  const selfLeave = member.userId === chatStore.currentUserId
  await ElMessageBox.confirm(
    selfLeave ? '确定要退出该群聊吗？' : `确定要将「${member.userName}」移出群聊吗？`,
    selfLeave ? '退出确认' : '移除确认',
    { type: 'warning', confirmButtonText: selfLeave ? '退出' : '移除', cancelButtonText: '取消' },
  )
  await chatStore.removeMember(props.conversationId, member.userId)
  if (selfLeave) {
    visible.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="群成员管理" width="520px">
    <div class="group-members__toolbar">
      <el-button v-if="canManageMember" type="primary" size="small" @click="openAddDialog">添加成员</el-button>
    </div>
    <el-table :data="members" empty-text="暂无成员" max-height="360">
      <el-table-column prop="userName" label="成员" min-width="140" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">
          <el-tag v-if="(row as ConversationMemberVO).role === CONVERSATION_MEMBER_ROLE_OWNER" type="warning">
            群主
          </el-tag>
          <el-tag v-else type="info">成员</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="joinedTime" label="加入时间" min-width="160" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="
              canManageMember &&
              ((row as ConversationMemberVO).userId === chatStore.currentUserId ||
                (isOwner && (row as ConversationMemberVO).role !== CONVERSATION_MEMBER_ROLE_OWNER))
            "
            link
            type="danger"
            @click="handleRemove(row as ConversationMemberVO)"
          >
            {{ (row as ConversationMemberVO).userId === chatStore.currentUserId ? '退出' : '移除' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="addingVisible" title="添加成员" width="420px" append-to-body>
    <el-select
      v-model="addingUserIds"
      multiple
      filterable
      remote
      reserve-keyword
      placeholder="输入姓名搜索用户，可多选"
      :remote-method="remoteSearchUsers"
      :loading="userSearchLoading"
      style="width: 100%"
    >
      <el-option v-for="opt in userOptions" :key="opt.id" :label="`${opt.name}（${opt.code}）`" :value="opt.id" />
    </el-select>
    <template #footer>
      <el-button @click="addingVisible = false">取消</el-button>
      <el-button type="primary" :loading="addingSubmitting" @click="submitAdd">添加</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.group-members__toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
</style>
