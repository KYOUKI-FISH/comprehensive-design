<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { cancelBooking, listMyBookings, modifyBooking } from '@/api/booking'
import { listRooms } from '@/api/room'
import type { BookingItemVO, ReservationStatus, RoomVO } from '@/types/api'

/**
 * 我的预约页（第 10 周；对应 FR-11 查看我的预约、FR-12 修改、FR-13 取消、NF-08 状态四态）。
 *
 * <p>★ 三处容易做错的地方，本页的处理方式写在下面，改代码前先看一眼：</p>
 * <ol>
 *   <li><b>状态不由前端算</b>：{@code status} 一律用后端返回的四态（PENDING／ONGOING／
 *       FINISHED／CANCELLED）。前端按时间自己算会出现"过去但库里还是 PENDING"两边不一致，
 *       也绕开了 NF-08 的单一口径。</li>
 *   <li><b>"能不能改／能不能取消"也不由前端拍板</b>：FR-12／FR-13 的 30 分钟口径、
 *       是否已取消、是否本人，最终都由后端判定。前端只把明显不可操作的按钮置灰
 *       （省一次无意义的往返），<b>不能</b>把它当成防线——后端拒了就把中文提示原样显示出来。</li>
 *   <li><b>取消后要点得动</b>：取消成功要重新拉列表，让该条状态立刻变成 CANCELLED，
 *       同时会议室详情页那一格应重新可订（NF-08 时段立即释放）。</li>
 * </ol>
 */
const router = useRouter()

const loading = ref(false)
const items = ref<BookingItemVO[]>([])
const rooms = ref<RoomVO[]>([])

/** 状态筛选（纯前端过滤，数据量小，不额外加接口参数） */
const statusFilter = ref<ReservationStatus | ''>('')

const STATUS_META: Record<ReservationStatus, { label: string; type: 'primary' | 'success' | 'info' | 'danger' }> = {
  PENDING: { label: '待开始', type: 'primary' },
  ONGOING: { label: '进行中', type: 'success' },
  FINISHED: { label: '已结束', type: 'info' },
  CANCELLED: { label: '已取消', type: 'danger' }
}

const filteredItems = computed(() =>
  statusFilter.value ? items.value.filter((it) => it.status === statusFilter.value) : items.value
)

/** 各状态条数，页面顶部给一行概览——验收时一眼看出四态都对 */
const counts = computed(() => {
  const base: Record<string, number> = { PENDING: 0, ONGOING: 0, FINISHED: 0, CANCELLED: 0 }
  for (const it of items.value) {
    base[it.status] = (base[it.status] ?? 0) + 1
  }
  return base
})

/** yyyy-MM-dd HH:mm:ss / HH:mm → 只留 HH:mm 用于表格展示 */
function fmtTime(value: string): string {
  return value && value.length >= 16 ? value.substring(11, 16) : value
}

/** yyyy-MM-dd HH:mm:ss → 只留日期部分 */
function fmtDate(value: string): string {
  return value && value.length >= 10 ? value.substring(0, 10) : value
}

async function load(): Promise<void> {
  loading.value = true
  try {
    items.value = await listMyBookings()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载我的预约失败')
  } finally {
    loading.value = false
  }
}

async function loadRooms(): Promise<void> {
  try {
    // 修改时要改会议室，所以把启用中的会议室取出来做下拉
    rooms.value = await listRooms({ status: 'ENABLED' })
  } catch {
    // 会议室列表取不到不影响主流程（只是改不了会议室），静默
    rooms.value = []
  }
}

/* ------------------------------------------------------------------ 修改（FR-12） */

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
/** 正在修改的那条；null 表示弹窗不是打开状态 */
const editing = ref<BookingItemVO | null>(null)

const form = reactive({
  roomId: undefined as number | undefined,
  date: '',
  startTime: '',
  endTime: '',
  topic: '',
  attendeeCount: 1 as number | undefined,
  contact: '',
  purpose: ''
})

/** FR-08 时间规则（与提交页同口径：结束晚于开始、不跨天、单次不超过 8 小时） */
const MAX_DURATION_MINUTES = 8 * 60

function toMinutes(value: string): number | null {
  const matched = /^(\d{1,2}):(\d{2})$/.exec(value)
  if (!matched) {
    return null
  }
  const hour = Number(matched[1])
  const minute = Number(matched[2])
  if (hour > 23 || minute > 59) {
    return null
  }
  return hour * 60 + minute
}

function validateTimeRule(): string | null {
  if (!form.startTime || !form.endTime) {
    return null
  }
  const start = toMinutes(form.startTime)
  const end = toMinutes(form.endTime)
  if (start === null || end === null) {
    return '时间格式不正确，应为 HH:mm'
  }
  if (end <= start) {
    return '结束时间必须晚于开始时间，且单次预约不能跨天'
  }
  if (end - start > MAX_DURATION_MINUTES) {
    return '单次预约不能超过 8 小时'
  }
  return null
}

const rules: FormRules<typeof form> = {
  roomId: [{ required: true, message: '请选择会议室', trigger: 'change' }],
  date: [{ required: true, message: '请选择预约日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule, _value, callback) => {
        const message = validateTimeRule()
        if (message) {
          callback(new Error(message))
          return
        }
        callback()
      },
      trigger: 'change'
    }
  ],
  topic: [{ required: true, message: '请填写会议主题', trigger: 'blur' }],
  attendeeCount: [{ required: true, message: '请填写参会人数', trigger: 'blur' }],
  contact: [{ required: true, message: '请填写联系人', trigger: 'blur' }]
}

/**
 * 打开修改弹窗。
 *
 * <p>★ 只有 PENDING 给"修改"入口：已开始的会改不了（FR-12 的 30 分钟口径后端也会拦，
 * 这里先不放行没意义）。但<b>是否真的超过 30 分钟判断不了</b>——那要服务器时钟，
 * 所以仍让用户点，由后端给出准确的中文拒绝原因。</p>
 */
function openEdit(item: BookingItemVO): void {
  editing.value = item
  form.roomId = item.roomId
  form.date = fmtDate(item.startTime) || item.date
  form.startTime = fmtTime(item.startTime)
  form.endTime = fmtTime(item.endTime)
  form.topic = item.topic
  form.attendeeCount = item.attendeeCount
  form.contact = item.contact
  form.purpose = item.purpose ?? ''
  dialogVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!formRef.value || !editing.value) {
    return
  }
  const timeRuleMessage = validateTimeRule()
  if (timeRuleMessage) {
    ElMessage.warning(timeRuleMessage)
    void formRef.value.validateField('endTime').catch(() => undefined)
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  try {
    await modifyBooking(editing.value.id, {
      roomId: form.roomId as number,
      date: form.date,
      startTime: `${form.date} ${form.startTime}`,
      endTime: `${form.date} ${form.endTime}`,
      topic: form.topic,
      attendeeCount: form.attendeeCount as number,
      contact: form.contact,
      purpose: form.purpose || undefined
    })
    ElMessage.success('修改成功')
    dialogVisible.value = false
    // ★ 重新拉列表，让表格里的时段／主题跟着变（"库里跟着变"要能从页面上看出来）
    await load()
  } catch (error) {
    // 30 分钟口径／已开始／冲突等拒绝原因都是后端给的中文，原样显示（NF-10）
    ElMessage.error(error instanceof Error ? error.message : '修改失败')
  } finally {
    submitting.value = false
  }
}

/* ------------------------------------------------------------------ 取消（FR-13） */

async function onCancel(item: BookingItemVO): Promise<void> {
  let reason = ''
  try {
    // 原因选填：用户自己取消不强制填（管理员强制取消才必填，FR-16）
    const result = await ElMessageBox.prompt(
      `确定取消预约「${item.reservationNo}」吗？取消后该时段立即释放，可以重新预订。`,
      '取消预约',
      {
        confirmButtonText: '确定取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '取消原因（选填）',
        inputValidator: () => true,
        type: 'warning'
      }
    )
    reason = (result.value ?? '').trim()
  } catch {
    // 用户点了"再想想"或关掉弹窗 → 什么都不做
    return
  }

  try {
    await cancelBooking(item.id, reason || undefined)
    ElMessage.success('已取消，该时段已释放')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '取消失败')
  }
}

/** 跳会议室详情页看该时段的占用情况（NF-08：取消后那一格应重新可订） */
function goDetail(item: BookingItemVO): void {
  void router.push({ name: 'room-detail', params: { id: String(item.roomId) }, query: { date: fmtDate(item.startTime) } })
}

onMounted(async () => {
  await Promise.all([load(), loadRooms()])
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">我的预约</h2>
      <span class="page-hint">对应 FR-11 查看 / FR-12 修改 / FR-13 取消 / NF-08 状态四态</span>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="statusFilter" size="default">
          <el-radio-button label="">全部（{{ items.length }}）</el-radio-button>
          <el-radio-button label="PENDING">待开始（{{ counts.PENDING }}）</el-radio-button>
          <el-radio-button label="ONGOING">进行中（{{ counts.ONGOING }}）</el-radio-button>
          <el-radio-button label="FINISHED">已结束（{{ counts.FINISHED }}）</el-radio-button>
          <el-radio-button label="CANCELLED">已取消（{{ counts.CANCELLED }}）</el-radio-button>
        </el-radio-group>
        <div class="toolbar-right">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="filteredItems" border stripe empty-text="暂无预约记录">
        <el-table-column prop="reservationNo" label="预约号" width="150" />
        <el-table-column prop="roomName" label="会议室" min-width="140" />
        <el-table-column label="日期" width="120">
          <template #default="{ row }">{{ fmtDate(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="时段" width="130">
          <template #default="{ row }">{{ fmtTime(row.startTime) }} – {{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="topic" label="会议主题" min-width="140" />
        <el-table-column prop="attendeeCount" label="人数" width="70" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status as ReservationStatus].type" disable-transitions>
              {{ STATUS_META[row.status as ReservationStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              @click="openEdit(row)"
            >修改</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="danger"
              @click="onCancel(row)"
            >取消</el-button>
            <el-button link type="info" @click="goDetail(row)">看占用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert type="info" :closable="false" show-icon class="page-tip">
        <template #title>
          可修改／可取消只对「待开始」开放，且需<b>距会议开始超过 30 分钟</b>（FR-12／FR-13）。
          列表里的状态由后端四态推导返回，页面不按时间自行计算（NF-08）。
        </template>
      </el-alert>
    </el-card>

    <el-dialog v-model="dialogVisible" title="修改预约" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="会议室" prop="roomId">
          <el-select v-model="form.roomId" placeholder="请选择会议室" style="width: 100%">
            <el-option
              v-for="room in rooms"
              :key="room.id"
              :label="`${room.name}（${room.location}，${room.capacity} 人）`"
              :value="room.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期" prop="date">
          <el-date-picker v-model="form.date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-select v-model="form.startTime" start="00:00" step="00:30" end="23:30" placeholder="选择开始时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-select v-model="form.endTime" start="00:30" step="00:30" end="23:59" placeholder="选择结束时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="会议主题" prop="topic">
          <el-input v-model="form.topic" maxlength="100" show-word-limit placeholder="如：需求评审" />
        </el-form-item>
        <el-form-item label="参会人数" prop="attendeeCount">
          <el-input-number v-model="form.attendeeCount" :min="1" :max="500" />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="form.contact" maxlength="50" placeholder="如：李知渔" />
        </el-form-item>
        <el-form-item label="用途说明">
          <el-input v-model="form.purpose" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.page-tip {
  margin-top: 12px;
}
</style>
