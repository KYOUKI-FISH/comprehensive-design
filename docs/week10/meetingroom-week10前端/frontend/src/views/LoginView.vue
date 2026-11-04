<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '@/api/auth'
import { isLoggedIn, saveSession } from '@/session/session'
import { toUserMessage } from '@/utils/errors'

const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref(
  route.query.reason === 'expired' ? '登录已过期，请重新登录。' : ''
)

function safeRedirect(): string {
  const value = typeof route.query.redirect === 'string' ? route.query.redirect : '/rooms'
  return value.startsWith('/') && !value.startsWith('//') ? value : '/rooms'
}

onMounted(() => {
  if (isLoggedIn.value) router.replace(safeRedirect())
})

async function handleSubmit() {
  if (submitting.value) return
  errorMessage.value = ''

  if (!username.value.trim() || !password.value) {
    errorMessage.value = '请输入账号和密码。'
    return
  }

  submitting.value = true
  try {
    const result = await login({
      username: username.value.trim(),
      password: password.value
    })
    saveSession(result)
    await router.replace(safeRedirect())
  } catch (error) {
    errorMessage.value = toUserMessage(error, '登录失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="login-page">
    <div class="login-card">
      <div class="login-brand">
        <span class="login-brand__mark" aria-hidden="true">MR</span>
        <div>
          <h1 class="login-title">智能会议室管理系统</h1>
          <p class="login-subtitle">V1.0 · 账号登录</p>
        </div>
      </div>

      <p v-if="errorMessage" class="alert alert--error" role="alert">
        {{ errorMessage }}
      </p>

      <form class="form" novalidate @submit.prevent="handleSubmit">
        <div class="field">
          <label class="field-label" for="login-username">账号</label>
          <input
            id="login-username"
            v-model="username"
            class="input"
            type="text"
            name="username"
            autocomplete="username"
            placeholder="请输入账号"
            :disabled="submitting"
          />
        </div>

        <div class="field">
          <label class="field-label" for="login-password">密码</label>
          <input
            id="login-password"
            v-model="password"
            class="input"
            type="password"
            name="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="submitting"
          />
        </div>

        <el-button
          class="btn btn--primary btn--block"
          native-type="submit"
          :disabled="submitting"
        >
          {{ submitting ? '登录中…' : '登录' }}
        </el-button>
      </form>

      <p class="login-footer">登录成功后，系统会按账号角色展示对应菜单。</p>
    </div>
  </section>
</template>
