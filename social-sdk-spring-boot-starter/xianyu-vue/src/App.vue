<script setup>
import { onMounted, ref } from 'vue'
import AccountManager from './components/AccountManager.vue'
import ProductManager from './components/ProductManager.vue'
import RuleManager from './components/RuleManager.vue'
import MessageManager from './components/MessageManager.vue'
import { xianyuApi } from './api/xianyu'

const currentTab = ref('accounts')
const healthInfo = ref(null)

const tabs = [
  { key: 'accounts', label: '多账号管理' },
  { key: 'messages', label: '消息收发' },
  { key: 'products', label: '商品管理' },
  { key: 'rules', label: '关键词规则' }
]

async function loadHealth() {
  try {
    healthInfo.value = await xianyuApi.health()
  } catch (error) {
    healthInfo.value = { error: error.message }
  }
}

onMounted(loadHealth)
</script>

<template>
  <main class="page">
    <header class="header">
      <h1>Social SDK · Xianyu Console</h1>
      <p>Vue3 + Vite 控制台，支持多账号、商品、关键词自动回复规则配置。</p>
      <p v-if="healthInfo?.sqlitePath">SQLite: {{ healthInfo.sqlitePath }}</p>
      <p v-if="healthInfo?.accountStatusSummary">
        账号状态: ACTIVE={{ healthInfo.accountStatusSummary.ACTIVE || 0 }},
        PENDING_VERIFY={{ healthInfo.accountStatusSummary.PENDING_VERIFY || 0 }},
        FAILED={{ healthInfo.accountStatusSummary.FAILED || 0 }}
      </p>
      <p v-if="healthInfo?.error">启动检查失败: {{ healthInfo.error }}</p>
    </header>

    <nav class="tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-btn"
        :class="{ active: currentTab === tab.key }"
        @click="currentTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <AccountManager v-if="currentTab === 'accounts'" />
    <MessageManager v-else-if="currentTab === 'messages'" />
    <ProductManager v-else-if="currentTab === 'products'" />
    <RuleManager v-else />
  </main>
</template>
