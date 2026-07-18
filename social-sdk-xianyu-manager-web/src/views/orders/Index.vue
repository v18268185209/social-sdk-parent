<template>
  <div>
    <el-card>
      <template #header>
        <span>订单管理</span>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="我卖出的" name="sold" />
        <el-tab-pane label="我买到的" name="bought" />
      </el-tabs>
      <el-table :data="orders" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="orderId" label="订单号" width="150" />
        <el-table-column prop="itemTitle" label="商品标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="buyerName" label="买家" width="100" />
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="{ PENDING: 'warning', PAID: 'primary', SHIPPED: 'success', COMPLETED: 'info' }[row.status]">
              {{ { PENDING: '待处理', PAID: '已付款', SHIPPED: '已发货', COMPLETED: '已完成', REFUNDING: '退款中' }[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="trackingNo" label="物流单号" width="150" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button v-if="row.status === 'PAID'" size="small" type="success" @click="deliver(row)">发货</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

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
import api from '@/api/request'

const orders = ref([])
const loading = ref(false)
const activeTab = ref('sold')
const showDeliverDialog = ref(false)
const deliverForm = ref({ orderId: null, trackingNo: '' })

async function loadOrders() {
  loading.value = true
  try {
    const res = await api.get('/orders', { params: { page: 1, size: 50, tab: activeTab.value } })
    if (res.success) orders.value = res.data.records || []
  } catch (e) {}
  finally { loading.value = false }
}

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

onMounted(loadOrders)
</script>
