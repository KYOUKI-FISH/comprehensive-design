<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/auth'
import { setSession } from '@/store/session'

/**
 * 登录页 —— 对应 UC-U01 用户登录 / FR-01、FR-02。
 *
 * 骨架阶段目标：证明"前端能起、跨域配对、后端能连库、账号密码能校验"。
 * 登录成功后不签发令牌（后端本周不发），登录态仅存内存。
 */
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 预填默认账号（种子数据里的 student01），省掉实测时的手工输入
const form = reactive({
  username: 'student01',
  password: '123456'
})

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    const user = await login({ username: form.username, password: form.password })
    setSession(user)
    ElMessage.success(`登录成功，欢迎 ${user.realName || user.username}`)

    const redirect = route.query.redirect
    await router.push(typeof redirect === 'string' && redirect ? redirect : { name: 'rooms' })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="never">
      <h2 class="login-title">智能会议室管理系统</h2>
      <p class="login-subtitle">V1.0 基础预约与管理平台 · 第 4 组</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" clearable />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            class="login-submit"
            type="primary"
            :loading="loading"
            native-type="submit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-tip">
        默认账号：<code>student01 / 123456</code>（普通用户）、<code>admin01 / 123456</code>（管理员）
      </p>
    </el-card>
  </div>
</template>
