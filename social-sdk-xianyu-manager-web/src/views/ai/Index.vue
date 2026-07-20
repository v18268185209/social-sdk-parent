<template>
  <div class="ai-page">
    <!-- ===== 页面头 ===== -->
    <div class="page-head">
      <div class="page-head__title">
        <h2>AI 厂商与模型管理</h2>
        <p>管理 AI 厂商与模型，配置可用于商品优化的对话能力</p>
      </div>
      <div class="page-head__actions">
        <el-button type="primary" @click="openProviderDialog()">
          <el-icon><Plus /></el-icon> 添加厂商
        </el-button>
      </div>
    </div>

    <el-row :gutter="16" class="ai-body">
      <!-- ===== 左栏：厂商列表（主） ===== -->
      <el-col :xs="24" :sm="9" :md="8" :lg="7" :xl="6">
        <el-card class="vendor-card" shadow="never">
          <div class="vendor-card__head">
            <span class="vendor-card__title">AI 厂商 <em>{{ providers.length }}</em></span>
          </div>

          <div class="vendor-list" v-if="providers.length">
            <div
              v-for="p in providers"
              :key="p.id"
              class="vendor-item"
              :class="{ 'is-active': selectedProvider && selectedProvider.id === p.id }"
              @click="selectProvider(p)"
            >
              <div class="vendor-item__avatar" :style="{ background: typeColor(p.providerType) }">
                {{ (p.name || '?').charAt(0).toUpperCase() }}
              </div>
              <div class="vendor-item__main">
                <div class="vendor-item__name">{{ p.name }}</div>
                <div class="vendor-item__meta">
                  <el-tag size="small" effect="plain" :type="typeTag(p.providerType)">
                    {{ typeLabel(p.providerType) }}
                  </el-tag>
                  <span class="vendor-item__url" :title="p.apiBaseUrl">{{ p.apiBaseUrl }}</span>
                </div>
              </div>
              <div class="vendor-item__side">
                <el-switch
                  v-model="p.enabled"
                  size="small"
                  @click.stop
                  @change="toggleProvider(p)"
                />
                <div class="vendor-item__ops">
                  <el-button text size="small" title="编辑" @click.stop="openProviderDialog(p)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button text size="small" type="danger" title="删除" @click.stop="deleteProvider(p)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </div>

          <el-empty v-else description="还没有 AI 厂商" :image-size="90">
            <el-button type="primary" size="small" @click="openProviderDialog()">添加厂商</el-button>
          </el-empty>
        </el-card>
      </el-col>

      <!-- ===== 右栏：模型管理（从） ===== -->
      <el-col :xs="24" :sm="15" :md="16" :lg="17" :xl="18">
        <!-- 已选厂商 -->
        <el-card class="model-card" shadow="never" v-if="selectedProvider">
          <div class="model-card__head">
            <div class="model-card__vendor">
              <div class="vendor-item__avatar" :style="{ background: typeColor(selectedProvider.providerType) }">
                {{ (selectedProvider.name || '?').charAt(0).toUpperCase() }}
              </div>
              <div>
                <div class="model-card__name">
                  {{ selectedProvider.name }}
                  <el-tag size="small" effect="plain" :type="typeTag(selectedProvider.providerType)">
                    {{ typeLabel(selectedProvider.providerType) }}
                  </el-tag>
                </div>
                <div class="model-card__url" :title="selectedProvider.apiBaseUrl">{{ selectedProvider.apiBaseUrl }}</div>
              </div>
            </div>
            <div class="model-card__actions">
              <span class="model-card__switch">
                <el-switch v-model="selectedProvider.enabled" size="small" @change="toggleProvider(selectedProvider)" />
                <span class="model-card__switch-label">{{ selectedProvider.enabled ? '已启用' : '已停用' }}</span>
              </span>
              <el-button @click="openProviderDialog(selectedProvider)">
                <el-icon><Edit /></el-icon> 编辑厂商
              </el-button>
              <el-button type="primary" @click="openModelDialog()">
                <el-icon><Plus /></el-icon> 添加模型
              </el-button>
            </div>
          </div>

          <el-divider style="margin: 14px 0;" />

          <div class="model-toolbar">
            <el-input
              v-model="keyword"
              placeholder="搜索模型名称 / 标识 / 能力"
              clearable
              :prefix-icon="Search"
              style="width: 300px;"
            />
            <span class="model-count">共 {{ filteredModels.length }} 个模型</span>
          </div>

          <el-table
            :data="filteredModels"
            stripe
            v-loading="loadingModels"
            class="model-table"
          >
            <el-table-column prop="displayName" label="展示名" min-width="120" />
            <el-table-column prop="modelName" label="模型标识" min-width="150" />
            <el-table-column prop="modelType" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="{ TEXT: 'primary', IMAGE: 'success', VIDEO: 'warning' }[row.modelType]">
                  {{ row.modelType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="能力" min-width="150">
              <template #default="{ row }">
                <span v-if="!capsArray(row).length" class="muted">—</span>
                <el-tag
                  v-for="c in capsArray(row)"
                  :key="c"
                  size="small"
                  effect="plain"
                  type="info"
                  style="margin: 2px 4px 2px 0;"
                >{{ c }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="defaultTemperature" label="温度" width="70" />
            <el-table-column prop="defaultMaxTokens" label="MaxTokens" width="100" />
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" @change="toggleModel(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="testModel(row)">
                  <el-icon><ChatDotRound /></el-icon> 测试
                </el-button>
                <el-button size="small" @click="openModelDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteModel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty
            v-if="!loadingModels && !filteredModels.length"
            :description="models.length ? '没有匹配的模型' : '该厂商下还没有模型，点击右上角添加'"
            :image-size="90"
            style="margin-top: 20px;"
          />
        </el-card>

        <!-- 未选厂商 -->
        <el-card class="model-card model-card--placeholder" shadow="never" v-else>
          <el-empty description="请选择左侧 AI 厂商，查看并管理其模型">
            <template #image>
              <el-icon :size="64" color="#c0c4cc"><Cpu /></el-icon>
            </template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 厂商对话框 ===== -->
    <el-dialog v-model="showProviderDialog" :title="providerForm.id ? '编辑厂商' : '添加厂商'" width="520px">
      <el-form :model="providerForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="providerForm.name" placeholder="如 OpenAI / DeepSeek" />
        </el-form-item>
        <el-form-item label="API Base URL" required>
          <el-input v-model="providerForm.apiBaseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key" required>
          <el-input v-model="providerForm.apiKey" type="password" show-password placeholder="sk-..." />
        </el-form-item>
        <el-form-item label="厂商类型">
          <el-select v-model="providerForm.providerType" style="width: 100%;">
            <el-option label="OpenAI 兼容" value="OPENAI_COMPATIBLE" />
            <el-option label="Claude" value="CLAUDE" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="providerForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showProviderDialog = false">取消</el-button>
        <el-button type="primary" @click="saveProvider">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 模型对话框 ===== -->
    <el-dialog v-model="showModelDialog" :title="modelForm.id ? '编辑模型' : '添加模型'" width="520px">
      <el-form :model="modelForm" label-width="100px">
        <el-form-item label="展示名" required>
          <el-input v-model="modelForm.displayName" placeholder="如 GPT-4o Mini" />
        </el-form-item>
        <el-form-item label="模型标识" required>
          <div class="model-name-row">
            <el-input v-model="modelForm.modelName" placeholder="如 gpt-4o-mini" />
            <el-button :loading="fetchingModels" @click="openFetchModels">
              <el-icon><Refresh /></el-icon> 获取模型
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="modelForm.modelType" style="width: 100%;">
            <el-option label="文本" value="TEXT" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力标签">
          <el-input v-model="modelForm.capabilities" placeholder="streaming, tools, thinking（逗号分隔）" />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-slider v-model="modelForm.defaultTemperature" :min="0" :max="2" :step="0.1" show-input />
        </el-form-item>
        <el-form-item label="Max Tokens">
          <el-input-number v-model="modelForm.defaultMaxTokens" :min="1" :max="128000" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="modelForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModelDialog = false">取消</el-button>
        <el-button type="primary" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <!-- ===== 获取模型对话框（OpenAI 标准 /models） ===== -->
    <el-dialog v-model="showFetchModels" title="从厂商获取模型列表" width="560px">
      <div class="fetch-tip">
        通过标准 OpenAI 接口 <code>GET {{ selectedProvider?.apiBaseUrl }}/models</code> 拉取该厂商的可用模型
      </div>
      <el-input
        v-model="fetchKeyword"
        placeholder="搜索模型名称"
        clearable
        :prefix-icon="Search"
        style="margin: 12px 0;"
      />
      <div class="remote-model-list" v-loading="fetchingModels">
        <div
          v-for="m in filteredRemoteModels"
          :key="m.id"
          class="remote-model-item"
          @click="pickRemoteModel(m)"
        >
          <span class="remote-model-item__id">{{ m.id }}</span>
          <span class="remote-model-item__owner" v-if="m.ownedBy">{{ m.ownedBy }}</span>
        </div>
        <el-empty
          v-if="!fetchingModels && !filteredRemoteModels.length"
          description="未获取到模型，请检查厂商 API 配置或网络"
          :image-size="80"
        />
      </div>
      <template #footer>
        <el-button @click="showFetchModels = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ===== 测试对话框（对话气泡样式） ===== -->
    <el-dialog v-model="showTestDialog" title="测试模型" width="640px">
      <div class="test-ctx">
        模型：<strong>{{ testModelName }}</strong>
        <span class="muted">（{{ selectedProvider ? selectedProvider.name : '' }}）</span>
      </div>

      <el-form label-width="70px" class="test-form">
        <el-form-item label="系统提示">
          <el-input v-model="testForm.systemPrompt" type="textarea" :rows="2" placeholder="系统提示词（可选）" />
        </el-form-item>
        <el-form-item label="输入">
          <el-input v-model="testForm.userMessage" type="textarea" :rows="3" placeholder="测试输入" />
        </el-form-item>
      </el-form>

      <div class="test-io">
        <div class="bubble bubble--user" v-if="testForm.userMessage">
          <div class="bubble__who">你</div>
          <div class="bubble__body">{{ testForm.userMessage }}</div>
        </div>
        <div class="bubble bubble--ai" v-if="testForm.reply || testing">
          <div class="bubble__who">AI</div>
          <div class="bubble__body" v-loading="testing">
            {{ testForm.reply || (testing ? '生成中…' : '') }}
          </div>
        </div>
        <div class="bubble bubble--hint" v-if="!testForm.userMessage && !testing">
          在上方输入内容并点击「发送测试」
        </div>
      </div>

      <template #footer>
        <el-button @click="showTestDialog = false">关闭</el-button>
        <el-button type="primary" @click="sendTest" :loading="testing">
          <el-icon><Promotion /></el-icon> 发送测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Edit, Delete, ChatDotRound, Promotion, Cpu, Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'

// ===== 厂商类型映射 =====
const TYPE_COLOR = { OPENAI_COMPATIBLE: '#409EFF', CLAUDE: '#d97706', CUSTOM: '#909399' }
const TYPE_TAG = { OPENAI_COMPATIBLE: 'primary', CLAUDE: 'warning', CUSTOM: 'info' }
const TYPE_LABEL = { OPENAI_COMPATIBLE: 'OpenAI 兼容', CLAUDE: 'Claude', CUSTOM: '自定义' }
const typeColor = (t) => TYPE_COLOR[t] || '#909399'
const typeTag = (t) => TYPE_TAG[t] || 'info'
const typeLabel = (t) => TYPE_LABEL[t] || t || '未知'

// ===== 厂商 =====
const providers = ref([])
const selectedProvider = ref(null)
const showProviderDialog = ref(false)
const providerForm = ref({ id: null, name: '', apiBaseUrl: '', apiKey: '', providerType: 'OPENAI_COMPATIBLE', remark: '' })

// ===== 模型 =====
const models = ref([])
const keyword = ref('')
const loadingModels = ref(false)
const showModelDialog = ref(false)
const modelForm = ref({ id: null, providerId: null, displayName: '', modelName: '', modelType: 'TEXT', capabilities: '', defaultTemperature: 0.7, defaultMaxTokens: 1024, remark: '' })

const filteredModels = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return models.value
  return models.value.filter(m =>
    (m.displayName || '').toLowerCase().includes(k) ||
    (m.modelName || '').toLowerCase().includes(k) ||
    (m.capabilities || '').toLowerCase().includes(k)
  )
})

// ===== 获取模型（OpenAI 标准 /models） =====
const showFetchModels = ref(false)
const fetchingModels = ref(false)
const remoteModels = ref([])
const fetchKeyword = ref('')

const filteredRemoteModels = computed(() => {
  const k = fetchKeyword.value.trim().toLowerCase()
  if (!k) return remoteModels.value
  return remoteModels.value.filter(m => (m.id || '').toLowerCase().includes(k))
})

const capsArray = (row) =>
  (row.capabilities || '')
    .split(/[,，、\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)

// ===== 测试 =====
const showTestDialog = ref(false)
const testing = ref(false)
const testForm = ref({ modelId: null, systemPrompt: '', userMessage: '', reply: '' })
const testModelName = ref('')

// ===== 厂商方法 =====
async function loadProviders() {
  try {
    const res = await api.get('/ai/providers')
    if (res.success) {
      providers.value = res.data.records || []
      const prevId = selectedProvider.value?.id
      if (prevId) {
        selectedProvider.value = providers.value.find((p) => p.id === prevId) || null
      }
      if (selectedProvider.value) {
        await loadModels(selectedProvider.value.id)
      } else if (providers.value.length) {
        selectProvider(providers.value[0])
      }
    }
  } catch (e) {}
}

function selectProvider(row) {
  selectedProvider.value = row
  loadModels(row.id)
}

function openProviderDialog(row) {
  providerForm.value = row
    ? { ...row }
    : { id: null, name: '', apiBaseUrl: '', apiKey: '', providerType: 'OPENAI_COMPATIBLE', remark: '' }
  showProviderDialog.value = true
}

async function saveProvider() {
  if (!providerForm.value.name || !providerForm.value.apiBaseUrl || !providerForm.value.apiKey) {
    ElMessage.warning('请填写名称、API Base URL 和 API Key')
    return
  }
  try {
    if (providerForm.value.id) {
      await api.put(`/ai/providers/${providerForm.value.id}`, providerForm.value)
    } else {
      await api.post('/ai/providers', providerForm.value)
    }
    ElMessage.success('保存成功')
    showProviderDialog.value = false
    await loadProviders()
  } catch (e) {}
}

async function toggleProvider(row) {
  try {
    await api.put(`/ai/providers/${row.id}`, { enabled: row.enabled })
  } catch (e) {}
}

async function deleteProvider(row) {
  await ElMessageBox.confirm('确认删除该厂商？关联的模型也将无法使用。', '提示', { type: 'warning' })
  try {
    await api.delete(`/ai/providers/${row.id}`)
    ElMessage.success('已删除')
    if (selectedProvider.value?.id === row.id) selectedProvider.value = null
    await loadProviders()
  } catch (e) {}
}

// ===== 模型方法 =====
async function loadModels(providerId) {
  if (!providerId) return
  loadingModels.value = true
  try {
    const res = await api.get(`/ai/providers/${providerId}/models`)
    if (res.success) {
      models.value = res.data || []
    }
  } catch (e) {}
  finally { loadingModels.value = false }
}

function openModelDialog(row) {
  modelForm.value = row
    ? { ...row, providerId: selectedProvider.value.id }
    : { id: null, providerId: selectedProvider.value.id, displayName: '', modelName: '', modelType: 'TEXT', capabilities: '', defaultTemperature: 0.7, defaultMaxTokens: 1024, remark: '' }
  showModelDialog.value = true
}

// 通过标准 OpenAI 接口 GET {base}/models 从厂商拉取可用模型
async function openFetchModels() {
  if (!selectedProvider.value?.id) return
  showFetchModels.value = true
  fetchKeyword.value = ''
  fetchingModels.value = true
  remoteModels.value = []
  try {
    const res = await api.get(`/ai/providers/${selectedProvider.value.id}/remote-models`)
    if (res.success) {
      remoteModels.value = res.data || []
      if (!remoteModels.value.length) ElMessage.info('该厂商未返回可用模型')
    }
  } catch (e) {}
  finally { fetchingModels.value = false }
}

function pickRemoteModel(m) {
  modelForm.value.modelName = m.id
  if (!modelForm.value.displayName) modelForm.value.displayName = m.id
  showFetchModels.value = false
}

async function saveModel() {
  if (!modelForm.value.displayName || !modelForm.value.modelName) {
    ElMessage.warning('请填写展示名和模型标识')
    return
  }
  try {
    if (modelForm.value.id) {
      await api.put(`/ai/models/${modelForm.value.id}`, modelForm.value)
    } else {
      await api.post('/ai/models', modelForm.value)
    }
    ElMessage.success('保存成功')
    showModelDialog.value = false
    await loadModels(selectedProvider.value.id)
  } catch (e) {}
}

async function toggleModel(row) {
  try {
    await api.put(`/ai/models/${row.id}`, { enabled: row.enabled })
  } catch (e) {}
}

async function deleteModel(row) {
  await ElMessageBox.confirm('确认删除该模型？', '提示', { type: 'warning' })
  try {
    await api.delete(`/ai/models/${row.id}`)
    ElMessage.success('已删除')
    await loadModels(selectedProvider.value.id)
  } catch (e) {}
}

async function testModel(row) {
  testForm.value = { modelId: row.id, systemPrompt: '', userMessage: '', reply: '' }
  testModelName.value = row.displayName || row.modelName
  showTestDialog.value = true
}

async function sendTest() {
  if (!testForm.value.userMessage) {
    ElMessage.warning('请输入测试内容')
    return
  }
  testing.value = true
  try {
    const res = await api.post('/ai/chat/test', testForm.value)
    if (res.success) {
      testForm.value.reply = res.data?.reply || 'AI 未返回内容'
    }
  } catch (e) {}
  finally { testing.value = false }
}

onMounted(() => { loadProviders() })
</script>

<style scoped>
.ai-page {
  padding: 0;
}

/* 页面头 */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-head__title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.page-head__title p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.ai-body {
  align-items: stretch;
}

/* 左：厂商卡片列表 */
.vendor-card {
  height: 100%;
}
.vendor-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.vendor-card__title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.vendor-card__title em {
  font-style: normal;
  margin-left: 6px;
  padding: 1px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 10px;
}

.vendor-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 4px;
}
.vendor-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ebeef5;
  border-left: 3px solid transparent;
  border-radius: 10px;
  cursor: pointer;
  background: #fff;
  transition: all 0.18s ease;
}
.vendor-item:hover {
  border-color: #c6e2ff;
  background: #f5faff;
}
.vendor-item.is-active {
  border-color: #409eff;
  border-left-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}
.vendor-item__avatar {
  flex: 0 0 auto;
  width: 38px;
  height: 38px;
  border-radius: 9px;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.vendor-item__main {
  flex: 1 1 auto;
  min-width: 0;
}
.vendor-item__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.vendor-item__meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}
.vendor-item__url {
  font-size: 12px;
  color: #a8abb2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 130px;
}
.vendor-item__side {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.vendor-item__ops {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.18s ease;
}
.vendor-item:hover .vendor-item__ops,
.vendor-item.is-active .vendor-item__ops {
  opacity: 1;
}

/* 右：模型面板 */
.model-card {
  height: 100%;
}
.model-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.model-card__vendor {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.model-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}
.model-card__url {
  font-size: 12px;
  color: #a8abb2;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 340px;
}
.model-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.model-card__switch {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 13px;
}
.model-card__switch-label {
  white-space: nowrap;
}
.model-card--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.model-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.model-count {
  font-size: 13px;
  color: #909399;
}
.model-table {
  margin-top: 4px;
}

/* 模型标识行：输入框 + 获取模型按钮 */
.model-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}
.model-name-row .el-input {
  flex: 1 1 auto;
}

/* 获取模型对话框 */
.fetch-tip {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.fetch-tip code {
  background: #f4f4f5;
  color: #409eff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  word-break: break-all;
}
.remote-model-list {
  max-height: 360px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 6px;
}
.remote-model-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.remote-model-item:hover {
  background: #ecf5ff;
}
.remote-model-item__id {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 14px;
  color: #303133;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.remote-model-item__owner {
  flex: 0 0 auto;
  font-size: 12px;
  color: #909399;
  background: #f4f4f5;
  padding: 1px 8px;
  border-radius: 10px;
}

.muted {
  color: #c0c4cc;
}

/* 测试对话框：对话气泡 */
.test-ctx {
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
}
.test-form {
  margin-bottom: 4px;
}
.test-io {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
  padding: 12px;
  background: #f7f8fa;
  border-radius: 10px;
  min-height: 120px;
}
.bubble {
  max-width: 85%;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble__who {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.bubble--user {
  align-self: flex-end;
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}
.bubble--user .bubble__who {
  color: rgba(255, 255, 255, 0.85);
  text-align: right;
}
.bubble--ai {
  align-self: flex-start;
  background: #fff;
  color: #303133;
  border: 1px solid #ebeef5;
  border-bottom-left-radius: 2px;
}
.bubble--hint {
  align-self: center;
  background: transparent;
  color: #c0c4cc;
  font-size: 13px;
}

/* 小屏：上下堆叠时取消拉伸 */
@media (max-width: 768px) {
  .vendor-list {
    max-height: 320px;
  }
}
</style>
