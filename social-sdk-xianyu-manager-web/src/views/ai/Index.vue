<template>
  <div class="ai-page">
    <el-row :gutter="16">
      <!-- ===== 左侧：厂商管理 ===== -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>AI 厂商管理</span>
              <el-button type="primary" size="small" @click="openProviderDialog()">
                <el-icon><Plus /></el-icon> 添加厂商
              </el-button>
            </div>
          </template>

          <el-table :data="providers" stripe @row-click="selectProvider" highlight-current-row>
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="providerType" label="类型" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.providerType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="60">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" @change="toggleProvider(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" @click.stop="openProviderDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click.stop="deleteProvider(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- ===== 右侧：模型管理 ===== -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>模型管理 — {{ selectedProvider ? selectedProvider.name : '请先选择厂商' }}</span>
              <el-button type="primary" size="small" @click="openModelDialog()" :disabled="!selectedProvider">
                <el-icon><Plus /></el-icon> 添加模型
              </el-button>
            </div>
          </template>

          <el-table :data="models" stripe v-loading="loadingModels">
            <el-table-column prop="displayName" label="展示名" width="120" />
            <el-table-column prop="modelName" label="模型标识" width="150" />
            <el-table-column prop="modelType" label="类型" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="{ TEXT: 'primary', IMAGE: 'success', VIDEO: 'warning' }[row.modelType]">
                  {{ row.modelType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="capabilities" label="能力" width="150" show-overflow-tooltip />
            <el-table-column prop="defaultTemperature" label="温度" width="60" />
            <el-table-column prop="defaultMaxTokens" label="MaxTokens" width="90" />
            <el-table-column label="状态" width="60">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" @change="toggleModel(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click.stop="testModel(row)">测试</el-button>
                <el-button size="small" @click.stop="openModelDialog(row)">编辑</el-button>
                <el-button size="small" type="danger" @click.stop="deleteModel(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 厂商对话框 ===== -->
    <el-dialog v-model="showProviderDialog" :title="providerForm.id ? '编辑厂商' : '添加厂商'" width="500px">
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
    <el-dialog v-model="showModelDialog" :title="modelForm.id ? '编辑模型' : '添加模型'" width="500px">
      <el-form :model="modelForm" label-width="100px">
        <el-form-item label="展示名" required>
          <el-input v-model="modelForm.displayName" placeholder="如 GPT-4o Mini" />
        </el-form-item>
        <el-form-item label="模型标识" required>
          <el-input v-model="modelForm.modelName" placeholder="如 gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="模型类型">
          <el-select v-model="modelForm.modelType" style="width: 100%;">
            <el-option label="文本" value="TEXT" />
            <el-option label="图片" value="IMAGE" />
            <el-option label="视频" value="VIDEO" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力标签">
          <el-input v-model="modelForm.capabilities" placeholder="streaming, tools, thinking" />
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

    <!-- ===== 测试对话框 ===== -->
    <el-dialog v-model="showTestDialog" title="测试模型" width="600px">
      <el-form label-width="80px">
        <el-form-item label="提示词">
          <el-input v-model="testForm.systemPrompt" type="textarea" :rows="3" placeholder="系统提示词（可选）" />
        </el-form-item>
        <el-form-item label="输入">
          <el-input v-model="testForm.userMessage" type="textarea" :rows="3" placeholder="测试输入" />
        </el-form-item>
        <el-form-item label="输出">
          <div v-loading="testing" style="min-height: 100px; background: #f5f7fa; padding: 12px; border-radius: 4px;">
            {{ testForm.reply || '点击发送测试' }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTestDialog = false">关闭</el-button>
        <el-button type="primary" @click="sendTest" :loading="testing">发送测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api/request'

// ===== 厂商 =====
const providers = ref([])
const selectedProvider = ref(null)
const showProviderDialog = ref(false)
const providerForm = ref({ id: null, name: '', apiBaseUrl: '', apiKey: '', providerType: 'OPENAI_COMPATIBLE', remark: '' })

// ===== 模型 =====
const models = ref([])
const loadingModels = ref(false)
const showModelDialog = ref(false)
const modelForm = ref({ id: null, displayName: '', modelName: '', modelType: 'TEXT', capabilities: '', defaultTemperature: 0.7, defaultMaxTokens: 1024, remark: '' })

// ===== 测试 =====
const showTestDialog = ref(false)
const testing = ref(false)
const testForm = ref({ modelId: null, systemPrompt: '', userMessage: '', reply: '' })

// ===== 厂商方法 =====
async function loadProviders() {
  try {
    const res = await api.get('/ai/providers')
    if (res.success) {
      providers.value = res.data.records || []
      if (providers.value.length > 0 && !selectedProvider.value) {
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
  providerForm.value = row ? { ...row } : { id: null, name: '', apiBaseUrl: '', apiKey: '', providerType: 'OPENAI_COMPATIBLE', remark: '' }
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
    const res = await api.get('/ai/models', { params: { providerId } })
    if (res.success) {
      models.value = res.data.records || []
    }
  } catch (e) {}
  finally { loadingModels.value = false }
}

function openModelDialog(row) {
  modelForm.value = row ? { ...row, providerId: selectedProvider.value.id }
    : { id: null, providerId: selectedProvider.value.id, displayName: '', modelName: '', modelType: 'TEXT', capabilities: '', defaultTemperature: 0.7, defaultMaxTokens: 1024, remark: '' }
  showModelDialog.value = true
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
      testForm.value.reply = res.data || 'AI 未返回内容'
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
</style>
