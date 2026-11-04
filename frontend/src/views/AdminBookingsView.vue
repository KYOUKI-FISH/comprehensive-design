<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { forceCancelBooking, listAllBookings, listRoomsForAdmin } from '@/api/admin'
import type { ReservationDetailVO, ReservationStatus, RoomVO } from '@/types/api'

/**
 * 全部预约（管理员，第 10 周；对应 FR-15 查看全部预约、FR-16 强制取消、NF-14 留痕）。
 *
 * <p>★ 本页有三个"演示时会被抽查"的点：</p>
 * <ol>
 *   <li><b>按会议室或日期筛一次</b>（任务书管理员第 2 步）——筛完的结果条数要与库内一致；</li>
 *   <li><b>强制取消必须填原因</b>（FR-16 原文「未填写原因时，系统拒绝处理并提示填写原因」）——
 *       所以这里做了两层：页面先拦（省一次往返），后端再拦（真正的防线）。演示时
 *       <b>要先故意不填点一次</b>，让"被拒"这个分支也走一遍，不能只演成功路径；</li>
 *   <li><b>取消完去操作日志页能查到</b>（NF-14）——那是管理员四步的第 4 步。</li>
 * </ol>
 */
const loading = ref(false)
const rows = ref<ReservationDetailVO[]>([])
const total = ref(0)
const rooms = ref<RoomVO[]>([])

const STATUS_META: Record<ReservationStatus, { label: string; type: 'primary' | 'success' | 'info' | 'danger' }> = {
  PENDING: { label: '待开始', type: 'primary' },
  ONGOING: { label: '进行中', type: 'success' },
  FINISHED: { label: '已结束', type: 'info' },
  CANCELLED: { label: '已取消', type: 'danger' }
}

/** 查询条件：一条都不填即查全部（后端参数全部可选） */
const query = reactive<{
  roomId: number | undefined
  status: ReservationStatus | undefined
  dateRange: [string, string] | null
  pageNum: number
  pageSize: number
}>({
  roomId: undefined,
  status: undefined,
  dateRange: null,
  pageNum: 1,
  pageSize: 10
})

function fmtTime(value: string): string {
  return value && value.length >= 16 ? value.substring(11, 16) : value
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const page = await listAllBookings({
      roomId: query.roomId,
      status: query.status,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
      pageNum: query.pageNum,
      pageSize: query.pageSize
    })
    rows.value = page.list ?? []
    total.value = page.total ?? 0
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查询全部预约失败')
  } finally {
    loading.value = false
  }
}

async function loadRooms(): Promise<void> {
  try {
    // 管理端要能筛到停用的会议室，所以这里不过滤 status
    rooms.value = await listRoomsForAdmin({})
  } catch {
    rooms.value = []
  }
}

function onSearch(): void {
  query.pageNum = 1
  void load()
}

function onReset(): void {
  query.roomId = undefined
  query.status = undefined
  query.dateRange = null
  query.pageNum = 1
  void load()
}

function onPageChange(page: number): void {
  query.pageNum = page
  void load()
}

/* --------------------------------------------------- 强制取消（FR-16，原因必填） */

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const target = ref<ReservationDetailVO | null>(null)
const reasonForm = reactive({ reason: '' })

const reasonRules: FormRules<typeof reasonForm> = {
  reason: [
    { required: true, message: '请填写取消原因（FR-16：未填写原因时系统拒绝处理）', trigger: 'blur' },
    { min: 5, max: 200, message: '取消原因需 5–200 个字符', trigger: 'blur' }
  ]
}

function openCancel(row: ReservationDetailVO): void {
  target.value = row
  reasonForm.reason = ''
  dialogVisible.value = true
}

async function submitCancel(): Promise<void> {
  if (!formRef.value || !target.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    // ★ 演示要点：这里就是"不填原因被拒"的第一道门；后端还有第二道
    return
  }
  submitting.value = true
  try {
    await forceCancelBooking(target.value.id, reasonForm.reason.trim())
    ElMessage.success('已强制取消，操作已写入操作日志')
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '强制取消失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await Promise.all([load(), loadRooms()])
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">全部预约</h2>
      <span class="page-hint">对应 FR-15 查看全部预约 / FR-16 强制取消（必须填原因）/ NF-14 操作留痕</span>
    </div>

    <el-card shadow="never">
      <el-form :inline="true" class="filter-form">
        <el-form-item label="会议室">
          <el-select v-model="query.roomId" clearable placeholder="全部会议室" style="width: 200px">
            <el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="room.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="待开始" value="PENDING" />
            <el-option label="进行中" value="ONGOING" />
            <el-option label="已结束" value="FINISHED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="没有符合条件的预约">
        <el-table-column prop="reservationNo" label="预约号" width="150" />
        <el-table-column prop="userName" label="预约人" width="100" />
        <el-table-column prop="roomName" label="会议室" min-width="130" />
        <el-table-column label="日期" width="115">
          <template #default="{ row }">{{ row.reserveDate }}</template>
        </el-table-column>
        <el-table-column label="时段" width="125">
          <template #default="{ row }">{{ fmtTime(row.startTime) }} – {{ fmtTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="topic" label="会议主题" min-width="130" />
        <el-table-column label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="STATUS_META[row.status as ReservationStatus].type" disable-transitions>
              {{ STATUS_META[row.status as ReservationStatus].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="取消原因" min-width="150">
          <template #default="{ row }">
            <span v-if="row.cancelReason">{{ row.cancelReason }}</span>
            <span v-else class="muted">—</span>
            <div v-if="row.operatorName" class="muted">by {{ row.operatorName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING' || row.status === 'ONGOING'"
              link
              type="danger"
              @click="openCancel(row)"
            >强制取消</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pager"
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        @current-change="onPageChange"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="强制取消预约" width="520px" destroy-on-close>
      <el-alert
        v-if="target"
        type="warning"
        :closable="false"
        show-icon
        :title="`即将取消：${target.reservationNo}（${target.userName ?? '用户'} / ${target.roomName} / ${target.reserveDate} ${fmtTime(target.startTime)}–${fmtTime(target.endTime)}）`"
        class="dialog-alert"
      />
      <el-form ref="formRef" :model="reasonForm" :rules="reasonRules" label-width="88px">
        <el-form-item label="取消原因" prop="reason">
          <el-input
            v-model="reasonForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="必填，不少于 5 个字符，如：该会议已取消，场地空出"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitCancel">确认强制取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.muted {
  color: #b1b3b8;
  font-size: 12px;
}

.dialog-alert {
  margin-bottom: 12px;
}
</style>
