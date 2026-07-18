<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>商品管理</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 创建商品
          </el-button>
        </div>
      </template>

      <el-form inline style="margin-bottom: 16px;">
        <el-form-item label="账号">
          <el-select v-model="filters.accountId" placeholder="全部" clearable style="width: 150px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索标题" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px;">
            <el-option label="在售" value="ON_SALE" />
            <el-option label="下架" value="OFF_SALE" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadProducts">搜索</el-button>
        </el-form-item>
        <el-form-item>
          <el-button
            type="success"
            :disabled="!filters.accountId"
            :loading="syncing"
            @click="handleSyncFromXianyu"
          >
            <el-icon><Refresh /></el-icon> 同步闲鱼商品
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="products" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="价格" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="浏览" width="70" />
        <el-table-column prop="favoriteCount" label="收藏" width="70" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="editPrice(row)">改价</el-button>
            <el-button size="small" @click="editStock(row)">改库存</el-button>
            <el-button v-if="row.status !== 'ON_SALE'" size="small" type="success" @click="shelfOn(row)">上架</el-button>
            <el-button v-else size="small" type="warning" @click="shelfOff(row)">下架</el-button>
            <el-button size="small" type="danger" @click="deleteProduct(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: center;"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="loadProducts"
      />
    </el-card>

    <!-- 创建商品对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建商品" width="500px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="账号">
          <el-select v-model="createForm.accountId" placeholder="选择账号" style="width: 100%;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="createForm.price" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="createForm.originalPrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="createForm.stock" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'

const products = ref([])
const accounts = ref([])
const loading = ref(false)
const submitting = ref(false)
const syncing = ref(false)
const showCreateDialog = ref(false)
const filters = ref({ accountId: null, keyword: '', status: '' })
const pagination = ref({ page: 1, size: 20, total: 0 })
const createForm = ref({ accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '' })

const statusType = (s) => ({ ON_SALE: 'success', OFF_SALE: 'warning', DRAFT: 'info' }[s] || 'info')

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* ignore */ }
}

async function loadProducts() {
  loading.value = true
  try {
    const params = { page: pagination.value.page, size: pagination.value.size }
    if (filters.value.accountId) params.accountId = filters.value.accountId
    if (filters.value.keyword) params.keyword = filters.value.keyword
    if (filters.value.status) params.status = filters.value.status
    const res = await api.get('/products', { params })
    if (res.success) {
      products.value = res.data.records || []
      pagination.value.total = res.data.total || 0
    }
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

async function handleSyncFromXianyu() {
  if (!filters.value.accountId) {
    ElMessage.warning('请先选择账号')
    return
  }
  syncing.value = true
  try {
    const res = await api.post('/products/sync', null, { params: { accountId: filters.value.accountId } })
    if (res.success) {
      const r = res.data || {}
      ElMessage.success(`同步完成：新增 ${r.inserted || 0}，更新 ${r.updated || 0}，合计 ${r.synced || 0}`)
      await loadProducts()
    } else if (res.code === 'COOKIE_EXPIRED') {
      ElMessage.error('Cookie 已过期，请重新扫码登录该账号')
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    ElMessage.error('同步请求失败')
  } finally { syncing.value = false }
}

async function handleCreate() {
  if (!createForm.value.accountId || !createForm.value.title) {
    ElMessage.warning('请选择账号并填写标题')
    return
  }
  submitting.value = true
  try {
    const res = await api.post('/products', createForm.value)
    if (res.success) {
      ElMessage.success('商品创建成功')
      showCreateDialog.value = false
      createForm.value = { accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '' }
      await loadProducts()
    }
  } catch (e) { /* ignore */ }
  finally { submitting.value = false }
}

async function shelfOn(row) {
  try {
    const res = await api.post(`/products/${row.id}/shelf-on`)
    if (res.success) {
      ElMessage.success('已上架')
      await loadProducts()
    }
  } catch (e) {}
}

async function shelfOff(row) {
  try {
    const res = await api.post(`/products/${row.id}/shelf-off`)
    if (res.success) {
      ElMessage.success('已下架')
      await loadProducts()
    }
  } catch (e) {}
}

function editPrice(row) {
  ElMessageBox.prompt('输入新价格', '修改价格', { inputValue: String(row.price) }).then(async ({ value }) => {
    try {
      const res = await api.put(`/products/${row.id}/price?price=${value}`)
      if (res.success) {
        ElMessage.success('价格已更新')
        await loadProducts()
      }
    } catch (e) {}
  }).catch(() => {})
}

function editStock(row) {
  ElMessageBox.prompt('输入新库存', '修改库存', { inputValue: String(row.stock) }).then(async ({ value }) => {
    try {
      const res = await api.put(`/products/${row.id}/stock?stock=${parseInt(value)}`)
      if (res.success) {
        ElMessage.success('库存已更新')
        await loadProducts()
      }
    } catch (e) {}
  }).catch(() => {})
}

async function deleteProduct(id) {
  await ElMessageBox.confirm('确认删除该商品？', '提示', { type: 'warning' })
  try {
    const res = await api.delete(`/products/${id}`)
    if (res.success) {
      ElMessage.success('已删除')
      await loadProducts()
    }
  } catch (e) {}
}

onMounted(() => { loadAccounts(); loadProducts() })
</script>
