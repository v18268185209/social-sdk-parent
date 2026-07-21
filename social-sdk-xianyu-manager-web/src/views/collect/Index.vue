<template>
  <div class="page-root">
    <el-card shadow="never" style="margin: 0;">
      <template #header>
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <span style="font-size:16px; font-weight:600;">收藏关注</span>
          <el-button
            type="primary"
            size="small"
            :loading="syncing"
            :disabled="!selectedAccountId"
            @click="handleSync"
          >
            <el-icon style="margin-right:4px;"><Refresh /></el-icon>
            {{ syncing ? '同步中...' : '同步闲鱼收藏' }}
          </el-button>
        </div>
      </template>
      <el-form inline style="margin-bottom: 16px;">
        <el-form-item label="账号">
          <el-select v-model="selectedAccountId" @change="loadCollects" placeholder="选择账号" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="selectedType" @change="loadCollects" placeholder="全部" style="width: 120px;" clearable>
            <el-option label="全部" value="" />
            <el-option label="商品" value="ITEM" />
            <el-option label="用户" value="USER" />
            <el-option label="店铺" value="SHOP" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="showAddDialog = true">添加收藏</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="collects" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="targetType" label="类型" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="typeColor(row.targetType)">{{ typeLabel(row.targetType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="目标ID" width="180" />
        <el-table-column prop="targetName" label="名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="collectedAt" label="收藏时间" width="180">
          <template #default="{ row }">{{ formatTime(row.collectedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openItem(row.targetId)">查看</el-button>
            <el-button size="small" link type="danger" @click="handleRemove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddDialog" title="添加收藏" width="440px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="账号">
          <el-select v-model="addForm.accountId" style="width: 100%;" :disabled="!!selectedAccountId">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="addForm.targetType" style="width: 100%;">
            <el-option label="商品" value="ITEM" />
            <el-option label="用户" value="USER" />
            <el-option label="店铺" value="SHOP" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input v-model="addForm.targetId" placeholder="输入商品/用户/店铺 ID" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="addForm.targetName" placeholder="可选，便于识别" />
        </el-form-item>
        <el-form-item label="账号ID" v-if="addForm.targetType !== 'ITEM'">
          <el-input v-model="addForm.accountId" placeholder="收藏使用的账号 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">添加并同步</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import api from '@/api/request'
import { listCollects, addCollect, removeCollect, syncCollects } from '@/api/collect'

const accounts = ref([])
const collects = ref([])
const selectedAccountId = ref(null)
const selectedType = ref('')
const showAddDialog = ref(false)
const adding = ref(false)
const syncing = ref(false)
const loading = ref(false)
const addForm = ref({ targetType: 'ITEM', targetId: '', targetName: '', accountId: null })

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      accounts.value = res.data
      // 默认选中第一个
      if (accounts.value.length > 0 && !selectedAccountId.value) {
        selectedAccountId.value = accounts.value[0].id
        addForm.value.accountId = accounts.value[0].id
        await loadCollects()
      }
    }
  } catch (e) {}
}

async function loadCollects() {
  if (!selectedAccountId.value) return
  loading.value = true
  try {
    const params = { accountId: selectedAccountId.value }
    if (selectedType.value) params.targetType = selectedType.value
    const res = await listCollects(params)
    if (res.success) {
      collects.value = res.data
    } else {
      ElMessage.error(res.message || '加载收藏列表失败')
    }
  } catch (e) {}
  loading.value = false
}

async function handleAdd() {
  if (!addForm.value.targetId) {
    ElMessage.warning('请填写目标ID')
    return
  }
  if (!addForm.value.accountId) {
    ElMessage.warning('请选择或输入账号')
    return
  }
  if (addForm.value.targetType !== 'ITEM') {
    // 非商品类型暂时只落库
    try {
      const res = await addCollect({
        accountId: addForm.value.accountId,
        targetType: addForm.value.targetType,
        targetId: addForm.value.targetId,
        targetName: addForm.value.targetName
      })
      if (res.success) {
        ElMessage.success('已添加（本地记录，需抓到对应同步接口后再支持远程同步）')
        showAddDialog.value = false
        addForm.value = { targetType: 'ITEM', targetId: '', targetName: '', accountId: selectedAccountId.value }
        await loadCollects()
      } else {
        ElMessage.error(res.message || '添加失败')
      }
    } catch (e) {}
    return
  }
  adding.value = true
  try {
    const res = await addCollect({
      accountId: addForm.value.accountId,
      targetType: addForm.value.targetType,
      targetId: addForm.value.targetId,
      targetName: addForm.value.targetName
    })
    if (res.success) {
      ElMessage.success('已添加并同步到闲鱼')
      showAddDialog.value = false
      addForm.value = { targetType: 'ITEM', targetId: '', targetName: '', accountId: selectedAccountId.value }
      await loadCollects()
    } else {
      ElMessage.error(res.message || '添加失败')
    }
  } catch (e) {}
  adding.value = false
}

async function handleRemove(row) {
  try {
    await ElMessageBox.confirm(`确定要移除收藏「${row.targetName || row.targetId}」？`, '确认移除', {
      type: 'warning'
    })
  } catch { return }
  try {
    const res = await removeCollect(row.id)
    if (res.success) {
      ElMessage.success('已移除')
      await loadCollects()
    } else {
      ElMessage.error(res.message || '移除失败')
    }
  } catch (e) {}
}

async function handleSync() {
  if (!selectedAccountId.value) return
  syncing.value = true
  try {
    const res = await syncCollects(selectedAccountId.value)
    if (res.success && res.data > 0) {
      ElMessage.success(`同步完成，新增 ${res.data} 条`)
      await loadCollects()
    } else if (res.success) {
      ElMessage.success('同步完成，无新增')
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {}
  syncing.value = false
}

function openItem(itemId) {
  window.open(`https://www.goofish.com/item?id=${itemId}`, '_blank')
}

function typeLabel(t) {
  return { ITEM: '商品', USER: '用户', SHOP: '店铺' }[t] || t
}

function typeColor(t) {
  return { ITEM: 'success', USER: 'warning', SHOP: 'danger' }[t] || 'info'
}

function formatTime(t) {
  if (!t) return ''
  try {
    return new Date(t).toLocaleString('zh-CN')
  } catch { return t }
}

onMounted(loadAccounts)
</script>
