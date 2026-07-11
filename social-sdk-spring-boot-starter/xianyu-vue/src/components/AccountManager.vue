<script setup>
import { onMounted, reactive, ref } from 'vue'
import { xianyuApi } from '../api/xianyu'

const loading = ref(false)
const status = ref('')
const statusType = ref('ok')
const accounts = ref([])

const form = reactive({
  accountName: '',
  cookieHeader: '',
  remark: ''
})

function setStatus(message, type = 'ok') {
  status.value = message
  statusType.value = type
}

async function loadAccounts() {
  loading.value = true
  try {
    accounts.value = await xianyuApi.listAccounts()
    setStatus(`已加载 ${accounts.value.length} 个账号`, 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loginByCookies() {
  if (!form.cookieHeader.trim()) {
    setStatus('请先填写 Cookie Header', 'error')
    return
  }
  loading.value = true
  try {
    await xianyuApi.loginByCookies({
      accountName: form.accountName,
      cookieHeader: form.cookieHeader,
      remark: form.remark
    })
    form.cookieHeader = ''
    await loadAccounts()
    setStatus('账号登录并保存成功', 'ok')
  } catch (error) {
    const verificationUrl = error?.payload?.data?.verificationUrl
    if (verificationUrl) {
      setStatus(`触发风控，请验证后重试: ${verificationUrl}`, 'error')
      return
    }
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function refreshProfile(accountId) {
  loading.value = true
  try {
    await xianyuApi.refreshProfile(accountId)
    await loadAccounts()
    setStatus('账号资料刷新成功', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function statusTagClass(status) {
  if (status === 'ACTIVE') {
    return 'ok'
  }
  if (status === 'PENDING_VERIFY') {
    return 'warn'
  }
  return 'danger'
}

function shortError(message) {
  if (!message) {
    return '-'
  }
  if (message.length <= 44) {
    return message
  }
  return `${message.slice(0, 44)}...`
}

async function markActive(accountId) {
  loading.value = true
  try {
    await xianyuApi.updateAccountStatus(accountId, { status: 'ACTIVE', lastError: '' })
    await loadAccounts()
    setStatus('账号状态已更新为 ACTIVE', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function removeAccount(accountId) {
  if (!window.confirm('确认删除该账号吗？')) {
    return
  }
  loading.value = true
  try {
    await xianyuApi.deleteAccount(accountId)
    await loadAccounts()
    setStatus('账号已删除', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadAccounts)
</script>

<template>
  <section class="grid">
    <div class="panel">
      <h2>账号登录 / Cookies 管理</h2>
      <div class="row">
        <div class="field">
          <label>账号备注名</label>
          <input v-model="form.accountName" placeholder="如: 店铺A" />
        </div>
        <div class="field">
          <label>备注信息</label>
          <input v-model="form.remark" placeholder="可选" />
        </div>
      </div>
      <div class="field" style="margin-top: 10px;">
        <label>Cookie Header</label>
        <textarea v-model="form.cookieHeader" placeholder="_m_h5_tk=...; _m_h5_tk_enc=...; cookie2=...;"></textarea>
      </div>
      <div class="row" style="margin-top: 10px;">
        <button class="action" :disabled="loading" @click="loginByCookies">登录并保存</button>
        <button class="action secondary" :disabled="loading" @click="loadAccounts">刷新列表</button>
      </div>
      <p class="status" :class="statusType">{{ status }}</p>
    </div>

    <div class="panel">
      <h2>账号列表</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>账号名</th>
              <th>UserId</th>
              <th>状态</th>
              <th>最近错误</th>
              <th>最后登录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in accounts" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.accountName || '-' }}</td>
              <td>{{ item.userId || '-' }}</td>
              <td>
                <span class="tag" :class="statusTagClass(item.status)">{{ item.status || 'UNKNOWN' }}</span>
              </td>
              <td :title="item.lastError || ''">{{ shortError(item.lastError) }}</td>
              <td>{{ item.lastLoginAt || '-' }}</td>
              <td>
                <div class="row">
                  <button class="action secondary" @click="refreshProfile(item.id)">刷新资料</button>
                  <button
                    v-if="item.status === 'PENDING_VERIFY' || item.status === 'FAILED'"
                    class="action"
                    @click="markActive(item.id)"
                  >
                    标记已验证
                  </button>
                  <button class="action warn" @click="removeAccount(item.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="accounts.length === 0">
              <td colspan="7">暂无账号数据</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
