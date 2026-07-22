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
          <el-button type="primary" @click="handleOpenAddDialog">添加收藏</el-button>
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

    <!-- 添加收藏弹窗：搜索选择商品 -->
    <el-dialog v-model="showAddDialog" title="添加收藏" width="600px">
      <!-- 类型选择 -->
      <el-form :model="addForm" label-width="80px">
        <el-form-item label="类别">
          <el-radio-group v-model="addForm.targetType" @change="onTypeChange">
            <el-radio-button value="ITEM">商品</el-radio-button>
            <el-radio-button value="USER">用户</el-radio-button>
            <el-radio-button value="SHOP">店铺</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <!-- 商品类：搜索选择 -->
      <template v-if="addForm.targetType === 'ITEM'">
        <el-form :model="addForm" label-width="80px">
          <el-form-item label="账号">
            <el-select v-model="addForm.accountId" style="width: 100%;" :disabled="!!selectedAccountId">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索商品名称..."
              clearable
              @keyup.enter="handleSearch"
            >
              <template #append>
                <el-button :loading="searching" @click="handleSearch">
                  <el-icon><Search /></el-icon>搜索
                </el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-form>

        <!-- 搜索结果 -->
        <div v-if="searchResults.length > 0" class="search-results">
          <div style="font-size:13px; color:#909399; margin-bottom:8px;">搜索结果：点击选中</div>
          <el-table
            :data="searchResults"
            max-height="280"
            @row-click="onSelectItem"
            size="small"
            stripe
          >
            <el-table-column label="商品" min-width="300">
              <template #default="{ row }">
                <div class="item-cell">
                  <el-image v-if="row.pic" :src="row.pic" style="width:40px;height:40px;border-radius:4px;flex-shrink:0;" fit="cover" />
                  <span class="item-title">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="价格" width="100">
              <template #default="{ row }">
                <span style="color:#f56c6c; font-weight:600;">¥{{ row.price }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else-if="searchKeyword && !searching && searched" class="no-result">
          无搜索结果，换个关键词试试
        </div>

        <!-- 选中后预览 -->
        <div v-if="addForm.targetId" class="selected-preview">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="商品ID">{{ addForm.targetId }}</el-descriptions-item>
            <el-descriptions-item label="商品名">{{ addForm.targetName }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 手动输入兜底 -->
        <div v-if="!addForm.targetId" style="margin-top: 12px;">
          <el-divider>手动填写</el-divider>
          <el-form :model="addForm" label-width="80px">
            <el-form-item label="商品ID">
              <el-input v-model="addForm.targetId" placeholder="输入商品ID" />
            </el-form-item>
            <el-form-item label="名称">
              <el-input v-model="addForm.targetName" placeholder="可选" />
            </el-form-item>
          </el-form>
        </div>
      </template>

      <!-- 非商品类 -->
      <template v-else>
        <el-form :model="addForm" label-width="80px">
          <el-form-item label="账号">
            <el-select v-model="addForm.accountId" style="width: 100%;">
              <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标ID">
            <el-input v-model="addForm.targetId" placeholder="输入目标ID" />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="addForm.targetName" placeholder="可选" />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="handleCloseAddDialog">取消</el-button>
        <el-button type="primary" :loading="adding" @click="handleAdd">确定收藏</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import api from '@/api/request'
import { listCollects, addCollect, removeCollect, syncCollects, searchCollectItems } from '@/api/collect'

const accounts = ref([])
const collects = ref([])
const selectedAccountId = ref(null)
const selectedType = ref('')
const showAddDialog = ref(false)
const adding = ref(false)
const syncing = ref(false)
const loading = ref(false)
const addForm = ref({ targetType: 'ITEM', targetId: '', targetName: '', accountId: null })

// 搜索相关
const searchKeyword = ref('')
const searchResults = ref([])
const searching = ref(false)
const searched = ref(false)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) {
      accounts.value = res.data
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

function handleOpenAddDialog() {
  addForm.value = { targetType: 'ITEM', targetId: '', targetName: '', accountId: selectedAccountId.value }
  searchKeyword.value = ''
  searchResults.value = []
  searched.value = false
  showAddDialog.value = true
}

function handleCloseAddDialog() {
  showAddDialog.value = false
  searchKeyword.value = ''
  searchResults.value = []
  searched.value = false
}

function onTypeChange() {
  addForm.value.targetId = ''
  addForm.value.targetName = ''
  searchResults.value = []
  searchKeyword.value = ''
}

async function handleSearch() {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  if (!addForm.value.accountId) {
    ElMessage.warning('请先选择账号')
    return
  }
  searching.value = true
  searched.value = true
  try {
    const res = await searchCollectItems(addForm.value.accountId, searchKeyword.value.trim())
    if (res.success) {
      searchResults.value = res.data || []
      if (searchResults.value.length === 0) {
        ElMessage.info('无搜索结果，可尝试更换关键词')
      }
    } else {
      ElMessage.error(res.message || '搜索失败')
      searchResults.value = []
    }
  } catch (e) {
    searchResults.value = []
  }
  searching.value = false
}

function onSelectItem(row) {
  addForm.value.targetId = row.itemId
  addForm.value.targetName = row.title
  ElMessage.success(`已选中：${row.title}`)
}

async function handleAdd() {
  if (!addForm.value.targetId) {
    ElMessage.warning('请填写或搜索选择目标ID')
    return
  }
  if (!addForm.value.accountId) {
    ElMessage.warning('请选择账号')
    return
  }

  if (addForm.value.targetType !== 'ITEM') {
    // 非商品类型只落库
    try {
      adding.value = true
      const res = await addCollect({
        accountId: addForm.value.accountId,
        targetType: addForm.value.targetType,
        targetId: addForm.value.targetId,
        targetName: addForm.value.targetName
      })
      if (res.success) {
        ElMessage.success('已添加')
        handleCloseAddDialog()
        await loadCollects()
      } else {
        ElMessage.error(res.message || '添加失败')
      }
    } catch (e) {}
    adding.value = false
    return
  }

  // 商品类型：同步到闲鱼
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
      handleCloseAddDialog()
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

<style scoped>
.page-root {
  padding: 0;
}

.search-results {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 12px;
}

.search-results :deep(.el-table__row) {
  cursor: pointer;
}

.search-results :deep(.el-table__row:hover) {
  background-color: #f5f7fa !important;
}

.item-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

.no-result {
  text-align: center;
  color: #909399;
  padding: 24px 0;
  font-size: 13px;
}

.selected-preview {
  margin-top: 12px;
  padding: 12px;
  background-color: #f0f9eb;
  border-radius: 4px;
}
</style>
