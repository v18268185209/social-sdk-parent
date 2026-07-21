<template>
  <div class="page-root">
    <!-- OpenList 状态面板 -->
    <el-card style="margin-bottom: 20px;">
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
              {{ phaseText }}
            </el-button>
            <el-button
              v-if="status.installed && !status.running"
              type="success"
              size="small"
              @click="handleStart"
              :loading="starting"
            >
              <el-icon><VideoPlay /></el-icon>
              {{ starting ? '启动中...' : '启动' }}
            </el-button>
            <el-button
              v-if="status.running"
              type="warning"
              size="small"
              @click="handleStop"
            >
              <el-icon><VideoPause /></el-icon> 停止
            </el-button>
            <el-button
              v-if="status.installed"
              type="info"
              size="small"
              @click="handleRestart"
              :loading="restarting"
            >
              <el-icon><RefreshRight /></el-icon>
              {{ restarting ? '重启中...' : '重启' }}
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
          <el-descriptions-item label="默认账号">{{ status.username || 'openlist' }}</el-descriptions-item>
          <el-descriptions-item label="默认密码">{{ status.password || 'openlist' }}</el-descriptions-item>
          <el-descriptions-item label="系统架构">{{ status.arch || '检测中...' }}</el-descriptions-item>
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

    <!-- 网盘挂载管理（对齐 OpenList 存储管理 API） -->
    <el-card style="margin-bottom: 20px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>🌐 网盘挂载（OpenList 存储管理）</span>
          <el-button type="primary" size="small" @click="showMountDialog = true" :disabled="!status.running">
            <el-icon><Plus /></el-icon> 添加网盘
          </el-button>
        </div>
      </template>

      <el-table :data="mounts" stripe v-loading="mountLoading" style="width: 100%;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="驱动" width="140">
          <template #default="{ row }">
            <el-tag>{{ driverLabel(row.driver) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="mountPath" label="挂载路径" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="健康" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'work' ? 'success' : 'warning'">
              {{ row.status === 'work' ? '正常' : row.status || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="order" label="排序" width="60" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="toggleMount(row)" :type="row.enabled ? 'warning' : 'success'">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="danger" @click="removeMount(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!mountLoading && mounts.length === 0" description="暂无挂载，请先启动 OpenList" />
    </el-card>

    <!-- 文件管理卡片 -->
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📄 文件管理</span>
          <div style="display: flex; gap: 10px;">
            <el-select v-model="filterMountPath" placeholder="筛选网盘" clearable style="width: 200px;" @change="loadFiles">
              <el-option
                v-for="m in mounts"
                :key="m.id"
                :label="m.mountPath"
                :value="m.mountPath"
              />
            </el-select>
            <el-button type="primary" size="small" @click="showUploadDialog = true" :disabled="!filterMountPath">
              <el-icon><Upload /></el-icon> 上传文件
            </el-button>
            <el-button size="small" @click="loadFiles" :disabled="!filterMountPath">
              <el-icon><RefreshRight /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="files" stripe v-loading="fileLoading">
        <el-table-column prop="name" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.size) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 1 ? 'primary' : 'success'" size="small">
              {{ row.type === 1 ? '目录' : '文件' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="modified" label="修改时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="downloadFile(row)" v-if="row.type !== 1">
              下载
            </el-button>
            <el-button size="small" type="danger" @click="deleteFile(row)" v-if="row.type !== 1">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!fileLoading && files.length === 0" description="暂无文件" />
    </el-card>

    <!-- 添加网盘存储对话框 -->
    <el-dialog v-model="showMountDialog" title="添加网盘存储" width="600px">
      <el-form label-width="100px">
        <el-form-item label="存储驱动">
          <el-select v-model="mountForm.driver" style="width: 100%;" @change="onDriverChange">
            <el-option
              v-for="d in drivers"
              :key="d.name"
              :label="d.label"
              :value="d.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="挂载路径">
          <el-input v-model="mountForm.mountPath" placeholder="/baidu" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="mountForm.order" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="mountForm.enabled" />
        </el-form-item>

        <!-- 动态配置字段 -->
        <el-divider>驱动配置</el-divider>
        <el-form-item v-for="field in currentDriverFields" :key="field.name" :label="field.label">
          <el-input
            v-if="field.type === 'string'"
            v-model="mountForm.config[field.name]"
            :placeholder="field.placeholder"
            style="width: 100%;"
          />
          <el-input
            v-if="field.type === 'password'"
            v-model="mountForm.config[field.name]"
            :placeholder="field.placeholder"
            type="password"
            show-password
            style="width: 100%;"
          />
          <el-input-number
            v-if="field.type === 'number'"
            v-model="mountForm.config[field.name]"
            :min="0"
            style="width: 100%;"
          />
          <el-switch
            v-if="field.type === 'boolean'"
            v-model="mountForm.config[field.name]"
          />
          <p v-if="field.description" style="color: #909399; font-size: 12px; margin: 0;">{{ field.description }}</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMountDialog = false">取消</el-button>
        <el-button type="primary" @click="addMount" :loading="addingMount">添加</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="500px">
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
  listStorages, createStorage, deleteStorage, enableStorage, disableStorage,
  listDrivers, getDriverInfo, listOpenListFiles, uploadOpenListFile, deleteOpenListFile
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

const starting = ref(false)
const restarting = ref(false)

const busy = computed(() => {
  const p = status.value.phase
  return p === 'downloading' || p === 'extracting' || p === 'starting'
})

const phaseText = computed(() => {
  const p = status.value.phase
  if (p === 'downloading') return '下载中...'
  if (p === 'extracting') return '解压中...'
  return '下载安装'
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
  starting.value = true
  try {
    await startOpenList()
    ElMessage.info('启动中...')
    startPolling()
  } catch (e) {
    ElMessage.error('启动失败')
  } finally {
    starting.value = false
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
  restarting.value = true
  try {
    await restartOpenList()
    ElMessage.info('重启中...')
    startPolling()
  } catch (e) {
    ElMessage.error('重启失败')
  } finally {
    restarting.value = false
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

// ============== 存储挂载管理 ==============
const mounts = ref([])
const mountLoading = ref(false)
const addingMount = ref(false)
const showMountDialog = ref(false)
const drivers = ref([])

const mountForm = ref({
  driver: 'BaiduNetdisk',
  mountPath: '/baidu',
  order: 0,
  enabled: true,
  config: {}
})

const currentDriverFields = ref([])

const driverLabel = name => {
  const d = drivers.value.find(x => x.name === name)
  return d ? d.label : name
}

const loadDrivers = async () => {
  try {
    const res = await listDrivers()
    drivers.value = res.data || []
  } catch (e) {
    console.error('加载驱动列表失败:', e)
  }
}

const loadMounts = async () => {
  if (!status.value.running) return
  mountLoading.value = true
  try {
    const res = await listStorages()
    mounts.value = res.data || []
  } catch (e) {
    console.error('加载存储列表失败:', e)
  } finally {
    mountLoading.value = false
  }
}

const onDriverChange = async (driverName) => {
  currentDriverFields.value = []
  try {
    const res = await getDriverInfo(driverName)
    let schema = []
    try {
      const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
      const driverData = data.data || data
      if (driverData && driverData.config) {
        const configFields = driverData.config
        schema = Object.entries(configFields).map(([k, v]) => ({
          name: k,
          type: typeof v === 'boolean' ? 'boolean' : typeof v === 'number' ? 'number' : 'string',
          label: k,
          description: ''
        }))
      }
    } catch (e) {
      console.error('解析驱动配置失败:', e)
    }
    currentDriverFields.value = schema
  } catch (e) {
    console.error('获取驱动信息失败:', e)
  }
}

const addMount = async () => {
  if (!mountForm.value.mountPath || !mountForm.value.mountPath.startsWith('/')) {
    ElMessage.warning('挂载路径必须以 / 开头')
    return
  }
  if (!mountForm.value.driver) {
    ElMessage.warning('请选择存储驱动')
    return
  }

  addingMount.value = true
  try {
    const payload = {
      mount_path: mountForm.value.mountPath,
      driver: mountForm.value.driver,
      order: mountForm.value.order,
      enabled: mountForm.value.enabled,
      config: mountForm.value.config
    }
    await createStorage(payload)
    ElMessage.success('存储挂载添加成功')
    showMountDialog.value = false
    loadMounts()
  } catch (e) {
    ElMessage.error('添加失败: ' + (e.message || e))
  } finally {
    addingMount.value = false
  }
}

const removeMount = async (row) => {
  await ElMessageBox.confirm(`确定删除挂载 ${row.mountPath} 吗？`, '提示', { type: 'warning' })
  try {
    await deleteStorage(row.id)
    ElMessage.success('已删除')
    loadMounts()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const toggleMount = async (row) => {
  try {
    if (row.enabled) {
      await disableStorage(row.id)
      ElMessage.success('已禁用')
    } else {
      await enableStorage(row.id)
      ElMessage.success('已启用')
    }
    loadMounts()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// ============== 文件管理 ==============
const files = ref([])
const fileLoading = ref(false)
const filterMountPath = ref(null)
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)

const formatSize = bytes => {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  while (bytes >= 1024 && i < units.length - 1) { bytes /= 1024; i++ }
  return `${bytes.toFixed(1)} ${units[i]}`
}

const loadFiles = async () => {
  if (!filterMountPath.value) {
    files.value = []
    return
  }
  fileLoading.value = true
  try {
    const res = await listOpenListFiles(filterMountPath.value)
    // 解析 OpenList 返回的文件列表
    if (res.data) {
      let fileList = []
      try {
        const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        if (data && data.data && data.data.content) {
          fileList = data.data.content.map(item => ({
            name: item.name,
            size: item.size || 0,
            type: item.type, // 1=目录, 0=文件
            modified: item.modified || '',
            raw: item
          }))
        }
      } catch (e) {
        console.error('解析文件列表失败:', e)
      }
      files.value = fileList
    } else {
      files.value = []
    }
  } catch (e) {
    console.error('加载文件列表失败:', e)
    files.value = []
  } finally {
    fileLoading.value = false
  }
}

const handleFileChange = (uploadFile) => {
  selectedFile.value = uploadFile.raw
}

const uploadFile = async () => {
  if (!selectedFile.value) return ElMessage.warning('请先选择文件')
  if (!filterMountPath.value) return ElMessage.warning('请先选择网盘')
  uploading.value = true
  try {
    await uploadOpenListFile(filterMountPath.value, selectedFile.value)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

const downloadFile = async (row) => {
  if (!filterMountPath.value) return
  try {
    // 直接构建 OpenList 下载链接
    const url = `${status.value.url}/d${filterMountPath.value}/${row.name}`
    window.open(url, '_blank')
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

const deleteFile = async (row) => {
  if (!filterMountPath.value) return
  await ElMessageBox.confirm(`确定删除 ${row.name} 吗？`, '提示', { type: 'warning' })
  try {
    await deleteOpenListFile(filterMountPath.value, row.name)
    ElMessage.success('已删除')
    loadFiles()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

watch(filterMountPath, () => loadFiles())

onMounted(() => {
  loadDrivers()
  loadStatus()
  loadMounts()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>
