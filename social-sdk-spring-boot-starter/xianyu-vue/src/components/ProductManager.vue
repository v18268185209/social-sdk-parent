<script setup>
import { onMounted, reactive, ref } from 'vue'
import { xianyuApi } from '../api/xianyu'

const loading = ref(false)
const status = ref('')
const statusType = ref('ok')
const products = ref([])
const selectedAccountId = ref('')

const form = reactive({
  id: null,
  accountId: '',
  itemId: '',
  title: '',
  price: '',
  stock: '',
  status: 'ON_SHELF',
  detailUrl: '',
  description: ''
})

function setStatus(message, type = 'ok') {
  status.value = message
  statusType.value = type
}

async function loadProducts() {
  loading.value = true
  try {
    const accountId = selectedAccountId.value ? Number(selectedAccountId.value) : undefined
    products.value = await xianyuApi.listProducts(accountId)
    setStatus(`已加载 ${products.value.length} 条商品`, 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function editProduct(row) {
  form.id = row.id
  form.accountId = row.accountId
  form.itemId = row.itemId || ''
  form.title = row.title || ''
  form.price = row.price ?? ''
  form.stock = row.stock ?? ''
  form.status = row.status || 'ON_SHELF'
  form.detailUrl = row.detailUrl || ''
  form.description = row.description || ''
}

function resetForm() {
  form.id = null
  form.accountId = ''
  form.itemId = ''
  form.title = ''
  form.price = ''
  form.stock = ''
  form.status = 'ON_SHELF'
  form.detailUrl = ''
  form.description = ''
}

async function saveProduct() {
  if (!form.accountId || !form.title.trim()) {
    setStatus('accountId 和 title 为必填项', 'error')
    return
  }

  const payload = {
    accountId: Number(form.accountId),
    itemId: form.itemId,
    title: form.title,
    price: form.price === '' ? null : Number(form.price),
    stock: form.stock === '' ? null : Number(form.stock),
    status: form.status,
    detailUrl: form.detailUrl,
    description: form.description
  }

  loading.value = true
  try {
    if (form.id) {
      await xianyuApi.updateProduct(form.id, payload)
      setStatus('商品已更新', 'ok')
    } else {
      await xianyuApi.createProduct(payload)
      setStatus('商品已创建', 'ok')
    }
    resetForm()
    await loadProducts()
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function removeProduct(id) {
  if (!window.confirm('确认删除商品吗？')) {
    return
  }
  loading.value = true
  try {
    await xianyuApi.deleteProduct(id)
    await loadProducts()
    setStatus('商品已删除', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadProducts)
</script>

<template>
  <section class="grid">
    <div class="panel">
      <h2>商品管理</h2>
      <div class="row">
        <div class="field">
          <label>筛选账号ID</label>
          <input v-model="selectedAccountId" placeholder="不填则查询全部" />
        </div>
        <div class="field" style="flex:0.5;min-width:130px;align-self:flex-end;">
          <button class="action secondary" :disabled="loading" @click="loadProducts">查询</button>
        </div>
      </div>
    </div>

    <div class="panel">
      <h2>{{ form.id ? '编辑商品' : '新增商品' }}</h2>
      <div class="row">
        <div class="field">
          <label>账号ID</label>
          <input v-model="form.accountId" placeholder="必填" />
        </div>
        <div class="field">
          <label>商品标题</label>
          <input v-model="form.title" placeholder="必填" />
        </div>
        <div class="field">
          <label>商品ID</label>
          <input v-model="form.itemId" placeholder="可选" />
        </div>
      </div>
      <div class="row">
        <div class="field">
          <label>价格</label>
          <input v-model="form.price" type="number" step="0.01" />
        </div>
        <div class="field">
          <label>库存</label>
          <input v-model="form.stock" type="number" />
        </div>
        <div class="field">
          <label>状态</label>
          <select v-model="form.status">
            <option value="ON_SHELF">ON_SHELF</option>
            <option value="OFF_SHELF">OFF_SHELF</option>
            <option value="LOCKED">LOCKED</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>详情页URL</label>
        <input v-model="form.detailUrl" />
      </div>
      <div class="field" style="margin-top: 10px;">
        <label>描述</label>
        <textarea v-model="form.description"></textarea>
      </div>
      <div class="row" style="margin-top: 10px;">
        <button class="action" :disabled="loading" @click="saveProduct">保存</button>
        <button class="action secondary" :disabled="loading" @click="resetForm">重置</button>
      </div>
      <p class="status" :class="statusType">{{ status }}</p>
    </div>

    <div class="panel">
      <h2>商品列表</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>账号ID</th>
              <th>标题</th>
              <th>价格</th>
              <th>库存</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in products" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.accountId }}</td>
              <td>{{ item.title }}</td>
              <td>{{ item.price ?? '-' }}</td>
              <td>{{ item.stock ?? '-' }}</td>
              <td>{{ item.status || '-' }}</td>
              <td>
                <div class="row">
                  <button class="action secondary" @click="editProduct(item)">编辑</button>
                  <button class="action warn" @click="removeProduct(item.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="products.length === 0">
              <td colspan="7">暂无商品</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
