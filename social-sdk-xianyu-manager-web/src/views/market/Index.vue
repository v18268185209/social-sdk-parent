<template>
  <div class="page-root">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="关键词管理" name="manage" />
      <el-tab-pane label="关键词趋势" name="trend" />
      <el-tab-pane label="价格分布" name="distribution" />
      <el-tab-pane label="卖家画像" name="seller" />
    </el-tabs>

    <!-- ====== 关键词管理 ====== -->
    <div v-show="activeTab === 'manage'">
      <el-card style="margin-bottom: 16px;">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
          <el-input v-model="newKeyword" placeholder="输入关键词，如：iPhone 15" style="width:220px;" @keyup.enter="addKeyword" />
          <el-input-number v-model="newInterval" :min="5" :max="1440" label="间隔(分钟)" style="width:130px;" />
          <el-button type="primary" @click="addKeyword" :disabled="!newKeyword.trim()">添加追踪</el-button>
        </div>
      </el-card>

      <el-card>
        <template #header>
          <span>已追踪关键词（{{ keywords.length }}）</span>
          <el-button style="float:right;" size="small" @click="loadKeywords">刷新</el-button>
        </template>
        <el-table :data="keywords" stripe size="small" v-loading="loadingKeywords">
          <el-table-column prop="keyword" label="关键词" min-width="150" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="crawlIntervalMinutes" label="间隔(分钟)" width="110" />
          <el-table-column prop="lastCrawlResultCount" label="上次抓取数" width="110" />
          <el-table-column prop="lastCrawlAt" label="上次抓取时间" width="180">
            <template #default="{ row }">
              {{ row.lastCrawlAt ? row.lastCrawlAt.replace('T', ' ').substring(0, 19) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280">
            <template #default="{ row }">
              <el-button size="small" @click="crawlKeyword(row)" :loading="row._crawling">立即抓取</el-button>
              <el-button v-if="row.status === 'ACTIVE'" size="small" @click="pauseKeyword(row)">暂停</el-button>
              <el-button v-else size="small" type="success" @click="resumeKeyword(row)">恢复</el-button>
              <el-button size="small" type="danger" @click="deleteKeyword(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loadingKeywords && keywords.length === 0" description="暂无追踪关键词，请添加" />
      </el-card>
    </div>

    <!-- ====== 关键词趋势 ====== -->
    <div v-show="activeTab === 'trend'">
      <el-card style="margin-bottom: 16px;">
        <div style="display:flex;gap:12px;align-items:center;flex-wrap:wrap;">
          <el-select v-model="keyword" placeholder="选择关键词" style="width:220px;" @change="loadTrend">
            <el-option v-for="kw in keywordOptions" :key="kw" :label="kw" :value="kw" />
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
            <el-option v-for="kw in keywordOptions" :key="kw" :label="kw" :value="kw" />
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
  getMarketKeywords, addMarketKeyword, pauseMarketKeyword, resumeMarketKeyword,
  deleteMarketKeyword, crawlMarketKeyword,
  getMarketTrend, getMarketLatest, getMarketDistribution,
  computeDailyStat, fetchSeller, searchSeller
} from '@/api/market'

use([CanvasRenderer, LineChart, TooltipComponent, LegendComponent, GridComponent])

const activeTab = ref('manage')
const keywords = ref([])
const newKeyword = ref('')
const newInterval = ref(30)
const loadingKeywords = ref(false)
const keyword = ref('')
const distKeyword = ref('')
const distDays = ref(7)
const trendData = ref([])
const latestStat = ref(null)
const distribution = ref(null)
const sellers = ref([])
const sellerQuery = ref('')
const loadingSellers = ref(false)

const keywordOptions = computed(() => keywords.value.map(k => k.keyword))

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
  loadingKeywords.value = true
  try {
    const r = await getMarketKeywords()
    if (r.success) keywords.value = r.data
  } catch (e) {}
  loadingKeywords.value = false
}

async function addKeyword() {
  if (!newKeyword.value.trim()) return
  try {
    const r = await addMarketKeyword(newKeyword.value.trim(), newInterval.value)
    if (r.success) {
      ElMessage.success('添加成功')
      newKeyword.value = ''
      await loadKeywords()
    }
  } catch (e) { ElMessage.error('添加失败') }
}

async function pauseKeyword(row) {
  try {
    await pauseMarketKeyword(row.keyword)
    ElMessage.success('已暂停')
    await loadKeywords()
  } catch (e) { ElMessage.error('操作失败') }
}

async function resumeKeyword(row) {
  try {
    await resumeMarketKeyword(row.keyword)
    ElMessage.success('已恢复')
    await loadKeywords()
  } catch (e) { ElMessage.error('操作失败') }
}

async function deleteKeyword(row) {
  try {
    await deleteMarketKeyword(row.keyword)
    ElMessage.success('已删除')
    await loadKeywords()
  } catch (e) { ElMessage.error('删除失败') }
}

async function crawlKeyword(row) {
  row._crawling = true
  try {
    const r = await crawlMarketKeyword(row.keyword)
    if (r.success && r.data >= 0) {
      ElMessage.success(`抓取完成，共 ${r.data} 条商品`)
    } else {
      ElMessage.warning('抓取失败，请检查账号 Cookie')
    }
    await loadKeywords()
  } catch (e) { ElMessage.error('抓取失败') }
  row._crawling = false
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
