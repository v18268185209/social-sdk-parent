<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div>
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.title }}</div>
            </div>
            <el-icon :size="40" :color="card.color"><component :is="card.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="12">
        <el-card>
          <template #header>账号状态分布</template>
          <div v-if="accountStats.length > 0">
            <el-descriptions :column="2" border>
              <el-descriptions-item v-for="item in accountStats" :key="item.accountId" :label="item.accountName">
                <el-tag :type="statusType(item.status)">{{ item.status }}</el-tag>
                <span style="margin-left: 8px;">商品: {{ item.productCount }}</span>
                <span style="margin-left: 8px;">消息: {{ item.messageCount }}</span>
                <span style="margin-left: 8px;">订单: {{ item.orderCount }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else description="暂无账号数据" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>系统信息</template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="应用名称">AI鱼多宝</el-descriptions-item>
            <el-descriptions-item label="版本">1.0.0</el-descriptions-item>
            <el-descriptions-item label="数据库">SQLite3</el-descriptions-item>
            <el-descriptions-item label="缓存">Caffeine In-Memory</el-descriptions-item>
            <el-descriptions-item label="Java 版本">{{ javaVersion }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'

const stats = ref({})
const accountStats = ref([])
const javaVersion = ref('')

const statCards = ref([
  { title: '总账号数', value: 0, icon: 'User', color: '#409EFF' },
  { title: '活跃账号', value: 0, icon: 'CircleCheck', color: '#67C23A' },
  { title: '总商品数', value: 0, icon: 'Goods', color: '#E6A23C' },
  { title: '在售商品', value: 0, icon: 'Shop', color: '#F56C6C' },
  { title: '今日消息', value: 0, icon: 'ChatLineRound', color: '#909399' },
  { title: '待处理订单', value: 0, icon: 'ShoppingCart', color: '#409EFF' },
  { title: '活跃规则', value: 0, icon: 'Setting', color: '#67C23A' },
  { title: '总收藏', value: 0, icon: 'Star', color: '#E6A23C' }
])

function statusType(status) {
  const map = { ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }
  return map[status] || 'info'
}

onMounted(async () => {
  try {
    const res1 = await api.get('/monitor/dashboard')
    if (res1.success) {
      stats.value = res1.data
      const s = res1.data
      statCards.value[0].value = s.totalAccounts || 0
      statCards.value[1].value = s.activeAccounts || 0
      statCards.value[2].value = s.totalProducts || 0
      statCards.value[3].value = s.onSaleProducts || 0
      statCards.value[4].value = s.todayMessages || 0
      statCards.value[5].value = s.pendingOrders || 0
      statCards.value[6].value = s.activeRules || 0
      statCards.value[7].value = s.totalCollects || 0
    }
  } catch (e) { /* ignore */ }

  try {
    const res2 = await api.get('/monitor/accounts')
    if (res2.success) accountStats.value = res2.data
  } catch (e) { /* ignore */ }

  try {
    const res3 = await api.get('/system/info')
    if (res3.success) javaVersion.value = res3.data?.javaVersion || ''
  } catch (e) { /* ignore */ }
})
</script>

<style scoped>
.dashboard { padding: 0; }
.stat-card { margin-bottom: 16px; }
.stat-content { display: flex; justify-content: space-between; align-items: center; }
.stat-value { font-size: 28px; font-weight: bold; color: #303133; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
</style>
