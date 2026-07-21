<template>
  <div class="page-root">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="关键词趋势" name="trend" />
      <el-tab-pane label="价格分布" name="distribution" />
      <el-tab-pane label="卖家画像" name="seller" />
    </el-tabs>

    <!-- ====== 关键词趋势 ====== -->
    <div v-show="activeTab === 'trend'">
      <el-card style="margin-bottom: 16px;">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
          <el-select v-model="keyword" placeholder="选择关键词" style="width:220px;" @change="loadTrend">
            <el-option v-for="kw in keywords" :key="kw" :label="kw" :value="kw" />
          </el-select>
          <el-button type="primary" @click="loadTrend" :disabled="!keyword">查询趋势</el-button>
          <el-button @click="computeStat" :disabled="!keyword">计算今日统计</el-button>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span>价格趋势 - {{ keyword }}</span>
          <span v-if="latestStat" style="margin-left:16px;color:#909399;font-size:13px;">
            最新：均价 {{ latestStat.avgPrice }} | 中位数 {{ latestStat.medianPrice | '-' }} | 最低 {{ latestStat.minPrice }} | 最高 {{ latestStat.maxPrice }}
          </span>
        </template>
        <v-chart :option="trendOption" autoresize style="height:360px;" />
      </el-card>

      <el-card style="margin-top:16px;" v-if="trendData.length > 0">
        <template #header>每日统计</template>
        <el-table :data="trendData" stripe size="small">
          <el-table-column prop="statDate" label="日期" width="120" />
          <el-table-column prop="minPrice" label="最低" width="100" />
          <el-table-column prop="maxPrice" label="最高" width="100" />
          <el-table-column prop="avgPrice" label="均价" width="100" />
          <el-table-column prop="medianPrice" label="中位数" width="100" />
          <el-table-column prop="volume" label="成交量" width="100" />
          <el-table-column prop="sampledCount" label="采样数" width="100" />
        </el-table>
      </el-card>
    </div>

    <!-- ====== 价格分布 ====== -->
    <div v-show="activeTab === 'distribution'">
      <el-card style="margin-bottom: 16px;">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
          <el-select v-model="distKeyword" placeholder="选择关键词" style="width:220px;">
            <el-option v-for="kw in keywords" :key="kw" :label="kw" :value="kw" />
          </el-select>
          <el-input-number v-model="distDays" :min="1" :max="90" label="天数" style="width:120px;" />
          <el-button type="primary" @click="loadDistribution">查询</el-button>
        </div>
      </el-card>

      <el-row :gutter="16" v-if="distribution">
        <el-col :span="6"><el-card><div class="stat-box"><div class="stat-num">{{ distribution.min | '-' }}</div><div class="stat-lbl">最低价</div></div></el-card></el-col>
        <el-col :span="6"><el-card><div class="stat-box"><div class="stat-num">{{ distribution.max | '-' }}</div><div class="stat-lbl">最高价</div></div></el-card></el-col>
        <el-col :span="6"><el-card><div class="stat-box"><div class="stat-num">{{ distribution.avg | '-' }}</div><div class="stat-lbl">平均价</div></div></el-card></el-col>
        <el-col :span="6"><el-card><div class="stat-box"><div class="stat-num">{{ distribution.median | '-' }}</div><div class="stat-lbl">中位数</div></div></el-card></el-col>
      </el-row>

      <p v-if="distribution" style="color:#909399;margin-top:12px;">P25: {{ distribution.p25 | '-' }} / P75: {{ distribution.p75 | '-' }} / 样本数: {{ distribution.sampleCount }}</p>
    </div>

    <!-- ====== 卖家画像 ====== -->
    <div v-show="activeTab === 'seller'">
      <el-card style="margin-bottom: 16px;">
        <div style="display:flex;gap:12px;align-items:center;">
          <el-input v-model="sellerQuery" placeholder="输入卖家昵称或UserId" style="width:280px;" />
          <el-button type="primary" @click="doSearchSeller">搜索</el-button>
          <el-button @click="doFetchSeller">抓取画像</el-button>
        </div>
      </el-card>

      <el-table :data="sellers" stripe size="small" v-loading="loadingSellers">
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="shopLevel" label="等级" width="100" />
        <el-table-column prop="creditScore" label="信用分" width="100" />
        <el-table-column prop="followers" label="粉丝" width="100" />
        <el-table-column prop="soldCount" label="已售" width="100" />
        <el-table-column prop="onSaleCount" label="在售" width="100" />
        <el-table-column prop="ipLocation" label="IP归属" width="120" />
        <el-table-column prop="introduction" label="简介" min-width="200" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage } from 'element-plus'
import {
  getMarketKeywords, getMarketTrend, getMarketLatest, getMarketDistribution,
  computeDailyStat, fetchSeller, searchSeller
} from '@/api/market'

use([CanvasRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent])

const activeTab = ref('trend')
const keywords = ref([])
const keyword = ref('')
const distKeyword = ref('')
const distDays = ref(7)
const trendData = ref([])
const latestStat = ref(null)
const distribution = ref(null)
const sellers = ref([])
const sellerQuery = ref('')
const loadingSellers = ref(false)

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  legend: { data: ['均价', '中位数', '最低', '最高'], bottom: 0 },
  grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
  xAxis: { type: 'category', data: trendData.value.map(d => d.statDate) },
  yAxis: { type: 'value' },
  series: [
    { name: '均价', type: 'line', smooth: true, data: trendData.value.map(d => d.avgPrice), itemStyle: { color: '#409EFF' } },
    { name: '中位数', type: 'line', smooth: true, data: trendData.value.map(d => d.medianPrice), itemStyle: { color: '#67C23A' } },
    { name: '最低', type: 'line', smooth: true, data: trendData.value.map(d => d.minPrice), itemStyle: { color: '#E6A23C' } },
    { name: '最高', type: 'line', smooth: true, data: trendData.value.map(d => d.maxPrice), itemStyle: { color: '#F56C6C' } },
  ]
}))

async function loadKeywords() {
  try {
    const r = await getMarketKeywords()
    if (r.success) keywords.value = r.data
  } catch (e) {}
}

async function loadTrend() {
  if (!keyword.value) return
  try {
    const [tr, lt] = await Promise.all([
      getMarketTrend(keyword.value, 30),
      getMarketLatest(keyword.value)
    ])
    if (tr.success) trendData.value = tr.data
    if (lt.success) latestStat.value = lt.data
  } catch (e) { ElMessage.error('加载失败') }
}

async function loadDistribution() {
  if (!distKeyword.value) return
  try {
    const r = await getMarketDistribution(distKeyword.value, distDays.value)
    if (r.success) distribution.value = r.data
  } catch (e) { ElMessage.error('加载失败') }
}

async function computeStat() {
  try {
    await computeDailyStat(keyword.value)
    ElMessage.success('已触发计算')
    await loadTrend()
  } catch (e) {}
}

async function doSearchSeller() {
  if (!sellerQuery.value) return
  loadingSellers.value = true
  try {
    const r = await searchSeller(sellerQuery.value)
    if (r.success) sellers.value = r.data
  } catch (e) {}
  loadingSellers.value = false
}

async function doFetchSeller() {
  if (!sellerQuery.value) return
  try {
    await fetchSeller(sellerQuery.value)
    ElMessage.success('抓取完成')
    await doSearchSeller(false)
  } catch (e) {}
}

onMounted(loadKeywords)
</script>

<style scoped>
.stat-box { text-align: center; padding: 12px 0; }
.stat-num { font-size: 24px; font-weight: bold; color: #303133; }
.stat-lbl { font-size: 12px; color: #909399; margin-top: 4px; }
</style>
