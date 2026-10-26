<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logout as logoutApi } from '@/api/auth'
import { clearSession, session } from '@/store/session'
import RoleMenu from '@/components/RoleMenu.vue'

/**
 * 应用外壳：顶部条 ＋ 角色菜单 ＋ 路由出口（第 9 周）。
 *
 * <p>第 9 周新增两处：
 * <ol>
 *   <li><b>角色菜单</b>（FR-02）：普通用户看不到管理入口；</li>
 *   <li><b>登出走服务端</b>（NF-04）：不只清前端状态，还要让后端作废令牌——
 *       否则旧令牌在有效期内仍能调接口。</li>
 * </ol>
 * </p>
 */
const router = useRouter()

const user = computed(() => session.user)
const roleLabel = computed(() => (user.value?.role === 'ADMIN' ? '管理员' : '普通用户'))
const displayName = computed(() => user.value?.realName || user.value?.username || '')

async function logout(): Promise<void> {
  try {
    // ★ 先让服务端作废令牌（NF-04），失败也不影响本地清理
    await logoutApi()
  } catch (error) {
    // 令牌本来就失效时后端也会返回成功；这里兜底防止"网络断了就退不出去"
    console.warn('登出接口调用失败，仍清理本地登录态', error)
  } finally {
    clearSession()
    ElMessage.success('已退出登录')
    void router.push({ name: 'login' })
  }
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div class="app-title">
        智能会议室管理系统 <span class="app-version">V1.0</span>
      </div>
      <RoleMenu v-if="user" />
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
