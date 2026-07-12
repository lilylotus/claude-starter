<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123',
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
    await authStore.login(form)
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch (error) {
    ElMessage.error((error as Error).message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login">
    <section class="login__brand">
      <div class="login__brand-mark">
        <svg width="28" height="28" viewBox="0 0 32 32" fill="none">
          <rect width="32" height="32" rx="8" fill="#ffffff" fill-opacity="0.16" />
          <circle cx="9" cy="10" r="3" fill="#fff" />
          <circle cx="23" cy="10" r="3" fill="#fff" />
          <circle cx="16" cy="22" r="3" fill="#fff" />
          <path d="M9 10 L16 22 L23 10" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round" />
        </svg>
        <span>RBAC 权限管理系统</span>
      </div>

      <svg class="login__chain" viewBox="0 0 320 420" fill="none">
        <path
          class="login__chain-line"
          d="M60 64 L198 142 L82 238 L218 338"
          stroke="rgba(255,255,255,0.55)"
          stroke-width="2"
          stroke-dasharray="6 8"
          fill="none"
        />
        <g class="login__chain-node" style="--delay: 0s">
          <circle cx="60" cy="64" r="7" fill="#fff" />
          <text x="78" y="68" class="login__chain-label">身份</text>
        </g>
        <g class="login__chain-node" style="--delay: 0.15s">
          <circle cx="198" cy="142" r="7" fill="#fff" />
          <text x="216" y="146" class="login__chain-label">角色</text>
        </g>
        <g class="login__chain-node" style="--delay: 0.3s">
          <circle cx="82" cy="238" r="7" fill="#fff" />
          <text x="100" y="242" class="login__chain-label">权限</text>
        </g>
        <g class="login__chain-node" style="--delay: 0.45s">
          <circle cx="218" cy="338" r="7" fill="#fff" />
          <text x="236" y="342" class="login__chain-label">资源</text>
        </g>
      </svg>

      <p class="login__tagline">身份、角色、权限、资源层层关联——让每一次访问都有据可查。</p>
    </section>

    <section class="login__panel">
      <div class="login__form-wrap">
        <h1 class="login__title">欢迎回来</h1>
        <p class="login__subtitle">登录以管理身份、应用与权限</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleSubmit">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
          </el-form-item>
          <el-form-item prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" :prefix-icon="Lock" show-password />
          </el-form-item>
          <el-button
            class="login__submit"
            type="primary"
            size="large"
            :loading="submitting"
            @click="handleSubmit"
          >
            登录
          </el-button>
        </el-form>

        <p class="login__hint">演示账号：admin / admin123</p>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.login {
  display: flex;
  min-height: 100vh;
  background: var(--color-canvas);
}

.login__brand {
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

.login__brand-mark {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #fff;
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 0.02em;
}

.login__chain {
  width: 100%;
  max-width: 320px;
  align-self: center;
  margin: 24px 0;
}

.login__chain-line {
  stroke-dasharray: 720;
  stroke-dashoffset: 720;
  animation: draw-chain 1.6s ease-out 0.2s forwards;
}

.login__chain-node {
  opacity: 0;
  transform-origin: center;
  animation: pop-node 0.5s ease-out forwards;
  animation-delay: calc(0.6s + var(--delay));
}

.login__chain-label {
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
  .login__chain-line {
    animation: none;
    stroke-dashoffset: 0;
  }
  .login__chain-node {
    animation: none;
    opacity: 1;
  }
}

.login__tagline {
  color: rgba(255, 255, 255, 0.85);
  font-size: 14px;
  line-height: 1.7;
  max-width: 320px;
  margin: 0;
}

.login__panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.login__form-wrap {
  width: 100%;
  max-width: 340px;
}

.login__title {
  font-size: 26px;
  margin-bottom: 6px;
}

.login__subtitle {
  color: var(--color-text-secondary);
  margin: 0 0 32px;
}

.login__submit {
  width: 100%;
  margin-top: 4px;
}

.login__hint {
  margin-top: 20px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-tertiary);
}
</style>
