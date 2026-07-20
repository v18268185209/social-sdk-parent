<template>
  <div class="page-root">
    <el-card style="margin: 0;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>审计日志</span>
          <el-button :icon="Refresh" circle @click="handleRefresh" title="刷新" />
        </div>
      </template>

      <!-- 搜索过滤 -->
      <el-form :inline="true" :model="filterForm" class="filter-form">
        <el-form-item label="操作">
          <el-input v-model="filterForm.action" placeholder="操作关键词" clearable style="width: 180px;" />
        </el-form-item>
        <el-form-item label="结果">
          <el-select v-model="filterForm.resourceType" placeholder="全部" clearable style="width: 120px;">
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILURE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 数据表格 -->
      <el-table :data="records" stripe v-loading="loading" empty-text="暂无数据">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="action" label="操作" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operatorName" label="操作人" width="120">
          <template #default="{ row }">
            {{ row.operatorName || '未知' }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.resourceType === 'SUCCESS' ? 'success' : 'danger'" size="small">
              {{ row.resourceType === 'SUCCESS' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="IP" width="130" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.actionTime) }}
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        style="margin-top: 16px; justify-content: flex-end;"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listAuditLogs } from '@/api/audit'

const records = ref([])
const total = ref(0)
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)

const filterForm = reactive({
  action: '',
  resourceType: ''
})

async function loadData() {
  loading.value = true
  try {
    const params = { page: pageNum.value, size: pageSize.value }
    if (filterForm.action) params.action = filterForm.action
    if (filterForm.resourceType) params.resourceType = filterForm.resourceType

    const res = await listAuditLogs(params)
    if (res.success) {
      records.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载审计日志失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function handleReset() {
  filterForm.action = ''
  filterForm.resourceType = ''
  pageNum.value = 1
  loadData()
}

async function handleRefresh() {
  await loadData()
  ElMessage.success('数据已刷新')
}

function formatTime(t) {
  if (!t) return '—'
  const d = typeof t === 'string' ? new Date(t) : t
  if (isNaN(d.getTime())) return '-'
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

onMounted(loadData)
</script>

<style scoped>
.filter-form {
  margin-bottom: 16px;
}
</style>
