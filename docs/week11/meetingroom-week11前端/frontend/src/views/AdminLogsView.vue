<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listOperationLogs } from '@/api/admin'
import type { OperationLogVO } from '@/types/api'
import { formatDateTime } from '@/utils/datetime'
import { toUserMessage } from '@/utils/errors'
import { operationTypeLabel, targetTypeLabel } from '@/utils/labels'

const logs = ref<OperationLogVO[]>([])
const loading = ref(true)
const loadError = ref('')

function pretty(value: unknown): string {
  if (value === null || value === undefined) return '—'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

async function loadLogs() {
  loading.value = true
  loadError.value = ''

  try {
    logs.value = await listOperationLogs()
  } catch (error) {
    logs.value = []
    loadError.value = toUserMessage(error, '操作日志加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">V1.0 · 管理端</p>
        <h1 class="page-title">操作日志</h1>
        <p class="page-subtitle">
          记录管理员的新增/停用用户、角色变更、会议室增删改、强制取消预约。日志只读，普通用户不可查看（NF-14）。
        </p>
      </div>
      <el-button class="btn btn--ghost" native-type="button" :disabled="loading" @click="loadLogs">
        刷新
      </el-button>
    </header>

    <div v-if="loadError" class="alert alert--error" role="alert">
      <div>{{ loadError }}</div>
      <el-button class="btn btn--ghost btn--small" native-type="button" @click="loadLogs">
        重试
      </el-button>
    </div>

    <div class="card table-card">
      <div class="table-card__head">
        <span>操作记录</span>
        <span class="muted">{{ loading ? '加载中…' : `共 ${logs.length} 条` }}</span>
      </div>

      <div v-if="loading" class="loading">正在加载操作日志…</div>

      <div v-else-if="logs.length === 0" class="empty-panel">
        <p>暂无操作日志。</p>
        <p class="muted">新增用户、修改会议室或强制取消预约后，这里会出现记录。</p>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">管理员操作日志</caption>
          <thead>
            <tr>
              <th>操作时间</th>
              <th>操作人</th>
              <th>类型</th>
              <th>对象</th>
              <th>摘要</th>
              <th>变更前后</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td class="nowrap">{{ formatDateTime(log.createdAt) }}</td>
              <td>
                {{ log.operatorName }}
                <p class="muted">{{ log.operatorUsername }}</p>
              </td>
              <td>{{ operationTypeLabel(log.operationType) }}</td>
              <td>
                {{ targetTypeLabel(log.targetType) }}
                <span v-if="log.targetId !== null" class="muted">#{{ log.targetId }}</span>
              </td>
              <td>{{ log.summary }}</td>
              <td>
                <details class="log-details">
                  <summary>查看</summary>
                  <div class="log-details__body">
                    <p class="log-details__label">变更前</p>
                    <pre class="log-json">{{ pretty(log.beforeData) }}</pre>
                    <p class="log-details__label">变更后</p>
                    <pre class="log-json">{{ pretty(log.afterData) }}</pre>
                  </div>
                </details>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
