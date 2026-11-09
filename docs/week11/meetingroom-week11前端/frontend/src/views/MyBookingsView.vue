<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import {
  cancelBooking,
  extractOccupiedSlots,
  listMyBookings,
  updateBooking
} from '@/api/booking'
import type { BookingVO, OccupiedSlot } from '@/types/api'
import {
  diffMinutes,
  formatDateTime,
  formatTime,
  isSameLocalDate,
  minutesUntilStart,
  normalizeDateTime
} from '@/utils/datetime'
import { toUserMessage } from '@/utils/errors'
import { reservationStatusLabel, reservationStatusTagType } from '@/utils/labels'

type EditField = 'subject' | 'attendees' | 'contact' | 'startTime' | 'endTime'

const bookings = ref<BookingVO[]>([])
const loading = ref(true)
const loadError = ref('')
const notice = ref('')

const editing = ref<BookingVO | null>(null)
const editForm = reactive({
  subject: '',
  attendees: '',
  contact: '',
  startTime: '',
  endTime: ''
})
const editErrors = reactive<Record<EditField, string>>({
  subject: '',
  attendees: '',
  contact: '',
  startTime: '',
  endTime: ''
})
const editError = ref('')
const editConflictSlots = ref<OccupiedSlot[]>([])
const saving = ref(false)

const cancelTarget = ref<BookingVO | null>(null)
const cancelError = ref('')
const cancelling = ref(false)

function formatRange(booking: BookingVO): string {
  return `${formatDateTime(booking.startTime)} — ${formatTime(booking.endTime)}`
}

/**
 * 只有“待开始且距开始超过 30 分钟”的预约展示可操作按钮。
 * 状态来自后端四态；30 分钟口径只用于提前禁用按钮，最终仍由后端校验（FR-12/FR-13）。
 */
function canOperate(booking: BookingVO): boolean {
  if (booking.status !== 'PENDING') return false
  const minutes = minutesUntilStart(booking.startTime)
  return minutes !== null && minutes > 30
}

function operationHint(booking: BookingVO): string {
  if (booking.status !== 'PENDING') return ''
  if (canOperate(booking)) return ''
  return '距会议开始不足 30 分钟，无法修改或取消。'
}

async function loadBookings() {
  loading.value = true
  loadError.value = ''

  try {
    bookings.value = await listMyBookings()
  } catch (error) {
    bookings.value = []
    loadError.value = toUserMessage(error, '预约记录加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function clearEditErrors() {
  for (const key of Object.keys(editErrors) as EditField[]) {
    editErrors[key] = ''
  }
}

async function focusFirstEditError() {
  await nextTick()
  document.querySelector<HTMLElement>('.modal [aria-invalid="true"]')?.focus()
}

function openEdit(booking: BookingVO) {
  if (!canOperate(booking)) return

  notice.value = ''
  editError.value = ''
  editConflictSlots.value = []
  clearEditErrors()

  editing.value = booking
  editForm.subject = booking.subject
  editForm.attendees = String(booking.attendees)
  editForm.contact = booking.contact
  editForm.startTime = booking.startTime.slice(0, 16)
  editForm.endTime = booking.endTime.slice(0, 16)
}

function closeEdit() {
  if (saving.value) return
  editing.value = null
  editError.value = ''
  editConflictSlots.value = []
  clearEditErrors()
}

function validateEdit(): boolean {
  clearEditErrors()

  if (!editForm.subject.trim()) editErrors.subject = '请填写会议主题。'

  // type="number" 的 v-model 会被 Vue 自动转成数字，这里统一按字符串校验
  const attendeesText = String(editForm.attendees).trim()
  if (!attendeesText) {
    editErrors.attendees = '请填写参会人数。'
  } else if (!/^\d+$/.test(attendeesText) || Number(attendeesText) <= 0) {
    editErrors.attendees = '参会人数需为正整数。'
  }

  if (!editForm.contact.trim()) editErrors.contact = '请填写联系人。'

  if (!editForm.startTime) editErrors.startTime = '请选择开始时间。'
  if (!editForm.endTime) editErrors.endTime = '请选择结束时间。'

  if (editForm.startTime && editForm.endTime) {
    const minutes = diffMinutes(editForm.startTime, editForm.endTime)
    if (minutes === null) {
      editErrors.endTime = '时间格式不正确。'
    } else if (minutes <= 0) {
      editErrors.endTime = '结束时间必须晚于开始时间。'
    } else if (!isSameLocalDate(editForm.startTime, editForm.endTime)) {
      editErrors.endTime = '单次预约不能跨天。'
    } else if (minutes > 8 * 60) {
      editErrors.endTime = '单次预约不能超过 8 小时。'
    }
  }

  return !Object.values(editErrors).some(Boolean)
}

async function handleUpdate() {
  if (!editing.value || saving.value) return

  editError.value = ''
  editConflictSlots.value = []

  if (!validateEdit()) {
    await focusFirstEditError()
    return
  }

  saving.value = true
  try {
    const updated = await updateBooking(editing.value.id, {
      subject: editForm.subject.trim(),
      attendees: Number(editForm.attendees),
      contact: editForm.contact.trim(),
      startTime: normalizeDateTime(editForm.startTime),
      endTime: normalizeDateTime(editForm.endTime)
    })

    editing.value = null
    notice.value = `预约 ${updated.reservationNo} 已修改，最新时间以列表为准。`
    await loadBookings()
  } catch (error) {
    const slots = extractOccupiedSlots(error)
    if (slots.length > 0) editConflictSlots.value = slots
    editError.value = toUserMessage(error, '预约修改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function openCancel(booking: BookingVO) {
  if (!canOperate(booking)) return

  notice.value = ''
  cancelError.value = ''
  cancelTarget.value = booking
}

function closeCancel() {
  if (cancelling.value) return
  cancelTarget.value = null
  cancelError.value = ''
}

async function handleCancel() {
  if (!cancelTarget.value || cancelling.value) return

  cancelling.value = true
  cancelError.value = ''
  try {
    const cancelled = await cancelBooking(cancelTarget.value.id)
    cancelTarget.value = null
    notice.value = `预约 ${cancelled.reservationNo} 已取消，该时段已重新释放。`
    await loadBookings()
  } catch (error) {
    cancelError.value = toUserMessage(error, '取消预约失败，请稍后重试')
  } finally {
    cancelling.value = false
  }
}

onMounted(loadBookings)
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">V1.0 · 核心闭环</p>
        <h1 class="page-title">我的预约</h1>
        <p class="page-subtitle">
          预约状态由后端按“待开始 / 进行中 / 已结束 / 已取消”四态返回，页面不自行计算。
          距会议开始不足 30 分钟时，修改与取消会被拒绝（FR-12、FR-13）。
        </p>
      </div>
      <el-button
        class="btn btn--ghost"
        native-type="button"
        :disabled="loading"
        @click="loadBookings"
      >
        刷新
      </el-button>
    </header>

    <div v-if="notice" class="alert alert--success" role="status">
      {{ notice }}
    </div>

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

      <div v-if="loading" class="loading">正在加载我的预约…</div>

      <div v-else-if="bookings.length === 0" class="empty-panel">
        <p>暂无预约记录。</p>
        <p class="muted">可以从会议室详情页发起一条新预约。</p>
        <RouterLink class="btn btn--primary btn--small" to="/rooms">去查会议室</RouterLink>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">我的预约记录</caption>
          <thead>
            <tr>
              <th>预约编号</th>
              <th>会议室 / 主题</th>
              <th>时间</th>
              <th>人数</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="booking in bookings" :key="booking.id" :data-booking-id="booking.id">
              <td class="mono">{{ booking.reservationNo }}</td>
              <td>
                <strong>{{ booking.roomName }}</strong>
                <p class="muted">{{ booking.subject }}</p>
                <p v-if="booking.cancelledAt" class="muted">
                  取消原因：{{ booking.cancelReason }}（{{ booking.cancelledBy }}）
                </p>
              </td>
              <td class="nowrap">{{ formatRange(booking) }}</td>
              <td>{{ booking.attendees }} 人</td>
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
                <template v-if="booking.status === 'PENDING'">
                  <el-button
                    class="btn btn--ghost btn--small"
                    native-type="button"
                    :disabled="!canOperate(booking)"
                    @click="openEdit(booking)"
                  >
                    修改
                  </el-button>
                  <el-button
                    class="btn btn--danger btn--small"
                    native-type="button"
                    :disabled="!canOperate(booking)"
                    @click="openCancel(booking)"
                  >
                    取消
                  </el-button>
                </template>
                <span v-else class="muted">无</span>
                <p v-if="operationHint(booking)" class="muted table__hint">
                  {{ operationHint(booking) }}
                </p>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <p class="muted section-block">
      取消成功后，会议室详情页中该时段会立即恢复为可预约状态（NF-08）。
    </p>

    <Teleport to="body">
      <div v-if="editing" class="modal-backdrop" @click.self="closeEdit">
        <div
          class="modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="edit-booking-title"
        >
          <div class="modal__head">
            <h2 id="edit-booking-title" class="modal__title">修改预约</h2>
            <p class="muted">{{ editing.reservationNo }} · {{ editing.roomName }}</p>
          </div>

          <div v-if="editError" class="alert alert--error" role="alert">
            <p>{{ editError }}</p>
            <div v-if="editConflictSlots.length > 0" class="conflict-panel">
              <p>以下时段已被占用：</p>
              <ul>
                <li v-for="slot in editConflictSlots" :key="slot.startTime">
                  {{ formatDateTime(slot.startTime) }} — {{ formatTime(slot.endTime) }}
                </li>
              </ul>
              <p class="muted">已填写的内容不会丢失，请调整时间后重新提交。</p>
            </div>
          </div>

          <form class="form" novalidate @submit.prevent="handleUpdate">
            <div class="form-grid">
              <div class="field field--full">
                <label class="field-label" for="edit-subject">
                  会议主题 <span class="required">*</span>
                </label>
                <input
                  id="edit-subject"
                  v-model="editForm.subject"
                  class="input"
                  :class="{ 'input--error': editErrors.subject }"
                  :aria-invalid="Boolean(editErrors.subject)"
                  type="text"
                  maxlength="100"
                  :disabled="saving"
                />
                <p v-if="editErrors.subject" class="field-error">{{ editErrors.subject }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="edit-attendees">
                  参会人数 <span class="required">*</span>
                </label>
                <input
                  id="edit-attendees"
                  v-model="editForm.attendees"
                  class="input"
                  :class="{ 'input--error': editErrors.attendees }"
                  :aria-invalid="Boolean(editErrors.attendees)"
                  type="number"
                  min="1"
                  step="1"
                  :disabled="saving"
                />
                <p v-if="editErrors.attendees" class="field-error">
                  {{ editErrors.attendees }}
                </p>
              </div>

              <div class="field">
                <label class="field-label" for="edit-contact">
                  联系人 <span class="required">*</span>
                </label>
                <input
                  id="edit-contact"
                  v-model="editForm.contact"
                  class="input"
                  :class="{ 'input--error': editErrors.contact }"
                  :aria-invalid="Boolean(editErrors.contact)"
                  type="text"
                  maxlength="30"
                  :disabled="saving"
                />
                <p v-if="editErrors.contact" class="field-error">{{ editErrors.contact }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="edit-start">
                  开始时间 <span class="required">*</span>
                </label>
                <input
                  id="edit-start"
                  v-model="editForm.startTime"
                  class="input"
                  :class="{ 'input--error': editErrors.startTime }"
                  :aria-invalid="Boolean(editErrors.startTime)"
                  type="datetime-local"
                  step="900"
                  :disabled="saving"
                />
                <p v-if="editErrors.startTime" class="field-error">
                  {{ editErrors.startTime }}
                </p>
              </div>

              <div class="field">
                <label class="field-label" for="edit-end">
                  结束时间 <span class="required">*</span>
                </label>
                <input
                  id="edit-end"
                  v-model="editForm.endTime"
                  class="input"
                  :class="{ 'input--error': editErrors.endTime }"
                  :aria-invalid="Boolean(editErrors.endTime)"
                  type="datetime-local"
                  step="900"
                  :disabled="saving"
                />
                <p v-if="editErrors.endTime" class="field-error">{{ editErrors.endTime }}</p>
              </div>

              <div class="field field--full">
                <p class="field-hint">
                  修改后由后端重新检测冲突；首尾相接不算冲突（FR-09）。
                </p>
              </div>
            </div>

            <div class="modal__foot">
              <el-button class="btn btn--primary" native-type="submit" :disabled="saving">
                {{ saving ? '保存中…' : '保存修改' }}
              </el-button>
              <el-button
                class="btn btn--ghost"
                native-type="button"
                :disabled="saving"
                @click="closeEdit"
              >
                返回列表
              </el-button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="cancelTarget" class="modal-backdrop" @click.self="closeCancel">
        <div
          class="modal modal--small"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cancel-booking-title"
        >
          <div class="modal__head">
            <h2 id="cancel-booking-title" class="modal__title">取消预约</h2>
            <p class="muted">{{ cancelTarget.reservationNo }} · {{ cancelTarget.subject }}</p>
          </div>

          <div v-if="cancelError" class="alert alert--error" role="alert">
            {{ cancelError }}
          </div>

          <p>
            取消后该时段会立即释放，其他用户可以重新预约；此操作不可撤销。
          </p>
          <dl class="summary-list">
            <div>
              <dt>会议室</dt>
              <dd>{{ cancelTarget.roomName }}</dd>
            </div>
            <div>
              <dt>时间</dt>
              <dd>{{ formatRange(cancelTarget) }}</dd>
            </div>
          </dl>

          <div class="modal__foot">
            <el-button
              class="btn btn--danger"
              native-type="button"
              :disabled="cancelling"
              @click="handleCancel"
            >
              {{ cancelling ? '取消中…' : '确认取消预约' }}
            </el-button>
            <el-button
              class="btn btn--ghost"
              native-type="button"
              :disabled="cancelling"
              @click="closeCancel"
            >
              暂不取消
            </el-button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>
