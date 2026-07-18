<template>
  <div class="dashboard">
    <!-- 顶部统计卡片 -->
    <el-row :gutter="16">
      <el-col :span="3" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card">
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

    <!-- 图表区域 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 账号状态分布饼图 -->
      <el-col :span="8">
        <el-card>
          <template #header>
            <span>账号状态分布</span>
            <el-button text style="float: right; padding: 0;" @click="refreshCharts">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </template>
          <v-chart :option="accountStatusOption" autoresize style="height: 280px;" />
        </el-card>
      </el-col>

      <!-- 商品状态分布饼图 -->
      <el-col :span="8">
        <el-card>
          <template #header>商品状态分布</template>
          <v-chart :option="productStatusOption" autoresize style="height: 280px;" />
        </el-card>
      </el-col>

      <!-- 订单状态饼图 -->
      <el-col :span="8">
        <el-card>
          <template #header>订单状态分布</template>
          <v-chart :option="orderStatusOption" autoresize style="height: 280px;" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 近 14 天订单趋势折线图 -->
      <el-col :span="12">
        <el-card>
          <template #header>近 14 天订单趋势</template>
          <v-chart :option="orderTrendOption" autoresize style="height: 300px;" />
        </el-card>
      </el-col>

      <!-- 近 14 天消息活跃度折线图 -->
      <el-col :span="12">
        <el-card>
          <template #header>近 14 天消息活跃度</template>
          <v-chart :option="messageActivityOption" autoresize style="height: 300px;" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 账号维度数据柱状图 -->
      <el-col :span="24">
        <el-card>
          <template #header>账号维度概览</template>
          <v-chart :option="accountOverviewOption" autoresize style="height: 350px;" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 账号详情表 -->
    <el-row :gutter="16" style="margin-top: 16px;">
      <el-col :span="24">
        <el-card>
          <template #header>账号详情</template>
          <el-table :data="accounts" stripe>
            <el-table-column prop="displayName" label="账号名" width="150">
              <template #default="{ row }">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <el-avatar :size="28" :src="row.avatar" />
                  <span>{{ row.displayName || row.accountName }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="productCount" label="商品数" sortable width="100" />
            <el-table-column prop="onSaleCount" label="在售" sortable width="100" />
            <el-table-column prop="viewCount" label="浏览量" sortable width="100" />
            <el-table-column prop="favoriteCount" label="收藏数" sortable width="100" />
            <el-table-column prop="todayReplies" label="今日回复" sortable width="100" />
            <el-table-column label="Cookie 有效期" width="160">
              <template #default="{ row }">
                <span :style="{ color: cookieColor(row.cookieExpiresAt) }">
                  {{ formatCookieExpires(row.cookieExpiresAt) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart, BarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent, TitleComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'

use([CanvasRenderer, PieChart, LineChart, BarChart, TooltipComponent, LegendComponent, GridComponent, TitleComponent])

// ===== 数据 =====
const overview = ref({})
const accounts = ref([])
const orderTrend = ref([])
const messageActivity = ref([])
const accountStatus = ref([])

const statCards = computed(() => [
  { title: '总账号数', value: overview.value.totalAccounts || 0, icon: 'User', color: '#409EFF' },
  { title: '在线账号', value: overview.value.onlineAccounts || 0, icon: 'CircleCheck', color: '#67C23A' },
  { title: '总商品数', value: overview.value.totalProducts || 0, icon: 'Goods', color: '#E6A23C' },
  { title: '在售商品', value: overview.value.onSaleProducts || 0, icon: 'Shop', color: '#F56C6C' },
  { title: '今日回复', value: overview.value.todayReplies || 0, icon: 'ChatLineRound', color: '#909399' },
  { title: '总浏览量', value: overview.value.totalViews || 0, icon: 'View', color: '#73C0DE' },
  { title: '总收藏', value: overview.value.totalFavorites || 0, icon: 'Star', color: '#FC8452' },
  { title: '异常账号', value: overview.value.cookieExpiredAccounts || 0, icon: 'Warning', color: '#EE6666' },
])

// ===== 图表配置 =====

// 账号状态饼图
const accountStatusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: '5%', left: 'center' },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    avoidLabelOverlap: false,
    label: { show: false, position: 'center' },
    emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
    labelLine: { show: false },
    data: accountStatus.value.length > 0 ? accountStatus.value : [
      { name: '在线', value: overview.value.onlineAccounts || 0 },
      { name: '离线', value: overview.value.offlineAccounts || 0 },
      { name: 'Cookie过期', value: overview.value.cookieExpiredAccounts || 0 },
    ],
    color: ['#67C23A', '#909399', '#F56C6C'],
  }]
}))

// 商品状态饼图
const productStatusOption = computed(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  legend: { bottom: '5%', left: 'center' },
  series: [{
    type: 'pie',
    radius: ['40%', '70%'],
    label: { show: false, position: 'center' },
    emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
    labelLine: { show: false },
    data: [
      { name: '在售', value: overview.value.onSaleProducts || 0 },
      { name: '下架', value: overview.value.offSaleProducts || 0 },
      { name: '草稿', value: overview.value.draftProducts || 0 },
    ],
    color: ['#67C23A', '#E6A23C', '#909399'],
  }]
}))

// 订单状态饼图
const orderStatusOption = computed(() => {
  const typeMap = { SOLD: '卖出', BOUGHT: '买入' }
  const soldCount = orderTrend.value.reduce((s, d) => s + (d.sold || 0), 0)
  const boughtCount = orderTrend.value.reduce((s, d) => s + (d.bought || 0), 0)
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '5%', left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      label: { show: false, position: 'center' },
      emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold' } },
      labelLine: { show: false },
      data: [
        { name: '卖出', value: soldCount },
        { name: '买入', value: boughtCount },
      ],
      color: ['#409EFF', '#E6A23C'],
    }]
  }
})

// 订单趋势折线图
const orderTrendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['卖出', '买入'], bottom: 0 },
  grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: orderTrend.value.map(d => d.date) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      name: '卖出', type: 'line', smooth: true,
      data: orderTrend.value.map(d => d.s || d.sold || 0),
      lineStyle: { color: '#409EFF' },
      itemStyle: { color: '#409EFF' },
      areaStyle: { color: 'rgba(64,158,255,0.15)' },
    },
    {
      name: '买入', type: 'line', smooth: true,
      data: orderTrend.value.map(d => d.b || d.bought || 0),
      lineStyle: { color: '#E6A23C' },
      itemStyle: { color: '#E6A23C' },
      areaStyle: { color: 'rgba(230,162,60,0.15)' },
    },
  ]
}))

// 消息活跃度折线图
const messageActivityOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['收到', '回复'], bottom: 0 },
  grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: messageActivity.value.map(d => d.date) },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      name: '收到', type: 'line', smooth: true,
      data: messageActivity.value.map(d => d.incoming || 0),
      lineStyle: { color: '#67C23A' },
      itemStyle: { color: '#67C23A' },
      areaStyle: { color: 'rgba(103,194,58,0.15)' },
    },
    {
      name: '回复', type: 'line', smooth: true,
      data: messageActivity.value.map(d => d.outgoing || 0),
      lineStyle: { color: '#F56C6C' },
      itemStyle: { color: '#F56C6C' },
      areaStyle: { color: 'rgba(245,108,108,0.15)' },
    },
  ]
}))

// 账号维度柱状图
const accountOverviewOption = computed(() => {
  const names = accounts.value.map(a => a.displayName || a.accountName || `账号${a.accountId}`)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['在售商品', '今日回复', '浏览量', '收藏数'], bottom: 0 },
    grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '在售商品', type: 'bar', data: accounts.value.map(a => a.onSaleCount || 0), itemStyle: { color: '#409EFF' } },
      { name: '今日回复', type: 'bar', data: accounts.value.map(a => a.todayReplies || 0), itemStyle: { color: '#67C23A' } },
      { name: '浏览量', type: 'bar', data: accounts.value.map(a => a.viewCount || 0), itemStyle: { color: '#E6A23C' } },
      { name: '收藏数', type: 'bar', data: accounts.value.map(a => a.favoriteCount || 0), itemStyle: { color: '#F56C6C' } },
    ]
  }
})

// ===== 工具函数 =====

function statusType(status) {
  return { ACTIVE: 'success', DISABLED: 'info', FROZEN: 'danger', COOKIE_EXPIRED: 'warning' }[status] || 'info'
}
function statusLabel(status) {
  return { ACTIVE: '在线', DISABLED: '离线', FROZEN: '冻结', COOKIE_EXPIRED: '过期' }[status] || status
}

function formatCookieExpires(t) {
  if (!t) return '—'
  const d = new Date(t.replace(' ', 'T'))
  const now = new Date()
  const diff = Math.floor((d - now) / (1000 * 60 * 60 * 24))
  if (diff < 0) return '已过期'
  if (diff === 0) return '今天到期'
  return `剩 ${diff} 天`
}

function cookieColor(t) {
  if (!t) return '#909399'
  const d = new Date(t.replace(' ', 'T'))
  const diff = Math.floor((d - new Date()) / (1000 * 60 * 60 * 24))
  if (diff < 0) return '#F56C6C'
  if (diff <= 3) return '#E6A23C'
  return '#67C23A'
}

// ===== 加载数据 =====

async function loadDashboard() {
  try {
    const res = await api.get('/monitor/dashboard')
    if (res.success) {
      overview.value = res.data.overview || {}
      accounts.value = res.data.accounts || []
      orderTrend.value = res.data.orderTrend || []
      messageActivity.value = res.data.messageActivity || []
      accountStatus.value = res.data.accountStatus || []
    }
  } catch (e) {
    ElMessage.error('加载仪表盘数据失败')
  }
}

async function refreshCharts() {
  await api.post('/monitor/cache/clear')
  await loadDashboard()
  ElMessage.success('数据已刷新')
}

onMounted(() => { loadDashboard() })
</script>

<style scoped>
.dashboard {
  padding: 0;
}

.stat-card {
  margin-bottom: 16px;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
