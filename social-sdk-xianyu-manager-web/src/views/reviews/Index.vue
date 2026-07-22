<template>
  <div class="review-page">
    <el-tabs v-model="activeTab">
      <!-- 评价管理 -->
      <el-tab-pane label="评价管理" name="reviews">
        <div class="toolbar">
          <el-select v-model="accountId" placeholder="选择账号" style="width: 220px" @change="loadReviews">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
          <el-input v-model="buyerId" placeholder="用户 ID（留空拉当前账号）" style="width: 220px; margin-left: 12px" clearable />
          <el-button type="primary" @click="loadReviews" style="margin-left: 12px">拉评价列表</el-button>
        </div>
        <el-table :data="reviews" border style="margin-top: 16px" v-loading="loading">
          <el-table-column label="订单号" width="180">
            <template #default="{ row }">{{ reviewOrderId(row) }}</template>
          </el-table-column>
          <el-table-column label="评分" width="80">
            <template #default="{ row }">
              <el-tag :type="reviewRateType(row)" size="small">
                {{ reviewRateLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="评价内容">
            <template #default="{ row }">{{ reviewFeedback(row) }}</template>
          </el-table-column>
          <el-table-column label="评价人" width="120">
            <template #default="{ row }">{{ reviewRater(row) }}</template>
          </el-table-column>
          <el-table-column label="卖家" width="120">
            <template #default="{ row }">{{ reviewSeller(row) }}</template>
          </el-table-column>
          <el-table-column label="买家" width="120">
            <template #default="{ row }">{{ reviewBuyer(row) }}</template>
          </el-table-column>
          <el-table-column label="商品" min-width="160">
            <template #default="{ row }">{{ reviewItemTitle(row) }}</template>
          </el-table-column>
          <el-table-column label="评价时间" width="180">
            <template #default="{ row }">{{ reviewCreateTime(row) }}</template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">发表评价</el-divider>
        <el-form :model="reviewForm" label-width="80" inline>
          <el-form-item label="订单号">
            <el-input v-model="reviewForm.orderId" style="width: 180px" />
          </el-form-item>
          <el-form-item label="评分">
            <el-select v-model="reviewForm.rating" style="width: 100px">
              <el-option label="好评" value="GOOD" />
              <el-option label="中评" value="NORMAL" />
              <el-option label="差评" value="BAD" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="reviewForm.content" style="width: 320px" placeholder="不错的买家" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitReview">提交评价</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- 信用画像 -->
      <el-tab-pane label="信用画像" name="credit">
        <div class="toolbar">
          <el-select v-model="accountId" placeholder="选择账号" style="width: 220px" @change="loadCredit">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
          <el-input v-model="creditUserId" placeholder="用户 ID（留空取自己）" style="width: 220px; margin-left: 12px" clearable />
          <el-button type="primary" @click="loadCredit" style="margin-left: 12px">拉信用画像</el-button>
        </div>

        <!-- 用户头部信息 -->
        <div v-if="creditData" class="credit-header">
          <el-avatar :size="64" :src="creditData.data?.module?.base?.avatar?.avatar" />
          <div class="credit-header-info">
            <div class="credit-header-name">{{ creditData.data?.module?.base?.displayName || '—' }}</div>
            <div class="credit-header-meta">
              <el-icon><Location /></el-icon>
              <span>{{ creditData.data?.module?.base?.ipLocation || '未知' }}</span>
              <el-divider direction="vertical" />
              <span class="credit-header-intro">{{ creditData.data?.module?.base?.introduction || '' }}</span>
            </div>
            <!-- 信用等级标签 -->
            <div v-if="creditData.data?.module?.base?.ylzTags?.length" class="credit-tags">
              <el-tag v-for="tag in creditData.data.module.base.ylzTags" :key="tag.code"
                :type="tag.code === 'cs_seller_level' ? 'success' : 'warning'" size="small" effect="dark">
                <img v-if="tag.icon" :src="tag.icon" class="credit-tag-icon" />
                {{ tag.text }} L{{ tag.attributes?.level }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 信用分卡片 -->
        <div v-if="creditCards.length" class="credit-cards">
          <el-card v-for="c in creditCards" :key="c.label" shadow="hover" class="credit-card">
            <div class="credit-card-value">{{ c.value }}</div>
            <div class="credit-card-label">{{ c.label }}</div>
          </el-card>
        </div>

        <!-- 详细数据 -->
        <el-descriptions v-if="creditData" :column="3" border size="small" class="credit-detail">
          <el-descriptions-item label="店铺等级">
            <el-tag type="primary">{{ creditData.data?.module?.shop?.level || '—' }}</el-tag>
            <span class="credit-detail-hint">
              (还差 {{ creditData.data?.module?.shop?.nextLevelNeedScore ?? -1 }} 分升级)
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="信用分">{{ creditData.data?.module?.shop?.score ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="评价数">{{ creditData.data?.module?.shop?.reviewNum ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="在售宝贝">{{ creditData.data?.module?.tabs?.item?.number ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="总评价">{{ creditData.data?.module?.tabs?.rate?.number ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="业务质量">
            <a v-if="creditData.data?.module?.shop?.businessQuality?.targetUrl" :href="creditData.data.module.shop.businessQuality.targetUrl" target="_blank">
              {{ creditData.data.module.shop.businessQuality.name }}
            </a>
            <span v-else>{{ creditData.data?.module?.shop?.businessQuality?.name || '—' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="粉丝数">{{ creditData.data?.module?.social?.followers ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="关注数">{{ creditData.data?.module?.social?.following ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="擦亮上限">{{ creditData.data?.module?.shop?.itemToppingLimit ?? 0 }} 次/天</el-descriptions-item>
        </el-descriptions>

        <!-- 原始 JSON -->
        <el-collapse style="margin-top: 16px">
          <el-collapse-item title="原始响应 JSON">
            <pre class="credit-json">{{ JSON.stringify(creditData, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>

        <el-empty v-if="!creditData" description="暂无信用数据" />
      </el-tab-pane>

      <!-- 退款管理 -->
      <el-tab-pane label="退款管理" name="refunds">
        <div class="toolbar">
          <el-select v-model="accountId" placeholder="选择账号" style="width: 220px" @change="loadRefunds">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
          <el-select v-model="refundStatus" placeholder="退款状态" style="width: 160px; margin-left: 12px" clearable>
            <el-option label="全部" value="" />
            <el-option label="退款中" value="1" />
            <el-option label="退款审核中" value="2" />
            <el-option label="退款处理中" value="3" />
            <el-option label="退款成功" value="5" />
          </el-select>
          <el-button type="primary" @click="loadRefunds" style="margin-left: 12px">拉退款列表</el-button>
        </div>
        <el-table :data="refunds" border style="margin-top: 16px" v-loading="loading">
          <el-table-column label="订单号" width="180">
            <template #default="{ row }">{{ refundOrderId(row) }}</template>
          </el-table-column>
          <el-table-column label="买家" width="120">
            <template #default="{ row }">{{ refundBuyer(row) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100">
            <template #default="{ row }">{{ refundAmount(row) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="refundStatusType(refundStatusValue(row))" size="small">
                {{ refundStatusLabel(refundStatusValue(row)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="退款原因">
            <template #default="{ row }">{{ refundReason(row) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="viewRefundDetail(refundOrderId(row))">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">申请退款</el-divider>
        <el-form :model="refundForm" label-width="80" inline>
          <el-form-item label="订单号">
            <el-input v-model="refundForm.orderId" style="width: 180px" />
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="refundForm.reason" style="width: 220px" />
          </el-form-item>
          <el-form-item label="金额">
            <el-input v-model="refundForm.amount" style="width: 120px" />
          </el-form-item>
          <el-form-item>
            <el-button type="warning" @click="submitRefund">申请退款</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <!-- 退款详情弹窗 -->
    <el-dialog v-model="refundDetailVisible" title="退款详情" width="700" top="8vh">
      <div v-if="refundDetail" class="refund-detail">
        <!-- 头部状态 -->
        <div class="refund-header">
          <el-tag :type="refundStatusTagType" size="large" effect="dark">
            {{ basicInfo.refundStatusDesc || '退款' }}
          </el-tag>
          <div class="refund-header-amount">¥{{ basicInfo.applyMoney || '-' }}</div>
          <div class="refund-header-title">{{ statusInfo.title || '退款详情' }}</div>
        </div>

        <!-- 时间线 -->
        <el-timeline v-if="nodeStatusList.length" class="refund-timeline">
          <el-timeline-item
            v-for="(node, idx) in nodeStatusList"
            :key="idx"
            :type="node.nodeStatus === 'finish' ? 'success' : (node.nodeStatus === 'complete' ? 'primary' : 'info')"
            :hollow="node.nodeStatus !== 'finish' && node.nodeStatus !== 'complete'"
            :timestamp="node.time"
            placement="top">
            <div class="timeline-txt">{{ node.txt }}</div>
          </el-timeline-item>
        </el-timeline>

        <!-- 基本信息 -->
        <el-descriptions v-if="basicInfo.refundId" :column="2" border size="small" class="refund-desc">
          <el-descriptions-item label="退款单号">{{ basicInfo.refundId }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ orderId }}</el-descriptions-item>
          <el-descriptions-item label="退款类型">{{ basicInfo.refundTypeDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退款原因">{{ basicInfo.reasonText || '-' }}</el-descriptions-item>
          <el-descriptions-item label="商品状态">{{ basicInfo.goodsStatusDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="运费承担">{{ basicInfo.postFeeBear || '-' }}</el-descriptions-item>
          <el-descriptions-item label="客服介入">{{ basicInfo.csStatusDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="退款结束时间">{{ basicInfo.disputeEndTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 进度详情 -->
        <div v-if="progressList.length" class="refund-progress">
          <el-divider content-position="left">进度详情</el-divider>
          <div v-for="(p, idx) in progressList" :key="idx" class="progress-item">
            <div class="progress-time">{{ p.timeStr }}</div>
            <div class="progress-text">{{ p.text }}</div>
            <div v-if="p.tips && p.tips.length" class="progress-tips">
              <el-tag v-for="tip in p.tips" :key="tip" size="small" type="info" effect="plain">{{ tip }}</el-tag>
            </div>
          </div>
        </div>

        <!-- 退款规则 -->
        <div v-if="refundDescribe.title" class="refund-describe">
          <el-divider content-position="left">{{ refundDescribe.title }}</el-divider>
          <div v-for="(line, li) in refundDescribe.descRichText" :key="li" class="describe-line">
            <span v-for="(seg, si) in line.data" :key="si">
              <a v-if="seg.linkUrl" :href="seg.linkUrl" target="_blank" style="color: #409eff">{{ seg.content }}</a>
              <span v-else>{{ seg.content }}</span>
            </span>
          </div>
        </div>

        <!-- 原始响应 JSON（不丢数据） -->
        <el-collapse style="margin-top: 16px">
          <el-collapse-item title="原始响应 JSON">
            <pre class="credit-json">{{ JSON.stringify(refundDetail, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>

      <!-- 底部操作栏 -->
      <template #footer>
        <el-button @click="refundDetailVisible = false">关闭</el-button>
        <el-button v-if="bottomBar.length" v-for="btn in bottomBar" :key="btn.code"
          :type="btn.style?.bgColor === 'yellow' ? 'warning' : 'primary'"
          @click="handleBottomAction(btn)">
          {{ btn.name }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Location, View } from '@element-plus/icons-vue'
import { reviewOrder, listReviews, getCredit, applyRefund, listRefunds, getRefundDetail } from '@/api/review'
import { listAccounts } from '@/api/account'

const activeTab = ref('reviews')
const accountId = ref(null)
const accounts = ref([])
const loading = ref(false)

// 评价
const buyerId = ref('')
const reviews = ref([])
const reviewForm = ref({ orderId: '', rating: 'GOOD', content: '' })

// 信用
const creditUserId = ref('')
const creditData = ref(null)

// 退款
const refundStatus = ref('')
const refunds = ref([])
const refundForm = ref({ orderId: '', reason: '', amount: '' })
const refundDetailVisible = ref(false)
const refundDetail = ref(null)

// 退款详情解析后的字段（从 mtop components 中提取）
const refundComponents = computed(() => refundDetail.value?.data?.data?.components || [])

const basicInfo = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'basicRefundInfo')
  return comp?.data || {}
})

const statusInfo = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'refundStatusInfo')
  return comp?.data || {}
})

const nodeStatusList = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'nodeStatusInfo')
  return comp?.data?.nodeStatusList || []
})

const progressList = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'progressDetail')
  return comp?.data?.progressNodeList || []
})

const refundDescribe = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'refundDescribe')
  return comp?.data || {}
})

const bottomBar = computed(() => {
  const comp = refundComponents.value.find(c => c.render === 'bottomBar')
  return Array.isArray(comp) ? comp : (Array.isArray(comp?.data) ? comp.data : [])
})

const orderId = computed(() => refundDetail.value?.data?.data?.orderId || '')

const refundStatusTagType = computed(() => {
  const status = basicInfo.value.refundStatus
  if (status === 'REFUND_SUCCESS') return 'success'
  if (status === 'REFUND_CLOSED' || status === 'REFUND_FAIL') return 'danger'
  return 'warning'
})

// 信用画像卡片（从 data.module.shop / data.module.tabs 提取）
const creditCards = computed(() => {
  if (!creditData.value) return []
  const shop = creditData.value?.data?.module?.shop || {}
  const tabs = creditData.value?.data?.module?.tabs || {}
  const base = creditData.value?.data?.module?.base || {}
  return [
    { label: '信用分', value: shop.score ?? '-' },
    { label: '评价数', value: shop.reviewNum ?? tabs?.rate?.number ?? '-' },
    { label: '店铺等级', value: shop.level ?? '-' },
    { label: '在售宝贝', value: tabs?.item?.number ?? '-' },
    { label: '业务质量', value: shop.businessQuality?.name ?? '-' },
  ].filter(c => c.value !== '-' && c.value != null)
})

onMounted(async () => {
  try {
    const res = await listAccounts()
    accounts.value = res.data || []
  } catch (e) {
    ElMessage.error('拉账号列表失败')
  }
})

async function loadReviews() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  loading.value = true
  try {
    const res = await listReviews(accountId.value, buyerId.value)
    const d = res.data
    reviews.value = extractReviewItems(d)
    if (!reviews.value.length && isMtopFailed(d)) {
      ElMessage.warning(mtopErrorMessage(d))
    }
  } catch (e) {
    ElMessage.error('拉评价失败: ' + (e?.response?.data?.message || e.message))
    reviews.value = []
  } finally { loading.value = false }
}

function extractReviewItems(payload) {
  if (Array.isArray(payload)) return payload
  const candidates = [
    payload?.data?.data?.cardList,
    payload?.data?.data?.items,
    payload?.data?.data?.list,
    payload?.data?.data?.rateList,
    payload?.data?.data?.rateInfos,
    payload?.data?.cardList,
    payload?.data?.items,
    payload?.data?.list,
    payload?.data?.rateList,
    payload?.data?.rateInfos,
    payload?.cardList,
    payload?.items,
    payload?.list,
    payload?.rateList,
    payload?.rateInfos,
  ]
  return candidates.find(Array.isArray) || []
}

function isMtopFailed(payload) {
  const ret = payload?.ret || payload?.data?.ret
  return Array.isArray(ret) && ret.some(item => String(item).startsWith('FAIL_'))
}

function mtopErrorMessage(payload) {
  const ret = payload?.ret || payload?.data?.ret || []
  return '闲鱼返回失败: ' + (ret[0] || '未知错误')
}

function reviewData(row) {
  return row?.cardData || row || {}
}

function reviewOrderId(row) {
  const data = reviewData(row)
  return data?.orderId || data?.tradeId || data?.bizOrderId || data?.orderNo || data?.trade?.id || data?.trade?.orderId || '-'
}

function reviewRate(row) {
  const data = reviewData(row)
  return data?.rate ?? data?.rateType ?? data?.rating ?? data?.score ?? data?.star
}

function reviewRateLabel(row) {
  const rate = reviewRate(row)
  const text = String(rate ?? '')
  if (rate === 1 || text === '1' || text.toUpperCase() === 'GOOD') return '好评'
  if (rate === 2 || text === '2' || text.toUpperCase() === 'NORMAL') return '中评'
  if (rate === 3 || text === '3' || text.toUpperCase() === 'BAD') return '差评'
  return rate || '-'
}

function reviewRateType(row) {
  const label = reviewRateLabel(row)
  if (label === '好评') return 'success'
  if (label === '差评') return 'danger'
  return 'info'
}

function reviewFeedback(row) {
  const data = reviewData(row)
  return data?.feedback || data?.content || data?.comment || data?.rateContent || data?.text || '-'
}

function reviewRater(row) {
  const data = reviewData(row)
  return data?.raterNick || data?.raterUserNick || data?.raterNickname || data?.raterName || data?.userNick || data?.nick || data?.user?.nick || '-'
}

function reviewSeller(row) {
  const data = reviewData(row)
  return data?.sellerName || data?.sellerNick || data?.seller?.nick || '-'
}

function reviewBuyer(row) {
  const data = reviewData(row)
  return data?.buyerName || data?.buyerNick || data?.buyer?.nick || '-'
}

function reviewItemTitle(row) {
  const data = reviewData(row)
  return data?.itemTitle || data?.title || data?.item?.title || '-'
}

function reviewCreateTime(row) {
  const data = reviewData(row)
  return data?.createTime || data?.gmtCreate || data?.gmtCreateStr || data?.rateTime || data?.timeDesc || data?.time || '-'
}

async function submitReview() {
  if (!accountId.value || !reviewForm.value.orderId) return ElMessage.warning('请填账号和订单号')
  try {
    await reviewOrder(accountId.value, reviewForm.value.orderId, reviewForm.value.rating, reviewForm.value.content)
    ElMessage.success('评价已提交')
    reviewForm.value.content = ''
  } catch (e) {
    ElMessage.error('评价失败: ' + e.message)
  }
}

async function loadCredit() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  try {
    const res = await getCredit(accountId.value, creditUserId.value)
    creditData.value = res.data
  } catch (e) {
    ElMessage.error('拉信用失败: ' + e.message)
    creditData.value = null
  }
}

async function loadRefunds() {
  if (!accountId.value) return ElMessage.warning('请选账号')
  loading.value = true
  try {
    const res = await listRefunds(accountId.value, refundStatus.value)
    const d = res.data
    refunds.value = extractRefundItems(d)
    if (!refunds.value.length && isMtopFailed(d)) {
      ElMessage.warning(mtopErrorMessage(d))
    }
  } catch (e) {
    ElMessage.error('拉退款列表失败: ' + (e?.response?.data?.message || e.message))
    refunds.value = []
  } finally { loading.value = false }
}

function extractRefundItems(payload) {
  if (Array.isArray(payload)) return payload
  const candidates = [
    payload?.data?.data?.items,
    payload?.data?.data?.list,
    payload?.data?.items,
    payload?.data?.list,
    payload?.items,
    payload?.list,
  ]
  return candidates.find(Array.isArray) || []
}

function refundData(row) {
  return row?.data || row?.refundInfo || row || {}
}

function refundOrderId(row) {
  const data = refundData(row)
  return data?.orderId || data?.bizOrderId || data?.tradeId || data?.commonData?.orderId || data?.commonData?.orderIdStr || '-'
}

function refundId(row) {
  const data = refundData(row)
  return data?.refundId || data?.disputeId || data?.refundApplyId || refundOrderId(row)
}

function refundBuyer(row) {
  const data = refundData(row)
  return data?.buyerInfoVO?.userNick || data?.buyerNick || data?.buyerName || data?.buyer?.nick || data?.counterpartyName || '-'
}

function refundAmount(row) {
  const data = refundData(row)
  return data?.priceVO?.auctionPrice || data?.refundFee || data?.refundAmount || data?.amount || data?.price || '-'
}

function refundStatusValue(row) {
  const data = refundData(row)
  return data?.disputeStatus || data?.refundStatus || data?.status || data?.commonData?.disputeStatus
}

function refundReason(row) {
  const data = refundData(row)
  return data?.reason || data?.refundReason || data?.desc || data?.title || '-'
}

async function submitRefund() {
  if (!accountId.value || !refundForm.value.orderId) return ElMessage.warning('请填账号和订单号')
  try {
    await applyRefund(accountId.value, refundForm.value.orderId, refundForm.value.reason, refundForm.value.amount)
    ElMessage.success('退款已申请')
    refundForm.value = { orderId: '', reason: '', amount: '' }
    loadRefunds()
  } catch (e) {
    ElMessage.error('申请退款失败: ' + e.message)
  }
}

async function viewRefundDetail(orderId) {
  if (!orderId || orderId === '-') return ElMessage.warning('无订单号，无法拉退款详情')
  try {
    const res = await getRefundDetail(accountId.value, orderId)
    refundDetail.value = res.data
    refundDetailVisible.value = true
  } catch (e) {
    ElMessage.error('拉退款详情失败: ' + (e?.response?.data?.message || e.message))
  }
}

// 退款状态标签
function refundStatusLabel(status) {
  const map = { '1': '退款中', '2': '退款审核中', '3': '退款处理中', '5': '退款成功' }
  return map[String(status)] || status || '-'
}
function refundStatusType(status) {
  const map = { '1': 'warning', '2': 'warning', '3': 'warning', '5': 'success' }
  return map[String(status)] || 'info'
}
</script>

<style scoped>
.review-page { padding: 16px; }
.toolbar { display: flex; align-items: center; }
.credit-header { display: flex; align-items: center; gap: 16px; margin-top: 16px; padding: 16px; background: #f5f7fa; border-radius: 8px; }
.credit-header-info { flex: 1; }
.credit-header-name { font-size: 18px; font-weight: 700; color: #303133; }
.credit-header-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; font-size: 13px; color: #909399; }
.credit-header-intro { color: #606266; }
.credit-tags { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.credit-tag-icon { width: 16px; height: 16px; vertical-align: middle; margin-right: 4px; }
.credit-cards { display: flex; gap: 12px; margin-top: 16px; flex-wrap: wrap; }
.credit-card { min-width: 140px; text-align: center; }
.credit-card-value { font-size: 24px; font-weight: 700; color: #409eff; }
.credit-card-label { font-size: 12px; color: #909399; margin-top: 4px; }
.credit-detail { margin-top: 16px; }
.credit-detail-hint { font-size: 12px; color: #909399; margin-left: 4px; }
.credit-json { background: #f5f7fa; padding: 16px; border-radius: 4px; max-height: 400px; overflow: auto; font-size: 12px; line-height: 1.5; }
.refund-detail { max-height: 70vh; overflow-y: auto; padding-right: 4px; }
.refund-header { text-align: center; margin-bottom: 20px; padding: 16px; background: #f5f7fa; border-radius: 8px; }
.refund-header-amount { font-size: 28px; font-weight: 700; color: #f56c6c; margin-top: 8px; }
.refund-header-title { font-size: 14px; color: #606266; margin-top: 4px; }
.refund-timeline { margin: 16px 0; }
.timeline-txt { font-size: 14px; color: #303133; }
.refund-desc { margin: 16px 0; }
.refund-progress { margin: 16px 0; }
.progress-item { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px dashed #ebeef5; }
.progress-item:last-child { border-bottom: none; }
.progress-time { width: 140px; font-size: 13px; color: #909399; flex-shrink: 0; }
.progress-text { flex: 1; font-size: 14px; color: #303133; }
.progress-tips { display: flex; gap: 6px; flex-wrap: wrap; }
.refund-describe { margin: 16px 0; }
.describe-line { font-size: 13px; color: #606266; line-height: 1.8; }
</style>
