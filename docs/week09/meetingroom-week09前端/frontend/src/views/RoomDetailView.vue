<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getOccupancy, listRooms } from '@/api/room'
import { isAdmin } from '@/session/session'
import type { OccupancyVO, RoomVO, TimeSlot } from '@/types/api'
import {
  formatDateLabel,
  formatTime,
  pad2,
  parseLocalDateTime,
  todayString
} from '@/utils/datetime'
import { formatEquipment, roomStatusClass, roomStatusLabel } from '@/utils/labels'

const props = defineProps<{ id: string }>()
const roomId = Number(props.id)
const router = useRouter()

const room = ref<RoomVO | null>(null)
const roomLoading = ref(true)
const roomError = ref('')

const selectedDate = ref(todayString())
const occupancy = ref<OccupancyVO | null>(null)
const occupancyLoading = ref(false)
const occupancyError = ref('')
let occupancyRequestId = 0

const canBook = computed(
  () => room.value?.status === 'AVAILABLE' && !isAdmin.value
)
const firstFreeSlot = computed<TimeSlot | undefined>(() => occupancy.value?.freeSlots[0])

async function loadRoom() {
  roomLoading.value = true
  roomError.value = ''

  try {
    // D1：详情基本信息复用列表接口，不调用一个不存在的 GET /api/rooms/{id}
    const rooms = await listRooms()
    room.value = rooms.find((item) => item.id === roomId) ?? null
    if (!room.value) roomError.value = '未找到该会议室，可能已被删除。'
  } catch (error) {
    roomError.value =
      error instanceof Error ? error.message : '会议室信息加载失败，请稍后重试。'
  } finally {
    roomLoading.value = false
  }
}

async function loadOccupancy() {
  if (!selectedDate.value) return

  const requestId = ++occupancyRequestId
  occupancyLoading.value = true
  occupancyError.value = ''

  try {
    const result = await getOccupancy(roomId, selectedDate.value)
    if (requestId === occupancyRequestId) occupancy.value = result
  } catch (error) {
    if (requestId === occupancyRequestId) {
      occupancy.value = null
      occupancyError.value =
        error instanceof Error ? error.message : '占用时段查询失败，请稍后重试。'
    }
  } finally {
    if (requestId === occupancyRequestId) occupancyLoading.value = false
  }
}

function goBooking(slot?: TimeSlot) {
  const query: Record<string, string> = {
    roomId: String(roomId),
    date: selectedDate.value
  }

  if (slot) {
    const start = parseLocalDateTime(slot.startTime)
    const end = parseLocalDateTime(slot.endTime)
    if (start && end) {
      const suggestedEnd = new Date(
        Math.min(end.getTime(), start.getTime() + 60 * 60 * 1000)
      )
      query.start = `${pad2(start.getHours())}:${pad2(start.getMinutes())}`
      query.end = `${pad2(suggestedEnd.getHours())}:${pad2(suggestedEnd.getMinutes())}`
    }
  }

  router.push({ path: '/bookings/new', query })
}

watch(selectedDate, (value) => {
  if (value) loadOccupancy()
})

onMounted(() => {
  loadRoom()
  loadOccupancy()
})
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">
          <RouterLink class="link" to="/rooms">← 返回会议室列表</RouterLink>
        </p>
        <h1 class="page-title">{{ room?.name ?? '会议室详情' }}</h1>
        <p v-if="room" class="page-subtitle">
          {{ room.location }} · 容量 {{ room.capacity }} 人
        </p>
      </div>
      <span v-if="room" :class="roomStatusClass(room.status)">
        {{ roomStatusLabel(room.status) }}
      </span>
    </header>

    <div v-if="roomLoading" class="loading">正在加载会议室信息…</div>

    <div v-else-if="roomError" class="alert alert--error" role="alert">
      <div>{{ roomError }}</div>
      <RouterLink class="btn btn--ghost btn--small" to="/rooms">返回列表</RouterLink>
    </div>

    <template v-else-if="room">
      <div v-if="room.status === 'DISABLED'" class="alert alert--warning">
        该会议室已停用，不能提交新预约；已有预约不受影响。
      </div>

      <div class="card card--pad">
        <h2 class="section-title">基本信息</h2>
        <dl class="meta-grid">
          <div class="meta-item">
            <dt class="meta-label">位置</dt>
            <dd class="meta-value">{{ room.location }}</dd>
          </div>
          <div class="meta-item">
            <dt class="meta-label">容量</dt>
            <dd class="meta-value">{{ room.capacity }} 人</dd>
          </div>
          <div class="meta-item">
            <dt class="meta-label">设备</dt>
            <dd class="meta-value">{{ formatEquipment(room.equipment) }}</dd>
          </div>
          <div class="meta-item">
            <dt class="meta-label">状态</dt>
            <dd class="meta-value">{{ roomStatusLabel(room.status) }}</dd>
          </div>
        </dl>
      </div>

      <div class="card card--pad section-block">
        <div class="section-head">
          <div>
            <h2 class="section-title">当日占用情况</h2>
            <p class="muted">
              基本信息来自会议室列表接口，占用时段来自占用时段接口（D1 组合供数）。
            </p>
          </div>
          <div class="field field--inline">
            <label class="field-label" for="occupancy-date">选择日期</label>
            <input
              id="occupancy-date"
              v-model="selectedDate"
              class="input"
              type="date"
            />
          </div>
        </div>

        <div v-if="occupancyLoading" class="loading">
          正在查询 {{ formatDateLabel(selectedDate) }} 的占用时段…
        </div>

        <div v-else-if="occupancyError" class="alert alert--error" role="alert">
          <div>{{ occupancyError }}</div>
          <button class="btn btn--ghost btn--small" type="button" @click="loadOccupancy">
            重试
          </button>
        </div>

        <template v-else-if="occupancy">
          <div class="slot-summary">
            <span class="badge badge--danger">
              已占用 {{ occupancy.occupiedSlots.length }} 段
            </span>
            <span class="badge badge--success">
              空闲 {{ occupancy.freeSlots.length }} 段
            </span>
            <span class="muted">日期：{{ formatDateLabel(occupancy.date) }}</span>
          </div>

          <h3 class="subsection-title">已占用时段</h3>
          <div v-if="occupancy.occupiedSlots.length === 0" class="empty-inline">
            当日暂无已占用时段。
          </div>
          <ul v-else class="slot-grid">
            <li
              v-for="slot in occupancy.occupiedSlots"
              :key="`occupied-${slot.startTime}`"
              class="slot-card slot-card--occupied"
            >
              <span class="slot-time">
                {{ formatTime(slot.startTime) }} – {{ formatTime(slot.endTime) }}
              </span>
              <span class="badge badge--danger">已占用</span>
            </li>
          </ul>

          <h3 class="subsection-title">空闲时段</h3>
          <div v-if="occupancy.freeSlots.length === 0" class="empty-inline">
            当日没有可预约的空闲时段。
          </div>
          <ul v-else class="slot-grid">
            <li
              v-for="slot in occupancy.freeSlots"
              :key="`free-${slot.startTime}`"
              class="slot-card slot-card--free"
            >
              <span class="slot-time">
                {{ formatTime(slot.startTime) }} – {{ formatTime(slot.endTime) }}
              </span>
              <button
                v-if="canBook"
                class="btn btn--ghost btn--small"
                type="button"
                @click="goBooking(slot)"
              >
                用此时段预约
              </button>
              <span v-else class="badge badge--success">空闲</span>
            </li>
          </ul>
        </template>

        <div class="form-actions">
          <button
            v-if="canBook"
            class="btn btn--primary"
            type="button"
            @click="goBooking(firstFreeSlot)"
          >
            去预约
          </button>
          <span v-else-if="isAdmin" class="muted">
            管理员账号不参与预约；可在第10周使用“全部预约处理”入口。
          </span>
          <span v-else class="muted">该会议室已停用，不能发起新预约。</span>
        </div>
      </div>
    </template>
  </section>
</template>
