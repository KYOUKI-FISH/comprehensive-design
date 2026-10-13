<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listRooms } from '@/api/room'
import type { RoomVO } from '@/types/api'

/**
 * 会议室查询页 —— 对应 UC-U02 / FR-05 按条件查询、FR-06 只显示启用中的会议室。
 *
 * 骨架阶段边界：展示会议室基本信息与指定日期的**已占用时段**；
 * 不提供"去预约"入口，也不做替代时段推荐 —— 预约提交、冲突检测属第 9～10 周。
 */
const loading = ref(false)
const rooms = ref<RoomVO[]>([])

/** 取本机当天日期，格式 yyyy-MM-dd（后端 date 参数按 ISO 日期解析） */
function today(): string {
  const d = new Date()
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const query = reactive<{ location: string; minCapacity: number | undefined; date: string }>({
  location: '',
  minCapacity: undefined,
  date: today()
})

async function load(): Promise<void> {
  loading.value = true
  try {
    rooms.value = await listRooms({
      location: query.location.trim() || undefined,
      minCapacity: query.minCapacity,
      // 固定只取启用中的会议室：停用会议室不出现在前台（FR-06、问题 P03）
      status: 'ENABLED',
      date: query.date || undefined
    })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询失败')
  } finally {
    loading.value = false
  }
}

function reset(): void {
  query.location = ''
  query.minCapacity = undefined
  query.date = today()
  void load()
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">会议室查询</h2>
      <span class="page-hint">对应 FR-05 按条件查询 / FR-06 只显示启用中的会议室</span>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="第 6 周工程骨架范围说明"
      description="本页仅用于验证「环境能跑、库能连、表能查」。预约提交、时间冲突检测（半开区间 [start, end)）、我的预约、管理后台均未实现，属第 9～10 周开发范围。"
      style="margin-bottom: 16px"
    />

    <el-form :inline="true" style="margin-bottom: 8px">
      <el-form-item label="位置关键字">
        <el-input
          v-model="query.location"
          placeholder="名称或位置，如「逸夫楼」"
          clearable
          style="width: 200px"
          @keyup.enter="load"
        />
      </el-form-item>
      <el-form-item label="最小容纳人数">
        <el-input-number v-model="query.minCapacity" :min="1" :max="500" placeholder="不限" />
      </el-form-item>
      <el-form-item label="日期">
        <el-date-picker
          v-model="query.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          style="width: 160px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rooms" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" align="center" />
      <el-table-column prop="name" label="会议室" min-width="180" />
      <el-table-column prop="location" label="位置" min-width="140" />
      <el-table-column prop="capacity" label="容纳人数" width="110" align="center" />
      <el-table-column prop="equipment" label="设备" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ row.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当日已占用时段" min-width="200">
        <template #default="{ row }">
          <template v-if="row.occupiedSlots && row.occupiedSlots.length">
            <el-tag
              v-for="slot in row.occupiedSlots"
              :key="slot"
              type="warning"
              size="small"
              style="margin: 2px 4px 2px 0"
            >
              {{ slot }}
            </el-tag>
          </template>
          <span v-else class="page-hint">当日无占用</span>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="没有符合条件的会议室（停用中的会议室不会出现在这里）" />
      </template>
    </el-table>

    <p class="page-hint" style="margin-top: 12px">
      共 {{ rooms.length }} 间（{{ query.date }}）。种子数据里特意留了 1 间停用会议室（学生活动中心 C102），按 FR-06 它不应出现在上表。
    </p>
  </div>
</template>
