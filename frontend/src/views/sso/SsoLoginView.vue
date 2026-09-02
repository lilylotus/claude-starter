<script setup lang="ts">
// SSO 专用登录页面：外部应用做 CAS/OAuth2 单点登录时、用户未登录情况下跳转来的
// 登录页，完全独立于管理端登录（不接入 stores/auth.ts / api/request.ts）。
// 登录成功后必须整页跳转（window.location.href）回 redirect 指向的后端原生 URL
// （/api/authn/cas/**、/api/authn/oauth/** 等），不能用 router.push。
//
// 支持三种登录方式（口令/短信/扫码），实际展示哪些由后端按 redirect 反解出的目标应用
// 认证配置决定（GET /authn/sso/login-methods）；仅 PASSWORD 时保持原有无标签页样式，
// 出现 SMS/QRCODE 时用 el-tabs 切换展示。
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ChatDotRound, Iphone, Lock, User } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import QRCode from 'qrcode'
import {
  createSsoQrcodeSession,
  getSsoLoginMethods,
  getSsoPublicKey,
  getSsoQrcodeStatus,
  ssoChangePassword,
  ssoLogin,
  ssoSendSmsCode,
  ssoSmsLogin,
} from '@/api/sso'
import { rsaEncrypt } from '@/utils/rsa'
import type { SsoLoginMethod, SsoQrcodeStatus } from '@/types/sso'

const route = useRoute()
const redirect = computed(() => (route.query.redirect as string) || '')

const formRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const submitting = ref(false)
const changingPassword = ref(false)
const isPasswordChange = ref(route.query.forcePasswordChange === 'true')

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

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

const title = computed(() => (isPasswordChange.value ? '首次登录修改密码' : '单点登录'))
const subtitle = computed(() =>
  isPasswordChange.value ? '为保障账号安全，请修改初始密码后继续' : '登录后将自动跳转回目标应用',
)

function redirectToTarget() {
  // 必须整页跳转：redirect 指向后端原生 URL（如 /api/authn/cas/**），不是 SPA 内部路由
  window.location.href = redirect.value || '/'
}

async function handleSubmit() {
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
    redirectToTarget()
  } catch {
    // 错误提示已由 api/sso.ts 的响应拦截器统一展示，这里不重复弹提示
  } finally {
    submitting.value = false
  }
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
    ElMessage.success('密码修改成功，正在跳转')
    redirectToTarget()
  } catch {
    // 错误提示已由 api/sso.ts 的响应拦截器统一展示，这里不重复弹提示
  } finally {
    changingPassword.value = false
  }
}

// ---- 登录方式：根据 redirect 反解出的目标应用认证配置，决定展示哪些标签页 ----

const loginMethods = ref<SsoLoginMethod[]>(['PASSWORD'])
const activeTab = ref<SsoLoginMethod>('PASSWORD')

async function loadLoginMethods() {
  try {
    const methods = await getSsoLoginMethods(redirect.value || undefined)
    loginMethods.value = methods.length > 0 ? methods : ['PASSWORD']
  } catch {
    // 查询失败时保守退化为仅口令登录，不影响页面可用性
    loginMethods.value = ['PASSWORD']
  }
  activeTab.value = loginMethods.value.includes('PASSWORD') ? 'PASSWORD' : loginMethods.value[0]
}

// ---- 短信验证码登录 ----

const MOBILE_PATTERN = /^1\d{10}$/

const smsFormRef = ref<FormInstance>()
const smsForm = reactive({
  mobile: '',
  code: '',
})
const smsRules: FormRules = {
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: MOBILE_PATTERN, message: '手机号格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
}
const smsSubmitting = ref(false)
const sendingSmsCode = ref(false)
const smsCountdown = ref(0)
let smsCountdownTimer: ReturnType<typeof setInterval> | undefined

function startSmsCountdown() {
  smsCountdown.value = 60
  smsCountdownTimer = setInterval(() => {
    smsCountdown.value -= 1
    if (smsCountdown.value <= 0 && smsCountdownTimer) {
      clearInterval(smsCountdownTimer)
      smsCountdownTimer = undefined
    }
  }, 1000)
}

async function handleSendSmsCode() {
  const valid = await smsFormRef.value?.validateField('mobile').catch(() => false)
  if (!valid) return

  sendingSmsCode.value = true
  try {
    await ssoSendSmsCode(redirect.value, smsForm.mobile)
    ElMessage.success('验证码已发送，请注意查收')
    startSmsCountdown()
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    sendingSmsCode.value = false
  }
}

async function handleSmsSubmit() {
  const valid = await smsFormRef.value?.validate().catch(() => false)
  if (!valid) return

  smsSubmitting.value = true
  try {
    const result = await ssoSmsLogin(redirect.value, smsForm.mobile, smsForm.code)
    if (result.firstLogin) {
      isPasswordChange.value = true
      return
    }
    redirectToTarget()
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    smsSubmitting.value = false
  }
}

// ---- 扫码登录：进入标签页时创建会话并渲染二维码，随后每 2 秒轮询状态 ----

const qrcodeDataUrl = ref('')
const qrcodeStatus = ref<SsoQrcodeStatus | 'INIT'>('INIT')
const qrcodeLoading = ref(false)
let qrcodeToken = ''
let qrcodePollTimer: ReturnType<typeof setInterval> | undefined

const qrcodeStatusText = computed(() => {
  switch (qrcodeStatus.value) {
    case 'PENDING':
      return '请使用手机浏览器扫描二维码登录'
    case 'SCANNED':
      return '已扫码，请在手机上确认登录'
    case 'EXPIRED':
      return '二维码已过期，请刷新后重试'
    case 'CONFIRMED':
      return '登录成功，正在跳转'
    default:
      return ''
  }
})

function stopQrcodePolling() {
  if (qrcodePollTimer) {
    clearInterval(qrcodePollTimer)
    qrcodePollTimer = undefined
  }
}

async function pollQrcodeStatus() {
  if (!qrcodeToken) return
  try {
    const result = await getSsoQrcodeStatus(qrcodeToken)
    qrcodeStatus.value = result.status
    if (result.status === 'CONFIRMED') {
      stopQrcodePolling()
      if (result.firstLogin) {
        isPasswordChange.value = true
        return
      }
      redirectToTarget()
    } else if (result.status === 'EXPIRED') {
      stopQrcodePolling()
    }
  } catch {
    // 轮询请求失败（网络抖动）不打断，等待下一次轮询重试
  }
}

async function initQrcodeSession() {
  stopQrcodePolling()
  qrcodeLoading.value = true
  qrcodeStatus.value = 'INIT'
  try {
    const session = await createSsoQrcodeSession(redirect.value)
    qrcodeToken = session.token
    const confirmUrl = window.location.origin + session.confirmPath
    qrcodeDataUrl.value = await QRCode.toDataURL(confirmUrl, { width: 220, margin: 1 })
    qrcodeStatus.value = 'PENDING'
    qrcodePollTimer = setInterval(pollQrcodeStatus, 2000)
  } catch {
    // 错误提示已由响应拦截器统一展示
  } finally {
    qrcodeLoading.value = false
  }
}

function handleRefreshQrcode() {
  initQrcodeSession()
}

function handleTabChange(name: string | number) {
  if (name === 'QRCODE') {
    initQrcodeSession()
  } else {
    stopQrcodePolling()
  }
}

onMounted(() => {
  loadLoginMethods()
})

onBeforeUnmount(() => {
  stopQrcodePolling()
  if (smsCountdownTimer) clearInterval(smsCountdownTimer)
})
</script>

<template>
  <div class="sso-login">
    <section class="sso-login__brand">
      <div class="sso-login__brand-mark">
        <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#ffffff" fill-opacity="0.16" />
          <circle cx="9" cy="10" r="3" fill="#fff" />
          <circle cx="23" cy="10" r="3" fill="#fff" />
          <circle cx="16" cy="22" r="3" fill="#fff" />
          <path d="M9 10 L16 22 L23 10" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round" />
        </svg>
        <span>RBAC 单点登录</span>
      </div>

      <svg class="sso-login__chain" viewBox="0 0 320 420" fill="none">
        <path
          class="sso-login__chain-line"
          d="M60 64 L198 142 L82 238 L218 338"
          stroke="rgba(255,255,255,0.55)"
          stroke-width="2"
          stroke-dasharray="6 8"
          fill="none"
        />
        <g class="sso-login__chain-node" style="--delay: 0s">
          <circle cx="60" cy="64" r="7" fill="#fff" />
          <text x="78" y="68" class="sso-login__chain-label">身份</text>
        </g>
        <g class="sso-login__chain-node" style="--delay: 0.15s">
          <circle cx="198" cy="142" r="7" fill="#fff" />
          <text x="216" y="146" class="sso-login__chain-label">授权</text>
        </g>
        <g class="sso-login__chain-node" style="--delay: 0.3s">
          <circle cx="82" cy="238" r="7" fill="#fff" />
          <text x="100" y="242" class="sso-login__chain-label">凭证</text>
        </g>
        <g class="sso-login__chain-node" style="--delay: 0.45s">
          <circle cx="218" cy="338" r="7" fill="#fff" />
          <text x="236" y="342" class="sso-login__chain-label">应用</text>
        </g>
      </svg>

      <p class="sso-login__tagline">一次登录，安全接入所有已授权的第三方应用。</p>
    </section>

    <section class="sso-login__panel">
      <div class="sso-login__form-wrap">
        <h1 class="sso-login__title">{{ title }}</h1>
        <p class="sso-login__subtitle">{{ subtitle }}</p>

        <el-form
          v-if="isPasswordChange"
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
            class="sso-login__submit"
            type="primary"
            size="large"
            :loading="changingPassword"
            @click="handlePasswordChange"
          >
            修改密码并继续
          </el-button>
        </el-form>

        <template v-else>
          <!-- 仅允许口令登录时，保持原有无标签页样式 -->
          <el-form
            v-if="loginMethods.length <= 1"
            ref="formRef"
            :model="form"
            :rules="rules"
            size="large"
            @keyup.enter="handleSubmit"
          >
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
              class="sso-login__submit"
              type="primary"
              size="large"
              :loading="submitting"
              @click="handleSubmit"
            >
              登录
            </el-button>
          </el-form>

          <el-tabs v-else v-model="activeTab" class="sso-login__tabs" @tab-change="handleTabChange">
            <el-tab-pane v-if="loginMethods.includes('PASSWORD')" label="口令登录" name="PASSWORD">
              <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleSubmit">
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
                  class="sso-login__submit"
                  type="primary"
                  size="large"
                  :loading="submitting"
                  @click="handleSubmit"
                >
                  登录
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane v-if="loginMethods.includes('SMS')" label="短信登录" name="SMS">
              <el-form ref="smsFormRef" :model="smsForm" :rules="smsRules" size="large" @keyup.enter="handleSmsSubmit">
                <el-form-item prop="mobile">
                  <el-input v-model="smsForm.mobile" placeholder="手机号" :prefix-icon="Iphone" maxlength="11" />
                </el-form-item>
                <el-form-item prop="code">
                  <div class="sso-login__sms-code-row">
                    <el-input v-model="smsForm.code" placeholder="验证码" :prefix-icon="ChatDotRound" />
                    <el-button
                      class="sso-login__sms-code-btn"
                      :disabled="smsCountdown > 0"
                      :loading="sendingSmsCode"
                      @click="handleSendSmsCode"
                    >
                      {{ smsCountdown > 0 ? `${smsCountdown}秒后重试` : '获取验证码' }}
                    </el-button>
                  </div>
                </el-form-item>
                <el-button
                  class="sso-login__submit"
                  type="primary"
                  size="large"
                  :loading="smsSubmitting"
                  @click="handleSmsSubmit"
                >
                  登录
                </el-button>
              </el-form>
            </el-tab-pane>

            <el-tab-pane v-if="loginMethods.includes('QRCODE')" label="扫码登录" name="QRCODE">
              <div v-loading="qrcodeLoading" class="sso-login__qrcode">
                <img
                  v-if="qrcodeDataUrl && qrcodeStatus !== 'EXPIRED'"
                  :src="qrcodeDataUrl"
                  class="sso-login__qrcode-img"
                  alt="扫码登录二维码"
                />
                <div v-else class="sso-login__qrcode-placeholder" />
                <p class="sso-login__qrcode-status">{{ qrcodeStatusText }}</p>
                <el-button v-if="qrcodeStatus === 'EXPIRED'" type="primary" @click="handleRefreshQrcode">
                  刷新二维码
                </el-button>
              </div>
            </el-tab-pane>
          </el-tabs>
        </template>

        <p v-if="isPasswordChange" class="sso-login__hint">修改成功后将继续原 CAS/OAuth2.0 登录流程</p>
        <p v-else-if="loginMethods.length <= 1 || activeTab === 'PASSWORD'" class="sso-login__hint">
          用户名为分配的用户编码，密码为初始密码或管理员重置后的默认密码
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.sso-login {
  display: flex;
  min-height: 100vh;
  background: var(--color-canvas);
}

.sso-login__brand {
  position: relative;
  flex: 1.1;
  display: none;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px 48px;
  background: linear-gradient(155deg, var(--color-primary) 0%, var(--color-primary-strong) 100%);
  overflow: hidden;

  @media (min-width: 900px) {
    display: flex;
  }
}

.sso-login__brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 0.02em;
}

.sso-login__chain {
  width: 100%;
  max-width: 320px;
  align-self: center;
  margin: 24px 0;
}

.sso-login__chain-line {
  stroke-dasharray: 720;
  stroke-dashoffset: 720;
  animation: draw-chain 1.6s ease-out 0.2s forwards;
}

.sso-login__chain-node {
  opacity: 0;
  transform-origin: center;
  animation: pop-node 0.5s ease-out forwards;
  animation-delay: calc(0.6s + var(--delay));
}

.sso-login__chain-label {
  fill: #fff;
  font-size: 15px;
  font-family: var(--font-display);
  font-weight: 500;
}

@keyframes draw-chain {
  to {
    stroke-dashoffset: 0;
  }
}

@keyframes pop-node {
  from {
    opacity: 0;
    transform: scale(0.4);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sso-login__chain-line {
    animation: none;
    stroke-dashoffset: 0;
  }
  .sso-login__chain-node {
    animation: none;
    opacity: 1;
  }
}

.sso-login__tagline {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  line-height: 1.7;
  max-width: 320px;
  margin: 0;
}

.sso-login__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.sso-login__form-wrap {
  width: 100%;
  max-width: 340px;
}

.sso-login__title {
  font-size: 26px;
  margin-bottom: 6px;
}

.sso-login__subtitle {
  color: var(--color-text-secondary);
  margin: 0 0 32px;
}

.sso-login__tabs {
  :deep(.el-tabs__nav) {
    width: 100%;
    display: flex;
  }

  :deep(.el-tabs__item) {
    flex: 1;
    text-align: center;
  }
}

.sso-login__submit {
  width: 100%;
  margin-top: 4px;
}

.sso-login__sms-code-row {
  display: flex;
  gap: 8px;
  width: 100%;

  .el-input {
    flex: 1;
  }
}

.sso-login__sms-code-btn {
  flex-shrink: 0;
  white-space: nowrap;
}

.sso-login__qrcode {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 12px 0 4px;
  min-height: 260px;
}

.sso-login__qrcode-img {
  width: 220px;
  height: 220px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  padding: 8px;
  background: #fff;
}

.sso-login__qrcode-placeholder {
  width: 220px;
  height: 220px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-canvas);
}

.sso-login__qrcode-status {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary);
  text-align: center;
}

.sso-login__hint {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
</style>
