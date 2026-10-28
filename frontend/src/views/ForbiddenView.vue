<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session } from '@/store/session'

/**
 * 无权访问页（403）。
 *
 * <p>由路由守卫在角色不匹配时跳转过来（FR-02）：
 * 普通用户访问管理员功能、管理员访问「提交预约」都会落到这里。</p>
 *
 * <p>★ 这一页只是给人看的说明，<strong>不是安全边界</strong>。
 * 真正的拦截在后端 {@code AuthInterceptor}——绕过前端直接调接口，
 * 后端必须同样返回 403（NF-03）。</p>
 */
const route = useRoute()
const router = useRouter()

const roleLabel = computed(() => (session.user?.role === 'ADMIN' ? '管理员' : '普通用户'))

/** 按来源给出具体原因，避免只甩一句"无权访问"让人不知道怎么处理 */
const reason = computed(() => {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  if (from.startsWith('/bookings/new')) {
    return '管理员账号不参与个人预约，提交预约功能仅对普通用户开放。如需处理预约，请使用「全部预约」。'
  }
  if (from.startsWith('/admin')) {
    return '普通用户不能进入管理功能。管理入口仅对管理员账号开放。'
  }
  return '当前账号的角色无权访问该页面。如需调整权限，请联系管理员。'
})
</script>

<template>
  <div class="page-narrow">
    <el-result icon="warning" title="403 无权访问" :sub-title="`当前身份：${roleLabel}`">
      <template #extra>
        <p class="forbidden-reason">{{ reason }}</p>
        <p class="page-hint">
          前端菜单隐藏只是使用体验的一部分，后端接口仍会按角色校验并拒绝越权请求（NF-03）。
        </p>
        <div class="forbidden-actions">
          <el-button type="primary" @click="router.push({ name: 'rooms' })">
            返回会议室列表
          </el-button>
          <el-button @click="router.back()">返回上一页</el-button>
        </div>
      </template>
    </el-result>
  </div>
</template>

<style scoped>
.page-narrow {
  max-width: 720px;
  margin: 0 auto;
  padding: 24px 16px;
}

.forbidden-reason {
  margin: 0 0 8px;
  font-size: 14px;
  color: #606266;
}

.forbidden-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-top: 16px;
}
</style>
