<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearSession, session } from '@/store/session'

/**
 * 应用外壳：顶部条 + 路由出口。
 * 骨架阶段只做登录态展示与退出，导航菜单待第 9～10 周补全。
 */
const router = useRouter()

const user = computed(() => session.user)
const roleLabel = computed(() => (user.value?.role === 'ADMIN' ? '管理员' : '普通用户'))
const displayName = computed(() => user.value?.realName || user.value?.username || '')

function logout(): void {
  clearSession()
  ElMessage.success('已退出登录')
  void router.push({ name: 'login' })
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div class="app-title">
        智能会议室管理系统 <span class="app-version">V1.0</span>
      </div>
      <div v-if="user" class="app-user">
        <el-tag size="small" type="info">{{ roleLabel }}</el-tag>
        <span class="app-username">{{ displayName }}</span>
        <el-button link type="primary" @click="logout">退出登录</el-button>
      </div>
    </el-header>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>
