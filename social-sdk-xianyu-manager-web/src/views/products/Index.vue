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
          <el-button
            type="primary"
            :disabled="!filters.accountId"
            :loading="syncing"
            @click="handleSyncFromXianyu"
          >
            <el-icon><Refresh /></el-icon> 同步闲鱼
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button type="success" @click="showAiOptimizeDialog = true">
            <el-icon><MagicStick /></el-icon> AI 优化文案
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
            <el-button size="small" @click="viewDetail(row)">详情</el-button>
            <el-button size="small" @click="editPrice(row)">改价</el-button>
            <el-button size="small" @click="editStock(row)">改库存</el-button>
            <el-button v-if="row.status === 'ON_SALE'" size="small" type="warning" @click="shelfOff(row)">下架</el-button>
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

    <!-- AI 优化文案对话框 -->
    <el-dialog v-model="showAiOptimizeDialog" title="AI 商品文案优化" width="600px">
      <el-form :model="aiForm" label-width="100px">
        <el-form-item label="AI 模型">
          <el-select v-model="aiForm.modelId" placeholder="选择 AI 模型" style="width: 100%;">
            <el-option v-for="m in aiModels" :key="m.id" :label="`${m.modelName} (${m.providerName})`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名">
          <el-input v-model="aiForm.productTitle" placeholder="如：iPhone 15 Pro 256G 原色钛金属" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="aiForm.keywordsRaw" placeholder="逗号分隔，如：iPhone,15 Pro,256G,原色钛金属" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="成色">
          <el-select v-model="aiForm.condition" style="width: 150px;">
            <el-option label="全新" value="全新" />
            <el-option label="九成新" value="九成新" />
            <el-option label="八成新" value="八成新" />
            <el-option label="七成新" value="七成新" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-divider />
      <el-tabs v-model="aiActiveTab">
        <el-tab-pane label="📝 标题优化" name="title">
          <el-input v-model="aiResult.title" type="textarea" :rows="3" readonly />
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="optimizeTitle" :loading="aiLoading">生成标题</el-button>
        </el-tab-pane>
        <el-tab-pane label="📄 描述优化" name="desc">
          <el-input v-model="aiResult.description" type="textarea" :rows="6" readonly />
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="optimizeDesc" :loading="aiLoading">生成描述</el-button>
        </el-tab-pane>
        <el-tab-pane label="🏷️ 关键词提取" name="kw">
          <div style="min-height: 50px;">
            <el-tag v-for="kw in aiResult.keywords" :key="kw" style="margin: 4px;">{{ kw }}</el-tag>
            <span v-if="!aiResult.keywords.length" style="color: #909399;">暂无关键词</span>
          </div>
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="extractKeywords" :loading="aiLoading">提取关键词</el-button>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="showAiOptimizeDialog = false">关闭</el-button>
        <el-button type="success" @click="useAiResult" v-if="aiResult.title">应用为商品信息</el-button>
      </template>
    </el-dialog>

    <!-- 创建商品对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建商品" width="600px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="账号">
          <el-select v-model="createForm.accountId" placeholder="选择账号" style="width: 100%;" @change="onAccountChange">
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
        <el-form-item label="分类">
          <el-cascader
            v-model="createForm.categoryIdPath"
            :options="categoryTree"
            :props="{ value: 'catId', label: 'catName', children: 'children', checkStrictly: true, emitPath: false }"
            placeholder="留空让闲鱼 AI 按标题自动推荐分类"
            clearable
            filterable
            style="width: 100%;"
            :loading="categoryTreeLoading"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">从闲鱼拉分类树选择，留空走 AI 推荐</div>
        </el-form-item>
        <el-form-item label="运费">
          <el-select v-model="createForm.deliveryChoice" style="width: 100%;">
            <el-option label="按距离计费（闲鱼默认）" value="按距离计费" />
            <el-option label="包邮" value="包邮" />
            <el-option label="一口价运费" value="一口价" />
            <el-option label="无需邮寄（虚拟商品）" value="无需邮寄" />
          </el-select>
          <el-input-number v-if="createForm.deliveryChoice === '一口价'"
            v-model="createForm.postPrice" :min="0" :precision="2"
            style="width: 100%; margin-top: 8px;" placeholder="运费金额" />
        </el-form-item>
        <el-form-item label="所在地">
          <el-input v-model="createForm.location" placeholder="留空走闲鱼账号默认所在地" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">填省市（如「杭州市」），留空走账号默认</div>
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            :file-list="imageFileList"
            list-type="picture-card"
            :limit="9"
            :on-change="(file, list) => handleUploadChange(list, 'images')"
            :on-remove="(file, list) => handleUploadRemove(list, 'images')"
            :http-request="(opts) => customUpload(opts, 'images')"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">最多 9 张图片，单张不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="视频">
          <el-upload
            :file-list="videoFileList"
            :limit="3"
            :on-change="(file, list) => handleUploadChange(list, 'videos')"
            :on-remove="(file, list) => handleUploadRemove(list, 'videos')"
            :http-request="(opts) => customUpload(opts, 'videos')"
            accept="video/*"
          >
            <el-button type="primary" plain>
              <el-icon><UploadFilled /></el-icon> 上传视频
            </el-button>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">最多 3 个视频，单个不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>

  <!-- 商品详情抽屉 -->
  <el-drawer v-model="showDetailDrawer" title="商品详情" size="50%" direction="rtl">
    <div v-if="detail" style="padding: 0 20px 20px;">
      <!-- 主图 + 视频预览 -->
      <el-carousel v-if="detail.images && detail.images.length" height="260px" style="margin-bottom: 16px; border-radius: 8px; overflow: hidden;">
        <el-carousel-item v-for="(img, idx) in detail.images" :key="idx">
          <el-image :src="img" fit="contain" style="width: 100%; height: 100%;" :preview-src-list="detail.images" />
        </el-carousel-item>
      </el-carousel>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="价格">¥{{ detail.price }}</el-descriptions-item>
        <el-descriptions-item label="原价">¥{{ detail.originalPrice || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存">{{ detail.stock }}</el-descriptions-item>
        <el-descriptions-item label="浏览">{{ detail.viewCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="收藏">{{ detail.favoriteCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="商品类型">
          <el-tag size="small">{{ goodsTypeLabel(detail.goodsType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发货类型">
          <el-tag size="small" v-if="detail.deliverType">{{ deliverTypeLabel(detail.deliverType) }}</el-tag>
          <span v-else style="color:#909399;">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="闲鱼 ID">{{ detail.itemId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品类">{{ detail.categoryId || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 描述 -->
      <el-card style="margin-top: 16px;" v-if="detail.description">
        <template #header><span>📝 商品描述</span></template>
        <div style="white-space: pre-wrap; line-height: 1.6;">{{ detail.description }}</div>
      </el-card>

      <!-- 发货模板 -->
      <el-card style="margin-top: 16px;" v-if="detail.deliverContentTemplate">
        <template #header><span>📦 发货内容模板</span></template>
        <div style="white-space: pre-wrap; line-height: 1.6; font-family: monospace;">{{ detail.deliverContentTemplate }}</div>
      </el-card>

      <!-- 视频列表 -->
      <el-card style="margin-top: 16px;" v-if="detail.videos && detail.videos.length">
        <template #header><span>🎥 视频列表</span></template>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          <div v-for="(v, idx) in detail.videos" :key="idx" style="display: flex; align-items: center; gap: 12px;">
            <span style="color: #909399; min-width: 50px;">视频 {{ idx + 1 }}</span>
            <el-link :href="v" target="_blank" type="primary" :underline="false">
              <el-icon><VideoPlay /></el-icon> {{ v }}
            </el-link>
          </div>
        </div>
      </el-card>

      <!-- 详情页链接 -->
      <el-card style="margin-top: 16px;" v-if="detail.detailUrl">
        <template #header><span>🔗 闲鱼详情</span></template>
        <el-link :href="detail.detailUrl" target="_blank" type="primary">
          <el-icon><Link /></el-icon> 在闲鱼打开
        </el-link>
      </el-card>
    </div>
    <el-empty v-else description="加载中..." />
  </el-drawer>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, MagicStick, VideoPlay, Link, Plus, UploadFilled } from '@element-plus/icons-vue'
import api from '@/api/request'
import { optimizeTitle as optimizeTitleApi, optimizeDescription as optimizeDescriptionApi, extractKeywords as extractKeywordsApi } from '@/api/ai'

const products = ref([])
const accounts = ref([])
const aiModels = ref([])
const loading = ref(false)
const submitting = ref(false)
const syncing = ref(false)
const showCreateDialog = ref(false)
const showAiOptimizeDialog = ref(false)
const showDetailDrawer = ref(false)
const detail = ref(null)
const imageFileList = ref([])
const videoFileList = ref([])
const aiLoading = ref(false)
const aiActiveTab = ref('title')
const filters = ref({ accountId: null, keyword: '', status: '' })
const pagination = ref({ page: 1, size: 20, total: 0 })
const createForm = ref({ accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '', categoryId: '', categoryIdPath: null, deliveryChoice: '按距离计费', postPrice: 0, location: '' })
const categoryTree = ref([])
const categoryTreeLoading = ref(false)
const categoryTreeLoadedAccountId = ref(null)
const aiForm = ref({ modelId: null, productTitle: '', keywordsRaw: '', condition: '九成新' })
const aiResult = ref({ title: '', description: '', keywords: [] })

const statusType = (s) => ({ ON_SALE: 'success', OFF_SALE: 'warning', DRAFT: 'info' }[s] || 'info')
const statusLabel = (s) => ({ ON_SALE: '在售', OFF_SALE: '下架', DRAFT: '草稿' }[s] || s)
const goodsTypeLabel = (s) => ({ PHYSICAL: '实物', VIRTUAL: '虚拟' }[s] || s || '实物')
const deliverTypeLabel = (s) => ({ CARD: '卡密', ACCOUNT: '账号', LINK: '链接', FILE: '网盘文件' }[s] || s)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* ignore */ }
}

// 拉闲鱼分类树 — 创建商品表单分类下拉用，按 accountId 缓存避免重复拉
async function loadCategoryTree(accountId) {
  if (!accountId) { categoryTree.value = []; return }
  if (categoryTreeLoadedAccountId.value === accountId && categoryTree.value.length > 0) return
  categoryTreeLoading.value = true
  try {
    const res = await api.get('/products/category-tree', { params: { accountId } })
    if (res.success) {
      // 闲鱼返回 data.children[] 或 data[] 形式的嵌套树，前端按 catId/catName/children 渲染
      // 兼容多种返回结构：data.children / data / 顶层数组
      let tree = res.data
      if (tree && tree.children) tree = tree.children
      else if (tree && Array.isArray(tree.data)) tree = tree.data
      else if (tree && Array.isArray(tree)) { /* ok */ }
      else tree = []
      categoryTree.value = tree
      categoryTreeLoadedAccountId.value = accountId
    } else {
      categoryTree.value = []
    }
  } catch (e) {
    categoryTree.value = []
  } finally {
    categoryTreeLoading.value = false
  }
}

// 账号切换时清已选分类 + 重拉分类树
async function onAccountChange() {
  createForm.value.categoryIdPath = null
  createForm.value.categoryId = ''
  await loadCategoryTree(createForm.value.accountId)
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
  const images = imageFileList.value
    .filter(f => f.status === 'success' && f.url)
    .map(f => f.url)
  const videos = videoFileList.value
    .filter(f => f.status === 'success' && f.url)
    .map(f => f.url)
  submitting.value = true
  try {
    // el-cascader 选中节点对象（checkStrictly + emitPath:false 时 v-model 拿到 catId 值）
    // 但 el-cascader 的 v-model 在 checkStrictly 模式下拿到的是选中节点 value（catId），不是 path
    const catId = createForm.value.categoryIdPath || ''
    const payload = { ...createForm.value, categoryId: catId, images, videos }
    delete payload.categoryIdPath
    const res = await api.post('/products', payload)
    if (res.success) {
      ElMessage.success('商品创建成功')
      showCreateDialog.value = false
      createForm.value = { accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '', categoryId: '', categoryIdPath: null, deliveryChoice: '按距离计费', postPrice: 0, location: '' }
      imageFileList.value = []
      videoFileList.value = []
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
      } else {
        ElMessage.error(res.message || '改价失败')
      }
    } catch (e) {
      ElMessage.error('改价失败：' + (e?.response?.data?.message || e?.message || '请重试'))
    }
  }).catch(() => {})
}

function editStock(row) {
  ElMessageBox.prompt('输入新库存', '修改库存', { inputValue: String(row.stock) }).then(async ({ value }) => {
    try {
      const res = await api.put(`/products/${row.id}/stock?stock=${parseInt(value)}`)
      if (res.success) {
        ElMessage.success('库存已更新')
        await loadProducts()
      } else {
        ElMessage.error(res.message || '改库存失败')
      }
    } catch (e) {
      ElMessage.error('改库存失败：' + (e?.response?.data?.message || e?.message || '请重试'))
    }
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

const parseKeywords = () =>
  (aiForm.value.keywordsRaw || '').split(/[,，、\s]+/).map(s => s.trim()).filter(Boolean)

async function optimizeTitle() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await optimizeTitleApi(
      aiForm.value.modelId,
      aiForm.value.productTitle,
      parseKeywords(),
      aiForm.value.condition
    )
    if (res.success) {
      aiResult.value.title = res.data?.title || res.data || ''
      ElMessage.success('标题已生成')
    }
  } finally { aiLoading.value = false }
}

async function optimizeDesc() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await optimizeDescriptionApi(
      aiForm.value.modelId,
      aiForm.value.productTitle,
      parseKeywords(),
      aiForm.value.condition
    )
    if (res.success) {
      aiResult.value.description = res.data?.description || res.data || ''
      ElMessage.success('描述已生成')
    }
  } finally { aiLoading.value = false }
}

async function extractKeywords() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await extractKeywordsApi(aiForm.value.modelId, aiForm.value.productTitle)
    if (res.success) {
      aiResult.value.keywords = Array.isArray(res.data) ? res.data : (res.data?.keywords || [])
      ElMessage.success(`已提取 ${aiResult.value.keywords.length} 个关键词`)
    }
  } finally { aiLoading.value = false }
}

function useAiResult() {
  if (aiResult.value.title) createForm.value.title = aiResult.value.title
  if (aiResult.value.description) createForm.value.description = aiResult.value.description
  showAiOptimizeDialog.value = false
  showCreateDialog.value = true
  ElMessage.success('已应用 AI 生成内容')
}

async function loadAiModels() {
  try {
    const res = await api.get('/ai/models', { params: { size: 100 } })
    aiModels.value = (res.data?.records || []).filter(m => m.enabled !== false)
  } catch {}
}

async function viewDetail(row) {
  detail.value = null
  showDetailDrawer.value = true
  try {
    const res = await api.get(`/products/${row.id}`)
    if (res.success) {
      const p = res.data
      detail.value = {
        ...p,
        images: parseJsonArray(p.images),
        videos: parseJsonArray(p.videos)
      }
    }
  } catch {}
}

function parseJsonArray(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch { return [] }
}

function handleUploadChange(fileList, field) {
  if (field === 'images') imageFileList.value = fileList
  else videoFileList.value = fileList
}

function handleUploadRemove(fileList, field) {
  if (field === 'images') imageFileList.value = fileList
  else videoFileList.value = fileList
}

async function customUpload(options, field) {
  const file = options.file
  if (!file) return options.onError(new Error('文件为空'))
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB')
    if (field === 'images') imageFileList.value = imageFileList.value.filter(f => f.uid !== file.uid)
    else videoFileList.value = videoFileList.value.filter(f => f.uid !== file.uid)
    return options.onError(new Error('文件过大'))
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await api.post('/products/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.success) {
      const url = res.data.url
      if (field === 'images') {
        const target = imageFileList.value.find(f => f.uid === file.uid)
        if (target) { target.url = url; target.status = 'success' }
      } else {
        const target = videoFileList.value.find(f => f.uid === file.uid)
        if (target) { target.url = url; target.status = 'success' }
      }
      options.onSuccess(url)
    } else {
      throw new Error(res.message || '上传失败')
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.message || '未知错误'))
    if (field === 'images') imageFileList.value = imageFileList.value.filter(f => f.uid !== file.uid)
    else videoFileList.value = videoFileList.value.filter(f => f.uid !== file.uid)
    options.onError(err)
  }
}

onMounted(() => { loadAccounts(); loadProducts(); loadAiModels() })
</script>
