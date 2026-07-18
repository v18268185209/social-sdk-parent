<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>规则管理</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 创建规则
          </el-button>
        </div>
      </template>

      <el-table :data="rules" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="ruleName" label="规则名称" width="150" />
        <el-table-column prop="keyword" label="关键词" width="150" />
        <el-table-column prop="matchType" label="匹配方式" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="{ CONTAINS: 'success', EXACT: 'warning', STARTS_WITH: 'info' }[row.matchType]">
              {{ { CONTAINS: '包含', EXACT: '精确', STARTS_WITH: '前缀' }[row.matchType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replyText" label="回复内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleRule(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" @click="testRule(row)">测试</el-button>
            <el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="showCreateDialog" title="创建规则" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="规则名称">
          <el-input v-model="form.ruleName" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keyword" />
        </el-form-item>
        <el-form-item label="匹配方式">
          <el-select v-model="form.matchType" style="width: 100%;">
            <el-option label="包含" value="CONTAINS" />
            <el-option label="精确" value="EXACT" />
            <el-option label="前缀" value="STARTS_WITH" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复内容">
          <el-input v-model="form.replyText" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api/request'

const rules = ref([])
const showCreateDialog = ref(false)
const form = ref({ ruleName: '', keyword: '', matchType: 'CONTAINS', replyText: '', priority: 100 })

async function loadRules() {
  try {
    const res = await api.get('/rules?accountId=1')
    if (res.success) rules.value = res.data
  } catch (e) {}
}

async function handleCreate() {
  if (!form.value.ruleName || !form.value.keyword) {
    ElMessage.warning('请填写规则名称和关键词')
    return
  }
  try {
    const res = await api.post('/rules', form.value)
    if (res.success) {
      ElMessage.success('规则创建成功')
      showCreateDialog.value = false
      form.value = { ruleName: '', keyword: '', matchType: 'CONTAINS', replyText: '', priority: 100 }
      await loadRules()
    }
  } catch (e) {}
}

async function toggleRule(row) {
  try {
    await api.post(`/rules/${row.id}/toggle?enabled=${row.enabled}`)
    ElMessage.success('规则状态已更新')
  } catch (e) {}
}

async function testRule(row) {
  try {
    const res = await api.post('/rules/test', {
      matchType: row.matchType,
      keyword: row.keyword,
      message: '你好，请问这个还在吗？'
    })
    ElMessageBox.alert(
      res.data ? '✅ 命中规则！' : '❌ 未命中规则',
      '规则测试结果',
      { type: res.data ? 'success' : 'info' }
    )
  } catch (e) {}
}

async function deleteRule(id) {
  await ElMessageBox.confirm('确认删除该规则？', '提示', { type: 'warning' })
  try { await api.delete(`/rules/${id}`); ElMessage.success('已删除'); await loadRules() } catch (e) {}
}

onMounted(loadRules)
</script>
