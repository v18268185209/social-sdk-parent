<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>规则管理</span>
          <div style="display: flex; gap: 12px; align-items: center;">
            <el-select
              v-model="selectedAccountId"
              @change="onAccountChange"
              placeholder="选择账号"
              style="width: 200px"
              :loading="accountsLoading"
              clearable
            >
              <el-option
                v-for="a in accounts"
                :key="a.id"
                :label="a.displayName || a.accountName"
                :value="a.id"
              />
            </el-select>
            <el-button type="primary" @click="openCreateDialog('KEYWORD')" :disabled="!selectedAccountId">
              <el-icon><Plus /></el-icon> 创建规则
            </el-button>
          </div>
        </div>
      </template>

      <!-- 三层 Tab -->
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="关键字词回复" name="KEYWORD" />
        <el-tab-pane label="AI 接管回复" name="AI" />
        <el-tab-pane label="自动回复" name="AUTO" />
      </el-tabs>

      <!-- ===== 关键字词回复列表 ===== -->
      <el-table v-if="activeTab === 'KEYWORD'" :data="keywordRules" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="ruleName" label="规则名称" width="150" />
        <el-table-column prop="keyword" label="关键词" width="150" />
        <el-table-column prop="matchType" label="匹配方式" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="{ CONTAINS: 'success', EXACT: 'warning', STARTS_WITH: 'info' }[row.matchType]">
              {{ { CONTAINS: '包含', EXACT: '精确', STARTS_WITH: '前缀' }[row.matchType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replyText" label="回复内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="testRule(row)">测试</el-button>
            <el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- ===== AI 接管配置 ===== -->
      <div v-else-if="activeTab === 'AI'" style="max-width: 600px;">
        <el-form :model="aiConfig" label-width="120px">
          <el-form-item label="启用 AI">
            <el-switch v-model="aiConfig.aiEnabled" />
          </el-form-item>
          <el-form-item label="AI 模型">
            <el-select v-model="aiConfig.aiModelId" placeholder="选择 AI 模型" style="width: 100%;" clearable>
              <el-option
                v-for="m in aiModels"
                :key="m.id"
                :label="m.displayName + ' (' + m.modelName + ')'"
                :value="m.id"
              />
            </el-select>
            <div style="color: #999; font-size: 12px; margin-top: 4px;">
              <router-link to="/ai">前往 AI 管理添加模型</router-link>
            </div>
          </el-form-item>
          <el-form-item label="Temperature">
            <el-slider v-model="aiConfig.aiTemperature" :min="0" :max="2" :step="0.1" show-input />
          </el-form-item>
          <el-form-item label="系统提示词">
            <el-input v-model="aiConfig.aiSystemPrompt" type="textarea" :rows="4" placeholder="你是一个友好的闲鱼卖家客服..." />
          </el-form-item>
          <el-form-item label="包含上下文">
            <el-switch v-model="aiConfig.includeChatHistory" />
            <span style="margin-left: 8px; color: #999; font-size: 12px;">带上历史对话，回复更精准</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveAiConfig" :loading="saving">保存配置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- ===== 自动回复配置 ===== -->
      <div v-else-if="activeTab === 'AUTO'" style="max-width: 600px;">
        <el-form :model="autoConfig" label-width="120px">
          <el-form-item label="启用兜底回复">
            <el-switch v-model="autoConfig.autoReplyEnabled" />
          </el-form-item>
          <el-form-item label="兜底回复话术">
            <el-input v-model="autoConfig.fallbackReply" type="textarea" :rows="2" placeholder="亲，我现在不在，稍后回复您~" />
          </el-form-item>
          <el-divider />
          <el-form-item label="欢迎语">
            <el-input v-model="autoConfig.welcomeMessage" type="textarea" :rows="2" placeholder="您好，欢迎光临~" />
            <div style="color: #999; font-size: 12px;">首次对话时自动发送</div>
          </el-form-item>
          <el-divider />
          <el-form-item label="超时回复">
            <el-input-number v-model="autoConfig.idleTimeoutMinutes" :min="1" :max="1440" />
            <span style="margin-left: 8px;">分钟未回复触发</span>
          </el-form-item>
          <el-form-item label="超时话术">
            <el-input v-model="autoConfig.idleReply" type="textarea" :rows="2" placeholder="亲，我现在忙，稍后回复您~" />
          </el-form-item>
          <el-divider />
          <el-form-item label="离线回复">
            <el-switch v-model="autoConfig.offlineReplyEnabled" />
          </el-form-item>
          <el-form-item label="离线话术">
            <el-input v-model="autoConfig.offlineReply" type="textarea" :rows="2" placeholder="亲，我现在离线，请留言~" />
          </el-form-item>
          <el-divider />
          <el-form-item label="新消息通知">
            <el-switch v-model="autoConfig.notifyOnNewMessage" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveAutoConfig" :loading="saving">保存配置</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 创建规则对话框 -->
    <el-dialog v-model="showCreateDialog" :title="createTitle" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="规则名称">
          <el-input v-model="form.ruleName" placeholder="如：在吗回复" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keyword" placeholder="如：在吗" />
        </el-form-item>
        <el-form-item label="匹配方式">
          <el-select v-model="form.matchType" style="width: 100%;">
            <el-option label="包含" value="CONTAINS" />
            <el-option label="精确" value="EXACT" />
            <el-option label="前缀" value="STARTS_WITH" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复内容">
          <el-input v-model="form.replyText" type="textarea" :rows="3" placeholder="亲，在的，有什么可以帮您？" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import api from '@/api/request'
import {
  listRules, createRule, toggleRule as toggleRuleApi,
  testRuleMatch, deleteRule as deleteRuleApi,
  getRuleConfig, saveRuleConfig
} from '@/api/rules'

// ===== 账号选择 =====
const accounts = ref([])
const accountsLoading = ref(false)
const selectedAccountId = ref(null)

// ===== Tab =====
const activeTab = ref('KEYWORD')

// ===== 关键字规则列表 =====
const keywordRules = ref([])
const loading = ref(false)

// ===== AI 配置 =====
const aiModels = ref([])
const aiConfig = reactive({
  aiEnabled: false,
  aiModelId: null,
  aiSystemPrompt: '',
  aiTemperature: 0.7,
  includeChatHistory: true
})

// ===== 自动回复配置 =====
const autoConfig = reactive({
  autoReplyEnabled: false,
  fallbackReply: '',
  welcomeMessage: '',
  idleTimeoutMinutes: 30,
  idleReply: '',
  offlineReplyEnabled: false,
  offlineReply: '',
  notifyOnNewMessage: true
})

// ===== 创建规则对话框 =====
const showCreateDialog = ref(false)
const form = ref({ ruleName: '', keyword: '', matchType: 'CONTAINS', replyText: '', priority: 100 })
const saving = ref(false)

const createTitle = computed(() => {
  return '创建关键字规则'
})

// ===== 方法 =====

async function loadAccounts() {
  accountsLoading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      accounts.value = list
      if (list.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = list[0].id
        await loadTabData()
      }
    }
  } catch (e) {}
  finally { accountsLoading.value = false }
}

async function onAccountChange() {
  await loadTabData()
}

async function onTabChange() {
  await loadTabData()
}

async function loadTabData() {
  if (!selectedAccountId.value) return
  if (activeTab.value === 'KEYWORD') {
    await loadKeywordRules()
  } else if (activeTab.value === 'AI') {
    await loadAiConfig()
  } else if (activeTab.value === 'AUTO') {
    await loadAutoConfig()
  }
}

// === 关键字规则 ===
async function loadKeywordRules() {
  if (!selectedAccountId.value) return
  loading.value = true
  try {
    const res = await listRules({ accountId: selectedAccountId.value, replyType: 'KEYWORD' })
    if (res.success) {
      keywordRules.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    }
  } catch (e) {}
  finally { loading.value = false }
}

function openCreateDialog(type) {
  form.value = { ruleName: '', keyword: '', matchType: 'CONTAINS', replyText: '', priority: 100 }
  showCreateDialog.value = true
}

async function handleCreate() {
  if (!form.value.ruleName || !form.value.keyword) {
    ElMessage.warning('请填写规则名称和关键词')
    return
  }
  form.value.accountId = selectedAccountId.value
  form.value.replyType = 'KEYWORD'
  try {
    const res = await createRule(form.value)
    if (res.success) {
      ElMessage.success('规则创建成功')
      showCreateDialog.value = false
      await loadKeywordRules()
    }
  } catch (e) {}
}

async function toggleRule(row) {
  try {
    await toggleRuleApi(row.id, row.enabled)
    ElMessage.success('规则状态已更新')
  } catch (e) {}
}

async function testRule(row) {
  try {
    const res = await testRuleMatch({
      matchType: row.matchType,
      keyword: row.keyword,
      message: '你好，请问这个还在吗？'
    })
    ElMessageBox.alert(
      res.data ? '✅ 命中规则！' : '❌ 未命中规则',
      '规则测试结果',
      { type: res.data ? 'success' : 'info' }
    )
  } catch (e) {}
}

async function deleteRule(id) {
  await ElMessageBox.confirm('确认删除该规则？', '提示', { type: 'warning' })
  try { await deleteRule(id); ElMessage.success('已删除'); await loadKeywordRules() } catch (e) {}
}

// === AI 配置 ===
async function loadAiModels() {
  try {
    // 加载所有模型（不过滤厂商，全量展示）
    const res = await api.get('/ai/models', { params: { size: 200 } })
    if (res.success) {
      aiModels.value = res.data.records || []
    }
  } catch (e) {}
}

async function loadAiConfig() {
  if (!selectedAccountId.value) return
  await loadAiModels()
  try {
    const res = await getRuleConfig(selectedAccountId.value)
    if (res.success && res.data) {
      Object.assign(aiConfig, res.data)
    }
  } catch (e) {}
}

async function saveAiConfig() {
  if (!selectedAccountId.value) return
  saving.value = true
  try {
    const res = await saveRuleConfig(selectedAccountId.value, aiConfig)
    if (res.success) {
      ElMessage.success('AI 配置保存成功')
    }
  } catch (e) {}
  finally { saving.value = false }
}

// === 自动回复配置 ===
async function loadAutoConfig() {
  if (!selectedAccountId.value) return
  try {
    const res = await getRuleConfig(selectedAccountId.value)
    if (res.success && res.data) {
      Object.assign(autoConfig, res.data)
    }
  } catch (e) {}
}

async function saveAutoConfig() {
  if (!selectedAccountId.value) return
  saving.value = true
  try {
    const res = await saveRuleConfig(selectedAccountId.value, autoConfig)
    if (res.success) {
      ElMessage.success('自动回复配置保存成功')
    }
  } catch (e) {}
  finally { saving.value = false }
}

onMounted(() => { loadAccounts() })
</script>
