<template>
  <div class="page-root">
    <el-card style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>AI 客服</span>
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="会话管理" name="sessions" />
        <el-tab-pane label="议价记录" name="bargains" />
        <el-tab-pane label="知识库" name="knowledge" />
      </el-tabs>

      <!-- 会话管理 -->
      <div v-show="activeTab === 'sessions'">
        <el-table :data="sessions" stripe size="small">
          <el-table-column prop="buyerNickname" label="买家昵称" width="140" />
          <el-table-column prop="buyerId" label="买家ID" width="140" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastMessageAt" label="最后消息" width="180" />
          <el-table-column prop="createdAt" label="创建时间" width="180" />
        </el-table>
      </div>

      <!-- 议价记录 -->
      <div v-show="activeTab === 'bargains'">
        <el-table :data="sessionStates" stripe size="small">
          <el-table-column prop="sessionId" label="会话ID" width="100" />
          <el-table-column prop="bargainRound" label="议价轮次" sortable width="100" />
          <el-table-column prop="originalPrice" label="原价" width="100" />
          <el-table-column prop="lowestOffer" label="买家最低" width="100" />
          <el-table-column prop="currentOffer" label="AI 当前报价" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.dealClosed ? (row.closedReason === 'DEAL' ? 'success' : 'danger') : 'warning'" size="small">
                {{ row.dealClosed ? (row.closedReason === 'DEAL' ? '已成交' : '丢单') : '进行中' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 知识库 -->
      <div v-show="activeTab === 'knowledge'">
        <div style="margin-bottom: 12px;">
          <el-button type="primary" size="small" @click="showAddDialog">新增知识条目</el-button>
        </div>
        <el-table :data="knowledgeList" stripe size="small">
          <el-table-column prop="question" label="问题" min-width="200" />
          <el-table-column prop="answer" label="答案" min-width="300" />
          <el-table-column label="分类" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ row.category }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="priority" label="优先级" sortable width="100" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="danger" link @click="deleteKnowledge(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 新增知识条目弹窗 -->
      <el-dialog v-model="dialogVisible" title="新增知识条目" width="500px">
        <el-form :model="knowledgeForm" label-width="80px">
          <el-form-item label="问题" required>
            <el-input v-model="knowledgeForm.question" placeholder="请输入问题关键词" />
          </el-form-item>
          <el-form-item label="答案" required>
            <el-input v-model="knowledgeForm.answer" type="textarea" :rows="4" placeholder="请输入标准答案" />
          </el-form-item>
          <el-form-item label="分类" required>
            <el-select v-model="knowledgeForm.category" placeholder="请选择分类" style="width: 100%;">
              <el-option label="价格议价" value="PRICE" />
              <el-option label="物流咨询" value="SHIPPING" />
              <el-option label="售后问题" value="AFTERSALES" />
              <el-option label="商品咨询" value="PRODUCT" />
              <el-option label="通用问题" value="GENERAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="优先级">
            <el-input-number v-model="knowledgeForm.priority" :min="0" :max="99" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitKnowledge">确定</el-button>
        </template>
      </el-dialog>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'

const activeTab = ref('sessions')
const sessions = ref([])
const sessionStates = ref([])
const knowledgeList = ref([])

const dialogVisible = ref(false)
const knowledgeForm = ref({
  question: '',
  answer: '',
  category: 'GENERAL',
  priority: 0
})

async function loadData() {
  try {
    const [sr, kr] = await Promise.all([
      api.get('/ai/cs/sessions?page=1&size=50'),
      api.get('/ai/cs/knowledge')
    ])
    if (sr.success) sessions.value = sr.data?.records || []
    if (kr.success) knowledgeList.value = kr.data?.records || []
  } catch (e) {}
}

function showAddDialog() {
  knowledgeForm.value = { question: '', answer: '', category: 'GENERAL', priority: 0 }
  dialogVisible.value = true
}

async function submitKnowledge() {
  const form = knowledgeForm.value
  if (!form.question || !form.answer || !form.category) return
  try {
    const res = await api.post('/ai/cs/knowledge', form)
    if (res.success) {
      dialogVisible.value = false
      loadData()
    }
  } catch (e) {}
}

async function deleteKnowledge(row) {
  try {
    const res = await api.delete(`/ai/cs/knowledge/${row.id}`)
    if (res.success) loadData()
  } catch (e) {}
}

onMounted(loadData)
</script>
