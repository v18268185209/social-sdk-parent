<template>
  <div class="page-root">
    <el-card style="margin: 0;">
      <template #header>
        <div style="display: flex; justify-content: space-between;">
          <span>监控面板</span>
          <el-button size="small" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6" v-for="card in statCards" :key="card.title">
          <el-card shadow="hover" style="margin-bottom: 16px;">
            <div class="stat-content">
              <div>
                <div class="stat-value">{{ card.value }}</div>
                <div class="stat-label">{{ card.title }}</div>
              </div>
              <el-icon :size="36" :color="card.color"><component :is="card.icon" /></el-icon>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top: 16px;">
      <template #header><span>账号维度统计</span></template>
      <el-table :data="accounts" stripe>
        <el-table-column prop="displayName" label="账号名称" width="180">
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 8px;">
              <el-avatar :size="28" :src="row.avatar" />
              <span>{{ row.displayName || row.accountName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" sortable width="100" />
        <el-table-column prop="onSaleCount" label="在售" sortable width="100" />
        <el-table-column prop="viewCount" label="浏览量" sortable width="100" />
        <el-table-column prop="favoriteCount" label="收藏数" sortable width="100" />
        <el-table-column prop="todayReplies" label="今日回复" sortable width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboard, clearCache } from '@/api/monitor'

const overview = ref({})
const accounts = ref([])

const statCards = computed(() => [
  { title: '总账号数', value: overview.value.totalAccounts || 0, icon: 'User', color: '#409EFF' },
  { title: '在线账号', value: overview.value.onlineAccounts || 0, icon: 'CircleCheck', color: '#67C23A' },
  { title: '总商品数', value: overview.value.totalProducts || 0, icon: 'Goods', color: '#E6A23C' },
  { title: '在售商品', value: overview.value.onSaleProducts || 0, icon: 'Shop', color: '#F56C6C' },
  { title: '今日回复', value: overview.value.todayReplies || 0, icon: 'ChatLineRound', color: '#909399' },
  { title: '总浏览量', value: overview.value.totalViews || 0, icon: 'View', color: '#73C0DE' },
  { title: '总收藏', value: overview.value.totalFavorites || 0, icon: 'Star', color: '#FC8452' },
  { title: '异常账号', value: overview.value.cookieExpiredAccounts || 0, icon: 'Warning', color: '#EE6666' }
])

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[status] || 'info'
}

function statusLabel(status) {
  return { ACTIVE: '在线', DISABLED: '离线', FROZEN: '冻结', COOKIE_EXPIRED: '过期' }[status] || status
}

async function loadDashboard() {
  try {
    const r1 = await getDashboard()
    if (r1.success) {
      overview.value = r1.data.overview || {}
      accounts.value = r1.data.accounts || []
    }
  } catch (e) {}
}

async function handleRefresh() {
  await clearCache()
  await loadDashboard()
}

onMounted(loadDashboard)
</script>

<style scoped>
.stat-content { display: flex; justify-content: space-between; align-items: center; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
