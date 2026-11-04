<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listOperationLogs, listUsers } from '@/api/admin'
import type { OperationLogVO, UserVO } from '@/types/api'

/**
 * 操作日志（管理员，第 10 周；对应 NF-14 可审计性）。
 *
 * <p>★ 这是管理员四步的<b>第 4 步</b>：强制取消完一条预约，要能在这里查到那条记录，
 * 且字段完整——<b>操作人账号／操作时间／操作类型／变更前后数据</b>四样都在（NF-14 的验证点）。</p>
 *
 * <p>★ 本页<b>只有查询，没有删除</b>：NF-14 要求日志"不能被业务界面删除"。
 * 后端 {@code AdminLogController} 也只有 GET——这不是"暂未实现"，
 * 是设计上就不该有：加一个删除接口，审计链就失去了可追溯的意义。</p>
 *
 * <p>★ 普通用户访问本页：后端返回 {@code code=403}「无权访问该功能」，
 * 页面把它显示成中文提示而不是白屏（NF-03 ＋ NF-10）。</p>
 */
const loading = ref(false)
const rows = ref<OperationLogVO[]>([])
const total = ref(0)
const operators = ref<UserVO[]>([])

/** 9 种敏感操作（与 database/01_schema.sql 的 ck_log_operation_type 一致） */
const OPERATION_TYPES: { value: string; label: string }[] = [
  { value: 'USER_CREATE', label: '新建账号' },
  { value: 'USER_DISABLE', label: '停用账号' },
  { value: 'USER_ENABLE', label: '启用账号' },
  { value: 'ROLE_CHANGE', label: '角色变更' },
  { value: 'ROOM_CREATE', label: '新增会议室' },
  { value: 'ROOM_UPDATE', label: '修改会议室' },
  { value: 'ROOM_STATUS', label: '会议室启停' },
  { value: 'ROOM_DELETE', label: '删除会议室' },
  { value: 'BOOKING_FORCE_CANCEL', label: '强制取消预约' }
]

const TARGET_TYPES: { value: string; label: string }[] = [
  { value: 'USER', label: '用户' },
  { value: 'ROOM', label: '会议室' },
  { value: 'BOOKING', label: '预约' }
]

function typeLabel(value: string): string {
  return OPERATION_TYPES.find((t) => t.value === value)?.label ?? value
}

function typeTagType(value: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'BOOKING_FORCE_CANCEL' || value === 'ROOM_DELETE' || value === 'USER_DISABLE') {
    return 'danger'
  }
  if (value.startsWith('ROOM')) {
    return 'warning'
  }
  if (value.startsWith('USER') || value === 'ROLE_CHANGE') {
    return 'primary'
  }
  return 'info'
}

const query = reactive<{
  operatorId: number | undefined
  operationType: string | undefined
  targetType: string | undefined
  dateRange: [string, string] | null
  pageNum: number
  pageSize: number
}>({
  operatorId: undefined,
  operationType: undefined,
  targetType: undefined,
  dateRange: null,
  pageNum: 1,
  pageSize: 10
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listOperationLogs({
      operatorId: query.operatorId,
      operationType: query.operationType,
      targetType: query.targetType,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = page.list ?? []
    total.value = page.total ?? 0
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询操作日志失败')
  } finally {
    loading.value = false
  }
}

async function loadOperators(): Promise<void> {
  try {
    // 只用来做"操作人"下拉；取全部再本地筛管理员没有意义，直接全取
    operators.value = await listUsers({})
  } catch {
    operators.value = []
  }
}

function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.operatorId = undefined
  query.operationType = undefined
  query.targetType = undefined
  query.dateRange = null
  query.pageNum = 1
  void load()
}

function onPageChange(page: number): void {
  query.pageNum = page
  void load()
}

/** JSON 串美化展示；解析不了就原样显示（不能因为格式坏就整行空白） */
function prettyJson(raw: string | null): string {
  if (!raw) {
    return '—'
  }
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

onMounted(async () => {
  await Promise.all([load(), loadOperators()])
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">操作日志</h2>
      <span class="page-hint">对应 NF-14 可审计性：管理员敏感操作全部留痕，只可查询、不可删除</span>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="操作类型">
          <el-select v-model="query.operationType" clearable placeholder="全部类型" style="width: 160px">
            <el-option v-for="t in OPERATION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="对象类型">
          <el-select v-model="query.targetType" clearable placeholder="全部对象" style="width: 130px">
            <el-option v-for="t in TARGET_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作人">
          <el-select v-model="query.operatorId" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="u in operators" :key="u.id" :label="u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="操作日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe row-key="id" empty-text="暂无操作日志">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="log-detail">
              <div class="log-detail-block">
                <div class="log-detail-title">变更前（beforeData）</div>
                <pre class="log-json">{{ prettyJson(row.beforeData) }}</pre>
              </div>
              <div class="log-detail-block">
                <div class="log-detail-title">变更后（afterData）</div>
                <pre class="log-json">{{ prettyJson(row.afterData) }}</pre>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column label="操作时间" width="170">
          <template #default="{ row }">{{ row.createdAt }}</template>
        </el-table-column>
        <el-table-column label="操作人" width="150">
          <template #default="{ row }">
            <div>{{ row.operatorName || row.operatorUsername || '—' }}</div>
            <div class="muted">{{ row.operatorUsername }} / {{ row.operatorRole }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作类型" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagType(row.operationType)" disable-transitions>{{ typeLabel(row.operationType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="对象" width="130">
          <template #default="{ row }">{{ row.targetType }} #{{ row.targetId ?? '—' }}</template>
        </el-table-column>
        <el-table-column prop="detail" label="变更摘要" min-width="260" show-overflow-tooltip />
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="onPageChange"
      />

      <el-alert type="success" :closable="false" show-icon class="page-tip">
        <template #title>
          点行首箭头可展开<b>变更前后的完整数据</b>（JSON）。NF-14 要求日志字段完整：
          操作人账号／操作时间／操作类型／变更前后数据，四样都要在。
        </template>
      </el-alert>
    </el-card>
  </div>
</template>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.muted {
  color: #b1b3b8;
  font-size: 12px;
}

.log-detail {
  display: flex;
  gap: 16px;
  padding: 8px 16px;
}

.log-detail-block {
  flex: 1;
  min-width: 0;
}

.log-detail-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.log-json {
  margin: 0;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
}

.page-tip {
  margin-top: 12px;
}
</style>
