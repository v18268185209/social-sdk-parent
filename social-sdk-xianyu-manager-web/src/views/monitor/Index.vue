<template>
  <div>
    <el-card>
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
      <el-table :data="accountStats" stripe>
        <el-table-column prop="accountName" label="账号名称" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="{ ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[row.status]">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productCount" label="商品数" width="100" />
        <el-table-column prop="messageCount" label="消息数" width="100" />
        <el-table-column prop="orderCount" label="订单数" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'

const stats = ref({})
const accountStats = ref([])
const statCards = ref([])

async function loadDashboard() {
  try {
    const r1 = await api.get('/monitor/dashboard')
    if (r1.success) {
      stats.value = r1.data
      const s = r1.data
      statCards.value = [
        { title: '总账号数', value: s.totalAccounts || 0, icon: 'User', color: '#409EFF' },
        { title: '活跃账号', value: s.activeAccounts || 0, icon: 'CircleCheck', color: '#67C23A' },
        { title: '总商品数', value: s.totalProducts || 0, icon: 'Goods', color: '#E6A23C' },
        { title: '在售商品', value: s.onSaleProducts || 0, icon: 'Shop', color: '#F56C6C' },
        { title: '今日消息', value: s.todayMessages || 0, icon: 'ChatLineRound', color: '#909399' },
        { title: '待处理订单', value: s.pendingOrders || 0, icon: 'ShoppingCart', color: '#409EFF' },
        { title: '活跃规则', value: s.activeRules || 0, icon: 'Setting', color: '#67C23A' },
        { title: '总收藏', value: s.totalCollects || 0, icon: 'Star', color: '#E6A23C' }
      ]
    }
  } catch (e) {}

  try {
    const r2 = await api.get('/monitor/accounts')
    if (r2.success) accountStats.value = r2.data
  } catch (e) {}
}

function handleRefresh() { loadDashboard() }

onMounted(loadDashboard)
</script>

<style scoped>
.stat-content { display: flex; justify-content: space-between; align-items: center; }
.stat-value { font-size: 24px; font-weight: bold; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
