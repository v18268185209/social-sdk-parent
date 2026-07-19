<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>订单管理</span>
          <div style="display: flex; gap: 12px; align-items: center;">
            <el-select
              v-model="selectedAccountId"
              placeholder="选择账号"
              style="width: 200px"
              :loading="accountsLoading"
              clearable
            >
              <el-option
                v-for="acc in accounts"
                :key="acc.id"
                :label="acc.displayName || acc.accountName"
                :value="acc.id"
              />
            </el-select>
            <el-button
              type="primary"
              :loading="syncing"
              :disabled="!selectedAccountId"
              @click="syncOrders"
            >
              <el-icon><Refresh /></el-icon> 同步订单
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="loadOrders">
        <el-tab-pane label="我卖出的" name="SOLD" />
        <el-tab-pane label="我买到的" name="BOUGHT" />
        <el-tab-pane label="全部" name="ALL" />
      </el-tabs>

      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单号" width="180" />
        <el-table-column prop="itemTitle" label="商品标题" min-width="200" show-overflow-tooltip />
        <el-table-column :label="activeTab === 'SOLD' ? '买家' : '卖家'" width="120">
          <template #default="{ row }">
            <span>{{ row.counterpartyName || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">
              {{ statusLabel(row.status, row.tradeStatusEnum) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="180">
          <template #default="{ row }">
            <span>{{ formatTime(row.orderTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="物流单号" width="150" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PAID' && activeTab === 'SOLD'"
              size="small"
              type="success"
              @click="deliver(row)"
            >
              发货
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadOrders"
          @current-change="loadOrders"
        />
      </div>
    </el-card>

    <!-- 发货对话框 -->
    <el-dialog v-model="showDeliverDialog" title="发货" width="400px">
      <el-form :model="deliverForm" label-width="100px">
        <el-form-item label="物流单号">
          <el-input v-model="deliverForm.trackingNo" placeholder="输入快递单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeliverDialog = false">取消</el-button>
        <el-button type="primary" @click="handleDelivery">确认发货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'

// ===== 账号选择 =====
const accounts = ref([])
const accountsLoading = ref(false)
const selectedAccountId = ref(null)

// ===== 订单列表 =====
const orders = ref([])
const loading = ref(false)
const activeTab = ref('SOLD')
const page = ref(1)
const size = ref(20)
const total = ref(0)

// ===== 同步 =====
const syncing = ref(false)

// ===== 发货 =====
const showDeliverDialog = ref(false)
const deliverForm = ref({ orderId: null, trackingNo: '' })

// 时间格式化
function formatTime(t) {
  if (!t) return '—'
  return t.replace('T', ' ').substring(0, 19)
}

// 状态标签
function statusLabel(status, tradeStatusEnum) {
  // 优先使用贸易枚举（更准确）
  if (tradeStatusEnum) {
    const enumMap = {
      'trade_success': '交易成功',
      'buyer_to_confirm': '买家待确认',
      'refund_success': '退款成功',
      'trade_refund': '退款中',
      'trade_in_audit': '退款审核中',
      'refund_agree': '同意退款',
      'refund_process': '退款处理中',
      'trade_closed': '交易关闭',
      'trade_cancelled': '已取消',
      'cancel': '已取消',
      'pending_pay': '待付款',
      'waiting_pay': '待付款',
      'trade_pending': '待付款',
      'trade_delivered': '已发货',
      'sent': '已发货',
      'paid': '已付款',
      'trade_paid': '已付款',
      'trade_suspended': '暂停'
    }
    return enumMap[tradeStatusEnum] || tradeStatusEnum
  }
  
  // 回退到标准化状态码
  return { 
    PENDING: '待付款', 
    PAID: '已付款', 
    SHIPPED: '已发货', 
    COMPLETED: '交易成功', 
    REFUNDING: '退款中', 
    REFUNDED: '退款成功', 
    CLOSED: '已关闭',
    BUYER_TO_CONFIRM: '买家待确认'
  }[status] || status
}

function statusTagType(status) {
  return { 
    PENDING: 'warning', 
    PAID: 'primary', 
    SHIPPED: 'info', 
    COMPLETED: 'success', 
    REFUNDING: 'danger', 
    REFUNDED: 'info', 
    CLOSED: '',
    BUYER_TO_CONFIRM: 'warning'
  }[status] || ''
}

// 加载账号列表
async function loadAccounts() {
  accountsLoading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      // /api/accounts 返回的是数组，不是分页对象
      const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      accounts.value = list
      // 默认选中第一个
      if (accounts.value.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = accounts.value[0].id
      }
    }
  } catch (e) {}
  finally { accountsLoading.value = false }
}

// 加载订单列表
async function loadOrders() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (selectedAccountId.value) {
      params.accountId = selectedAccountId.value
    }
    if (activeTab.value !== 'ALL') {
      params.type = activeTab.value
    }
    const res = await api.get('/orders', { params })
    if (res.success) {
      orders.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

// 同步订单
async function syncOrders() {
  if (!selectedAccountId.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  syncing.value = true
  try {
    const res = await api.post(`/orders/accounts/${selectedAccountId.value}/sync`)
    if (res.success) {
      ElMessage.success(`同步完成：买到 ${res.data.boughtCount} 条，卖出 ${res.data.soldCount} 条`)
      await loadOrders()
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    ElMessage.error('同步请求失败')
  }
  finally { syncing.value = false }
}

// 发货
function deliver(row) {
  deliverForm.value = { orderId: row.id, trackingNo: '' }
  showDeliverDialog.value = true
}

async function handleDelivery() {
  if (!deliverForm.value.trackingNo) {
    ElMessage.warning('请输入物流单号')
    return
  }
  try {
    const res = await api.post(`/orders/${deliverForm.value.orderId}/delivery?trackingNo=${deliverForm.value.trackingNo}`)
    if (res.success) {
      ElMessage.success('发货成功')
      showDeliverDialog.value = false
      await loadOrders()
    }
  } catch (e) {}
}

onMounted(async () => {
  await loadAccounts()
  await loadOrders()
})
</script>
