<template>
  <div class="polish-page">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="单擦" name="single">
        <el-form :model="singleForm" label-width="100" inline>
          <el-form-item label="账号">
            <el-select v-model="singleForm.accountId" placeholder="选择账号" style="width: 220px">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品 ID">
            <el-input v-model="singleForm.itemId" style="width: 220px" />
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
            <el-select v-model="batchForm.accountId" placeholder="选择账号" style="width: 220px">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品 ID 列表">
            <el-input v-model="batchForm.itemIdsText" type="textarea" :rows="4"
              placeholder="一行一个 itemId，或逗号分隔" style="width: 400px" />
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
            <el-select v-model="superForm.accountId" placeholder="选择账号" style="width: 220px">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.id" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品 ID">
            <el-input v-model="superForm.itemId" style="width: 220px" />
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
import { polishItem, batchPolish, superPolish } from '@/api/polish'
import { listAccounts } from '@/api/account'

const activeTab = ref('single')
const accounts = ref([])
const loading = ref(false)

const singleForm = ref({ accountId: null, itemId: '' })
const singleResult = ref(null)
const batchForm = ref({ accountId: null, itemIdsText: '' })
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

async function doPolish() {
  if (!singleForm.value.accountId || !singleForm.value.itemId) return ElMessage.warning('请填账号和商品 ID')
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
  if (!batchForm.value.accountId || !batchForm.value.itemIdsText) return ElMessage.warning('请填账号和商品 ID')
  const ids = batchForm.value.itemIdsText.split(/[\n,，]/).map(s => s.trim()).filter(Boolean)
  if (!ids.length) return ElMessage.warning('未解析到有效 ID')
  loading.value = true
  try {
    const res = await batchPolish(batchForm.value.accountId, ids)
    batchResult.value = res.data
    ElMessage.success(`批量擦亮完成：成功 ${res.data?.success || 0} / ${res.data?.total || 0}`)
  } catch (e) {
    ElMessage.error('批量擦亮失败: ' + e.message)
  } finally { loading.value = false }
}

async function doSuperPolish() {
  if (!superForm.value.accountId || !superForm.value.itemId) return ElMessage.warning('请填账号和商品 ID')
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
