<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listRooms } from '@/api/room'
import type { RoomVO } from '@/types/api'
import { formatEquipment, roomStatusClass, roomStatusLabel } from '@/utils/labels'

const filters = reactive({
  name: '',
  minCapacity: '',
  location: ''
})

const rooms = ref<RoomVO[]>([])
const loading = ref(false)
const errorMessage = ref('')
const capacityError = ref('')

async function loadRooms() {
  capacityError.value = ''

  let minCapacity: number | undefined
  // type="number" 的 v-model 会被 Vue 自动转成数字，这里统一按字符串判断空值
  const minCapacityText = String(filters.minCapacity).trim()
  if (minCapacityText) {
    const value = Number(minCapacityText)
    if (!Number.isInteger(value) || value <= 0) {
      capacityError.value = '容量需填写正整数。'
      return
    }
    minCapacity = value
  }

  loading.value = true
  errorMessage.value = ''

  try {
    rooms.value = await listRooms({
      name: filters.name,
      location: filters.location,
      minCapacity
    })
  } catch (error) {
    rooms.value = []
    errorMessage.value =
      error instanceof Error ? error.message : '会议室查询失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function resetFilters() {
  filters.name = ''
  filters.minCapacity = ''
  filters.location = ''
  await loadRooms()
}

onMounted(loadRooms)
</script>

<template>
  <section class="page">
    <header class="page-head">
      <p class="eyebrow">第9周 · 会议室查询</p>
      <h1 class="page-title">会议室列表</h1>
      <p class="page-subtitle">
        支持按名称、最小容量和位置筛选。列表数据来自后端接口，不使用前端假数据。
      </p>
    </header>

    <form class="card card--pad filters" novalidate @submit.prevent="loadRooms">
      <div class="field">
        <label class="field-label" for="filter-name">名称</label>
        <input
          id="filter-name"
          v-model="filters.name"
          class="input"
          type="text"
          placeholder="如：301"
        />
      </div>

      <div class="field">
        <label class="field-label" for="filter-capacity">最小容量（人）</label>
        <input
          id="filter-capacity"
          v-model="filters.minCapacity"
          class="input"
          :class="{ 'input--error': capacityError }"
          type="number"
          min="1"
          step="1"
          placeholder="如：20"
        />
        <p v-if="capacityError" class="field-error">{{ capacityError }}</p>
      </div>

      <div class="field">
        <label class="field-label" for="filter-location">位置</label>
        <input
          id="filter-location"
          v-model="filters.location"
          class="input"
          type="text"
          placeholder="如：3号楼"
        />
      </div>

      <div class="filters__actions">
        <button class="btn btn--primary" type="submit" :disabled="loading">
          {{ loading ? '查询中…' : '查询' }}
        </button>
        <button class="btn btn--ghost" type="button" :disabled="loading" @click="resetFilters">
          重置
        </button>
      </div>
    </form>

    <div v-if="errorMessage" class="alert alert--error" role="alert">
      <div>{{ errorMessage }}</div>
      <button class="btn btn--ghost btn--small" type="button" @click="loadRooms">重试</button>
    </div>

    <div class="card table-card">
      <div class="table-card__head">
        <span>查询结果</span>
        <span class="muted">{{ loading ? '加载中…' : `共 ${rooms.length} 间` }}</span>
      </div>

      <div v-if="loading" class="loading">正在加载会议室…</div>

      <div v-else-if="rooms.length === 0" class="empty-panel">
        <p>未找到符合条件的会议室。</p>
        <p class="muted">可以放宽名称、容量或位置条件后重试。</p>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">会议室查询结果</caption>
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
            <tr v-for="room in rooms" :key="room.id">
              <td><strong>{{ room.name }}</strong></td>
              <td>{{ room.location }}</td>
              <td>{{ room.capacity }} 人</td>
              <td>{{ formatEquipment(room.equipment) }}</td>
              <td>
                <span :class="roomStatusClass(room.status)">
                  {{ roomStatusLabel(room.status) }}
                </span>
              </td>
              <td class="table__actions">
                <RouterLink class="btn btn--ghost btn--small" :to="`/rooms/${room.id}`">
                  进详情
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
