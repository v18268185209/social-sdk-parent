<template>
  <div>
    <el-card>
      <template #header>
        <span>自动回复日志</span>
      </template>

      <!-- 筛选 -->
      <el-form :inline="true" :model="filterForm">
        <el-form-item label="账号">
          <el-select v-model="filterForm.accountId" placeholder="全部账号" clearable style="width: 180px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.displayName || a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filterForm.replyType" clearable style="width: 120px;">
            <el-option label="关键词" value="KEYWORD" />
            <el-option label="AI" value="AI" />
            <el-option label="兜底" value="AUTO" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配">
          <el-select v-model="filterForm.matched" clearable style="width: 100px;">
            <el-option label="命中" :value="true" />
            <el-option label="未命中" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadLogs">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="账号" width="120">
          <template #default="{ row }">
            {{ getAccountName(row.accountId) }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="{ KEYWORD: 'success', AI: 'primary', AUTO: 'warning' }[row.replyType] || 'info'">
              {{ { KEYWORD: '关键词', AI: 'AI', AUTO: '兜底' }[row.replyType] || row.replyType || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ruleName" label="规则" width="120" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" width="100" show-overflow-tooltip />
        <el-table-column prop="buyerMessage" label="买家消息" min-width="200" show-overflow-tooltip />
        <el-table-column prop="replyText" label="回复内容" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.matched ? 'success' : 'danger'">
              {{ row.matched ? '命中' : '未命中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import api from '@/api/request'

const logs = ref([])
const accounts = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filterForm = reactive({ accountId: null, replyType: null, matched: null })

function formatTime(t) {
  if (!t) return '—'
  return t.replace('T', ' ').substring(0, 19)
}

function getAccountName(id) {
  if (!id) return '-'
  const a = accounts.value.find(x => x.id === id)
  return a ? (a.displayName || a.accountName) : `账号${id}`
}

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      accounts.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    }
  } catch (e) {}
}

async function loadLogs() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (filterForm.accountId) params.accountId = filterForm.accountId
    if (filterForm.replyType) params.replyType = filterForm.replyType
    if (filterForm.matched !== null && filterForm.matched !== undefined) params.matched = filterForm.matched
    const res = await api.get('/reply-logs', { params })
    if (res.success) {
      logs.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

function resetFilter() {
  filterForm.accountId = null
  filterForm.replyType = null
  filterForm.matched = null
  page.value = 1
  loadLogs()
}

onMounted(async () => {
  await loadAccounts()
  await loadLogs()
})
</script>
