<template>
  <div class="polish-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="单擦" name="single">
        <el-form :model="singleForm" label-width="100" inline>
          <el-form-item label="账号">
            <el-select v-model="singleForm.accountId" placeholder="选择账号" style="width: 220px"
              @change="onAccountChange">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品">
            <el-select v-model="singleForm.itemId" placeholder="选择商品" filterable style="width: 320px"
              :loading="productLoading" :disabled="!singleForm.accountId">
              <el-option v-for="p in productOptions" :key="p.itemId"
                :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doPolish" :loading="loading">擦亮</el-button>
          </el-form-item>
        </el-form>
        <pre v-if="singleResult">{{ JSON.stringify(singleResult, null, 2) }}</pre>
      </el-tab-pane>

      <el-tab-pane label="批量擦" name="batch">
        <el-form :model="batchForm" label-width="100">
          <el-form-item label="账号">
            <el-select v-model="batchForm.accountId" placeholder="选择账号" style="width: 220px"
              @change="onAccountChange">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品">
            <el-select v-model="batchForm.itemIds" placeholder="选择商品（可多选）" multiple filterable
              collapse-tags collapse-tags-tooltip style="width: 480px" :loading="productLoading"
              :disabled="!batchForm.accountId">
              <el-option v-for="p in productOptions" :key="p.itemId"
                :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doBatchPolish" :loading="loading">批量擦亮</el-button>
          </el-form-item>
        </el-form>
        <pre v-if="batchResult">{{ JSON.stringify(batchResult, null, 2) }}</pre>
      </el-tab-pane>

      <el-tab-pane label="超级擦亮" name="super">
        <el-alert type="warning" :closable="false" show-icon
          title="超级擦亮 = 同一商品连续多次 polish，间隔 60s 防风控，顶到搜索前列。耗时较长，请耐心等待。" />
        <el-form :model="superForm" label-width="100" inline style="margin-top: 16px">
          <el-form-item label="账号">
            <el-select v-model="superForm.accountId" placeholder="选择账号" style="width: 220px"
              @change="onAccountChange">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品">
            <el-select v-model="superForm.itemId" placeholder="选择商品" filterable style="width: 320px"
              :loading="productLoading" :disabled="!superForm.accountId">
              <el-option v-for="p in productOptions" :key="p.itemId"
                :label="`${p.title} · ${p.itemId}`" :value="p.itemId" />
            </el-select>
          </el-form-item>
          <el-form-item label="次数">
            <el-input-number v-model="superForm.times" :min="1" :max="10" style="width: 120px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doSuperPolish" :loading="loading">超级擦亮</el-button>
          </el-form-item>
        </el-form>
        <pre v-if="superResult">{{ JSON.stringify(superResult, null, 2) }}</pre>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/api/request'
import { polishItem, batchPolish, superPolish } from '@/api/polish'
import { listAccounts } from '@/api/account'

const activeTab = ref('single')
const accounts = ref([])
const loading = ref(false)
const productLoading = ref(false)
// 已同步且在售的商品（含 itemId，可直接提交后台）
const productOptions = ref([])

const singleForm = ref({ accountId: null, itemId: '' })
const singleResult = ref(null)
const batchForm = ref({ accountId: null, itemIds: [] })
const batchResult = ref(null)
const superForm = ref({ accountId: null, itemId: '', times: 3 })
const superResult = ref(null)

onMounted(async () => {
  try {
    const res = await listAccounts()
    accounts.value = res.data || []
  } catch (e) {
    ElMessage.error('拉账号列表失败')
  }
})

// 选账号后拉取该账号「在售且已同步」的商品，过滤掉没有 itemId 的（未同步）
async function onAccountChange(accountId) {
  // 切换账号时清空已选商品，避免串号
  singleForm.value.itemId = ''
  batchForm.value.itemIds = []
  superForm.value.itemId = ''
  productOptions.value = []
  if (!accountId) return
  productLoading.value = true
  try {
    const res = await request.get('/products', {
      params: { accountId, status: 'ON_SALE', page: 1, size: 200 }
    })
    const list = (res.data?.records || []).filter(p => p.itemId)
    productOptions.value = list
    if (list.length === 0) {
      ElMessage.warning('该账号暂无已同步的在售商品，请先在「商品管理」同步闲鱼')
    }
  } catch (e) {
    ElMessage.error('拉取商品列表失败')
  } finally {
    productLoading.value = false
  }
}

async function doPolish() {
  if (!singleForm.value.accountId || !singleForm.value.itemId) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await polishItem(singleForm.value.accountId, singleForm.value.itemId)
    singleResult.value = res.data
    ElMessage.success(res.data?.success ? '擦亮成功' : '擦亮已发（请看响应）')
  } catch (e) {
    ElMessage.error('擦亮失败: ' + e.message)
  } finally { loading.value = false }
}

async function doBatchPolish() {
  if (!batchForm.value.accountId || !batchForm.value.itemIds.length) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await batchPolish(batchForm.value.accountId, batchForm.value.itemIds)
    batchResult.value = res.data
    ElMessage.success(`批量擦亮完成：成功 ${res.data?.success || 0} / ${res.data?.total || 0}`)
  } catch (e) {
    ElMessage.error('批量擦亮失败: ' + e.message)
  } finally { loading.value = false }
}

async function doSuperPolish() {
  if (!superForm.value.accountId || !superForm.value.itemId) return ElMessage.warning('请选择账号和商品')
  loading.value = true
  try {
    const res = await superPolish(superForm.value.accountId, superForm.value.itemId, superForm.value.times)
    superResult.value = res.data
    ElMessage.success(`超级擦亮完成：成功 ${res.data?.success || 0} / ${res.data?.times || 0}`)
  } catch (e) {
    ElMessage.error('超级擦亮失败: ' + e.message)
  } finally { loading.value = false }
}
</script>

<style scoped>
.polish-page { padding: 16px; }
</style>
