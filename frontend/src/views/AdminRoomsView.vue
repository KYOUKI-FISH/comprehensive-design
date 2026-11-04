<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createRoom, deleteRoom, listRoomsForAdmin, updateRoom } from '@/api/admin'
import type { RoomVO } from '@/types/api'

/**
 * 会议室管理（管理员，第 10 周；对应 FR-03 维护会议室信息、FR-04 修改、FR-06 启用／停用）。
 *
 * <p>★ 本页要能演示一个<b>失败分支</b>：删有预约的会议室 → 必须被拒，提示
 * 「该会议室存在有效预约，请先处理预约（当前挂着 N 条记录）」。
 * 拒绝来自数据库 {@code ON DELETE RESTRICT}，不是业务代码假装拦——它证明的是
 * "历史预约不会因为删会议室而丢失归属"（FR-03）。演示时挑那间有预约的删，
 * <b>不要为了"删得掉"去挑空地</b>，那一支没有说服力。</p>
 *
 * <p>★ 停用走 {@code PUT} 改 status，不单独开接口：需求清单里停用不是独立功能，
 * 是会议室的一个字段。单独开接口会让接口总数超出第 8 周定稿的 14 条。</p>
 */
const loading = ref(false)
const rows = ref<RoomVO[]>([])

const query = reactive<{ status: 'ENABLED' | 'DISABLED' | undefined }>({ status: undefined })

async function load(): Promise<void> {
  loading.value = true
  try {
    // 管理端不过滤停用：管理员要能看到停用的那间（对比前台只显示启用中的，FR-06）
    rows.value = await listRoomsForAdmin({ status: query.status })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载会议室失败')
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------------------ 新增／编辑（FR-03／FR-04） */

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
/** null=新增，非 null=编辑该条 */
const editingId = ref<number | null>(null)

const form = reactive({
  name: '',
  location: '',
  capacity: 10 as number | undefined,
  equipment: '',
  remark: '',
  status: 'ENABLED' as 'ENABLED' | 'DISABLED'
})

const rules: FormRules<typeof form> = {
  name: [{ required: true, message: '请填写会议室名称', trigger: 'blur' }],
  location: [{ required: true, message: '请填写位置', trigger: 'blur' }],
  capacity: [{ required: true, message: '请填写可容纳人数', trigger: 'blur' }]
}

function openCreate(): void {
  editingId.value = null
  form.name = ''
  form.location = ''
  form.capacity = 10
  form.equipment = ''
  form.remark = ''
  form.status = 'ENABLED'
  dialogVisible.value = true
}

function openEdit(row: RoomVO): void {
  editingId.value = row.id
  form.name = row.name
  form.location = row.location
  form.capacity = row.capacity
  form.equipment = row.equipment ?? ''
  // ★ RoomVO 里没有 remark 字段（第 8 周定稿口径），所以编辑时留空表示"不修改 remark"，
  //   不能为了回显一行备注去改接口出参——那会让前后端字段约定多一项。
  form.remark = ''
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      location: form.location.trim(),
      capacity: form.capacity as number,
      equipment: form.equipment.trim() || undefined,
      status: form.status
    }
    if (editingId.value === null) {
      await createRoom({ ...payload, remark: form.remark.trim() || undefined })
      ElMessage.success('新增成功（已写入操作日志）')
    } else {
      // 编辑时把 remark 一并带上（有值才带，空串表示不动）
      await updateRoom(editingId.value, { ...payload, remark: form.remark.trim() || undefined })
      ElMessage.success('修改成功（已写入操作日志）')
    }
    dialogVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    submitting.value = false
  }
}

/* ---------------------------------------------------------------------- 启用／停用 */

async function toggleStatus(row: RoomVO): Promise<void> {
  const next = row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
  const label = next === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${label}会议室「${row.name}」吗？${next === 'DISABLED' ? '停用后前台不再显示，已存在的预约不受影响。' : ''}`,
      `${label}会议室`,
      { confirmButtonText: `确定${label}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateRoom(row.id, { status: next })
    ElMessage.success(`已${label}（已写入操作日志）`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${label}失败`)
  }
}

/* ------------------------------------------------------------------------ 删除 */

async function onDelete(row: RoomVO): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `确定删除会议室「${row.name}」吗？此操作不可恢复。`,
      '删除会议室',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'error' }
    )
  } catch {
    return
  }
  try {
    await deleteRoom(row.id)
    ElMessage.success('删除成功（已写入操作日志）')
    await load()
  } catch (error) {
    // ★ 关键分支：有预约时后端返回 409「该会议室存在有效预约，请先处理预约」，
    //   这里必须把中文原因原样显示出来，不能只说"删除失败"（NF-10）
    ElMessage({
      type: 'error',
      message: error instanceof Error ? error.message : '删除失败',
      duration: 5000,
      showClose: true
    })
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">会议室管理</h2>
      <span class="page-hint">对应 FR-03 维护会议室 / FR-04 修改 / FR-06 启用停用（删除受 ON DELETE RESTRICT 保护）</span>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="load">
          <el-radio-button :label="undefined">全部</el-radio-button>
          <el-radio-button label="ENABLED">启用中</el-radio-button>
          <el-radio-button label="DISABLED">已停用</el-radio-button>
        </el-radio-group>
        <div class="toolbar-right">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" @click="openCreate">新增会议室</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无会议室">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="location" label="位置" min-width="150" />
        <el-table-column prop="capacity" label="容纳人数" width="100" align="center" />
        <el-table-column prop="equipment" label="设备" min-width="150" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" disable-transitions>
              {{ row.status === 'ENABLED' ? '启用中' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ENABLED' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ENABLED' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert type="warning" :closable="false" show-icon class="page-tip">
        <template #title>
          删除一间<b>已有预约</b>的会议室会被数据库拒绝（外键 <code>ON DELETE RESTRICT</code>），
          提示「该会议室存在有效预约，请先处理预约」——这是 FR-03 的要求，不是缺陷。
        </template>
      </el-alert>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId === null ? '新增会议室' : '编辑会议室'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" placeholder="如：逸夫楼 A101" />
        </el-form-item>
        <el-form-item label="位置" prop="location">
          <el-input v-model="form.location" maxlength="100" placeholder="如：逸夫楼一层" />
        </el-form-item>
        <el-form-item label="容纳人数" prop="capacity">
          <el-input-number v-model="form.capacity" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="设备">
          <el-input v-model="form.equipment" maxlength="255" placeholder="如：投影仪、白板" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" :placeholder="editingId === null ? '选填' : '留空表示不修改备注'" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio label="ENABLED">启用</el-radio>
            <el-radio label="DISABLED">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
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
