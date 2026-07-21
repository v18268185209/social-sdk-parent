<template>
  <div class="page-root">
    <!-- 全局重置 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>熔断器管理</span>
          <el-button size="small" @click="loadBreakers">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon>
        熔断器用于按账号+服务维度自动降级，防止雪崩。状态：关闭(正常) → 打开(拒绝请求) → 半开(探测中) → 关闭(恢复)。
      </el-alert>

      <el-table :data="breakers" stripe v-loading="loading" style="margin-top: 16px;" row-key="id">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="账号" width="180">
          <template #default="{ row }">
            {{ row.accountId ? (accountMap[row.accountId] || row.accountId + ' (已删除)') : '全局' }}
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="stateType(row.state)" effect="dark">
              {{ stateLabel(row.state) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败/成功" width="100">
          <template #default="{ row }">{{ row.failureCount }} / {{ row.successCount }}</template>
        </el-table-column>
        <el-table-column prop="lastFailureAt" label="上次失败时间" width="170">
          <template #default="{ row }">{{ formatTime(row.lastFailureAt) }}</template>
        </el-table-column>
        <el-table-column prop="cooldownUntil" label="冷却截止" width="170">
          <template #default="{ row }">
            <span v-if="row.state === 'OPEN'">{{ formatTime(row.cooldownUntil) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="thresholdCount" label="阈值" width="70" align="center" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button type="primary" link :disabled="!row.accountId" @click="handleReset(row)">
              重置
            </el-button>
            <el-button v-if="!row.accountId" type="warning" link @click="handleGlobalReset(row)">
              全局重置
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !breakers.length" description="暂无熔断器记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, WarningFilled } from '@element-plus/icons-vue'
import { listCircuitBreakers, resetCircuitBreaker, resetGlobalCircuitBreaker } from '@/api/circuitBreaker'
import { fetchAccounts } from '@/api/account'

const loading = ref(false)
const breakers = ref([])
const accounts = ref([])
const accountMap = ref({})

onMounted(async () => {
  await loadAccounts()
  await loadBreakers()
})

async function loadAccounts() {
  try {
    const res = await fetchAccounts()
    if (res.success) {
      accounts.value = Array.isArray(res.data) ? res.data : []
      for (const a of accounts.value) accountMap.value[a.id] = a.displayName || a.accountName
    }
  } catch (e) {}
}

async function loadBreakers() {
  loading.value = true
  try {
    const res = await listCircuitBreakers()
    if (res.success) breakers.value = res.data || []
  } catch (e) {}
  finally { loading.value = false }
}

function handleReset(row) {
  ElMessageBox.confirm(`确定要重置熔断器「${row.serviceName}」吗？`, '重置确认', {
    confirmButtonText: '重置',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const r = await resetCircuitBreaker(row.accountId, row.serviceName)
    if (r.success) {
      ElMessage.success('熔断器已重置')
      await loadBreakers()
    } else {
      ElMessage.error(r.message || '重置失败')
    }
  }).catch(() => {})
}

function handleGlobalReset(row) {
  ElMessageBox.confirm(`确定要重置全局熔断器「${row.serviceName}」吗？`, '全局重置确认', {
    confirmButtonText: '重置',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    const r = await resetGlobalCircuitBreaker(row.serviceName)
    if (r.success) {
      ElMessage.success('全局熔断器已重置')
      await loadBreakers()
    } else {
      ElMessage.error(r.message || '重置失败')
    }
  }).catch(() => {})
}

function stateType(s) { return { CLOSED: 'success', OPEN: 'danger', HALF_OPEN: 'warning' }[s] || 'info' }
function stateLabel(s) { return { CLOSED: '关闭(正常)', OPEN: '打开(拒绝)', HALF_OPEN: '半开(探测)' }[s] || s }
function formatTime(t) {
  if (!t) return '-'
  return t.replace('T', ' ')
}
</script>

<style scoped>
.page-root { padding: 0; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
