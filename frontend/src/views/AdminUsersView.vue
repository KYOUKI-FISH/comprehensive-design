<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { createUser, listUsers, updateUser } from '@/api/admin'
import type { UserVO } from '@/types/api'

/**
 * 用户管理（管理员，第 10 周；对应 FR-14 新建与停用用户、NF-14 操作留痕、NF-05 口令不外泄）。
 *
 * <p>★ 本页有一条<b>刻意的"功能缺失"</b>，不是没做完：<b>没有删除用户</b>。
 * FR-14 只要求"新建与停用"。而且 {@code reservation.user_id} 与
 * {@code operation_log.operator_id} 都是 {@code ON DELETE RESTRICT}，
 * 删用户会破坏历史预约与审计日志的归属——"停用"才是正确的下线方式。
 * 演示时若被问"怎么删用户"，答案就是这个，别现场想。</p>
 *
 * <p>★ 出参<b>不含 password</b>：后端 {@code UserVO} 从类型层面就没有该字段
 * （比"记得 setPassword(null)"可靠）。前端也不缓存口令——新建时填的那次，
 * 提交完就清空。</p>
 */
const loading = ref(false)
const rows = ref<UserVO[]>([])

const query = reactive<{ status: 'ACTIVE' | 'DISABLED' | undefined; role: 'USER' | 'ADMIN' | undefined }>({
  status: undefined,
  role: undefined
})

async function load(): Promise<void> {
  loading.value = true
  try {
    rows.value = await listUsers({ status: query.status, role: query.role })
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

/* ------------------------------------------------------------------ 新建用户（FR-14） */

const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()

const createForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  role: 'USER' as 'USER' | 'ADMIN'
})

const createRules: FormRules<typeof createForm> = {
  username: [{ required: true, message: '请填写账号', trigger: 'blur' }],
  password: [
    { required: true, message: '请填写初始口令', trigger: 'blur' },
    { min: 6, max: 32, message: '口令长度 6–32 位', trigger: 'blur' }
  ]
}

function openCreate(): void {
  createForm.username = ''
  createForm.password = ''
  createForm.realName = ''
  createForm.phone = ''
  createForm.role = 'USER'
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!createFormRef.value) {
    return
  }
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  creating.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      password: createForm.password,
      realName: createForm.realName.trim() || undefined,
      phone: createForm.phone.trim() || undefined,
      role: createForm.role
    })
    ElMessage.success('新建成功（已写入操作日志）')
    createVisible.value = false
    // ★ 提交完立刻清掉前端持有的明文口令
    createForm.password = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '新建用户失败')
  } finally {
    creating.value = false
  }
}

/* -------------------------------------------------------- 编辑用户（改名／改角色／重置口令） */

const editVisible = ref(false)
const editing = ref(false)
const editFormRef = ref<FormInstance>()
const editingRow = ref<UserVO | null>(null)

const editForm = reactive({
  realName: '',
  phone: '',
  role: 'USER' as 'USER' | 'ADMIN',
  /** 留空表示不重置口令 */
  password: ''
})

const editRules: FormRules<typeof editForm> = {
  password: [
    {
      validator: (_rule, value, callback) => {
        if (value && (value.length < 6 || value.length > 32)) {
          callback(new Error('口令长度 6–32 位'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

function openEdit(row: UserVO): void {
  editingRow.value = row
  editForm.realName = row.realName ?? ''
  editForm.phone = row.phone ?? ''
  editForm.role = row.role
  editForm.password = ''
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  if (!editFormRef.value || !editingRow.value) {
    return
  }
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  editing.value = true
  try {
    await updateUser(editingRow.value.id, {
      realName: editForm.realName.trim() || undefined,
      phone: editForm.phone.trim() || undefined,
      role: editForm.role,
      password: editForm.password || undefined
    })
    ElMessage.success('修改成功（已写入操作日志）')
    editVisible.value = false
    editForm.password = ''
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改用户失败')
  } finally {
    editing.value = false
  }
}

/* ----------------------------------------------------------------------- 启用／停用 */

async function toggleStatus(row: UserVO): Promise<void> {
  const next = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const label = next === 'DISABLED' ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${label}账号「${row.username}」吗？${next === 'DISABLED' ? '停用后该账号无法登录，已有预约记录保留。' : ''}`,
      `${label}用户`,
      { confirmButtonText: `确定${label}`, cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateUser(row.id, { status: next })
    ElMessage.success(`已${label}（已写入操作日志）`)
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : `${label}失败`)
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">用户管理</h2>
      <span class="page-hint">对应 FR-14 新建与停用用户 / NF-14 操作留痕 / NF-05 口令不外泄</span>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <div class="toolbar-left">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px" @change="load">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
          <el-select v-model="query.role" clearable placeholder="全部角色" style="width: 130px" @change="load">
            <el-option label="普通用户" value="USER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </div>
        <div class="toolbar-right">
          <el-button :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" @click="openCreate">新建用户</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe empty-text="暂无用户">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="username" label="账号" width="130" />
        <el-table-column prop="realName" label="姓名" width="110">
          <template #default="{ row }">{{ row.realName || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'warning' : 'info'" disable-transitions>
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="电话" min-width="130">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" disable-transitions>
              {{ row.status === 'ACTIVE' ? '正常' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert type="info" :closable="false" show-icon class="page-tip">
        <template #title>
          本系统<b>不删除用户</b>：FR-14 只要求「新建与停用」，且预约记录与操作日志都通过
          <code>ON DELETE RESTRICT</code> 保留归属。下线一律用「停用」。
        </template>
      </el-alert>
    </el-card>

    <!-- 新建 -->
    <el-dialog v-model="createVisible" title="新建用户" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="96px">
        <el-form-item label="账号" prop="username">
          <el-input v-model="createForm.username" maxlength="50" placeholder="如：user3" />
        </el-form-item>
        <el-form-item label="初始口令" prop="password">
          <el-input v-model="createForm.password" type="password" show-password maxlength="32" placeholder="6–32 位，后端 BCrypt 加密后落库" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="createForm.realName" maxlength="50" placeholder="选填" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="createForm.phone" maxlength="20" placeholder="选填" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="createForm.role">
            <el-radio label="USER">普通用户</el-radio>
            <el-radio label="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="520px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="96px">
        <el-form-item label="账号">
          <el-input :model-value="editingRow?.username" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" maxlength="50" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="editForm.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="editForm.role">
            <el-radio label="USER">普通用户</el-radio>
            <el-radio label="ADMIN">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重置口令" prop="password">
          <el-input v-model="editForm.password" type="password" show-password maxlength="32" placeholder="留空表示不修改" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
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

.toolbar-left,
.toolbar-right {
  display: flex;
  gap: 8px;
}

.page-tip {
  margin-top: 12px;
}
</style>
