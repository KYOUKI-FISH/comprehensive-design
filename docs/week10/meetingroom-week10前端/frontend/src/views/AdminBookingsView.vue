<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { forceCancelBooking, listAdminBookings, listAdminRooms } from '@/api/admin'
import type { AdminBookingVO, RoomVO } from '@/types/api'
import { formatDateTime, formatTime } from '@/utils/datetime'
import { toUserMessage } from '@/utils/errors'
import { reservationStatusLabel, reservationStatusTagType } from '@/utils/labels'

const rooms = ref<RoomVO[]>([])
const bookings = ref<AdminBookingVO[]>([])
const loading = ref(true)
const loadError = ref('')
const notice = ref('')

const filters = reactive({
  roomId: '' as number | '',
  date: ''
})

const cancelTarget = ref<AdminBookingVO | null>(null)
const cancelReason = ref('')
const reasonError = ref('')
const cancelError = ref('')
const cancelling = ref(false)

function formatRange(booking: AdminBookingVO): string {
  return `${formatDateTime(booking.startTime)} — ${formatTime(booking.endTime)}`
}

async function loadRooms() {
  try {
    rooms.value = await listAdminRooms()
  } catch {
    // 筛选下拉失败不阻断列表；列表本身会展示错误
    rooms.value = []
  }
}

async function loadBookings() {
  loading.value = true
  loadError.value = ''

  let roomId: number | undefined
  if (filters.roomId !== '') {
    const value = Number(filters.roomId)
    if (Number.isInteger(value) && value > 0) roomId = value
  }

  try {
    bookings.value = await listAdminBookings({
      roomId,
      date: filters.date || undefined
    })
  } catch (error) {
    bookings.value = []
    loadError.value = toUserMessage(error, '全部预约加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

async function resetFilters() {
  filters.roomId = ''
  filters.date = ''
  await loadBookings()
}

async function refreshAll() {
  notice.value = ''
  await Promise.all([loadRooms(), loadBookings()])
}

function openCancel(booking: AdminBookingVO) {
  if (booking.status === 'CANCELLED') return
  notice.value = ''
  cancelError.value = ''
  reasonError.value = ''
  cancelReason.value = ''
  cancelTarget.value = booking
}

function closeCancel() {
  if (cancelling.value) return
  cancelTarget.value = null
  cancelReason.value = ''
  reasonError.value = ''
  cancelError.value = ''
}

async function focusReason() {
  await nextTick()
  document.querySelector<HTMLElement>('#force-cancel-reason')?.focus()
}

async function handleForceCancel() {
  if (!cancelTarget.value || cancelling.value) return

  cancelError.value = ''
  reasonError.value = ''

  if (!cancelReason.value.trim()) {
    reasonError.value = '请填写取消原因。'
    await focusReason()
    return
  }

  cancelling.value = true
  try {
    const cancelled = await forceCancelBooking(
      cancelTarget.value.id,
      cancelReason.value.trim()
    )
    cancelTarget.value = null
    notice.value = `预约 ${cancelled.reservationNo} 已强制取消，操作日志已记录。`
    await loadBookings()
  } catch (error) {
    cancelError.value = toUserMessage(error, '强制取消失败，请稍后重试')
  } finally {
    cancelling.value = false
  }
}

onMounted(refreshAll)
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">第10周 · 管理端</p>
        <h1 class="page-title">全部预约处理</h1>
        <p class="page-subtitle">
          支持按会议室或日期筛选（FR-19 简化版）。强制取消必须填写原因，操作记录写入操作日志（FR-16、NF-14）。
        </p>
      </div>
      <el-button
        class="btn btn--ghost"
        native-type="button"
        :disabled="loading"
        @click="refreshAll"
      >
        刷新
      </el-button>
    </header>

    <div v-if="notice" class="alert alert--success" role="status">
      {{ notice }}
    </div>

    <form class="card card--pad filters filters--admin" novalidate @submit.prevent="loadBookings">
      <div class="field">
        <label class="field-label" for="admin-room-filter">会议室</label>
        <select id="admin-room-filter" v-model="filters.roomId" class="select">
          <option value="">全部会议室</option>
          <option v-for="room in rooms" :key="room.id" :value="room.id">
            {{ room.name }}
          </option>
        </select>
      </div>

      <div class="field">
        <label class="field-label" for="admin-date-filter">日期</label>
        <input id="admin-date-filter" v-model="filters.date" class="input" type="date" />
      </div>

      <div class="filters__actions">
        <el-button class="btn btn--primary" native-type="submit" :disabled="loading">
          {{ loading ? '查询中…' : '查询' }}
        </el-button>
        <el-button
          class="btn btn--ghost"
          native-type="button"
          :disabled="loading"
          @click="resetFilters"
        >
          重置
        </el-button>
      </div>
    </form>

    <div v-if="loadError" class="alert alert--error" role="alert">
      <div>{{ loadError }}</div>
      <el-button class="btn btn--ghost btn--small" native-type="button" @click="loadBookings">
        重试
      </el-button>
    </div>

    <div class="card table-card">
      <div class="table-card__head">
        <span>预约记录</span>
        <span class="muted">{{ loading ? '加载中…' : `共 ${bookings.length} 条` }}</span>
      </div>

      <div v-if="loading" class="loading">正在加载全部预约…</div>

      <div v-else-if="bookings.length === 0" class="empty-panel">
        <p>没有符合条件的预约记录。</p>
        <p class="muted">可以清空会议室或日期条件后重试。</p>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">全部预约记录</caption>
          <thead>
            <tr>
              <th>预约编号</th>
              <th>会议室</th>
              <th>预约人</th>
              <th>主题</th>
              <th>时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="booking in bookings" :key="booking.id" :data-booking-id="booking.id">
              <td class="mono">{{ booking.reservationNo }}</td>
              <td>{{ booking.roomName }}</td>
              <td>
                {{ booking.userName }}
                <p class="muted">{{ booking.username }}</p>
              </td>
              <td>
                {{ booking.subject }}
                <p v-if="booking.cancelledAt" class="muted">
                  取消原因：{{ booking.cancelReason }}（{{ booking.cancelledBy }}）
                </p>
              </td>
              <td class="nowrap">{{ formatRange(booking) }}</td>
              <td>
                <el-tag
                  :type="reservationStatusTagType(booking.status)"
                  size="small"
                  disable-transitions
                >
                  {{ reservationStatusLabel(booking.status) }}
                </el-tag>
              </td>
              <td class="table__actions">
                <el-button
                  v-if="booking.status !== 'CANCELLED'"
                  class="btn btn--danger btn--small"
                  native-type="button"
                  @click="openCancel(booking)"
                >
                  强制取消
                </el-button>
                <span v-else class="muted">已取消</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="cancelTarget" class="modal-backdrop" @click.self="closeCancel">
        <div
          class="modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="force-cancel-title"
        >
          <div class="modal__head">
            <h2 id="force-cancel-title" class="modal__title">强制取消预约</h2>
            <p class="muted">
              {{ cancelTarget.reservationNo }} · {{ cancelTarget.roomName }} ·
              {{ formatRange(cancelTarget) }}
            </p>
          </div>

          <div v-if="cancelError" class="alert alert--error" role="alert">
            {{ cancelError }}
          </div>

          <div class="alert alert--warning">
            强制取消会立即释放时段，并在操作日志中记录操作人、时间、类型和变更前后数据。
            未填写原因时系统拒绝处理（FR-16）。
          </div>

          <form class="form" novalidate @submit.prevent="handleForceCancel">
            <div class="field field--full">
              <label class="field-label" for="force-cancel-reason">
                取消原因 <span class="required">*</span>
              </label>
              <textarea
                id="force-cancel-reason"
                v-model="cancelReason"
                class="input textarea"
                :class="{ 'input--error': reasonError }"
                :aria-invalid="Boolean(reasonError)"
                rows="3"
                maxlength="200"
                placeholder="如：会议室临时维修，原预约无法使用"
                :disabled="cancelling"
              ></textarea>
              <p v-if="reasonError" class="field-error">{{ reasonError }}</p>
              <p v-else class="field-hint">{{ cancelReason.trim().length }} / 200</p>
            </div>

            <div class="modal__foot">
              <el-button class="btn btn--danger" native-type="submit" :disabled="cancelling">
                {{ cancelling ? '处理中…' : '确认强制取消' }}
              </el-button>
              <el-button
                class="btn btn--ghost"
                native-type="button"
                :disabled="cancelling"
                @click="closeCancel"
              >
                返回
              </el-button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>
  </section>
</template>
