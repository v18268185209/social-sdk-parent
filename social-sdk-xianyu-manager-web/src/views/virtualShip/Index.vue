<template>
  <div class="page-root">
    <!-- 发货配置卡片 -->
    <el-card style="margin: 0;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>⚙️ 虚拟发货配置</span>
          <el-button type="primary" size="small" @click="saveConfig" :loading="configLoading">保存配置</el-button>
        </div>
      </template>

      <el-form :model="configForm" label-width="140px">
        <el-form-item label="启用自动发货">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
        <el-form-item label="发货延迟(秒)">
          <el-input-number v-model="configForm.delaySeconds" :min="0" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">支付成功后延时发货（防风控）</span>
        </el-form-item>
        <el-form-item label="自动确认收货">
          <span style="color: #909399; font-size: 12px;">
            订单在 <el-input-number v-model="configForm.autoConfirmDays" :min="1" :max="30" size="small" style="width: 80px; margin: 0 8px;" /> 天后自动确认收货
          </span>
        </el-form-item>
        <el-form-item label="发货后通知">
          <el-switch v-model="configForm.notifyAfterShip" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">发货后站内通知运营</span>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 卡密池卡片 -->
    <el-card style="margin-bottom: 20px;" v-if="deliverType === 'VIRTUAL_CARD'">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>🔑 卡密池</span>
          <el-button type="primary" size="small" @click="showAddCardDialog = true">
            <el-icon><Plus /></el-icon> 批量添加卡密
          </el-button>
        </div>
      </template>

      <el-table :data="cards" stripe v-loading="cardLoading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="cardContent" label="卡密内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.used ? 'info' : 'success'">{{ row.used ? '已使用' : '可用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="添加时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="deleteCard(row.id)" :disabled="row.used">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!cardLoading && cards.length === 0" description="暂无卡密" />
    </el-card>

    <!-- 网盘发货卡片 -->
    <el-card style="margin-bottom: 20px;" v-if="deliverType === 'FILE'">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>📁 网盘文件（发货素材）</span>
          <el-button type="primary" size="small" @click="showUploadDialog = true">
            <el-icon><Upload /></el-icon> 上传文件
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
        提示：买家下单后，系统自动从已上传文件中选择一个，创建网盘分享链接发给买家。
      </el-alert>

      <el-select v-model="fileFilterAccountId" placeholder="选择网盘账号" clearable style="width: 200px; margin-bottom: 12px;" @change="loadFiles">
        <el-option v-for="acc in storageAccounts" :key="acc.id" :label="`${providerLabel(acc.provider)} (${acc.uid || '-'})`" :value="acc.id" />
      </el-select>

      <el-table :data="files" stripe v-loading="fileLoading" style="margin-top: 12px;">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.uploadStatus)">{{ row.uploadStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="testShare(row)" v-if="row.uploadStatus === 'COMPLETED'">测试分享</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!fileLoading && files.length === 0" description="暂无文件" />
    </el-card>

    <!-- 发货任务卡片 -->
    <el-card>
      <template #header>
        <span>📦 虚拟发货任务记录</span>
      </template>

      <el-table :data="tasks" stripe v-loading="taskLoading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单ID" width="120" />
        <el-table-column prop="accountId" label="账号ID" width="80" />
        <el-table-column label="发货类型" width="120">
          <template #default="{ row }">
            <el-tag :type="row.deliverType === 'FILE' ? 'warning' : 'primary'">
              {{ row.deliverType === 'FILE' ? '网盘' : '卡密' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deliverContent" label="发货内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="triggerShip(row)" v-if="row.status === 'PENDING'">手动发货</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!taskLoading && tasks.length === 0" description="暂无发货任务" />
    </el-card>

    <!-- 批量添加卡密对话框 -->
    <el-dialog v-model="showAddCardDialog" title="批量添加卡密" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px;">每行一个卡密，如：XXXX-XXXX-XXXX-XXXX</el-alert>
      <el-input v-model="cardText" type="textarea" :rows="8" placeholder="粘贴卡密..." />
      <template #footer>
        <el-button @click="showAddCardDialog = false">取消</el-button>
        <el-button type="primary" @click="addCards">确定添加</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件（网盘）" width="500px">
      <el-upload drag :auto-upload="false" :on-change="handleFileChange" :show-file-list="true" accept="*">
        <el-icon class="el-icon--upload"><Upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处或点击上传</div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="uploadFile" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import api from '@/api/request'
import {
  listVirtualShipTasks, getVirtualShipConfig, saveVirtualShipConfig,
  listVirtualCards, importVirtualCards, deleteVirtualCard,
  listStorageAccounts, listStorageFiles, shareStorageFile, uploadStorageFile
} from '@/api/virtualShip'

// 配置（字段与后端 VirtualShipConfig 对齐）
const configForm = ref({
  accountId: null,
  enabled: true,
  delaySeconds: 30,
  autoConfirmDays: 7,
  notifyAfterShip: true
})
// 发货类型是前端展示用，后端不存储
const deliverType = ref('VIRTUAL_CARD')
const configLoading = ref(false)

// 账号
const accounts = ref([])
const loadAccounts = async () => {
  try { const r = await api.get('/accounts'); accounts.value = r.data || [] } catch {}
}
const loadConfig = async () => {
  if (!accounts.value.length) return
  try {
    const r = await getVirtualShipConfig(accounts.value[0].id)
    if (r.data) {
      // 只回写后端存在的字段，避免覆盖前端独有状态
      configForm.value = {
        ...configForm.value,
        accountId: r.data.accountId,
        enabled: r.data.enabled,
        delaySeconds: r.data.delaySeconds,
        autoConfirmDays: r.data.autoConfirmDays,
        notifyAfterShip: r.data.notifyAfterShip
      }
    }
  } catch {}
}
const saveConfig = async () => {
  if (!accounts.value.length) {
    return ElMessage.warning('请先添加账号')
  }
  configLoading.value = true
  try {
    // 提交时带上当前账号 id，字段名与后端一致
    const payload = {
      accountId: accounts.value[0].id,
      enabled: configForm.value.enabled,
      delaySeconds: configForm.value.delaySeconds,
      autoConfirmDays: configForm.value.autoConfirmDays,
      notifyAfterShip: configForm.value.notifyAfterShip
    }
    await saveVirtualShipConfig(payload)
    ElMessage.success('配置已保存')
    await loadConfig()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '保存配置失败')
  } finally { configLoading.value = false }
}

// 卡密
const cards = ref([])
const cardLoading = ref(false)
const showAddCardDialog = ref(false)
const cardText = ref('')
const loadCards = async () => {
  cardLoading.value = true
  try { const r = await listVirtualCards(); cards.value = r.data || [] }
  finally { cardLoading.value = false }
}
const addCards = async () => {
  const list = cardText.value.split('\n').map(s => s.trim()).filter(Boolean)
  if (!list.length) return ElMessage.warning('请输入至少一个卡密')
  await importVirtualCards({ productId: null, cards: list })
  ElMessage.success(`已添加 ${list.length} 个卡密`)
  showAddCardDialog.value = false
  cardText.value = ''
  loadCards()
}
const deleteCard = async (id) => {
  await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
  await deleteVirtualCard(id)
  ElMessage.success('已删除')
  loadCards()
}

// 网盘文件
const storageAccounts = ref([])
const files = ref([])
const fileLoading = ref(false)
const fileFilterAccountId = ref(null)
const showUploadDialog = ref(false)
const uploading = ref(false)
const selectedFile = ref(null)
const loadStorageAccounts = async () => {
  try { const r = await listStorageAccounts(); storageAccounts.value = r.data || [] } catch {}
}
const loadFiles = async () => {
  if (!fileFilterAccountId.value) { files.value = []; return }
  fileLoading.value = true
  try { const r = await listStorageFiles(fileFilterAccountId.value); files.value = r.data || [] }
  finally { fileLoading.value = false }
}
const handleFileChange = f => { selectedFile.value = f.raw }
const uploadFile = async () => {
  if (!selectedFile.value || !fileFilterAccountId.value) return ElMessage.warning('请选择账号和文件')
  uploading.value = true
  try {
    await uploadStorageFile(fileFilterAccountId.value, selectedFile.value)
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    loadFiles()
  } catch { ElMessage.error('上传失败') }
  finally { uploading.value = false }
}
const testShare = async (row) => {
  const r = await shareStorageFile(row.id)
  ElMessage.success(`分享链接: ${r.data}`)
  loadFiles()
}

// 发货任务
const tasks = ref([])
const taskLoading = ref(false)
const loadTasks = async () => {
  taskLoading.value = true
  try { const r = await listVirtualShipTasks({ size: 50 }); tasks.value = r.data?.records || r.data || [] }
  finally { taskLoading.value = false }
}
const triggerShip = async (row) => {
  try {
    await api.post(`/virtual-ship/tasks/${row.id}/trigger`)
    ElMessage.success('发货已触发')
    loadTasks()
  } catch {}
}

const formatTime = t => t ? new Date(t).toLocaleString('zh-CN') : '-'
const formatSize = b => {
  if (!b) return '0 B'
  const u = ['B','KB','MB','GB']; let i = 0
  while (b >= 1024 && i < u.length-1) { b /= 1024; i++ }
  return `${b.toFixed(1)} ${u[i]}`
}
const providerLabel = p => ({ BAIDU_NETDISK:'百度网盘', QUARK_NETDISK:'夸克网盘', ALIYUN_DRIVE:'阿里云盘' }[p] || p)
const statusType = s => ({ COMPLETED:'success', UPLOADING:'warning', FAILED:'danger', PENDING:'info' }[s] || 'info')
const taskStatusType = s => ({ SUCCESS:'success', SHIPPED:'success', FAILED:'error', PENDING:'warning' }[s] || 'info')

onMounted(() => {
  loadAccounts().then(loadConfig)
  loadCards()
  loadStorageAccounts()
  loadTasks()
})
</script>
