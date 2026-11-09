<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { createUser, listAdminUsers, updateUser } from '@/api/admin'
import { sessionUser } from '@/session/session'
import type { AdminUserVO, UserRole, UserStatus } from '@/types/api'
import { formatDateTime } from '@/utils/datetime'
import { toUserMessage } from '@/utils/errors'
import { roleLabel, userStatusLabel, userStatusTagType } from '@/utils/labels'

type FieldName = 'username' | 'name' | 'password'

const users = ref<AdminUserVO[]>([])
const loading = ref(true)
const loadError = ref('')
const notice = ref('')

const formOpen = ref(false)
const editingUser = ref<AdminUserVO | null>(null)
const form = reactive({
  username: '',
  name: '',
  department: '',
  role: 'USER' as UserRole,
  password: '',
  status: 'ACTIVE' as UserStatus
})
const errors = reactive<Record<FieldName, string>>({
  username: '',
  name: '',
  password: ''
})
const formError = ref('')
const saving = ref(false)

const isSelf = computed(
  () => Boolean(editingUser.value && sessionUser.value?.id === editingUser.value.id)
)

function clearErrors() {
  errors.username = ''
  errors.name = ''
  errors.password = ''
  formError.value = ''
}

async function loadUsers() {
  loading.value = true
  loadError.value = ''

  try {
    users.value = await listAdminUsers()
  } catch (error) {
    users.value = []
    loadError.value = toUserMessage(error, '用户列表加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  notice.value = ''
  clearErrors()
  editingUser.value = null
  formOpen.value = true
  form.username = ''
  form.name = ''
  form.department = ''
  form.role = 'USER'
  form.password = ''
  form.status = 'ACTIVE'
}

function openEdit(user: AdminUserVO) {
  notice.value = ''
  clearErrors()
  editingUser.value = user
  formOpen.value = true
  form.username = user.username
  form.name = user.name
  form.department = user.department || ''
  form.role = user.role
  form.password = ''
  form.status = user.status
}

function closeForm() {
  if (saving.value) return
  formOpen.value = false
  editingUser.value = null
  clearErrors()
}

async function focusFirstError() {
  await nextTick()
  document.querySelector<HTMLElement>('.modal [aria-invalid="true"]')?.focus()
}

function validate(): boolean {
  clearErrors()

  if (!editingUser.value) {
    if (!form.username.trim()) {
      errors.username = '请填写账号。'
    } else if (!/^[A-Za-z0-9_]{3,20}$/.test(form.username.trim())) {
      errors.username = '账号需为 3-20 位字母、数字或下划线。'
    }

    if (!form.password) {
      errors.password = '请填写初始密码。'
    } else if (form.password.length < 6) {
      errors.password = '初始密码至少 6 位。'
    }
  }

  if (!form.name.trim()) errors.name = '请填写姓名。'

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

  try {
    if (editingUser.value) {
      const updated = await updateUser(editingUser.value.id, {
        name: form.name.trim(),
        department: form.department.trim(),
        role: form.role,
        status: form.status
      })
      notice.value = `用户「${updated.username}」已更新。`
    } else {
      const created = await createUser({
        username: form.username.trim(),
        name: form.name.trim(),
        department: form.department.trim(),
        role: form.role,
        password: form.password
      })
      notice.value = `用户「${created.username}」已创建，可使用初始密码登录。`
    }
    formOpen.value = false
    editingUser.value = null
    await loadUsers()
  } catch (error) {
    formError.value = toUserMessage(error, '用户保存失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadUsers)
</script>

<template>
  <section class="page">
    <header class="page-head page-head--row">
      <div>
        <p class="eyebrow">V1.0 · 管理端</p>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">
          支持创建账号、修改姓名/部门/角色与启停账号。新增、启停与角色变更都会写入操作日志（NF-14）。
        </p>
      </div>
      <div class="page-head__actions">
        <el-button
          class="btn btn--ghost"
          native-type="button"
          :disabled="loading"
          @click="loadUsers"
        >
          刷新
        </el-button>
        <el-button class="btn btn--primary" native-type="button" @click="openCreate">
          新增用户
        </el-button>
      </div>
    </header>

    <div v-if="notice" class="alert alert--success" role="status">
      {{ notice }}
    </div>

    <div v-if="loadError" class="alert alert--error" role="alert">
      <div>{{ loadError }}</div>
      <el-button class="btn btn--ghost btn--small" native-type="button" @click="loadUsers">
        重试
      </el-button>
    </div>

    <div class="card table-card">
      <div class="table-card__head">
        <span>用户列表</span>
        <span class="muted">{{ loading ? '加载中…' : `共 ${users.length} 个账号` }}</span>
      </div>

      <div v-if="loading" class="loading">正在加载用户…</div>

      <div v-else-if="users.length === 0" class="empty-panel">
        <p>暂无用户。</p>
      </div>

      <div v-else class="table-wrap">
        <table class="table">
          <caption class="sr-only">用户管理列表</caption>
          <thead>
            <tr>
              <th>账号</th>
              <th>姓名</th>
              <th>部门</th>
              <th>角色</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id" :data-user-id="user.id">
              <td class="mono">{{ user.username }}</td>
              <td>{{ user.name }}</td>
              <td>{{ user.department || '—' }}</td>
              <td>{{ roleLabel(user.role) }}</td>
              <td>
                <el-tag :type="userStatusTagType(user.status)" size="small" disable-transitions>
                  {{ userStatusLabel(user.status) }}
                </el-tag>
              </td>
              <td>{{ user.createdAt ? formatDateTime(user.createdAt) : '—' }}</td>
              <td class="table__actions">
                <el-button
                  class="btn btn--ghost btn--small"
                  native-type="button"
                  @click="openEdit(user)"
                >
                  编辑
                </el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Teleport to="body">
      <div v-if="formOpen" class="modal-backdrop" @click.self="closeForm">
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="user-form-title">
          <div class="modal__head">
            <h2 id="user-form-title" class="modal__title">
              {{ editingUser ? '编辑用户' : '新增用户' }}
            </h2>
            <p class="muted">
              {{ editingUser ? `正在编辑 ${editingUser.username}` : '创建后可直接使用初始密码登录' }}
            </p>
          </div>

          <div v-if="formError" class="alert alert--error" role="alert">
            {{ formError }}
          </div>

          <form class="form" novalidate @submit.prevent="handleSave">
            <div class="form-grid">
              <div class="field">
                <label class="field-label" for="user-username">
                  账号 <span class="required">*</span>
                </label>
                <input
                  id="user-username"
                  v-model="form.username"
                  class="input"
                  :class="{ 'input--error': errors.username }"
                  :aria-invalid="Boolean(errors.username)"
                  type="text"
                  maxlength="20"
                  :disabled="saving || Boolean(editingUser)"
                  :placeholder="editingUser ? '' : '3-20 位字母、数字或下划线'"
                />
                <p v-if="errors.username" class="field-error">{{ errors.username }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="user-name">
                  姓名 <span class="required">*</span>
                </label>
                <input
                  id="user-name"
                  v-model="form.name"
                  class="input"
                  :class="{ 'input--error': errors.name }"
                  :aria-invalid="Boolean(errors.name)"
                  type="text"
                  maxlength="30"
                  :disabled="saving"
                />
                <p v-if="errors.name" class="field-error">{{ errors.name }}</p>
              </div>

              <div class="field">
                <label class="field-label" for="user-department">部门</label>
                <input
                  id="user-department"
                  v-model="form.department"
                  class="input"
                  type="text"
                  maxlength="50"
                  :disabled="saving"
                />
              </div>

              <div class="field">
                <label class="field-label" for="user-role">角色</label>
                <select
                  id="user-role"
                  v-model="form.role"
                  class="select"
                  :disabled="saving || isSelf"
                >
                  <option value="USER">普通用户</option>
                  <option value="ADMIN">管理员</option>
                </select>
                <p v-if="isSelf" class="field-hint">不能修改当前登录账号的角色。</p>
              </div>

              <div v-if="!editingUser" class="field">
                <label class="field-label" for="user-password">
                  初始密码 <span class="required">*</span>
                </label>
                <input
                  id="user-password"
                  v-model="form.password"
                  class="input"
                  :class="{ 'input--error': errors.password }"
                  :aria-invalid="Boolean(errors.password)"
                  type="password"
                  autocomplete="new-password"
                  :disabled="saving"
                />
                <p v-if="errors.password" class="field-error">{{ errors.password }}</p>
                <p v-else class="field-hint">至少 6 位。</p>
              </div>

              <div v-if="editingUser" class="field">
                <label class="field-label" for="user-status">状态</label>
                <select
                  id="user-status"
                  v-model="form.status"
                  class="select"
                  :disabled="saving || isSelf"
                >
                  <option value="ACTIVE">正常</option>
                  <option value="DISABLED">已停用</option>
                </select>
                <p v-if="isSelf" class="field-hint">不能停用当前登录账号。</p>
              </div>
            </div>

            <div class="modal__foot">
              <el-button class="btn btn--primary" native-type="submit" :disabled="saving">
                {{ saving ? '保存中…' : '保存用户' }}
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
  </section>
</template>
