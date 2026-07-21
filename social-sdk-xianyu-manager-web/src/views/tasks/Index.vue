<template>
  <div class="page-root">
    <el-card style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>监控任务</span>
          <el-button type="primary" size="small" @click="createVisible = true">新增任务</el-button>
        </div>
      </template>
      <el-table :data="tasks" stripe v-loading="loading" size="small">
        <el-table-column prop="name" label="任务名称" width="160" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.taskType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keyword" label="关键词" width="140" />
        <el-table-column label="价格区间" width="140">
          <template #default="{ row }">
            {{ row.minPrice || 0 }} - {{ row.maxPrice || '∞' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : row.status === 'PAUSED' ? 'warning' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="runCount" label="运行次数" sortable width="100" />
        <el-table-column prop="lastRunAt" label="上次运行" width="160" />
        <el-table-column prop="nextRunAt" label="下次运行" width="160" />
        <el-table-column label="熔断" width="80">
          <template #default="{ row }">
            <span v-if="row.circuitOpen" style="color:#F56C6C;">开闸</span>
            <span v-else style="color:#909399;">正常</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewResults(row)">结果</el-button>
            <el-button size="small" @click="togglePause(row)">
              {{ row.status === 'ACTIVE' ? '暂停' : '恢复' }}
            </el-button>
            <el-button size="small" @click="runNow(row)">立即</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增任务弹窗 -->
    <el-dialog v-model="createVisible" title="新增监控任务" width="500px">
      <el-form :model="taskForm" label-width="100px">
        <el-form-item label="任务名称"><el-input v-model="taskForm.name" /></el-form-item>
        <el-form-item label="绑定账号">
          <el-select v-model="taskForm.accountId" placeholder="选择账号">
            <el-option v-for="a in accounts" :key="a.id" :label="a.displayName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词"><el-input v-model="taskForm.keyword" /></el-form-item>
        <el-form-item label="最低价"><el-input-number v-model="taskForm.minPrice" :min="0" /></el-form-item>
        <el-form-item label="最高价"><el-input-number v-model="taskForm.maxPrice" :min="0" /></el-form-item>
        <el-form-item label="间隔分钟"><el-input-number v-model="taskForm.intervalMinutes" :min="5" /></el-form-item>
        <el-form-item label="AI 分析">
          <el-switch v-model="taskForm.aiEnabled" />
        </el-form-item>
        <el-form-item v-if="taskForm.aiEnabled" label="AI 模型">
          <el-select v-model="taskForm.aiModelId" placeholder="选择模型">
            <el-option v-for="m in aiModels" :key="m.id" :label="m.displayName" :value="m.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="doCreateTask">创建</el-button>
      </template>
    </el-dialog>

    <!-- 结果弹窗 -->
    <el-dialog v-model="resultsVisible" title="监控结果" width="700px">
      <el-table :data="results" stripe size="small">
        <el-table-column prop="itemTitle" label="商品标题" min-width="200" />
        <el-table-column prop="price" label="价格" width="100" />
        <el-table-column prop="sellerNickname" label="卖家" width="120" />
        <el-table-column prop="aiScore" label="AI评分" sortable width="100" />
        <el-table-column label="链接" width="100">
          <template #default="{ row }">
            <el-button size="small" link tag="a" :href="row.itemUrl" target="_blank">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskList, createTask, pauseTask, resumeTask, runTask, getTaskResults } from '@/api/market'
import api from '@/api/request'

const tasks = ref([])
const loading = ref(false)
const createVisible = ref(false)
const resultsVisible = ref(false)
const results = ref([])
const accounts = ref([])
const aiModels = ref([])
const taskForm = ref({
  name: '',
  accountId: null,
  keyword: '',
  minPrice: null,
  maxPrice: null,
  intervalMinutes: 30,
  aiEnabled: false,
  aiModelId: null,
})

async function loadData() {
  loading.value = true
  try {
    const r = await getTaskList(0, 50)
    if (r.success) tasks.value = r.data
  } catch (e) {}
  loading.value = false
}

async function doCreateTask() {
  try {
    await createTask(taskForm.value)
    ElMessage.success('任务已创建')
    createVisible.value = false
    await loadData()
  } catch (e) {}
}

async function togglePause(row) {
  try {
    if (row.status === 'ACTIVE') await pauseTask(row.id)
    else await resumeTask(row.id)
    await loadData()
  } catch (e) {}
}

async function runNow(row) {
  try {
    await runTask(row.id)
    ElMessage.success('已触发执行')
  } catch (e) {}
}

async function viewResults(row) {
  try {
    const r = await getTaskResults(row.id, 30)
    if (r.success) {
      results.value = r.data
      resultsVisible.value = true
    }
  } catch (e) {}
}

onMounted(async () => {
  await loadData()
  try {
    // 真实端点：GET /api/accounts（返回数组）、GET /api/ai/models（返回分页 Page）
    const [ar, mr] = await Promise.all([
      api.get('/accounts'),
      api.get('/ai/models', { params: { size: 200 } })
    ])
    if (ar.success) accounts.value = ar.data
    if (mr.success) aiModels.value = (mr.data?.records || []).filter(m => m.enabled !== false)
  } catch (e) {}
})
</script>
