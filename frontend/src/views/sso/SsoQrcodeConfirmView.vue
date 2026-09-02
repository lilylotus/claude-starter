<script setup lang="ts">
// 扫码登录确认页：手机浏览器扫描 PC 端登录页"扫码登录"标签页展示的二维码后打开的独立
// 响应式页面，完全独立于管理端登录，不接入 stores/auth.ts / api/request.ts（与
// SsoLoginView.vue 同属一套 SSO 专用最小 axios 实例 api/sso.ts）。
//
// 页面本身不做轮询、不感知 PC 端状态：onMounted 时标记"已扫码"（尽力而为，失败静默
// 忽略）并查询当前浏览器是否已有有效 SSO 会话；未登录时展示与 PC 端口令登录一致的表单，
// 登录成功后留在当前页转入"已登录"状态，不整页跳转；已登录时展示"确认登录"按钮，点击
// 后调用确认接口，PC 端下一次轮询会拿到登录结果。
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { CircleCheckFilled, Lock, User, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  confirmSsoQrcodeLogin,
  getSsoPublicKey,
  getSsoSessionStatus,
  markSsoQrcodeScanned,
  ssoChangePassword,
  ssoLogin,
} from '@/api/sso'
import { rsaEncrypt } from '@/utils/rsa'

const route = useRoute()
const token = computed(() => (route.query.token as string) || '')

// 初始化态：加载中 / 令牌缺失（不需要请求任何接口，直接展示失效提示）
const initializing = ref(true)
const tokenMissing = ref(false)

// 是否已具备有效 SSO 会话（口令登录成功、或本来就已登录）
const authenticated = ref(false)
// 首次登录强制改密：口令登录返回 firstLogin=true 时进入该态，改密成功后转为已登录
const isPasswordChange = ref(false)
// 是否已点击"确认登录"并成功
const confirmed = ref(false)

// ---- 口令登录表单：与 SsoLoginView.vue 口令登录 Tab 是同一套接口，登录成功后不整页跳转 ----

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const { publicKey } = await getSsoPublicKey()
    const [account, password] = await Promise.all([
      rsaEncrypt(publicKey, form.username),
      rsaEncrypt(publicKey, form.password),
    ])
    const result = await ssoLogin(account, password)
    if (result.firstLogin) {
      passwordForm.oldPassword = form.password
      form.password = ''
      isPasswordChange.value = true
      return
    }
    authenticated.value = true
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    submitting.value = false
  }
}

// ---- 首登强制改密：与 SsoLoginView.vue 改密表单一致，成功后转入已登录状态 ----

const passwordFormRef = ref<FormInstance>()
const changingPassword = ref(false)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '新密码长度需在 6-64 个字符之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

async function handlePasswordChange() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changingPassword.value = true
  try {
    await ssoChangePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    ElMessage.success('密码修改成功')
    isPasswordChange.value = false
    authenticated.value = true
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    changingPassword.value = false
  }
}

// ---- 确认登录 ----

const confirming = ref(false)

async function handleConfirm() {
  if (!token.value) return
  confirming.value = true
  try {
    await confirmSsoQrcodeLogin(token.value)
    confirmed.value = true
    ElMessage.success('已确认，请返回电脑端查看')
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    confirming.value = false
  }
}

onMounted(async () => {
  if (!token.value) {
    tokenMissing.value = true
    initializing.value = false
    return
  }
  // 标记已扫码：尽力而为，接口设计上本就"静默忽略失败"，不阻塞后续查询登录态
  markSsoQrcodeScanned(token.value).catch(() => {})
  try {
    const status = await getSsoSessionStatus()
    authenticated.value = status.authenticated
  } catch {
    authenticated.value = false
  } finally {
    initializing.value = false
  }
})
</script>

<template>
  <div class="qrcode-confirm">
    <section class="qrcode-confirm__card" v-loading="initializing">
      <div class="qrcode-confirm__brand">
        <svg width="24" height="24" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="var(--color-primary)" fill-opacity="0.12" />
          <circle cx="9" cy="10" r="3" fill="var(--color-primary)" />
          <circle cx="23" cy="10" r="3" fill="var(--color-primary)" />
          <circle cx="16" cy="22" r="3" fill="var(--color-primary)" />
          <path
            d="M9 10 L16 22 L23 10"
            stroke="var(--color-primary)"
            stroke-width="2"
            fill="none"
            stroke-linecap="round"
          />
        </svg>
        <span>RBAC 单点登录</span>
      </div>

      <template v-if="!initializing">
        <div v-if="tokenMissing" class="qrcode-confirm__state">
          <el-icon class="qrcode-confirm__state-icon"><WarningFilled /></el-icon>
          <p class="qrcode-confirm__state-text">二维码已失效，请重新扫码</p>
        </div>

        <template v-else>
          <!-- 已确认：完成态 -->
          <div v-if="confirmed" class="qrcode-confirm__state">
            <el-icon class="qrcode-confirm__state-icon qrcode-confirm__state-icon--success"><CircleCheckFilled /></el-icon>
            <p class="qrcode-confirm__state-text">已确认，请返回电脑端查看</p>
          </div>

          <!-- 首登强制改密 -->
          <template v-else-if="isPasswordChange">
            <h1 class="qrcode-confirm__title">首次登录修改密码</h1>
            <p class="qrcode-confirm__subtitle">为保障账号安全，请修改初始密码后继续</p>
            <el-form
              ref="passwordFormRef"
              :model="passwordForm"
              :rules="passwordRules"
              size="large"
              @keyup.enter="handlePasswordChange"
            >
              <el-form-item prop="oldPassword">
                <el-input
                  v-model="passwordForm.oldPassword"
                  type="password"
                  placeholder="原密码"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="newPassword">
                <el-input
                  v-model="passwordForm.newPassword"
                  type="password"
                  placeholder="新密码（6-64 个字符）"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-form-item prop="confirmPassword">
                <el-input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  placeholder="确认新密码"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-button
                class="qrcode-confirm__submit"
                type="primary"
                size="large"
                :loading="changingPassword"
                @click="handlePasswordChange"
              >
                修改密码并继续
              </el-button>
            </el-form>
          </template>

          <!-- 已登录：展示确认登录按钮 -->
          <template v-else-if="authenticated">
            <h1 class="qrcode-confirm__title">确认登录</h1>
            <p class="qrcode-confirm__subtitle">是否确认在电脑端登录？</p>
            <el-button
              class="qrcode-confirm__submit"
              type="primary"
              size="large"
              :loading="confirming"
              :disabled="confirmed"
              @click="handleConfirm"
            >
              确认登录
            </el-button>
          </template>

          <!-- 未登录：展示与 PC 端一致的口令登录表单 -->
          <template v-else>
            <h1 class="qrcode-confirm__title">登录后确认</h1>
            <p class="qrcode-confirm__subtitle">请先登录，登录后可确认在电脑端完成登录</p>
            <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
              <el-form-item prop="username">
                <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
              </el-form-item>
              <el-form-item prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="密码"
                  :prefix-icon="Lock"
                  show-password
                />
              </el-form-item>
              <el-button
                class="qrcode-confirm__submit"
                type="primary"
                size="large"
                :loading="submitting"
                @click="handleLogin"
              >
                登录
              </el-button>
            </el-form>
          </template>
        </template>
      </template>
    </section>
  </div>
</template>

<style scoped lang="scss">
.qrcode-confirm {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--color-canvas);
}

.qrcode-confirm__card {
  width: 100%;
  max-width: 360px;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  padding: 28px 24px;
}

.qrcode-confirm__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-primary);
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 24px;
}

.qrcode-confirm__title {
  font-size: 20px;
  margin: 0 0 6px;
}

.qrcode-confirm__subtitle {
  color: var(--color-text-secondary);
  font-size: 13px;
  margin: 0 0 24px;
}

.qrcode-confirm__submit {
  width: 100%;
  margin-top: 4px;
}

.qrcode-confirm__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 24px 0;
  text-align: center;
}

.qrcode-confirm__state-icon {
  font-size: 40px;
  color: var(--color-text-tertiary);
}

.qrcode-confirm__state-icon--success {
  color: var(--color-success, #67c23a);
}

.qrcode-confirm__state-text {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}
</style>
