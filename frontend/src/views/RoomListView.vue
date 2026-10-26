<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listRooms } from '@/api/room'
import type { RoomVO } from '@/types/api'

/**
 * 会议室查询页 —— 对应 UC-U02 / FR-05 按条件查询、FR-06 只显示启用中的会议室。
 *
 * <p>第 9 周变化：每行加了"进详情"入口（跳会议室详情页），
 * 详情页展示该会议室所选日期的已占用时段，并提供"去预约"入口。
 * 第 6 周的"骨架范围说明"提示条已移除——那时页面只有查询功能，
 * 现在它已是完整的查询入口。</p>
 */
const router = useRouter()

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

/** 进会议室详情页（第 9 周；把所选日期一并带过去，详情页默认查这天的占用） */
function goDetail(roomId: number): void {
  // ★ id 必须走 path 参数（/rooms/:id），不能放 query：
  //   详情页用 route.params.id 取，路由定义是 '/rooms/:id'。
  void router.push({ name: 'room-detail', params: { id: String(roomId) }, query: { date: query.date } })
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
      title="第 9 周最小原型"
      description="本页可按位置／容量／日期筛选会议室。点「进详情」看该会议室所选日期的已占用时段，再点「去预约」提交预约——提交时若时间重叠会给出明确提示与被占用的具体时段（FR-09、FR-10）。"
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
      <el-table-column label="操作" width="110" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row.id)">进详情</el-button>
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
