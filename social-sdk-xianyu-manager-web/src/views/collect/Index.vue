<template>
  <div>
    <el-card>
      <template #header><span>收藏关注</span></template>
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

      <el-table :data="collects" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="targetType" label="类型" width="80" />
        <el-table-column prop="targetId" label="目标ID" width="150" />
        <el-table-column prop="targetName" label="名称" min-width="150" />
        <el-table-column prop="collectedAt" label="收藏时间" width="180" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button size="small" type="danger" @click="handleRemove(row.id)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showAddDialog" title="添加收藏" width="400px">
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="addForm.targetType" style="width: 100%;">
            <el-option label="商品" value="ITEM" />
            <el-option label="用户" value="USER" />
            <el-option label="店铺" value="SHOP" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标ID">
          <el-input v-model="addForm.targetId" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="addForm.targetName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/request'
import { listCollects, addCollect, removeCollect } from '@/api/collect'

const accounts = ref([])
const collects = ref([])
const selectedAccountId = ref(null)
const selectedType = ref('')
const showAddDialog = ref(false)
const addForm = ref({ targetType: 'ITEM', targetId: '', targetName: '' })

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) {}
}

async function loadCollects() {
  if (!selectedAccountId.value) return
  try {
    const params = { accountId: selectedAccountId.value }
    if (selectedType.value) params.targetType = selectedType.value
    const res = await listCollects(params)
    if (res.success) collects.value = res.data
  } catch (e) {}
}

async function handleAdd() {
  if (!addForm.value.targetId) {
    ElMessage.warning('请填写目标ID')
    return
  }
  try {
    addForm.value.accountId = selectedAccountId.value
    const res = await addCollect(addForm.value)
    if (res.success) {
      ElMessage.success('已添加')
      showAddDialog.value = false
      addForm.value = { targetType: 'ITEM', targetId: '', targetName: '' }
      await loadCollects()
    }
  } catch (e) {}
}

async function handleRemove(id) {
  try {
    const res = await removeCollect(id)
    if (res.success) {
      ElMessage.success('已移除')
      await loadCollects()
    }
  } catch (e) {}
}

onMounted(loadAccounts)
</script>
