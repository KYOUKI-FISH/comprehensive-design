<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createBooking } from '@/api/booking'
import { getOccupancy, listRooms } from '@/api/room'
import { ApiBusinessError } from '@/api/http'
import type { OccupiedSlotVO, RoomVO } from '@/types/api'

/**
 * 预约表单页（第 9 周；对应 FR-07 提交预约、FR-08 时间规则、FR-09 冲突检测、
 * FR-10 冲突提示、FR-18 失败后保留已填信息、FR-06 停用会议室）。
 *
 * <p>★ 两个必须做对的地方：
 * <ol>
 *   <li><b>FR-10</b>：提交冲突时，后端 409 会在 {@code data.occupiedSlots} 里返回
 *       "被占用的时段"，本页要把它<strong>展示出来</strong>——只说"冲突了"是不够的；</li>
 *   <li><b>FR-18</b>：预约因冲突失败后，<strong>保留用户已填的内容</strong>，
 *       方便改个时间重提。这一条最容易漏——清空表单就等于没做。</li>
 * </ol>
 * </p>
 */
const route = useRoute()
const router = useRouter()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const rooms = ref<RoomVO[]>([])
const occupiedSlots = ref<OccupiedSlotVO[]>([])
/** 冲突时展示的被占用时段（FR-10） */
const conflictSlots = ref<OccupiedSlotVO[]>([])

function formatDate(d: Date): string {
  const p = (n: number): string => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

const form = reactive({
  roomId: Number(route.query.roomId) || undefined as number | undefined,
  date: (route.query.date as string) || formatDate(new Date()),
  startTime: '',
  endTime: '',
  topic: '',
  attendeeCount: 1 as number | undefined,
  contact: '',
  purpose: ''
})

const rules: FormRules<typeof form> = {
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  date: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  topic: [{ required: true, message: '请填写会议主题', trigger: 'blur' }],
  attendeeCount: [{ required: true, message: '请填写参会人数', trigger: 'blur' }],
  contact: [{ required: true, message: '请填写联系人', trigger: 'blur' }]
}

/** 停用的会议室不出现在选项里（FR-06） */
const bookableRooms = computed(() => rooms.value.filter((r) => r.status === 'ENABLED'))

function fmtTime(value: string): string {
  return value.length >= 16 ? value.substring(11, 16) : value
}

async function loadRooms(): Promise<void> {
  try {
    const all = await listRooms({})
    rooms.value = all
    if (form.roomId === undefined && bookableRooms.value.length > 0) {
      form.roomId = bookableRooms.value[0].id
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载会议室失败')
  }
}

async function loadOccupancy(): Promise<void> {
  conflictSlots.value = []
  if (!form.roomId || !form.date) {
    occupiedSlots.value = []
    return
  }
  try {
    const occupancy = await getOccupancy(form.roomId, form.date)
    occupiedSlots.value = occupancy.occupiedSlots ?? []
  } catch (error) {
    // 占用时段查不到不影响提交，这里静默处理
    occupiedSlots.value = []
  }
}

async function onSubmit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    const result = await createBooking({
      roomId: form.roomId as number,
      date: form.date,
      startTime: `${form.date} ${form.startTime}`,
      endTime: `${form.date} ${form.endTime}`,
      topic: form.topic,
      attendeeCount: form.attendeeCount as number,
      contact: form.contact,
      purpose: form.purpose || undefined
    })
    ElMessage.success(`预约成功，预约号：${result.reservationNo}`)
    // 成功后清空表单（FR-18 只要求"失败时保留"，成功可以清）
    form.startTime = ''
    form.endTime = ''
    form.topic = ''
    form.attendeeCount = 1
    form.contact = ''
    form.purpose = ''
    await loadOccupancy()
  } catch (error) {
    // ★ FR-10：冲突时展示后端返回的被占用时段
    if (error instanceof ApiBusinessError && error.code === 409) {
      const data = error.data as { occupiedSlots?: OccupiedSlotVO[] } | null
      conflictSlots.value = data?.occupiedSlots ?? []
      ElMessage.error(error.message)
    } else {
      ElMessage.error(error instanceof Error ? error.message : '提交失败')
    }
    // ★ FR-18：失败时**保留已填内容**——上面不重置 form 即可
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await loadRooms()
  await loadOccupancy()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">提交预约</h2>
      <span class="page-hint">对应 FR-07 提交预约 / FR-09 冲突检测 / FR-10 冲突提示 / FR-18 失败保留已填信息</span>
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-width="90px"
            @submit.prevent="onSubmit"
          >
            <el-form-item label="会议室" prop="roomId">
              <el-select v-model="form.roomId" placeholder="请选择会议室" style="width: 100%">
                <el-option
                  v-for="r in bookableRooms"
                  :key="r.id"
                  :label="`${r.name}（${r.location}，${r.capacity}人）`"
                  :value="r.id"
                />
              </el-select>
              <div class="page-hint">
                停用的会议室不会出现在这里（FR-06）。
              </div>
            </el-form-item>

            <el-form-item label="预约日期" prop="date">
              <el-date-picker
                v-model="form.date"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
                @change="loadOccupancy"
              />
            </el-form-item>

            <el-form-item label="开始时间" prop="startTime">
              <el-time-picker
                v-model="form.startTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="选择开始时间"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="结束时间" prop="endTime">
              <el-time-picker
                v-model="form.endTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="选择结束时间"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="会议主题" prop="topic">
              <el-input v-model="form.topic" placeholder="如：项目周会" maxlength="100" show-word-limit />
            </el-form-item>

            <el-form-item label="参会人数" prop="attendeeCount">
              <el-input-number v-model="form.attendeeCount" :min="1" :max="500" />
            </el-form-item>

            <el-form-item label="联系人" prop="contact">
              <el-input v-model="form.contact" placeholder="如：张三" maxlength="50" />
            </el-form-item>

            <el-form-item label="用途说明">
              <el-input
                v-model="form.purpose"
                type="textarea"
                :rows="2"
                placeholder="选填"
                maxlength="255"
                show-word-limit
              />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" :loading="submitting" native-type="submit">提交预约</el-button>
              <el-button @click="router.back()">返回</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :span="10">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>
            <span>{{ form.date }} 该会议室已被占用的时段</span>
          </template>
          <div v-if="occupiedSlots.length">
            <el-tag
              v-for="(slot, index) in occupiedSlots"
              :key="index"
              type="warning"
              size="small"
              style="margin: 2px 4px 2px 0"
            >
              {{ fmtTime(slot.startTime) }}-{{ fmtTime(slot.endTime) }}
            </el-tag>
          </div>
          <el-empty v-else description="当日无占用" :image-size="60" />
        </el-card>

        <!-- ★ FR-10：冲突时展示"被占用的时段"，只说"冲突了"是不够的 -->
        <el-card v-if="conflictSlots.length" shadow="never">
          <template #header>
            <span class="conflict-title">本次冲突的时段</span>
          </template>
          <el-alert
            type="error"
            :closable="false"
            show-icon
            title="该会议室在此时间段已被占用"
            description="下面是被占用的具体时段，请调整时间后重新提交（已填内容已保留）。"
            style="margin-bottom: 12px"
          />
          <el-tag
            v-for="(slot, index) in conflictSlots"
            :key="index"
            type="danger"
            size="small"
            style="margin: 2px 4px 2px 0"
          >
            {{ fmtTime(slot.startTime) }}-{{ fmtTime(slot.endTime) }}
          </el-tag>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.conflict-title {
  color: #f56c6c;
  font-weight: 600;
}
</style>
