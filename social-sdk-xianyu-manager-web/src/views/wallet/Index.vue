<template>
  <div>
    <el-card>
      <template #header><span>钱包资产</span></template>
      <el-form inline>
        <el-form-item label="账号">
          <el-select v-model="selectedAccountId" @change="loadWallet" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="wallet" :column="2" border style="margin-top: 16px;">
        <el-descriptions-item label="余额">¥{{ wallet.balance }}</el-descriptions-item>
        <el-descriptions-item label="冻结金额">¥{{ wallet.frozenAmount }}</el-descriptions-item>
        <el-descriptions-item label="支付宝">{{ wallet.alipayAccount || '-' }}</el-descriptions-item>
        <el-descriptions-item label="银行卡">{{ wallet.bankCard || '-' }}</el-descriptions-item>
      </el-descriptions>
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
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="transactionTime" label="时间" width="180" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import api from '@/api/request'

const accounts = ref([])
const wallet = ref(null)
const transactions = ref([])
const selectedAccountId = ref(null)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) {}
}

async function loadWallet() {
  if (!selectedAccountId.value) return
  try {
    const r1 = await api.get(`/wallet/${selectedAccountId.value}`)
    if (r1.success) wallet.value = r1.data
  } catch (e) {}
  try {
    const r2 = await api.get(`/wallet/${selectedAccountId.value}/recent?limit=20`)
    if (r2.success) transactions.value = r2.data
  } catch (e) {}
}

onMounted(() => { loadAccounts() })
</script>
