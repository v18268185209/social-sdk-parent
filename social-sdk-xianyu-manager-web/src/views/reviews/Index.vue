<template>
  <div class="review-page">
    <el-tabs v-model="activeTab">
      <!-- 评价管理 -->
      <el-tab-pane label="评价管理" name="reviews">
        <div class="toolbar">
          <el-select v-model="accountId" placeholder="选择账号" style="width: 220px" @change="loadReviews">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
          <el-input v-model="buyerId" placeholder="买家 ID（留空拉全部）" style="width: 220px; margin-left: 12px" clearable />
          <el-button type="primary" @click="loadReviews" style="margin-left: 12px">拉评价列表</el-button>
        </div>
        <el-table :data="reviews" border style="margin-top: 16px" v-loading="loading">
          <el-table-column prop="orderId" label="订单号" width="180" />
          <el-table-column prop="rating" label="评分" width="80" />
          <el-table-column prop="content" label="评价内容" />
          <el-table-column prop="buyerNick" label="买家" width="120" />
          <el-table-column prop="createdAt" label="评价时间" width="180" />
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
            <el-input v-model="reviewForm.content" style="width: 320px" />
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
        <pre v-if="creditData" style="margin-top: 16px; background: #f5f7fa; padding: 16px; border-radius: 4px">{{ JSON.stringify(creditData, null, 2) }}</pre>
        <el-empty v-else description="暂无信用数据" />
      </el-tab-pane>

      <!-- 退款管理 -->
      <el-tab-pane label="退款管理" name="refunds">
        <div class="toolbar">
          <el-select v-model="accountId" placeholder="选择账号" style="width: 220px" @change="loadRefunds">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
          </el-select>
          <el-input v-model="refundStatus" placeholder="退款状态" style="width: 160px; margin-left: 12px" clearable />
          <el-button type="primary" @click="loadRefunds" style="margin-left: 12px">拉退款列表</el-button>
        </div>
        <el-table :data="refunds" border style="margin-top: 16px" v-loading="loading">
          <el-table-column prop="refundId" label="退款单号" width="180" />
          <el-table-column prop="orderId" label="订单号" width="180" />
          <el-table-column prop="reason" label="退款原因" />
          <el-table-column prop="amount" label="金额" width="100" />
          <el-table-column prop="disputeStatus" label="状态" width="120" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button size="small" @click="viewRefundDetail(row.refundId)">详情</el-button>
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

    <el-dialog v-model="refundDetailVisible" title="退款详情" width="600">
      <pre>{{ JSON.stringify(refundDetail, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
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
    // 后台返回 JsonNode，可能是数组或 {data:{items:[...]}}
    const d = res.data
    reviews.value = Array.isArray(d) ? d : (d?.data?.items || d?.items || [])
  } catch (e) {
    ElMessage.error('拉评价失败: ' + e.message)
    reviews.value = []
  } finally { loading.value = false }
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
    refunds.value = Array.isArray(d) ? d : (d?.data?.items || d?.items || [])
  } catch (e) {
    ElMessage.error('拉退款列表失败: ' + e.message)
    refunds.value = []
  } finally { loading.value = false }
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

async function viewRefundDetail(refundId) {
  try {
    const res = await getRefundDetail(accountId.value, refundId)
    refundDetail.value = res.data
    refundDetailVisible.value = true
  } catch (e) {
    ElMessage.error('拉退款详情失败: ' + e.message)
  }
}
</script>

<style scoped>
.review-page { padding: 16px; }
.toolbar { display: flex; align-items: center; }
</style>
