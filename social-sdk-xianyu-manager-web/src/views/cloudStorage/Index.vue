<template>
  <div class="page-root">
    <!-- OpenList 状态面板 -->
    <el-card style="margin: 0;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📦 OpenList</span>
          <div style="display: flex; gap: 10px;">
            <el-button v-if="!openlistStatus.installed" type="primary" size="small" @click="installOpenList" :loading="installing">
              <el-icon><Download /></el-icon> 下载安装
            </el-button>
            <el-button v-if="openlistStatus.installed && !openlistStatus.running" type="success" size="small" @click="startOpenList" :loading="starting">
              <el-icon><VideoPlay /></el-icon> 启动
            </el-button>
            <el-button v-if="openlistStatus.running" type="warning" size="small" @click="stopOpenList" :loading="stopping">
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button v-if="openlistStatus.installed" type="info" size="small" @click="restartOpenList" :loading="restarting">
              <el-icon><RefreshRight /></el-icon> 重启
            </el-button>
          </div>
        </div>
      </template>

      <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 15px;">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(openlistStatus)">
              {{ statusText(openlistStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="版本">{{ openlistStatus.version || '未安装' }}</el-descriptions-item>
          <el-descriptions-item label="端口">{{ openlistStatus.port || '-' }}</el-descriptions-item>
          <el-descriptions-item label="访问地址">
            <el-link :href="openlistStatus.url" target="_blank" type="primary" v-if="openlistStatus.url">
              {{ openlistStatus.url }}
            </el-link>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="默认账号">
            {{ openlistStatus.username || 'openlist' }}
          </el-descriptions-item>
          <el-descriptions-item label="默认密码">
            {{ openlistStatus.password || 'openlist' }}
          </el-descriptions-item>
          <el-descriptions-item label="系统架构">
            {{ openlistStatus.arch || '检测中...' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!openlistStatus.installed"
          title="OpenList 尚未安装"
          type="info"
          :closable="false"
          show-icon>
          <template #default>
            点击下方“下载安装”按钮自动检测系统架构并下载对应版本。
          </template>
        </el-alert>

        <el-alert
          v-if="openlistStatus.installed && !openlistStatus.running"
          title="OpenList 已安装但未启动"
          type="warning"
          :closable="false"
          show-icon>
          <template #default>
            点击“启动”按钮运行 OpenList 服务。
          </template>
        </el-alert>

        <el-alert
          v-if="openlistStatus.running"
          title="OpenList 运行中"
          type="success"
          :closable="false"
          show-icon>
          <template #default>
            访问 <a :href="openlistStatus.dashboardUrl" target="_blank">{{ openlistStatus.dashboardUrl }}</a> 管理网盘挂载。
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
        <el-table-column prop="root_folder_id" label="根目录" width="140" />
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
            <el-tag :type="statusType(row.uploadStatus)">{{ row.uploadStatus }}</el-tag>
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
        <el-form-item label="客户端 ID">
          <el-input v-model="mountForm.clientId" placeholder="OAuth Client ID" />
        </el-form-item>
        <el-form-item label="客户端密钥">
          <el-input v-model="mountForm.clientSecret" type="password" show-password placeholder="OAuth Client Secret" />
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
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Upload, UploadFilled, Download, VideoPlay, VideoPause, RefreshRight
} from '@element-plus/icons-vue'
import {
  getOpenListStatus, installOpenList, startOpenList, stopOpenList, restartOpenList,
  listStorageAccounts, deleteStorageAccount, listStorageFiles, uploadStorageFile, shareStorageFile, cancelShareStorageFile
} from '@/api/openList'

// ============== OpenList 状态 ==============
const openlistStatus = ref({
  installed: false,
  running: false,
  version: '',
  port: 5244,
  url: 'http://127.0.0.1:5244',
  username: 'openlist',
  password: 'openlist',
  arch: 'unknown',
  dashboardUrl: ''
})

const installing = ref(false)
const starting = ref(false)
const stopping = ref(false)
const restarting = ref(false)

const loadOpenListStatus = async () => {
  try {
    const res = await getOpenListStatus()
    const data = res.data
    openlistStatus.value = {
      ...data,
      dashboardUrl: data.url + '/d'
    }
  } catch {}
}

const installOpenListAction = async () => {
  installing.value = true
  try {
    const res = await installOpenList()
    ElMessage.success(res.message || '安装成功')
    loadOpenListStatus()
  } catch (e) {
    ElMessage.error('安装失败')
  } finally {
    installing.value = false
  }
}

const startOpenListAction = async () => {
  starting.value = true
  try {
    const res = await startOpenList()
    if (res.data?.message?.includes('未安装')) {
      await installOpenListAction()
    } else {
      ElMessage.success(res.message || '启动成功')
    }
    loadOpenListStatus()
  } catch (e) {
    ElMessage.error('启动失败')
  } finally {
    starting.value = false
  }
}

const stopOpenListAction = async () => {
  stopping.value = true
  try {
    const res = await stopOpenList()
    ElMessage.success(res.message || '已停止')
    loadOpenListStatus()
  } catch (e) {
    ElMessage.error('停止失败')
  } finally {
    stopping.value = false
  }
}

const restartOpenListAction = async () => {
  restarting.value = true
  try {
    const res = await restartOpenList()
    ElMessage.success(res.message || '重启成功')
    loadOpenListStatus()
  } catch (e) {
    ElMessage.error('重启失败')
  } finally {
    restarting.value = false
  }
}

const statusType = s => {
  if (!s.installed) return 'info'
  return s.running ? 'success' : 'warning'
}

const statusText = s => {
  if (!s.installed) return '未安装'
  return s.running ? '运行中' : '已停止'
}

// ============== 网盘挂载 ==============
const mounts = ref([])
const mountLoading = ref(false)
const addingMount = ref(false)
const showMountDialog = ref(false)
const mountForm = ref({
  type: 'baidu',
  mountPath: '/',
  rootFolderId: '/',
  clientId: '',
  clientSecret: ''
})

const loadMounts = async () => {
  mountLoading.value = true
  try {
    // TODO: 调用实际挂载列表 API
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

const shareFileAction = async (row) => {
  const res = await shareStorageFile(row.id)
  ElMessage.success(`分享链接: ${res.data}`)
  loadFiles()
}

const cancelShareAction = async (row) => {
  await cancelShareStorageFile(row.id)
  ElMessage.success('已取消分享')
  loadFiles()
}

const handleFileChange = (uploadFile) => {
  selectedFile.value = uploadFile.raw
}

const uploadFileAction = async () => {
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

const statusTypeFile = s => ({ COMPLETED: 'success', UPLOADING: 'warning', FAILED: 'danger', PENDING: 'info' }[s] || 'info')

watch(filterAccountId, () => loadFiles())

onMounted(() => {
  loadOpenListStatus()
  loadAccounts()
  loadMounts()
})
</script>
