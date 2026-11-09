<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { createBooking, extractOccupiedSlots } from '@/api/booking'
import { listRooms } from '@/api/room'
import type { BookingVO, OccupiedSlot, RoomVO } from '@/types/api'
import { toUserMessage } from '@/utils/errors'
import {
  combineDateTime,
  diffMinutes,
  formatDateTime,
  formatTime,
  isSameLocalDate,
  normalizeDateTime
} from '@/utils/datetime'
import {
  formatEquipment,
  reservationStatusLabel,
  reservationStatusTagType
} from '@/utils/labels'

type FieldName =
  | 'roomId'
  | 'subject'
  | 'attendees'
  | 'contact'
  | 'startTime'
  | 'endTime'

const route = useRoute()

const rooms = ref<RoomVO[]>([])
const roomsLoading = ref(true)
const roomsError = ref('')
const roomNotice = ref('')

const form = reactive({
  roomId: '' as number | '',
  subject: '',
  attendees: '',
  contact: '',
  startTime: '',
  endTime: ''
})

const errors = reactive<Record<FieldName, string>>({
  roomId: '',
  subject: '',
  attendees: '',
  contact: '',
  startTime: '',
  endTime: ''
})

const topError = ref('')
const conflictSlots = ref<OccupiedSlot[]>([])
const submitting = ref(false)
const success = ref<BookingVO | null>(null)

const availableRooms = computed(() =>
  rooms.value.filter((room) => room.status === 'AVAILABLE')
)
const selectedRoom = computed(
  () => rooms.value.find((room) => room.id === form.roomId) ?? null
)

function queryString(key: string): string {
  const value = route.query[key]
  return typeof value === 'string' ? value : ''
}

function applyQueryPrefill() {
  const rawRoomId = Number(queryString('roomId'))
  if (Number.isInteger(rawRoomId) && rawRoomId > 0) {
    const matched = availableRooms.value.find((room) => room.id === rawRoomId)
    if (matched) {
      form.roomId = matched.id
    } else {
      roomNotice.value = '指定的会议室不存在或已停用，请从可用会议室中重新选择。'
    }
  }

  const date = queryString('date')
  const start = queryString('start')
  const end = queryString('end')
  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date)
  const validTime = /^\d{2}:\d{2}$/.test(start) && /^\d{2}:\d{2}$/.test(end)

  if (validDate && validTime) {
    form.startTime = combineDateTime(date, start)
    form.endTime = combineDateTime(date, end)
  }
}

async function loadRooms() {
  roomsLoading.value = true
  roomsError.value = ''

  try {
    rooms.value = await listRooms()
    applyQueryPrefill()
  } catch (error) {
    roomsError.value = toUserMessage(error, '会议室加载失败，请稍后重试')
  } finally {
    roomsLoading.value = false
  }
}

function clearErrors() {
  for (const key of Object.keys(errors) as FieldName[]) {
    errors[key] = ''
  }
}

async function focusFirstError() {
  await nextTick()
  const field = document.querySelector<HTMLElement>('[aria-invalid="true"]')
  field?.focus()
}

function validate(): boolean {
  clearErrors()

  if (!form.roomId) errors.roomId = '请选择会议室。'

  if (!form.subject.trim()) errors.subject = '请填写会议主题。'

  // type="number" 的 v-model 会被 Vue 自动转成数字，这里统一按字符串校验
  const attendeesText = String(form.attendees).trim()
  if (!attendeesText) {
    errors.attendees = '请填写参会人数。'
  } else if (!/^\d+$/.test(attendeesText) || Number(attendeesText) <= 0) {
    errors.attendees = '参会人数需为正整数。'
  }

  if (!form.contact.trim()) errors.contact = '请填写联系人。'

  if (!form.startTime) errors.startTime = '请选择开始时间。'
  if (!form.endTime) errors.endTime = '请选择结束时间。'

  if (form.startTime && form.endTime) {
    const minutes = diffMinutes(form.startTime, form.endTime)

    if (minutes === null) {
      errors.endTime = '时间格式不正确。'
    } else if (minutes <= 0) {
      errors.endTime = '结束时间必须晚于开始时间。'
    } else if (!isSameLocalDate(form.startTime, form.endTime)) {
      errors.endTime = '单次预约不能跨天。'
    } else if (minutes > 8 * 60) {
      errors.endTime = '单次预约不能超过 8 小时。'
    }
  }

  return !Object.values(errors).some(Boolean)
}

async function handleSubmit() {
  if (submitting.value) return

  topError.value = ''
  conflictSlots.value = []

  if (!validate()) {
    await focusFirstError()
    return
  }

  submitting.value = true

  try {
    const booking = await createBooking({
      roomId: Number(form.roomId),
      subject: form.subject.trim(),
      attendees: Number(form.attendees),
      contact: form.contact.trim(),
      startTime: normalizeDateTime(form.startTime),
      endTime: normalizeDateTime(form.endTime)
    })

    success.value = booking
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    // FR-18：失败时不清空任何已填字段，用户改完时间可直接重提。
    const slots = extractOccupiedSlots(error)
    if (slots.length > 0) {
      conflictSlots.value = slots
      errors.startTime = '所选时间段与已有预约冲突。'
      errors.endTime = '请调整时间后重新提交。'
    }

    topError.value = toUserMessage(error, '预约提交失败，请稍后重试')
    await nextTick()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  success.value = null
  topError.value = ''
  conflictSlots.value = []
  clearErrors()

  form.subject = ''
  form.attendees = ''
  form.contact = ''
  form.startTime = ''
  form.endTime = ''
  // 保留会议室选择，方便连续预约同一间会议室
}

onMounted(loadRooms)
</script>

<template>
  <section class="page page--narrow">
    <header class="page-head">
      <p class="eyebrow">V1.0 · 预约申请</p>
      <h1 class="page-title">提交预约</h1>
      <p class="page-subtitle">
        会议主题、参会人数、联系人和起止时间均为必填项。提交失败时会保留已填写内容。
      </p>
    </header>

    <div v-if="success" class="alert alert--success success-panel" role="status">
      <p class="alert__title">预约成功</p>
      <dl class="summary-list">
        <div>
          <dt>预约编号</dt>
          <dd class="mono">{{ success.reservationNo }}</dd>
        </div>
        <div>
          <dt>会议室</dt>
          <dd>{{ success.roomName }}</dd>
        </div>
        <div>
          <dt>时间</dt>
          <dd>
            {{ formatDateTime(success.startTime) }} —
            {{ formatTime(success.endTime) }}
          </dd>
        </div>
        <div>
          <dt>会议主题</dt>
          <dd>{{ success.subject }}</dd>
        </div>
        <div>
          <dt>参会人数</dt>
          <dd>{{ success.attendees }} 人</dd>
        </div>
        <div>
          <dt>联系人</dt>
          <dd>{{ success.contact }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>
            <el-tag
              :type="reservationStatusTagType(success.status)"
              size="small"
              disable-transitions
            >
              {{ reservationStatusLabel(success.status) }}
            </el-tag>
          </dd>
        </div>
      </dl>
      <div class="form-actions">
        <el-button class="btn btn--primary" native-type="button" @click="resetForm">
          再预约一条
        </el-button>
        <RouterLink class="btn btn--ghost" to="/rooms">返回会议室列表</RouterLink>
      </div>
    </div>

    <div v-if="topError" class="alert alert--error" role="alert">
      <p class="alert__title">{{ topError }}</p>
      <div v-if="conflictSlots.length > 0" class="conflict-panel">
        <p>以下时段已被占用：</p>
        <ul>
          <li v-for="slot in conflictSlots" :key="slot.startTime">
            {{ formatDateTime(slot.startTime) }} — {{ formatTime(slot.endTime) }}
          </li>
        </ul>
        <p class="muted">请调整开始或结束时间后重新提交，已填写的信息不会丢失。</p>
      </div>
    </div>

    <div v-if="roomNotice" class="alert alert--warning" role="alert">
      {{ roomNotice }}
    </div>

    <div v-if="roomsError" class="alert alert--error" role="alert">
      <div>{{ roomsError }}</div>
      <el-button class="btn btn--ghost btn--small" native-type="button" @click="loadRooms">
        重试
      </el-button>
    </div>

    <div v-if="roomsLoading" class="loading">正在加载可预约会议室…</div>

    <template v-else-if="!success">
      <div
        v-if="!roomsError && availableRooms.length === 0"
        class="alert alert--warning"
      >
        当前没有可预约的会议室。已停用的会议室不会作为新预约选项（FR-06）。
      </div>

      <form class="card card--pad form" novalidate @submit.prevent="handleSubmit">
        <div class="form-grid">
          <div class="field field--full">
            <label class="field-label" for="booking-room">
              会议室 <span class="required">*</span>
            </label>
            <select
              id="booking-room"
              v-model="form.roomId"
              class="select"
              :class="{ 'input--error': errors.roomId }"
              :aria-invalid="Boolean(errors.roomId)"
              :disabled="submitting || Boolean(roomsError)"
            >
              <option value="">请选择可用会议室</option>
              <option v-for="room in availableRooms" :key="room.id" :value="room.id">
                {{ room.name }}（{{ room.location }} · {{ room.capacity }} 人）
              </option>
            </select>
            <p v-if="errors.roomId" class="field-error">{{ errors.roomId }}</p>
            <p v-else-if="selectedRoom" class="field-hint">
              设备：{{ formatEquipment(selectedRoom.equipment) }}；容量：
              {{ selectedRoom.capacity }} 人。已停用的会议室不会出现在此列表中。
            </p>
          </div>

          <div class="field field--full">
            <label class="field-label" for="booking-subject">
              会议主题 <span class="required">*</span>
            </label>
            <input
              id="booking-subject"
              v-model="form.subject"
              class="input"
              :class="{ 'input--error': errors.subject }"
              :aria-invalid="Boolean(errors.subject)"
              type="text"
              maxlength="100"
              placeholder="如：课题组周会"
              :disabled="submitting"
            />
            <p v-if="errors.subject" class="field-error">{{ errors.subject }}</p>
          </div>

          <div class="field">
            <label class="field-label" for="booking-attendees">
              参会人数 <span class="required">*</span>
            </label>
            <input
              id="booking-attendees"
              v-model="form.attendees"
              class="input"
              :class="{ 'input--error': errors.attendees }"
              :aria-invalid="Boolean(errors.attendees)"
              type="number"
              min="1"
              step="1"
              placeholder="如：15"
              :disabled="submitting"
            />
            <p v-if="errors.attendees" class="field-error">{{ errors.attendees }}</p>
          </div>

          <div class="field">
            <label class="field-label" for="booking-contact">
              联系人 <span class="required">*</span>
            </label>
            <input
              id="booking-contact"
              v-model="form.contact"
              class="input"
              :class="{ 'input--error': errors.contact }"
              :aria-invalid="Boolean(errors.contact)"
              type="text"
              maxlength="30"
              placeholder="请输入联系人姓名"
              :disabled="submitting"
            />
            <p v-if="errors.contact" class="field-error">{{ errors.contact }}</p>
          </div>

          <div class="field">
            <label class="field-label" for="booking-start">
              开始时间 <span class="required">*</span>
            </label>
            <input
              id="booking-start"
              v-model="form.startTime"
              class="input"
              :class="{ 'input--error': errors.startTime }"
              :aria-invalid="Boolean(errors.startTime)"
              type="datetime-local"
              step="900"
              :disabled="submitting"
            />
            <p v-if="errors.startTime" class="field-error">{{ errors.startTime }}</p>
          </div>

          <div class="field">
            <label class="field-label" for="booking-end">
              结束时间 <span class="required">*</span>
            </label>
            <input
              id="booking-end"
              v-model="form.endTime"
              class="input"
              :class="{ 'input--error': errors.endTime }"
              :aria-invalid="Boolean(errors.endTime)"
              type="datetime-local"
              step="900"
              :disabled="submitting"
            />
            <p v-if="errors.endTime" class="field-error">{{ errors.endTime }}</p>
          </div>

          <div class="field field--full">
            <p class="field-hint">
              时间规则：结束时间必须晚于开始时间；单次预约不能跨天，且不超过 8 小时（FR-08）。
            </p>
          </div>
        </div>

        <div class="form-actions">
          <el-button class="btn btn--primary" native-type="submit" :disabled="submitting">
            {{ submitting ? '提交中…' : '提交预约' }}
          </el-button>
          <RouterLink class="btn btn--ghost" to="/rooms">返回会议室列表</RouterLink>
        </div>
      </form>
    </template>
  </section>
</template>
