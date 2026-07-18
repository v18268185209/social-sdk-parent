<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>账号管理</span>
          <el-button type="primary" @click="showLoginDialog = true">
            <el-icon><Plus /></el-icon> 添加账号
          </el-button>
        </div>
      </template>

      <el-table :data="accounts" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="accountName" label="账号名称" width="150" />
        <el-table-column prop="displayName" label="昵称" width="150" />
        <el-table-column prop="userId" label="用户ID" width="150" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column prop="lastLoginAt" label="最后登录" width="180" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editStatus(row)">切换状态</el-button>
            <el-button size="small" type="danger" @click="deleteAccount(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加账号对话框 -->
    <el-dialog v-model="showLoginDialog" title="添加账号" width="400px">
      <el-form :model="loginForm" label-width="80px">
        <el-form-item label="账号名称">
          <el-input v-model="loginForm.accountName" placeholder="如: 账号A" />
        </el-form-item>
        <el-form-item label="Cookie">
          <el-input v-model="loginForm.cookieHeader" type="textarea" :rows="4" placeholder="粘贴 Cookie 字符串" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="loginForm.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLoginDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleLogin">确定</el-button>
      </template>
    </el-dialog>

    <!-- 切换状态对话框 -->
    <el-dialog v-model="showStatusDialog" title="切换状态" width="300px">
      <el-form :model="statusForm" label-width="80px">
        <el-form-item label="状态">
          <el-select v-model="statusForm.status">
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
            <el-option label="冻结" value="FROZEN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStatusDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStatusUpdate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/request'

const accounts = ref([])
const loading = ref(false)
const showLoginDialog = ref(false)
const showStatusDialog = ref(false)
const submitting = ref(false)
const loginForm = ref({ accountName: '', cookieHeader: '', remark: '' })
const statusForm = ref({ id: null, status: 'ACTIVE', remark: '' })

const statusType = (s) => ({ ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[s] || 'info')

async function loadAccounts() {
  loading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

async function handleLogin() {
  if (!loginForm.value.accountName || !loginForm.value.cookieHeader) {
    ElMessage.warning('请填写账号名称和 Cookie')
    return
  }
  submitting.value = true
  try {
    const res = await api.post('/accounts/login', loginForm.value)
    if (res.success) {
      ElMessage.success('账号添加成功')
      showLoginDialog.value = false
      loginForm.value = { accountName: '', cookieHeader: '', remark: '' }
      await loadAccounts()
    }
  } catch (e) { /* handled by interceptor */ }
  finally { submitting.value = false }
}

function editStatus(row) {
  statusForm.value = { id: row.id, status: row.status, remark: row.remark || '' }
  showStatusDialog.value = true
}

async function handleStatusUpdate() {
  try {
    const res = await api.put(`/accounts/${statusForm.value.id}/status`, statusForm.value)
    if (res.success) {
      ElMessage.success('状态已更新')
      showStatusDialog.value = false
      await loadAccounts()
    }
  } catch (e) { /* ignore */ }
}

async function deleteAccount(id) {
  await ElMessageBox.confirm('确认删除该账号？', '提示', { type: 'warning' })
  try {
    await api.delete(`/accounts/${id}`)
    ElMessage.success('已删除')
    await loadAccounts()
  } catch (e) { /* ignore */ }
}

onMounted(loadAccounts)
</script>
