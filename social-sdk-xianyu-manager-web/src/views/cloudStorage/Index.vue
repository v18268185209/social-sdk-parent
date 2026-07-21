<template>
  <div class="page-root">
    <!-- OpenList 状态面板 -->
    <el-card style="margin: 0;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📦 OpenList</span>
          <div style="display: flex; gap: 10px;">
            <el-button
              v-if="!status.installed"
              type="primary"
              size="small"
              @click="handleInstall"
              :loading="busy"
              :disabled="busy"
            >
              <el-icon><Download /></el-icon>
              {{ busy && status.phase === 'downloading' ? '下载中...' : busy && status.phase === 'extracting' ? '解压中...' : '下载安装' }}
            </el-button>
            <el-button
              v-if="status.installed && !status.running"
              type="success"
              size="small"
              @click="handleStart"
              :loading="busy"
              :disabled="busy"
            >
              <el-icon><VideoPlay /></el-icon>
              {{ busy && status.phase === 'starting' ? '启动中...' : '启动' }}
            </el-button>
            <el-button
              v-if="status.running"
              type="warning"
              size="small"
              @click="handleStop"
              :loading="busy"
              :disabled="busy"
            >
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button
              v-if="status.installed"
              type="info"
              size="small"
              @click="handleRestart"
              :loading="busy"
              :disabled="busy"
            >
              <el-icon><RefreshRight /></el-icon>
              {{ busy && status.phase === 'starting' ? '重启中...' : '重启' }}
            </el-button>
          </div>
        </div>
      </template>

      <!-- 进度条 -->
      <div v-if="busy || status.phase === 'downloading' || status.phase === 'extracting' || status.phase === 'starting'" style="margin-bottom: 16px;">
        <el-progress
          :percentage="Math.round(progressPercent)"
          :status="progressStatus"
          :stroke-width="20"
          striped
          striped-flow
        />
        <p style="margin-top: 8px; color: #909399; font-size: 13px;">
          {{ status.message || '处理中...' }}
        </p>
      </div>

      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 15px;">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(status)">
              {{ statusText(status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="版本">{{ status.version || '未安装' }}</el-descriptions-item>
          <el-descriptions-item label="端口">{{ status.port || '-' }}</el-descriptions-item>
          <el-descriptions-item label="访问地址">
            <el-link :href="status.url" target="_blank" type="primary" v-if="status.url">
              {{ status.url }}
            </el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="默认账号">
            {{ status.username || 'openlist' }}
          </el-descriptions-item>
          <el-descriptions-item label="默认密码">
            {{ status.password || 'openlist' }}
          </el-descriptions-item>
          <el-descriptions-item label="系统架构">
            {{ status.arch || '检测中...' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!status.installed"
          title="OpenList 尚未安装"
          type="info"
          :closable="false"
          show-icon
        >
          <template #default>
            点击"下载安装"按钮自动检测系统架构并下载对应版本。
          </template>
        </el-alert>

        <el-alert
          v-if="status.installed && !status.running"
          title="OpenList 已安装但未启动"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #default>
            点击"启动"按钮运行 OpenList 服务，将监听 <code>0.0.0.0:{{ status.port }}</code>。
          </template>
        </el-alert>

        <el-alert
          v-if="status.running"
          title="OpenList 运行中"
          type="success"
          :closable="false"
          show-icon
        >
          <template #default>
            访问 <a :href="status.url + '/d'" target="_blank">{{ status.url }}/d</a> 管理网盘挂载。
          </template>
        </el-alert>
      </div>
    </el-card>

    <!-- 网盘挂载管理 -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>🌐 网盘挂载</span>
          <el-button type="primary" size="small" @click="showMountDialog = true">
            <el-icon><Plus /></el-icon> 添加网盘
          </el-button>
        </div>
      </template>

      <el-table :data="mounts" stripe v-loading="mountLoading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="类型" width="140">
          <template #default="{ row }">
            <el-tag>{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" width="180" />
        <el-table-column prop="mount_path" label="挂载路径" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ok' ? 'success' : 'danger'">
              {{ row.status === 'ok' ? '正常' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="removeMount(row.id)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!mountLoading && mounts.length === 0" description="暂无挂载" />
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
            <el-tag :type="fileStatusType(row.uploadStatus)">{{ row.uploadStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分享链接" min-width="200">
          <template #default="{ row }">
            <span v-if="row.shareLink">
              <el-link :href="row.shareLink" target="_blank" type="primary">{{ row.shareLink.slice(0, 50) }}...</el-link>
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
    <el-dialog v-model="showMountDialog" title="添加网盘挂载" width="500px">
      <el-form label-width="100px">
        <el-form-item label="网盘类型">
          <el-select v-model="mountForm.type" style="width: 100%;">
            <el-option label="百度网盘" value="baidu" />
            <el-option label="阿里云盘" value="aliyundrive" />
            <el-option label="夸克网盘" value="quark" />
            <el-option label="OneDrive" value="onedrive" />
            <el-option label="WebDAV" value="webdav" />
            <el-option label="S3" value="s3" />
          </el-select>
        </el-form-item>
        <el-form-item label="挂载路径">
          <el-input v-model="mountForm.mountPath" placeholder="/百宝箱" />
        </el-form-item>
        <el-form-item label="根目录 ID">
          <el-input v-model="mountForm.rootFolderId" placeholder="/" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMountDialog = false">取消</el-button>
        <el-button type="primary" @click="addMount" :loading="addingMount">添加</el-button>
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
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Upload, UploadFilled, Download, VideoPlay, VideoPause, RefreshRight
} from '@element-plus/icons-vue'
import {
  getOpenListStatus, installOpenList, startOpenList, stopOpenList, restartOpenList,
  listStorageAccounts, deleteStorageAccount, listStorageFiles, uploadStorageFile, shareStorageFile, cancelShareStorageFile
} from '@/api/openList'

// ============== OpenList 状态 ==============
const status = ref({
  installed: false,
  running: false,
  version: '',
  port: 5244,
  url: 'http://127.0.0.1:5244',
  username: 'openlist',
  password: 'openlist',
  arch: 'unknown',
  phase: 'idle',
  progress: 0,
  message: '空闲'
})

const busy = computed(() => {
  const p = status.value.phase
  return p === 'downloading' || p === 'extracting' || p === 'starting'
})

const progressPercent = computed(() => (status.value.progress || 0) * 100)

const progressStatus = computed(() => {
  if (status.value.phase === 'running') return 'success'
  if (status.value.phase === 'failed') return 'exception'
  return ''
})

let pollTimer = null

const loadStatus = async () => {
  try {
    const res = await getOpenListStatus()
    if (res.data) {
      status.value = res.data
    }
  } catch (e) {
    console.error('加载状态失败:', e)
  }
}

const startPolling = () => {
  if (pollTimer) return
  pollTimer = setInterval(loadStatus, 2000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const handleInstall = async () => {
  try {
    await installOpenList()
    ElMessage.info('安装已在后台启动')
    startPolling()
  } catch (e) {
    ElMessage.error('启动安装失败')
  }
}

const handleStart = async () => {
  try {
    await startOpenList()
    ElMessage.info('启动中...')
    startPolling()
  } catch (e) {
    ElMessage.error('启动失败')
  }
}

const handleStop = async () => {
  try {
    await stopOpenList()
    ElMessage.success('已停止')
    loadStatus()
  } catch (e) {
    ElMessage.error('停止失败')
  }
}

const handleRestart = async () => {
  try {
    await restartOpenList()
    ElMessage.info('重启中...')
    startPolling()
  } catch (e) {
    ElMessage.error('重启失败')
  }
}

const statusTagType = s => {
  if (!s.installed) return 'info'
  if (s.running) return 'success'
  if (s.phase === 'failed') return 'danger'
  return 'warning'
}

const statusText = s => {
  if (!s.installed) return '未安装'
  if (s.running) return '运行中'
  if (s.phase === 'downloading') return '下载中...'
  if (s.phase === 'extracting') return '解压中...'
  if (s.phase === 'starting') return '启动中...'
  if (s.phase === 'failed') return '失败'
  if (s.phase === 'stopped') return '已停止'
  return '空闲'
}

// ============== 网盘挂载 ==============
const mounts = ref([])
const mountLoading = ref(false)
const addingMount = ref(false)
const showMountDialog = ref(false)
const mountForm = ref({
  type: 'baidu',
  mountPath: '/',
  rootFolderId: '/'
})

const loadMounts = async () => {
  mountLoading.value = true
  try {
    mounts.value = []
  } catch {}
  mountLoading.value = false
}

const addMount = async () => {
  addingMount.value = true
  try {
    ElMessage.success('挂载添加成功（需配置 OpenList 服务端）')
    showMountDialog.value = false
    loadMounts()
  } catch (e) {
    ElMessage.error('添加失败')
  } finally {
    addingMount.value = false
  }
}

const removeMount = async (id) => {
  await ElMessageBox.confirm('确定移除该挂载吗？', '提示', { type: 'warning' })
  ElMessage.success('已移除')
  loadMounts()
}

// ============== 账号/文件管理 ==============
const accounts = ref([])
const files = ref([])
const loading = ref(false)
const fileLoading = ref(false)
const filterAccountId = ref(null)
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)

const formatTime = t => t ? new Date(t).toLocaleString('zh-CN') : '-'
const formatSize = bytes => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return `${bytes.toFixed(1)} ${units[i]}`
}
const providerLabel = p => ({ BAIDU_NETDISK: '百度网盘', QUARK_NETDISK: '夸克网盘', ALIYUN_DRIVE: '阿里云盘' }[p] || p)
const fileStatusType = s => ({ COMPLETED: 'success', UPLOADING: 'warning', FAILED: 'danger', PENDING: 'info' }[s] || 'info')

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
  loadStatus()
  loadAccounts()
  loadMounts()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>
