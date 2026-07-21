<template>
  <div class="page-root">
    <el-card style="margin-bottom: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>买家画像</span>
          <el-button size="small" @click="loadData">刷新</el-button>
        </div>
      </template>
      <div style="display: flex; gap: 12px; margin-bottom: 16px;">
        <el-input v-model="searchKeyword" placeholder="搜索昵称或买家ID" style="width: 280px;" clearable @keyup.enter="loadData" />
        <el-button type="primary" @click="loadData">搜索</el-button>
      </div>
      <el-table :data="buyers" stripe v-loading="loading" size="small">
        <el-table-column prop="nickname" label="昵称" width="140" />
        <el-table-column prop="buyerId" label="买家ID" width="140" />
        <el-table-column prop="credibilityScore" label="可信度" width="100" sortable>
          <template #default="{ row }">
            <el-tag :type="credType(row.credibilityScore)" size="small">{{ row.credibilityScore || 50 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalSessions" label="会话数" sortable width="100" />
        <el-table-column prop="totalMessages" label="消息数" sortable width="100" />
        <el-table-column prop="totalOrders" label="成交数" sortable width="100" />
        <el-table-column prop="totalSpent" label="成交金额" sortable width="120" />
        <el-table-column prop="bargainCount" label="议价次数" sortable width="100" />
        <el-table-column label="标签" min-width="200">
          <template #default="{ row }">
            <el-tag v-for="t in parseTags(row.tags)" :key="t" size="small" style="margin: 2px;">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        @current-change="loadData"
      />
    </el-card>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="买家详情" width="600px">
      <div v-if="currentBuyer">
        <p><b>买家ID:</b> {{ currentBuyer.buyerId }}</p>
        <p><b>昵称:</b> {{ currentBuyer.nickname }}</p>
        <p><b>可信度:</b> {{ currentBuyer.credibilityScore || 50 }}</p>
        <p><b>首次交互账号:</b> {{ currentBuyer.firstAccountId }}</p>
        <p><b>总会话/消息/成交:</b> {{ currentBuyer.totalSessions }} / {{ currentBuyer.totalMessages }} / {{ currentBuyer.totalOrders }}</p>
        <p><b>累计成交金额:</b> ¥{{ currentBuyer.totalSpent || 0 }}</p>
        <p><b>议价次数:</b> {{ currentBuyer.bargainCount }}</p>
        <p><b>标签:</b>
          <el-tag v-for="t in parseTags(currentBuyer.tags)" :key="t" size="small" style="margin:2px;">{{ t }}</el-tag>
        </p>
        <p><b>运营备注:</b></p>
        <el-input v-model="notes" type="textarea" :rows="3" />
        <el-button type="primary" @click="saveNotes" style="margin-top: 8px;">保存备注</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getBuyerList, getBuyer, setBuyerNotes } from '@/api/market'

const buyers = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const detailVisible = ref(false)
const currentBuyer = ref(null)
const notes = ref('')

function parseTags(tags) {
  if (!tags) return []
  try { return JSON.parse(tags) } catch { return [] }
}
function credType(score) {
  if (score >= 80) return 'success'
  if (score >= 50) return 'warning'
  return 'danger'
}

async function loadData() {
  loading.value = true
  try {
    const r = await getBuyerList(page.value - 1, size.value, searchKeyword.value)
    if (r.success) {
      buyers.value = r.data || []
      total.value = buyers.value.length
    }
  } catch (e) {}
  loading.value = false
}

function viewDetail(row) {
  currentBuyer.value = row
  notes.value = row.notes || ''
  detailVisible.value = true
}

async function saveNotes() {
  if (!currentBuyer.value) return
  try {
    await setBuyerNotes(currentBuyer.value.buyerId, notes.value)
    ElMessage.success('备注已保存')
    currentBuyer.value.notes = notes.value
  } catch (e) {}
}

onMounted(loadData)
</script>
