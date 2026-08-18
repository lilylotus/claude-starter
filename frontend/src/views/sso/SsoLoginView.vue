<script setup lang="ts">
// SSO 专用登录页面：外部应用做 CAS/OAuth2 单点登录时、用户未登录情况下跳转来的
// 登录页，完全独立于管理端登录（不接入 stores/auth.ts / api/request.ts）。
// 登录成功后必须整页跳转（window.location.href）回 redirect 指向的后端原生 URL
// （/api/authn/cas/**、/api/authn/oauth/** 等），不能用 router.push。
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { getSsoPublicKey, ssoLogin } from '@/api/sso'
import { rsaEncrypt } from '@/utils/rsa'

const route = useRoute()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
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
    await ssoLogin(account, password)
    // 必须整页跳转：redirect 指向后端原生 URL（如 /api/authn/cas/**），不是 SPA 内部路由
    window.location.href = (route.query.redirect as string) || '/'
  } catch {
    // 错误提示已由 api/sso.ts 的响应拦截器统一展示，这里不重复弹提示
  } finally {
    submitting.value = false
  }
}
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
        <h1 class="sso-login__title">单点登录</h1>
        <p class="sso-login__subtitle">登录后将自动跳转回目标应用</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
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

        <p class="sso-login__hint">用户名为分配的用户编码，密码为初始密码或管理员重置后的默认密码</p>
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

.sso-login__submit {
  width: 100%;
  margin-top: 4px;
}

.sso-login__hint {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
</style>
