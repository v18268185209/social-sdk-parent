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
          <el-button type="primary" size="small">新增知识条目</el-button>
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
        </el-table>
      </div>
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

async function loadData() {
  try {
    const [sr, kr] = await Promise.all([
      api.get('/cs/sessions?page=0&size=50'),
      api.get('/cs/knowledge')
    ])
    if (sr.success) sessions.value = sr.data
    if (kr.success) knowledgeList.value = kr.data
  } catch (e) {}
}

onMounted(loadData)
</script>
