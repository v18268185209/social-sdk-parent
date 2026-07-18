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
        <el-table-column label="最后登录" width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.lastLoginAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" @click="editStatus(row)">切换状态</el-button>
            <el-button size="small" type="danger" @click="deleteAccount(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加账号对话框 -->
    <el-dialog v-model="showLoginDialog" title="添加账号" width="500px">
      <!-- 登录方式切换 -->
      <el-tabs v-model="loginMode" style="margin-bottom: 20px;">
        <el-tab-pane label="📱 二维码登录" name="qr" />
        <el-tab-pane label="🍪 Cookie 登录" name="cookie" />
      </el-tabs>

      <!-- 二维码登录 -->
      <div v-if="loginMode === 'qr'">
        <el-form :model="qrForm" label-width="80px">
          <el-form-item label="账号名称">
            <el-input v-model="qrForm.accountName" placeholder="如：账号A" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="qrForm.remark" placeholder="可选" />
          </el-form-item>
        </el-form>

        <div v-if="qrState.loading" style="text-align: center; padding: 20px;">
          <el-icon class="is-loading" :size="40"><Loading /></el-icon>
          <p>正在生成二维码...</p>
        </div>

        <div v-else-if="qrState.qrCodeDataUrl" class="qr-container">
          <img :src="qrState.qrCodeDataUrl" alt="二维码" class="qr-image" />
          <p class="qr-tip">请使用闲鱼 APP 扫码登录</p>
          <p v-if="qrState.status === 'SCANNED'" class="qr-scanned">
            <el-icon :size="16"><SuccessFilled /></el-icon> 已扫码，请在手机上确认
          </p>
          <p v-if="qrState.status === 'VERIFICATION_REQUIRED'" class="qr-verify">
            <el-alert title="需要验证" type="warning" :closable="false" show-icon />
          </p>
          <p v-if="qrState.status === 'EXPIRED'" class="qr-expired">
            <el-alert title="二维码已过期" type="error" :closable="false" show-icon>
              <template #default>请重新生成二维码</template>
            </el-alert>
          </p>
          <p v-if="qrState.status === 'ERROR'" class="qr-error">
            <el-alert :title="qrState.message || '生成失败'" type="error" :closable="false" show-icon />
          </p>
        </div>

        <div v-else-if="qrState.error" class="qr-error">
          <el-alert :title="qrState.message || '生成失败'" type="error" :closable="false" show-icon />
        </div>

        <div v-if="qrState.qrCodeDataUrl || qrState.error" style="margin-top: 16px; text-align: center;">
          <el-button v-if="qrState.status === 'EXPIRED'" type="primary" @click="refreshQrCode">
            <el-icon><Refresh /></el-icon> 刷新二维码
          </el-button>
          <el-button v-if="['WAITING', 'SCANNED'].includes(qrState.status)" type="danger" plain @click="cancelQrLogin">
            <el-icon><Close /></el-icon> 取消登录
          </el-button>
        </div>
      </div>

      <!-- Cookie 登录 -->
      <div v-if="loginMode === 'cookie'">
        <el-form :model="loginForm" label-width="80px">
          <el-form-item label="账号名称">
            <el-input v-model="loginForm.accountName" placeholder="如：账号A" />
          </el-form-item>
          <el-form-item label="Cookie">
            <el-input v-model="loginForm.cookieHeader" type="textarea" :rows="4" placeholder="粘贴 Cookie 字符串" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="loginForm.remark" placeholder="可选" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="closeDialog">取消</el-button>
        <el-button
          v-if="loginMode === 'cookie'"
          type="primary"
          :loading="submitting"
          @click="handleCookieLogin"
        >
          确定
        </el-button>
        <el-button
          v-else
          type="primary"
          :loading="qrState.submitting"
          :disabled="!qrForm.accountName"
          @click="handleQrLogin"
        >
          生成二维码
        </el-button>
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
        <el-form-item label="备注">
          <el-input v-model="statusForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showStatusDialog = false">取消</el-button>
        <el-button type="primary" @click="handleStatusUpdate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 账号详情抽屉 -->
    <el-drawer v-model="showDetailDrawer" :title="`账号详情 — ${detailForm.accountName || ''}`" size="540px">
      <div v-loading="detailLoading" style="padding: 0 20px 20px;">
        <!-- 头像和基本信息 -->
        <div style="text-align: center; margin-bottom: 24px;">
          <el-avatar :size="80" :src="detailForm.avatar" style="font-size: 28px;">
            {{ detailForm.displayName ? detailForm.displayName.charAt(0) : '?' }}
          </el-avatar>
          <h3 style="margin: 12px 0 4px;">{{ detailForm.displayName || '—' }}</h3>
          <p style="color: #909399; margin: 0;">{{ detailForm.accountName }}</p>
          <el-tag :type="statusType(detailForm.status)" style="margin-top: 8px;">{{ detailForm.status }}</el-tag>
        </div>

        <!-- 统计信息 -->
        <div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 24px;">
          <div style="text-align: center; padding: 12px; background: #f5f7fa; border-radius: 8px;">
            <div style="font-size: 22px; font-weight: bold; color: #409EFF;">{{ detailForm.followers || 0 }}</div>
            <div style="font-size: 12px; color: #909399; margin-top: 4px;">粉丝</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #f5f7fa; border-radius: 8px;">
            <div style="font-size: 22px; font-weight: bold; color: #67C23A;">{{ detailForm.following || 0 }}</div>
            <div style="font-size: 12px; color: #909399; margin-top: 4px;">关注</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #f5f7fa; border-radius: 8px;">
            <div style="font-size: 22px; font-weight: bold; color: #E6A23C;">{{ detailForm.soldCount || 0 }}</div>
            <div style="font-size: 12px; color: #909399; margin-top: 4px;">卖出</div>
          </div>
          <div style="text-align: center; padding: 12px; background: #f5f7fa; border-radius: 8px;">
            <div style="font-size: 22px; font-weight: bold; color: #F56C6C;">{{ detailForm.purchaseCount || 0 }}</div>
            <div style="font-size: 12px; color: #909399; margin-top: 4px;">买过</div>
          </div>
        </div>

        <!-- 详细资料 -->
        <el-descriptions :column="1" border size="default">
          <el-descriptions-item label="用户ID">{{ detailForm.userId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="IP 属地">{{ detailForm.ipLocation || '—' }}</el-descriptions-item>
          <el-descriptions-item label="个人简介">{{ detailForm.introduction || '—' }}</el-descriptions-item>
          <el-descriptions-item label="在售宝贝">{{ detailForm.onSaleCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="收藏数">{{ detailForm.collectionCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="店铺等级">{{ detailForm.shopLevel || '—' }}</el-descriptions-item>
          <el-descriptions-item label="信用分">{{ detailForm.creditScore || 0 }}</el-descriptions-item>
          <el-descriptions-item label="评价数">{{ detailForm.reviewNum || 0 }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detailForm.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="最后登录">{{ formatTime(detailForm.lastLoginAt) }}</el-descriptions-item>
          <el-descriptions-item label="上次同步">{{ formatTime(detailForm.profileSyncedAt) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 操作按钮 -->
        <div style="margin-top: 24px; display: flex; gap: 12px;">
          <el-button type="primary" :loading="detailSyncing" @click="syncProfile" style="flex: 1;">
            <el-icon><Refresh /></el-icon> 刷新实时数据
          </el-button>
          <el-button @click="copyCookie" style="flex: 1;">复制 Cookie</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/request'

// 时间格式化
function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

// ===== 列表 =====
const accounts = ref([])
const loading = ref(false)

async function loadAccounts() {
  loading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

// ===== 状态切换 =====
const showStatusDialog = ref(false)
const statusForm = ref({ id: null, status: 'ACTIVE', remark: '' })

const statusType = (s) => ({ ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[s] || 'info')

function editStatus(row) {
  statusForm.value = { id: row.id, status: row.status, remark: row.remark || '' }
  showStatusDialog.value = true
}

async function handleStatusUpdate() {
  try {
    const res = await api.put(`/accounts/${statusForm.value.id}/status`, {
      status: statusForm.value.status,
      remark: statusForm.value.remark
    })
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
    const res = await api.delete(`/accounts/${id}`)
    if (res.success) {
      ElMessage.success('已删除')
      await loadAccounts()
    }
  } catch (e) { /* ignore */ }
}

// ===== Cookie 登录 =====
const showLoginDialog = ref(false)
const loginMode = ref('qr') // 'qr' | 'cookie'
const submitting = ref(false)
const loginForm = ref({ accountName: '', cookieHeader: '', remark: '' })

async function handleCookieLogin() {
  if (!loginForm.value.accountName || !loginForm.value.cookieHeader) {
    ElMessage.warning('请填写账号名称和 Cookie')
    return
  }
  submitting.value = true
  try {
    const res = await api.post('/accounts/login', loginForm.value)
    if (res.success) {
      ElMessage.success('账号添加成功')
      closeDialog()
      await loadAccounts()
    }
  } catch (e) { /* handled by interceptor */ }
  finally { submitting.value = false }
}

// ===== 二维码登录 =====
const qrForm = ref({ accountName: '', remark: '' })
const qrState = ref({
  loading: false,
  sessionId: null,
  status: null,
  qrCodeDataUrl: null,
  message: null,
  error: false,
  submitting: false
})

let qrPollTimer = null

function closeDialog() {
  showLoginDialog.value = false
  stopQrPolling()
  resetQrState()
  loginMode.value = 'qr'
  qrForm.value = { accountName: '', remark: '' }
  loginForm.value = { accountName: '', cookieHeader: '', remark: '' }
}

function resetQrState() {
  qrState.value = {
    loading: false,
    sessionId: null,
    status: null,
    qrCodeDataUrl: null,
    message: null,
    error: false,
    submitting: false
  }
}

function stopQrPolling() {
  if (qrPollTimer) {
    clearInterval(qrPollTimer)
    qrPollTimer = null
  }
}

async function handleQrLogin() {
  if (!qrForm.value.accountName) {
    ElMessage.warning('请填写账号名称')
    return
  }
  qrState.value.submitting = true
  qrState.value.loading = true
  qrState.value.error = false

  try {
    const res = await api.post('/accounts/qr-login', qrForm.value)
    if (res.success && res.data) {
      const data = res.data
      qrState.value.sessionId = data.sessionId
      qrState.value.qrCodeDataUrl = data.qrCodeDataUrl
      qrState.value.status = data.status
      qrState.value.message = data.message
      qrState.value.loading = false

      if (data.status === 'SUCCESS') {
        // 直接登录成功
        ElMessage.success('二维码登录成功！账号已添加')
        closeDialog()
        await loadAccounts()
      } else {
        // 开始轮询
        startQrPolling()
      }
    } else {
      qrState.value.error = true
      qrState.value.message = res.message || '生成二维码失败'
      qrState.value.loading = false
    }
  } catch (e) {
    qrState.value.error = true
    qrState.value.message = '生成二维码失败: ' + (e.message || '未知错误')
    qrState.value.loading = false
  } finally {
    qrState.value.submitting = false
  }
}

function startQrPolling() {
  stopQrPolling()
  qrPollTimer = setInterval(async () => {
    if (!qrState.value.sessionId) return

    try {
      const res = await api.get('/accounts/qr-login/status', {
        params: { sessionId: qrState.value.sessionId }
      })
      if (res.success && res.data) {
        const data = res.data
        qrState.value.status = data.status
        qrState.value.message = data.message

        if (data.status === 'SUCCESS') {
          stopQrPolling()
          ElMessage.success('二维码登录成功！账号已添加')
          closeDialog()
          await loadAccounts()
        } else if (data.status === 'SCANNED') {
          qrState.value.message = '已扫码，请在手机上确认'
        } else if (data.status === 'EXPIRED' || data.status === 'CANCELLED' || data.status === 'ERROR') {
          stopQrPolling()
          if (data.status === 'CANCELLED') {
            ElMessage.info('已取消登录')
            closeDialog()
          } else {
            qrState.value.error = true
            qrState.value.message = data.message || '登录失败或已过期'
          }
        }
      }
    } catch (e) {
      // polling error, continue
    }
  }, 3000)
}

async function refreshQrCode() {
  resetQrState()
  qrState.value.loading = true
  await handleQrLogin()
}

async function cancelQrLogin() {
  stopQrPolling()
  qrState.value.status = 'CANCELLED'
  ElMessage.info('已取消登录')
  closeDialog()
}

// ===== 详情抽屉 =====
const showDetailDrawer = ref(false)
const detailLoading = ref(false)
const detailSyncing = ref(false)
const detailForm = ref({
  id: null,
  accountName: '',
  displayName: '',
  userId: '',
  avatar: '',
  status: '',
  remark: '',
  introduction: '',
  ipLocation: '',
  followers: 0,
  following: 0,
  soldCount: 0,
  purchaseCount: 0,
  collectionCount: 0,
  onSaleCount: 0,
  shopLevel: '',
  creditScore: 0,
  reviewNum: 0,
  lastLoginAt: '',
  profileSyncedAt: '',
  cookieHeader: ''
})

async function viewDetail(row) {
  // 先展示抽屉，显示数据库已有数据
  detailForm.value = { ...row }
  showDetailDrawer.value = true
  detailLoading.value = true

  // 实时获取最新 profile
  try {
    const res = await api.get(`/accounts/${row.id}/profile`)
    if (res.success && res.data) {
      const data = res.data
      detailForm.value = {
        ...detailForm.value,
        displayName: data.displayName || detailForm.value.displayName,
        avatar: data.avatar || detailForm.value.avatar,
        introduction: data.introduction || '',
        ipLocation: data.ipLocation || '',
        followers: data.followers || 0,
        following: data.following || 0,
        soldCount: data.soldCount || 0,
        purchaseCount: data.purchaseCount || 0,
        collectionCount: data.collectionCount || 0,
        onSaleCount: data.onSaleCount || 0,
        shopLevel: data.shopLevel || '',
        creditScore: data.creditScore || 0,
        reviewNum: data.reviewNum || 0,
      }
    }
  } catch (e) {
    // 获取失败，保留数据库已有数据
  } finally {
    detailLoading.value = false
  }
}

async function syncProfile() {
  if (!detailForm.value.id) return
  detailSyncing.value = true
  try {
    const res = await api.post(`/accounts/${detailForm.value.id}/profile/sync`)
    if (res.success && res.data) {
      const data = res.data
      detailForm.value = {
        ...detailForm.value,
        displayName: data.displayName || detailForm.value.displayName,
        avatar: data.avatar || detailForm.value.avatar,
        introduction: data.introduction || '',
        ipLocation: data.ipLocation || '',
        followers: data.followers || 0,
        following: data.following || 0,
        soldCount: data.soldCount || 0,
        purchaseCount: data.purchaseCount || 0,
        collectionCount: data.collectionCount || 0,
        onSaleCount: data.onSaleCount || 0,
        shopLevel: data.shopLevel || '',
        creditScore: data.creditScore || 0,
        reviewNum: data.reviewNum || 0,
        profileSyncedAt: data.syncedAt || '',
      }
      ElMessage.success('数据已同步到数据库')
      // 刷新列表
      await loadAccounts()
    }
  } catch (e) {
    ElMessage.error('同步失败')
  } finally {
    detailSyncing.value = false
  }
}

async function copyCookie() {
  if (!detailForm.value.cookieHeader) {
    ElMessage.warning('该账号暂无 Cookie')
    return
  }
  try {
    await navigator.clipboard.writeText(detailForm.value.cookieHeader)
    ElMessage.success('Cookie 已复制到剪贴板')
  } catch (e) {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = detailForm.value.cookieHeader
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('Cookie 已复制到剪贴板')
  }
}

// ===== 生命周期 =====
onUnmounted(() => {
  stopQrPolling()
})

onMounted(loadAccounts)
</script>

<style scoped>
.qr-container {
  text-align: center;
  padding: 20px;
}

.qr-image {
  width: 280px;
  height: 280px;
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 10px;
  background: #fff;
}

.qr-tip {
  margin-top: 12px;
  color: #606266;
  font-size: 14px;
}

.qr-scanned {
  margin-top: 8px;
  color: #67c23a;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.qr-verify {
  margin-top: 12px;
}

.qr-expired {
  margin-top: 12px;
}

.qr-error {
  margin-top: 12px;
}
</style>
