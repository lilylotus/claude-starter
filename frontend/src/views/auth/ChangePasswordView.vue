<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as authApi from '@/api/auth'

const router = useRouter()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

function validateConfirmPassword(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await authApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    authStore.setFirstLogin(false)
    ElMessage.success('密码修改成功')
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="change-password">
    <section class="change-password__panel">
      <h1 class="change-password__title">首次登录，请修改密码</h1>
      <p class="change-password__subtitle">为保障账号安全，首次登录或密码被重置后需先完成改密才能继续使用系统</p>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" label-position="top" @keyup.enter="handleSubmit">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" placeholder="请输入原密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" placeholder="请输入新密码" :prefix-icon="Lock" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入新密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-button class="change-password__submit" type="primary" size="large" :loading="submitting" @click="handleSubmit">
          确认修改
        </el-button>
      </el-form>
    </section>
  </div>
</template>

<style scoped lang="scss">
.change-password {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--color-canvas);
  padding: 24px;
}

.change-password__panel {
  width: 100%;
  max-width: 380px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  padding: 32px 28px;
}

.change-password__title {
  font-size: 22px;
  margin: 0 0 8px;
  color: var(--color-ink);
}

.change-password__subtitle {
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 24px;
}

.change-password__submit {
  width: 100%;
  margin-top: 4px;
}
</style>
