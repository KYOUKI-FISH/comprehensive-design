<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { createRoom, deleteRoom, listAdminRooms, updateRoom } from '@/api/admin'
import type { RoomStatus, RoomVO } from '@/types/api'
import { toUserMessage } from '@/utils/errors'
import {
  formatEquipment,
  roomStatusLabel,
  roomStatusTagType
} from '@/utils/labels'

type FieldName = 'name' | 'location' | 'capacity'

const rooms = ref<RoomVO[]>([])
const loading = ref(true)
const loadError = ref('')
const notice = ref('')

const editingRoom = ref<RoomVO | null>(null)
const formOpen = ref(false)
const form = reactive({
  name: '',
  location: '',
  capacity: '',
  equipment: '',
  status: 'AVAILABLE' as RoomStatus
})
const errors = reactive<Record<FieldName, string>>({
  name: '',
  location: '',
  capacity: ''
})
const formError = ref('')
const saving = ref(false)

const deleteTarget = ref<RoomVO | null>(null)
const deleteError = ref('')
const deleting = ref(false)

function clearErrors() {
  errors.name = ''
  errors.location = ''
  errors.capacity = ''
  formError.value = ''
}

async function loadRooms() {
  loading.value = true
  loadError.value = ''

  try {
    rooms.value = await listAdminRooms()
  } catch (error) {
    rooms.value = []
    loadError.value = toUserMessage(error, '会议室列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  notice.value = ''
  clearErrors()
  editingRoom.value = null
  formOpen.value = true
  form.name = ''
  form.location = ''
  form.capacity = ''
  form.equipment = ''
  form.status = 'AVAILABLE'
}

function equipmentText(room: RoomVO): string {
  if (Array.isArray(room.equipment)) return room.equipment.join('，')
  if (typeof room.equipment === 'string') return room.equipment
  return ''
}

function openEdit(room: RoomVO) {
  notice.value = ''
  clearErrors()
  editingRoom.value = room
  formOpen.value = true
  form.name = room.name
  form.location = room.location
  form.capacity = String(room.capacity)
  form.equipment = equipmentText(room)
  form.status = room.status
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  editingRoom.value = null
  clearErrors()
}

async function focusFirstError() {
  await nextTick()
  document.querySelector<HTMLElement>('.modal [aria-invalid="true"]')?.focus()
}

function validate(): boolean {
  clearErrors()

  if (!form.name.trim()) errors.name = '请填写会议室名称。'
  if (!form.location.trim()) errors.location = '请填写位置。'

  const capacityText = String(form.capacity).trim()
  if (!capacityText) {
    errors.capacity = '请填写容量。'
  } else if (!/^\d+$/.test(capacityText) || Number(capacityText) <= 0) {
    errors.capacity = '容量需为正整数。'
  }

  return !Object.values(errors).some(Boolean)
}

async function handleSave() {
  if (saving.value) return
  if (!validate()) {
    await focusFirstError()
    return
  }

  saving.value = true
  formError.value = ''
  const payload = {
    name: form.name.trim(),
    location: form.location.trim(),
    capacity: Number(form.capacity),
    equipment: form.equipment,
    status: form.status
  }

  try {
    if (editingRoom.value) {
      const updated = await updateRoom(editingRoom.value.id, payload)
      notice.value = `会议室「${updated.name}」已更新。`
    } else {
      const created = await createRoom(payload)
      notice.value = `会议室「${created.name}」已新增。`
    }
    formOpen.value = false
    editingRoom.value = null
    await loadRooms()
  } catch (error) {
    formError.value = toUserMessage(error, '会议室保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

function openDelete(room: RoomVO) {
  notice.value = ''
  deleteError.value = ''
  deleteTarget.value = room
}

function closeDelete() {
  if (deleting.value) return
  deleteTarget.value = null
  deleteError.value = ''
}

async function handleDelete() {
  if (!deleteTarget.value || deleting.value) return

  deleting.value = true
  deleteError.value = ''
  try {
    await deleteRoom(deleteTarget.value.id)
    notice.value = `会议室「${deleteTarget.value.name}」已删除。`
    deleteTarget.value = null
    await loadRooms()
  } catch (error) {
    // FR-03：存在有效预约时后端返回 409，这里展示中文原因，不关闭弹窗
    deleteError.value = toUserMessage(error, '删除会议室失败，请稍后重试')
  } finally {
    deleting.value = false
  }
}

onMounted(loadRooms)
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">V1.0 · 管理端</p>
        <h1 class="page-title">会议室管理</h1>
        <p class="page-subtitle">
          会议室的新增、修改、启停与删除。存在有效预约的会议室不能删除，接口返回 409（FR-03）。
        </p>
      </div>
      <div class="page-head__actions">
        <el-button
          class="btn btn--ghost"
          native-type="button"
          :disabled="loading"
          @click="loadRooms"
        >
          刷新
        </el-button>
        <el-button class="btn btn--primary" native-type="button" @click="openCreate">
          新增会议室
        </el-button>
      </div>
    </header>

    <div v-if="notice" class="alert alert--success" role="status">
      {{ notice }}
    </div>

    <div v-if="loadError" class="alert alert--error" role="alert">
      <div>{{ loadError }}</div>
      <el-button class="btn btn--ghost btn--small" native-type="button" @click="loadRooms">
        重试
      </el-button>
    </div>

    <div class="card table-card">
      <div class="table-card__head">
        <span>会议室列表</span>
        <span class="muted">{{ loading ? '加载中…' : `共 ${rooms.length} 间` }}</span>
      </div>

      <div v-if="loading" class="loading">正在加载会议室…</div>

      <div v-else-if="rooms.length === 0" class="empty-panel">
        <p>暂无会议室。</p>
        <el-button
          class="btn btn--primary btn--small"
          native-type="button"
          @click="openCreate"
        >
          新增第一间
        </el-button>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">会议室管理列表</caption>
          <thead>
            <tr>
              <th>名称</th>
              <th>位置</th>
              <th>容量</th>
              <th>设备</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="room in rooms" :key="room.id" :data-room-id="room.id">
              <td><strong>{{ room.name }}</strong></td>
              <td>{{ room.location }}</td>
              <td>{{ room.capacity }} 人</td>
              <td>{{ formatEquipment(room.equipment) }}</td>
              <td>
                <el-tag :type="roomStatusTagType(room.status)" size="small" disable-transitions>
                  {{ roomStatusLabel(room.status) }}
                </el-tag>
              </td>
              <td class="table__actions">
                <el-button
                  class="btn btn--ghost btn--small"
                  native-type="button"
                  @click="openEdit(room)"
                >
                  编辑
                </el-button>
                <el-button
                  class="btn btn--danger btn--small"
                  native-type="button"
                  @click="openDelete(room)"
                >
                  删除
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="room-form-title">
          <div class="modal__head">
            <h2 id="room-form-title" class="modal__title">
              {{ editingRoom ? '编辑会议室' : '新增会议室' }}
            </h2>
            <p class="muted">
              {{ editingRoom ? `正在编辑 ${editingRoom.name}` : '填写会议室基本信息' }}
            </p>
          </div>

          <div v-if="formError" class="alert alert--error" role="alert">
            {{ formError }}
          </div>

          <form class="form" novalidate @submit.prevent="handleSave">
            <div class="form-grid">
              <div class="field">
                <label class="field-label" for="room-name">
                  名称 <span class="required">*</span>
                </label>
                <input
                  id="room-name"
                  v-model="form.name"
                  class="input"
                  :class="{ 'input--error': errors.name }"
                  :aria-invalid="Boolean(errors.name)"
                  type="text"
                  maxlength="50"
                  :disabled="saving"
                />
                <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="room-location">
                  位置 <span class="required">*</span>
                </label>
                <input
                  id="room-location"
                  v-model="form.location"
                  class="input"
                  :class="{ 'input--error': errors.location }"
                  :aria-invalid="Boolean(errors.location)"
                  type="text"
                  maxlength="100"
                  :disabled="saving"
                />
                <p v-if="errors.location" class="field-error">{{ errors.location }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="room-capacity">
                  容量（人） <span class="required">*</span>
                </label>
                <input
                  id="room-capacity"
                  v-model="form.capacity"
                  class="input"
                  :class="{ 'input--error': errors.capacity }"
                  :aria-invalid="Boolean(errors.capacity)"
                  type="number"
                  min="1"
                  step="1"
                  :disabled="saving"
                />
                <p v-if="errors.capacity" class="field-error">{{ errors.capacity }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="room-status">状态</label>
                <select id="room-status" v-model="form.status" class="select" :disabled="saving">
                  <option value="AVAILABLE">可预约</option>
                  <option value="DISABLED">已停用</option>
                </select>
              </div>

              <div class="field field--full">
                <label class="field-label" for="room-equipment">设备</label>
                <input
                  id="room-equipment"
                  v-model="form.equipment"
                  class="input"
                  type="text"
                  placeholder="如：投影仪，白板，视频会议"
                  :disabled="saving"
                />
                <p class="field-hint">多个设备用中文逗号、英文逗号或顿号分隔。</p>
              </div>
            </div>

            <div class="modal__foot">
              <el-button class="btn btn--primary" native-type="submit" :disabled="saving">
                {{ saving ? '保存中…' : '保存会议室' }}
              </el-button>
              <el-button
                class="btn btn--ghost"
                native-type="button"
                :disabled="saving"
                @click="closeForm"
              >
                返回列表
              </el-button>
            </div>
          </form>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="deleteTarget" class="modal-backdrop" @click.self="closeDelete">
        <div
          class="modal modal--small"
          role="dialog"
          aria-modal="true"
          aria-labelledby="room-delete-title"
        >
          <div class="modal__head">
            <h2 id="room-delete-title" class="modal__title">删除会议室</h2>
            <p class="muted">{{ deleteTarget.name }} · {{ deleteTarget.location }}</p>
          </div>

          <div v-if="deleteError" class="alert alert--error" role="alert">
            {{ deleteError }}
          </div>

          <p>
            删除后不可恢复；若该会议室仍存在有效预约，系统会拒绝删除并提示先处理预约。
          </p>

          <div class="modal__foot">
            <el-button
              class="btn btn--danger"
              native-type="button"
              :disabled="deleting"
              @click="handleDelete"
            >
              {{ deleting ? '删除中…' : '确认删除' }}
            </el-button>
            <el-button
              class="btn btn--ghost"
              native-type="button"
              :disabled="deleting"
              @click="closeDelete"
            >
              暂不删除
            </el-button>
          </div>
        </div>
      </div>
    </Teleport>
  </section>
</template>
