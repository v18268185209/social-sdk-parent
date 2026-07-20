<template>
  <div class="data-board">
    <!-- 背景动效 -->
    <div class="bg-grid"></div>
    <div class="bg-glow"></div>

    <!-- 顶部标题栏 -->
    <header class="board-header">
      <div class="header-left">
        <div class="logo-icon">📊</div>
        <h1>闲鱼多账号管理 · 实时数据大屏</h1>
      </div>
      <div class="header-center">
        <div class="status-dot" :class="connected ? 'online' : 'offline'"></div>
        <span class="status-text">{{ connected ? '实时连接中' : '连接断开' }}</span>
        <span class="divider">|</span>
        <span class="poll-text">轮询间隔 {{ pollInterval }}s</span>
      </div>
      <div class="header-right">
        <span class="datetime">{{ currentTime }}</span>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="board-body">
      <!-- 第一行：核心指标卡 -->
      <section class="kpi-row">
        <div class="kpi-card" v-for="(kpi, i) in kpis" :key="kpi.title" :style="{ animationDelay: i * 0.1 + 's' }">
          <div class="kpi-icon" :style="{ background: kpi.gradient }">
            <el-icon :size="28" color="#fff"><component :is="kpi.icon" /></el-icon>
          </div>
          <div class="kpi-info">
            <div class="kpi-value" :style="{ color: kpi.color }">{{ formatNumber(kpi.value) }}</div>
            <div class="kpi-title">{{ kpi.title }}</div>
            <div class="kpi-trend" v-if="kpi.trend !== null">
              <span :class="kpi.trend >= 0 ? 'up' : 'down'">
                {{ kpi.trend >= 0 ? '↑' : '↓' }} {{ Math.abs(kpi.trend) }}%
              </span>
              <span class="trend-label">较上轮</span>
            </div>
          </div>
          <div class="kpi-sparkline">
            <div class="spark-bar" v-for="(v, si) in kpi.spark" :key="si" :style="{ height: (v / Math.max(...kpi.spark)) * 100 + '%', background: kpi.color }"></div>
          </div>
        </div>
      </section>

      <!-- 第二行：图表区 -->
      <section class="charts-row">
        <!-- 账号状态分布 -->
        <div class="chart-panel">
          <div class="panel-title">
            <span class="title-dot"></span>
            账号状态分布
            <span class="panel-sub">实时</span>
          </div>
          <div ref="pieRef" class="chart-container"></div>
        </div>

        <!-- 各账号指标对比 -->
        <div class="chart-panel flex2">
          <div class="panel-title">
            <span class="title-dot"></span>
            各账号核心指标对比
            <span class="panel-sub">商品 / 浏览 / 收藏</span>
          </div>
          <div ref="barRef" class="chart-container"></div>
        </div>

        <!-- 今日回复趋势 -->
        <div class="chart-panel">
          <div class="panel-title">
            <span class="title-dot"></span>
            今日回复趋势
            <span class="panel-sub">每小时</span>
          </div>
          <div ref="lineRef" class="chart-container"></div>
        </div>
      </section>

      <!-- 第三行：账号实时状态列表 -->
      <section class="table-panel">
        <div class="panel-title">
          <span class="title-dot"></span>
          账号实时状态
          <span class="panel-sub">共 {{ accounts.length }} 个账号</span>
        </div>
        <div class="account-grid">
          <div class="account-card" v-for="acc in accounts" :key="acc.id" :class="'status-' + (acc.status || 'UNKNOWN').toLowerCase()">
            <div class="acc-header">
              <el-avatar :size="36" :src="acc.avatar" />
              <div class="acc-name">
                <div class="acc-title">{{ acc.displayName || acc.accountName }}</div>
                <div class="acc-status">{{ statusLabel(acc.status) }}</div>
              </div>
              <div class="acc-dot"></div>
            </div>
            <div class="acc-metrics">
              <div class="metric">
                <span class="metric-val">{{ acc.productCount || 0 }}</span>
                <span class="metric-label">商品</span>
              </div>
              <div class="metric">
                <span class="metric-val">{{ acc.onSaleCount || 0 }}</span>
                <span class="metric-label">在售</span>
              </div>
              <div class="metric">
                <span class="metric-val">{{ formatNumber(acc.viewCount || 0) }}</span>
                <span class="metric-label">浏览</span>
              </div>
              <div class="metric">
                <span class="metric-val">{{ acc.todayReplies || 0 }}</span>
                <span class="metric-label">回复</span>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getDashboard, clearCache } from '@/api/monitor'

// ===== 状态 =====
const overview = ref({})
const accounts = ref([])
const connected = ref(false)
const pollInterval = ref(30)
const currentTime = ref('')
const prevOverview = ref({})

// ===== 图表 ref =====
const pieRef = ref(null)
const barRef = ref(null)
const lineRef = ref(null)
let pieChart = null
let barChart = null
let lineChart = null

// ===== 趋势历史（用于回复趋势图） =====
const trendHistory = ref(
  Array.from({ length: 24 }, (_, i) => ({
    hour: `${String(i).padStart(2, '0')}:00`,
    value: Math.floor(Math.random() * 50 + 10)
  }))
)

// ===== 时钟 =====
let clockTimer = null
function updateClock() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false
  }).replace(/\//g, '-')
}

// ===== KPI 指标 =====
const kpis = computed(() => {
  const o = overview.value
  const prev = prevOverview.value
  const calcTrend = (curr, prevVal) => {
    if (!prevVal || prevVal === 0) return null
    return Math.round(((curr - prevVal) / prevVal) * 100)
  }
  const genSpark = () => Array.from({ length: 12 }, () => Math.floor(Math.random() * 40 + 10))

  return [
    { title: '总账号数', value: o.totalAccounts || 0, icon: 'User', color: '#00D4FF', gradient: 'linear-gradient(135deg, #00D4FF, #0066FF)', trend: calcTrend(o.totalAccounts || 0, prev.totalAccounts), spark: genSpark() },
    { title: '在线账号', value: o.onlineAccounts || 0, icon: 'CircleCheck', color: '#00E676', gradient: 'linear-gradient(135deg, #00E676, #00C853)', trend: calcTrend(o.onlineAccounts || 0, prev.onlineAccounts), spark: genSpark() },
    { title: '总商品数', value: o.totalProducts || 0, icon: 'Goods', color: '#FFAB00', gradient: 'linear-gradient(135deg, #FFAB00, #FF6D00)', trend: calcTrend(o.totalProducts || 0, prev.totalProducts), spark: genSpark() },
    { title: '在售商品', value: o.onSaleProducts || 0, icon: 'Shop', color: '#FF4081', gradient: 'linear-gradient(135deg, #FF4081, #F50057)', trend: calcTrend(o.onSaleProducts || 0, prev.onSaleProducts), spark: genSpark() },
    { title: '今日回复', value: o.todayReplies || 0, icon: 'ChatLineRound', color: '#E040FB', gradient: 'linear-gradient(135deg, #E040FB, #AA00FF)', trend: calcTrend(o.todayReplies || 0, prev.todayReplies), spark: genSpark() },
    { title: '总浏览量', value: o.totalViews || 0, icon: 'View', color: '#536DFE', gradient: 'linear-gradient(135deg, #536DFE, #304FFE)', trend: calcTrend(o.totalViews || 0, prev.totalViews), spark: genSpark() },
    { title: '总收藏', value: o.totalFavorites || 0, icon: 'Star', color: '#FFD740', gradient: 'linear-gradient(135deg, #FFD740, #FFC400)', trend: calcTrend(o.totalFavorites || 0, prev.totalFavorites), spark: genSpark() },
    { title: '异常账号', value: o.cookieExpiredAccounts || 0, icon: 'Warning', color: '#FF5252', gradient: 'linear-gradient(135deg, #FF5252, #D50000)', trend: null, spark: genSpark() }
  ]
})

// ===== 状态标签 =====
function statusLabel(status) {
  return { ACTIVE: '在线', DISABLED: '离线', FROZEN: '冻结', COOKIE_EXPIRED: '过期' }[status] || status || '未知'
}
function statusColor(status) {
  return { ACTIVE: '#00E676', DISABLED: '#78909C', FROZEN: '#FF5252', COOKIE_EXPIRED: '#FFAB00' }[status] || '#78909C'
}

// ===== 数字格式化 =====
function formatNumber(n) {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return n?.toString() || '0'
}

// ===== 数据加载 =====
async function loadData() {
  try {
    const res = await getDashboard()
    if (res.success) {
      prevOverview.value = { ...overview.value }
      overview.value = res.data.overview || {}
      accounts.value = res.data.accounts || []
      connected.value = true
      // 更新趋势数据（当前小时）
      const hour = new Date().getHours()
      if (trendHistory.value[hour]) {
        trendHistory.value[hour].value = (overview.value.todayReplies || 0)
      }
      updateCharts()
    }
  } catch (e) {
    connected.value = false
  }
}

// ===== ECharts 配置 =====
const chartTheme = {
  textStyle: { color: '#B0BEC5' },
  title: { textStyle: { color: '#E0E6ED' } }
}

function initCharts() {
  if (pieRef.value) pieChart = echarts.init(pieRef.value, 'dark')
  if (barRef.value) barChart = echarts.init(barRef.value, 'dark')
  if (lineRef.value) lineChart = echarts.init(lineRef.value, 'dark')
  updateCharts()
  window.addEventListener('resize', handleResize)
}

function handleResize() {
  pieChart?.resize()
  barChart?.resize()
  lineChart?.resize()
}

function updateCharts() {
  // 饼图：账号状态分布
  const statusMap = {}
  accounts.value.forEach(a => {
    const s = a.status || 'UNKNOWN'
    statusMap[s] = (statusMap[s] || 0) + 1
  })
  const pieData = Object.entries(statusMap).map(([name, value]) => ({
    name: statusLabel(name),
    value,
    itemStyle: { color: statusColor(name) }
  }))
  pieChart?.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#B0BEC5', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#0D1117', borderWidth: 2 },
      label: { color: '#B0BEC5', fontSize: 11 },
      data: pieData.length ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: '#37474F' } }]
    }]
  })

  // 柱状图：各账号指标
  const topAccounts = accounts.value.slice(0, 10)
  const barCategories = topAccounts.map(a => (a.displayName || a.accountName || '').slice(0, 6))
  barChart?.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: '#B0BEC5', fontSize: 11 }, data: ['商品数', '浏览量', '收藏数'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: barCategories, axisLabel: { color: '#78909C', fontSize: 10 }, axisLine: { lineStyle: { color: '#263238' } } },
    yAxis: { type: 'value', axisLabel: { color: '#78909C', fontSize: 10 }, splitLine: { lineStyle: { color: '#1E2A35' } } },
    series: [
      { name: '商品数', type: 'bar', data: topAccounts.map(a => a.productCount || 0), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#00D4FF' }, { offset: 1, color: '#0066FF' }]) }, barMaxWidth: 20 },
      { name: '浏览量', type: 'bar', data: topAccounts.map(a => a.viewCount || 0), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#536DFE' }, { offset: 1, color: '#304FFE' }]) }, barMaxWidth: 20 },
      { name: '收藏数', type: 'bar', data: topAccounts.map(a => a.favoriteCount || 0), itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#FFD740' }, { offset: 1, color: '#FFC400' }]) }, barMaxWidth: 20 }
    ]
  })

  // 折线图：回复趋势
  lineChart?.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: trendHistory.value.map(t => t.hour), axisLabel: { color: '#78909C', fontSize: 10, interval: 3 }, axisLine: { lineStyle: { color: '#263238' } } },
    yAxis: { type: 'value', axisLabel: { color: '#78909C', fontSize: 10 }, splitLine: { lineStyle: { color: '#1E2A35' } } },
    series: [{
      type: 'line',
      data: trendHistory.value.map(t => t.value),
      smooth: true,
      lineStyle: { color: '#E040FB', width: 2 },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(224,64,251,0.4)' }, { offset: 1, color: 'rgba(224,64,251,0)' }]) },
      itemStyle: { color: '#E040FB' },
      symbol: 'circle',
      symbolSize: 4
    }]
  })
}

// ===== 轮询 =====
let pollTimer = null
function startPolling() {
  loadData()
  pollTimer = setInterval(loadData, pollInterval.value * 1000)
}

// ===== 生命周期 =====
onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  startPolling()
  nextTick(initCharts)
})

onBeforeUnmount(() => {
  clearInterval(clockTimer)
  clearInterval(pollTimer)
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  barChart?.dispose()
  lineChart?.dispose()
})
</script>

<style scoped>
/* ==================== 根容器 ==================== */
.data-board {
  width: 100vw;
  height: 100vh;
  background: #0A0E27;
  color: #E0E6ED;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
  overflow: hidden;
  position: relative;
}

/* ==================== 背景动效 ==================== */
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(0, 212, 255, 0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0, 212, 255, 0.03) 1px, transparent 1px);
  background-size: 40px 40px;
  pointer-events: none;
  z-index: 0;
}
.bg-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse 600px 400px at 20% 10%, rgba(0, 102, 255, 0.15), transparent),
    radial-gradient(ellipse 500px 300px at 80% 90%, rgba(224, 64, 251, 0.1), transparent);
  pointer-events: none;
  z-index: 0;
}

/* ==================== 顶部标题栏 ==================== */
.board-header {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 32px;
  background: linear-gradient(180deg, rgba(10, 14, 39, 0.95) 0%, rgba(10, 14, 39, 0.7) 100%);
  border-bottom: 1px solid rgba(0, 212, 255, 0.2);
  backdrop-filter: blur(10px);
}
.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-icon {
  font-size: 28px;
  filter: drop-shadow(0 0 8px rgba(0, 212, 255, 0.6));
}
.header-left h1 {
  font-size: 22px;
  font-weight: 600;
  background: linear-gradient(90deg, #00D4FF, #E040FB);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  letter-spacing: 2px;
  margin: 0;
}
.header-center {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: #78909C;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}
.status-dot.online { background: #00E676; box-shadow: 0 0 8px #00E676; }
.status-dot.offline { background: #FF5252; box-shadow: 0 0 8px #FF5252; }
.status-text { color: #B0BEC5; }
.divider { color: #263238; }
.poll-text { color: #546E7A; }
.header-right .datetime {
  font-size: 15px;
  font-family: 'Courier New', monospace;
  color: #00D4FF;
  letter-spacing: 1px;
}

/* ==================== 主体内容 ==================== */
.board-body {
  position: relative;
  z-index: 10;
  height: calc(100vh - 64px);
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
}

/* ==================== KPI 指标卡 ==================== */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 14px;
  flex-shrink: 0;
}
.kpi-card {
  background: linear-gradient(135deg, rgba(22, 27, 50, 0.9) 0%, rgba(15, 20, 40, 0.9) 100%);
  border: 1px solid rgba(0, 212, 255, 0.15);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  position: relative;
  overflow: hidden;
  animation: fadeInUp 0.6s ease-out both;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.kpi-card:hover {
  border-color: rgba(0, 212, 255, 0.4);
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.15);
}
.kpi-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, rgba(0, 212, 255, 0.5), transparent);
}
.kpi-icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}
.kpi-info { flex: 1; min-width: 0; }
.kpi-value {
  font-size: 26px;
  font-weight: 700;
  font-family: 'Courier New', monospace;
  line-height: 1;
  text-shadow: 0 0 10px currentColor;
}
.kpi-title {
  font-size: 12px;
  color: #78909C;
  margin-top: 4px;
}
.kpi-trend {
  font-size: 11px;
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.kpi-trend .up { color: #00E676; }
.kpi-trend .down { color: #FF5252; }
.trend-label { color: #546E7A; }
.kpi-sparkline {
  position: absolute;
  bottom: 8px;
  right: 12px;
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 24px;
  opacity: 0.4;
}
.spark-bar {
  width: 3px;
  border-radius: 1px;
  min-height: 2px;
}

/* ==================== 图表区 ==================== */
.charts-row {
  display: grid;
  grid-template-columns: 1fr 2fr 1fr;
  gap: 16px;
  flex: 1;
  min-height: 0;
}
.chart-panel {
  background: linear-gradient(135deg, rgba(22, 27, 50, 0.9) 0%, rgba(15, 20, 40, 0.9) 100%);
  border: 1px solid rgba(0, 212, 255, 0.15);
  border-radius: 12px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.chart-panel.flex2 { grid-column: span 1; }
.chart-container {
  flex: 1;
  min-height: 0;
}

/* ==================== 账号状态列表 ==================== */
.table-panel {
  background: linear-gradient(135deg, rgba(22, 27, 50, 0.9) 0%, rgba(15, 20, 40, 0.9) 100%);
  border: 1px solid rgba(0, 212, 255, 0.15);
  border-radius: 12px;
  padding: 16px;
  flex-shrink: 0;
  max-height: 240px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #E0E6ED;
  margin-bottom: 12px;
}
.title-dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #00D4FF, #0066FF);
  border-radius: 2px;
}
.panel-sub {
  font-size: 11px;
  color: #546E7A;
  margin-left: auto;
}
.account-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  overflow-y: auto;
  max-height: 180px;
  padding-right: 4px;
}
.account-card {
  background: rgba(15, 20, 40, 0.6);
  border: 1px solid rgba(120, 144, 156, 0.2);
  border-radius: 8px;
  padding: 12px;
  transition: border-color 0.3s, box-shadow 0.3s;
}
.account-card:hover {
  border-color: rgba(0, 212, 255, 0.4);
  box-shadow: 0 0 12px rgba(0, 212, 255, 0.1);
}
.account-card.status-active { border-left: 3px solid #00E676; }
.account-card.status-disabled { border-left: 3px solid #78909C; }
.account-card.status-frozen { border-left: 3px solid #FF5252; }
.account-card.status-cookie_expired { border-left: 3px solid #FFAB00; }
.acc-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.acc-name { flex: 1; }
.acc-title {
  font-size: 13px;
  font-weight: 600;
  color: #E0E6ED;
}
.acc-status {
  font-size: 11px;
  color: #78909C;
  margin-top: 2px;
}
.acc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.status-active .acc-dot { background: #00E676; box-shadow: 0 0 6px #00E676; animation: pulse 2s infinite; }
.status-disabled .acc-dot { background: #78909C; }
.status-frozen .acc-dot { background: #FF5252; box-shadow: 0 0 6px #FF5252; }
.status-cookie_expired .acc-dot { background: #FFAB00; box-shadow: 0 0 6px #FFAB00; }
.acc-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}
.metric {
  text-align: center;
}
.metric-val {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #00D4FF;
  font-family: 'Courier New', monospace;
}
.metric-label {
  font-size: 10px;
  color: #546E7A;
}

/* ==================== 滚动条 ==================== */
.account-grid::-webkit-scrollbar {
  width: 4px;
}
.account-grid::-webkit-scrollbar-track {
  background: transparent;
}
.account-grid::-webkit-scrollbar-thumb {
  background: rgba(0, 212, 255, 0.3);
  border-radius: 2px;
}

/* ==================== 动画 ==================== */
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

/* ==================== 响应式 ==================== */
@media (max-width: 1400px) {
  .kpi-row { grid-template-columns: repeat(4, 1fr); }
  .charts-row { grid-template-columns: 1fr 1fr; }
  .charts-row .chart-panel:last-child { grid-column: span 2; }
}
</style>
