<template>
  <div>
    <!-- 账号管理卡片 -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📁 网盘账号</span>
          <el-button type="primary" size="small" @click="showAddDialog = true">
            <el-icon><Plus /></el-icon> 添加网盘
          </el-button>
        </div>
      </template>

      <el-table :data="accounts" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="网盘类型" width="140">
          <template #default="{ row }">
            <el-tag :type="providerType(row.provider)">{{ providerLabel(row.provider) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="uid" label="UID" width="140" />
        <el-table-column label="空间使用" width="200">
          <template #default="{ row }">
            <el-progress
              :percentage="spacePercent(row)"
              :status="spacePercent(row) > 90 ? 'exception' : ''"
            />
            <span style="font-size: 12px; color: #909399;">
              {{ formatSize(row.usedSpace) }} / {{ formatSize(row.totalSpace) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="Token 过期" width="180">
          <template #default="{ row }">
            <el-tag :type="isExpired(row) ? 'danger' : 'success'">
              {{ row.tokenExpiresAt ? formatTime(row.tokenExpiresAt) : '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="添加时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="authorizeAccount(row)" v-if="isExpired(row)">
              重新授权
            </el-button>
            <el-button size="small" type="danger" @click="deleteAccount(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && accounts.length === 0" description="暂未添加网盘账号" />
    </el-card>

    <!-- 文件管理卡片 -->
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📄 文件管理</span>
          <div style="display: flex; gap: 10px;">
            <el-select v-model="filterAccountId" placeholder="筛选网盘账号" clearable style="width: 200px;">
              <el-option
                v-for="acc in accounts"
                :key="acc.id"
                :label="`${providerLabel(acc.provider)} (${acc.uid || '-'})`"
                :value="acc.id"
              />
            </el-select>
            <el-button type="primary" size="small" @click="showUploadDialog = true" :disabled="!filterAccountId">
              <el-icon><Upload /></el-icon> 上传文件
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="files" stripe v-loading="fileLoading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="上传状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.uploadStatus)">{{ row.uploadStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分享链接" min-width="200">
          <template #default="{ row }">
            <span v-if="row.shareLink">
              <el-link :href="row.shareLink" target="_primary" type="primary">{{ row.shareLink.slice(0, 50) }}...</el-link>
              <span v-if="row.extractCode" style="margin-left: 8px; color: #e6a23c;">提取码: {{ row.extractCode }}</span>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="分享有效期" width="180">
          <template #default="{ row }">
            {{ row.shareExpiresAt ? formatTime(row.shareExpiresAt) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="shareFile(row)" v-if="row.uploadStatus === 'COMPLETED' && !row.shareLink">
              创建分享
            </el-button>
            <el-button size="small" type="warning" @click="cancelShare(row)" v-if="row.shareLink">
              取消分享
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!fileLoading && files.length === 0" description="暂无文件" />
    </el-card>

    <!-- 添加网盘对话框 -->
    <el-dialog v-model="showAddDialog" title="添加网盘账号" width="500px">
      <el-form label-width="100px">
        <el-form-item label="网盘类型">
          <el-select v-model="authForm.provider" style="width: 100%;">
            <el-option label="百度网盘" value="BAIDU_NETDISK" />
            <el-option label="夸克网盘（暂未开放）" value="QUARK_NETDISK" disabled />
          </el-select>
        </el-form-item>
        <el-form-item label="闲鱼账号">
          <el-select v-model="authForm.accountId" placeholder="选择关联的闲鱼账号" style="width: 100%;">
            <el-option v-for="acc in xianyuAccounts" :key="acc.id" :label="acc.accountName" :value="acc.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="startOAuth">前往授权</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件到网盘" width="500px">
      <el-upload
        drag
        :auto-upload="false"
        :on-change="handleFileChange"
        :show-file-list="true"
        accept="*"
      >
        <el-icon class="el-icon--upload"><Upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或 <em>点击上传</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="uploadFile" :loading="uploading">开始上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Upload, UploadFilled
} from '@element-plus/icons-vue'
import {
  listStorageAccounts, deleteStorageAccount, getAuthUrl, handleOAuthCallback,
  listStorageFiles, uploadStorageFile, shareStorageFile, cancelShareStorageFile
} from '@/api/cloudStorage'
import api from '@/api/request'

const accounts = ref([])
const files = ref([])
const xianyuAccounts = ref([])
const loading = ref(false)
const fileLoading = ref(false)
const filterAccountId = ref(null)
const showAddDialog = ref(false)
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)

const authForm = ref({
  provider: 'BAIDU_NETDISK',
  accountId: null
})

// ============== 工具函数 ==============
const formatTime = t => t ? new Date(t).toLocaleString('zh-CN') : '-'
const formatSize = bytes => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return `${bytes.toFixed(1)} ${units[i]}`
}
const providerLabel = p => ({ BAIDU_NETDISK: '百度网盘', QUARK_NETDISK: '夸克网盘', ALIYUN_DRIVE: '阿里云盘' }[p] || p)
const providerType = p => ({ BAIDU_NETDISK: 'primary', QUARK_NETDISK: 'warning', ALIYUN_DRIVE: 'success' }[p] || '')
const statusType = s => ({ COMPLETED: 'success', UPLOADING: 'warning', FAILED: 'danger', PENDING: 'info' }[s] || 'info')
const isExpired = row => !row.tokenExpiresAt || new Date(row.tokenExpiresAt) < new Date()
const spacePercent = row => row.totalSpace ? Math.round((row.usedSpace / row.totalSpace) * 100) : 0

// ============== 数据加载 ==============
const loadAccounts = async () => {
  loading.value = true
  try {
    const res = await listStorageAccounts()
    accounts.value = res.data || []
  } finally {
    loading.value = false
  }
}

const loadFiles = async () => {
  fileLoading.value = true
  try {
    const res = await listStorageFiles(filterAccountId.value)
    files.value = res.data || []
  } finally {
    fileLoading.value = false
  }
}

const loadXianyuAccounts = async () => {
  try {
    const res = await api.get('/accounts')
    xianyuAccounts.value = (res.data || []).filter(a => a.status === 'ACTIVE')
  } catch {}
}

// ============== 操作 ==============
const authorizeAccount = async (row) => {
  const redirectUri = window.location.origin + '/cloud-storage/callback'
  const res = await getAuthUrl(row.provider, redirectUri)
  const { authUrl } = res.data
  window.open(authUrl, '_blank', 'width=800,height=600')
  ElMessage.info('已打开授权页面，请完成授权后刷新列表')
}

const startOAuth = async () => {
  if (!authForm.value.accountId) return ElMessage.warning('请选择关联的闲鱼账号')
  const redirectUri = window.location.origin + '/cloud-storage/callback'
  const res = await getAuthUrl(authForm.value.provider, redirectUri)
  const { authUrl, state } = res.data
  localStorage.setItem('cloudStorageState', JSON.stringify({ ...authForm.value, state }))
  window.open(authUrl, '_blank', 'width=800,height=600')
  showAddDialog.value = false
  ElMessage.info('已打开授权页面，请完成授权')
}

const deleteAccount = async (id) => {
  await ElMessageBox.confirm('确定删除该网盘账号吗？', '提示', { type: 'warning' })
  await deleteStorageAccount(id)
  ElMessage.success('已删除')
  loadAccounts()
}

const shareFile = async (row) => {
  const res = await shareStorageFile(row.id)
  ElMessage.success(`分享链接: ${res.data}`)
  loadFiles()
}

const cancelShare = async (row) => {
  await cancelShareStorageFile(row.id)
  ElMessage.success('已取消分享')
  loadFiles()
}

const handleFileChange = (uploadFile) => {
  selectedFile.value = uploadFile.raw
}

const uploadFile = async () => {
  if (!selectedFile.value) return ElMessage.warning('请先选择文件')
  uploading.value = true
  try {
    await uploadStorageFile(filterAccountId.value, selectedFile.value)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

watch(filterAccountId, () => loadFiles())

onMounted(() => {
  loadAccounts()
  loadXianyuAccounts()
  // 检查是否有 OAuth 回调参数
  const urlParams = new URLSearchParams(window.location.search)
  if (urlParams.get('code') && urlParams.get('state')) {
    handleCallback(urlParams)
  }
})

const handleCallback = async (params) => {
  try {
    const { code, state } = params
    const saved = JSON.parse(localStorage.getItem('cloudStorageState') || '{}')
    await handleOAuthCallback(saved.provider, code, state, saved.accountId)
    ElMessage.success('授权成功')
    localStorage.removeItem('cloudStorageState')
    window.history.replaceState({}, document.title, window.location.pathname)
    loadAccounts()
  } catch (e) {
    ElMessage.error('授权失败: ' + e.message)
  }
}
</script>
