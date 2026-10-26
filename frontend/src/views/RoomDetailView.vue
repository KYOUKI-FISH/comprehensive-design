<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOccupancy, listRooms } from '@/api/room'
import type { OccupiedSlotVO, RoomVO } from '@/types/api'

/**
 * 会议室详情页（第 9 周；对应 FR-05 按条件查询、FR-17 占用时段、FR-10 冲突提示的数据来源）。
 *
 * <p>★ 供数方式（决策 D1，详见 {@code docs/week09/01_接口变更记录.md}）：
 * <strong>基本信息</strong>来自 {@code GET /api/rooms} 列表项，
 * <strong>当日占用时段</strong>来自 {@code GET /api/rooms/{id}/occupancy}。
 * 不新增 {@code GET /api/rooms/{id}} 接口，保持与第 7—8 周冻结的 14 条接口同源。</p>
 *
 * <p>页面展示：会议室名称／位置／容量／设备／状态 ＋ 所选日期的已占用时段 ＋「去预约」入口。
 * 停用的会议室不能预约（FR-06），「去预约」按钮置灰。</p>
 */
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const room = ref<RoomVO | null>(null)
const occupiedSlots = ref<OccupiedSlotVO[]>([])

/** 所选日期，默认取路由带的日期（从列表页点进来时是列表页选的那天），否则今天 */
const selectedDate = ref<string>((route.query.date as string) || formatDate(new Date()))

function formatDate(d: Date): string {
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 把后端返回的时间点格式化成 HH:mm */
function fmtTime(value: string): string {
  return value.length >= 16 ? value.substring(11, 16) : value
}

const roomId = computed(() => Number(route.params.id))

/** 会议室是否可预约（FR-06：停用会议室不可作为新预约选项） */
const bookable = computed(() => room.value?.status === 'ENABLED')

async function load(): Promise<void> {
  if (!Number.isFinite(roomId.value) || roomId.value <= 0) {
    ElMessage.error('会议室不存在')
    return
  }
  loading.value = true
  try {
    // ① 基本信息：从列表接口里取对应项（决策 D1：组合供数，不新增详情接口）
    const rooms = await listRooms({ date: selectedDate.value })
    room.value = rooms.find((r) => r.id === roomId.value) ?? null
    if (!room.value) {
      // 列表接口默认只返回启用中的会议室，停用的会查不到——再查一次不筛状态
      const all = await listRooms({})
      room.value = all.find((r) => r.id === roomId.value) ?? null
    }
    if (!room.value) {
      ElMessage.error('会议室不存在')
      return
    }

    // ② 占用时段：独立接口（第 8 周清单第 4 条）
    const occupancy = await getOccupancy(roomId.value, selectedDate.value)
    occupiedSlots.value = occupancy.occupiedSlots ?? []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function goBooking(): void {
  if (!room.value) {
    return
  }
  void router.push({
    name: 'booking-new',
    query: { roomId: String(room.value.id), date: selectedDate.value }
  })
}

watch(selectedDate, () => {
  void load()
})

onMounted(() => {
  void load()
})
</script>

<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2 class="page-title">会议室详情</h2>
      <span class="page-hint">对应 FR-05 按条件查询 / FR-17 占用时段</span>
    </div>

    <el-page-header content="会议室信息" @back="router.back()" style="margin-bottom: 16px" />

    <el-card shadow="never" style="margin-bottom: 16px">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="名称">{{ room?.name ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ room?.location ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="容纳人数">{{ room?.capacity ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="设备">{{ room?.equipment || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="room?.status === 'ENABLED' ? 'success' : 'info'" size="small">
            {{ room?.status === 'ENABLED' ? '启用' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="选择日期">
          <el-date-picker
            v-model="selectedDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
          />
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ selectedDate }} 的占用情况</span>
          <el-button
            type="primary"
            :disabled="!bookable"
            @click="goBooking"
          >
            去预约
          </el-button>
        </div>
      </template>

      <el-table :data="occupiedSlots" border style="width: 100%">
        <el-table-column label="开始时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="160" align="center">
          <template #default="{ row }">{{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" align="center">
          <template #default>
            <el-tag type="warning" size="small">已被占用</el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="当日无占用，全天可预约" />
        </template>
      </el-table>

      <p v-if="!bookable" class="page-hint" style="margin-top: 12px">
        该会议室已停用，按 FR-06 不可作为新预约选项。
      </p>
    </el-card>
  </div>
</template>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
