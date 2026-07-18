<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>商品管理</span>
          <div style="display: flex; gap: 12px; align-items: center;">
            <el-select v-model="selectedAccountId" placeholder="选择账号" style="width: 200px;" :loading="accountsLoading" clearable>
              <el-option v-for="a in accounts" :key="a.id" :label="a.displayName || a.accountName" :value="a.id" />
            </el-select>
            <el-button type="primary" :loading="syncing" :disabled="!selectedAccountId" @click="syncProducts">
              <el-icon><Refresh /></el-icon> 同步商品
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="loadProducts">
        <el-tab-pane label="在售" name="ON_SALE" />
        <el-tab-pane label="全部" name="ALL" />
      </el-tabs>

      <el-table :data="products" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="图片" width="80">
          <template #default="{ row }">
            <el-image :src="row.imageUrl" style="width: 50px; height: 50px;" fit="cover" />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="商品标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ row.amount || row.price || '0.00' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="{ ON_SALE: 'success', OFF_SALE: 'info' }[row.status] || 'info'">
              {{ { ON_SALE: '在售', OFF_SALE: '下架' }[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="editProduct(row)">编辑</el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 'ON_SALE'" @click="offShelf(row)">下架</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
        <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" layout="total, prev, pager, next" @current-change="loadProducts" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'

const accounts = ref([])
const accountsLoading = ref(false)
const selectedAccountId = ref(null)
const products = ref([])
const loading = ref(false)
const activeTab = ref('ON_SALE')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const syncing = ref(false)

async function loadAccounts() {
  accountsLoading.value = true
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      const list = Array.isArray(res.data) ? res.data : (res.data?.records || [])
      accounts.value = list
      if (list.length > 0 && !selectedAccountId.value) selectedAccountId.value = list[0].id
    }
  } catch (e) {}
  finally { accountsLoading.value = false }
}

async function loadProducts() {
  loading.value = true
  try {
    const params = { page: page.value, size: size.value }
    if (selectedAccountId.value) params.accountId = selectedAccountId.value
    if (activeTab.value !== 'ALL') params.status = activeTab.value
    const res = await api.get('/products', { params })
    if (res.success) {
      products.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

async function syncProducts() {
  if (!selectedAccountId.value) return
  syncing.value = true
  try {
    const res = await api.post(`/products/accounts/${selectedAccountId.value}/sync`)
    if (res.success) {
      ElMessage.success(`同步完成，共 ${res.data.count} 件商品`)
      loadProducts()
    } else { ElMessage.error(res.message || '同步失败') }
  } catch (e) {}
  finally { syncing.value = false }
}

function editProduct(row) { ElMessage.info('编辑功能待 UI 完善') }
async function offShelf(row) {
  await ElMessage.confirm('确认下架？')
  // TODO: 调用下架 API
}

onMounted(async () => { await loadAccounts(); await loadProducts() })
</script>
