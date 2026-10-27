<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api/auth'
import { setSession } from '@/store/session'

/**
 * 登录页 —— 对应 UC-U01 用户登录 / FR-01、FR-02。
 *
 * <p>第 9 周变化：登录成功后后端会<strong>签发会话令牌</strong>，
 * 前端把令牌与用户信息存入 localStorage（见 {@code store/session.ts}），
 * 后续请求由请求拦截器自动带上 {@code Authorization: Bearer <token>}。
 * 刷新页面不掉登录（NF-04）。</p>
 */
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

// 预填默认账号（database/02_seed.sql 里的 user1，普通用户）
// ★ 预填普通用户而不是 admin：任务书第 9 周要演示"普通用户看不到管理入口"（FR-02），
//   默认就登管理员的话，演示者容易直接用 admin，点不到角色隔离的效果。
//   演示管理员功能时手动把账号改成 admin 即可。
const form = reactive({
  username: 'user1',
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
        演示账号与口令见仓库根目录 <code>README.md</code> 的「默认账号」一节。
      </p>
    </el-card>
  </div>
</template>
