<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span>钱包资产</span>
          <el-button
            type="primary"
            size="small"
            :loading="syncing"
            :disabled="!selectedAccountId"
            @click="syncWallet">同步钱包</el-button>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="账号">
          <el-select v-model="selectedAccountId" @change="loadWallet" placeholder="选择账号" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="syncMsg"
        :title="syncMsg"
        :type="syncOk ? 'success' : 'warning'"
        :closable="false"
        style="margin-bottom: 12px;"
        show-icon />
      <el-descriptions v-if="wallet" :column="2" border style="margin-top: 16px;">
        <el-descriptions-item label="余额">¥{{ wallet.balance }}</el-descriptions-item>
        <el-descriptions-item label="冻结金额">¥{{ wallet.frozenAmount }}</el-descriptions-item>
        <el-descriptions-item label="可用余额">¥{{ wallet.availableBalance != null ? wallet.availableBalance : '-' }}</el-descriptions-item>
        <el-descriptions-item label="总资产">¥{{ wallet.totalAssets != null ? wallet.totalAssets : '-' }}</el-descriptions-item>
        <el-descriptions-item label="可提现">¥{{ wallet.withdrawableAmount != null ? wallet.withdrawableAmount : '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付宝">{{ wallet.alipayAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付宝实名">{{ wallet.alipayRealName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="银行卡">{{ wallet.bankCard || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请选择账号查看钱包信息" />
    </el-card>

    <el-card style="margin-top: 16px;">
      <template #header><span>交易记录</span></template>
      <el-table :data="transactions" stripe>
        <el-table-column prop="transactionId" label="交易ID" width="200" />
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="{ INCOME: 'success', EXPENSE: 'danger', TRANSFER: 'info' }[row.type]">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">¥{{ row.amount }}</template>
        </el-table-column>
        <el-table-column label="余额" width="100">
          <template #default="{ row }">¥{{ row.balanceAfter }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务类型" width="120" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="transactionTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'
import { ElMessage } from 'element-plus'

const accounts = ref([])
const wallet = ref(null)
const transactions = ref([])
const selectedAccountId = ref(null)
const syncing = ref(false)
const syncMsg = ref('')
const syncOk = ref(true)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) {}
}

async function loadWallet() {
  syncMsg.value = ''
  if (!selectedAccountId.value) {
    wallet.value = null
    transactions.value = []
    return
  }
  try {
    const r1 = await api.get(`/wallet/${selectedAccountId.value}`)
    if (r1.success) wallet.value = r1.data
  } catch (e) { wallet.value = null }
  try {
    const r2 = await api.get(`/wallet/${selectedAccountId.value}/recent?limit=20`)
    if (r2.success) transactions.value = r2.data
  } catch (e) { transactions.value = [] }
}

async function syncWallet() {
  if (!selectedAccountId.value) return
  syncing.value = true
  syncMsg.value = ''
  try {
    const res = await api.post(`/wallet/${selectedAccountId.value}/sync`)
    if (res.success) {
      syncOk.value = true
      const d = res.data || {}
      syncMsg.value = `同步完成：余额已更新，账单 ${d.billCount || 0} 条`
      ElMessage.success('钱包同步成功')
      await loadWallet()
    } else {
      syncOk.value = false
      syncMsg.value = '同步未返回有效数据：' + (res.message || '未知错误')
      ElMessage.warning('钱包同步未完成，请确认接口名（详见后端日志 /api/wallet/api-names）')
    }
  } catch (e) {
    syncOk.value = false
    syncMsg.value = '同步请求失败，请检查后端日志'
  } finally {
    syncing.value = false
  }
}

onMounted(() => { loadAccounts() })
</script>
