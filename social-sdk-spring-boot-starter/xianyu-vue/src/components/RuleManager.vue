<script setup>
import { onMounted, reactive, ref } from 'vue'
import { xianyuApi } from '../api/xianyu'

const loading = ref(false)
const status = ref('')
const statusType = ref('ok')
const rules = ref([])

const filterAccountId = ref('')

const form = reactive({
  id: null,
  accountId: '',
  ruleName: '',
  keyword: '',
  matchType: 'CONTAINS',
  replyText: '',
  enabled: true,
  priority: 100
})

const matcher = reactive({
  accountId: '',
  text: '',
  result: null
})

function setStatus(message, type = 'ok') {
  status.value = message
  statusType.value = type
}

async function loadRules() {
  loading.value = true
  try {
    const accountId = filterAccountId.value ? Number(filterAccountId.value) : undefined
    rules.value = await xianyuApi.listRules(accountId)
    setStatus(`已加载 ${rules.value.length} 条规则`, 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function editRule(row) {
  form.id = row.id
  form.accountId = row.accountId ?? ''
  form.ruleName = row.ruleName || ''
  form.keyword = row.keyword || ''
  form.matchType = row.matchType || 'CONTAINS'
  form.replyText = row.replyText || ''
  form.enabled = !!row.enabled
  form.priority = row.priority ?? 100
}

function resetForm() {
  form.id = null
  form.accountId = ''
  form.ruleName = ''
  form.keyword = ''
  form.matchType = 'CONTAINS'
  form.replyText = ''
  form.enabled = true
  form.priority = 100
}

async function saveRule() {
  if (!form.keyword.trim() || !form.replyText.trim()) {
    setStatus('keyword 和 replyText 为必填', 'error')
    return
  }

  const payload = {
    accountId: form.accountId === '' ? null : Number(form.accountId),
    ruleName: form.ruleName,
    keyword: form.keyword,
    matchType: form.matchType,
    replyText: form.replyText,
    enabled: !!form.enabled,
    priority: Number(form.priority)
  }

  loading.value = true
  try {
    if (form.id) {
      await xianyuApi.updateRule(form.id, payload)
      setStatus('规则已更新', 'ok')
    } else {
      await xianyuApi.createRule(payload)
      setStatus('规则已创建', 'ok')
    }
    resetForm()
    await loadRules()
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function removeRule(id) {
  if (!window.confirm('确认删除规则吗？')) {
    return
  }
  loading.value = true
  try {
    await xianyuApi.deleteRule(id)
    await loadRules()
    setStatus('规则已删除', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function testMatch() {
  if (!matcher.text.trim()) {
    setStatus('请输入测试文本', 'error')
    return
  }
  loading.value = true
  try {
    matcher.result = await xianyuApi.matchRule({
      accountId: matcher.accountId ? Number(matcher.accountId) : null,
      text: matcher.text
    })
    setStatus('规则匹配完成', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

onMounted(loadRules)
</script>

<template>
  <section class="grid">
    <div class="panel">
      <h2>关键词回复规则</h2>
      <div class="row">
        <div class="field">
          <label>筛选账号ID</label>
          <input v-model="filterAccountId" placeholder="不填则查看全部" />
        </div>
        <div class="field" style="flex:0.5;min-width:130px;align-self:flex-end;">
          <button class="action secondary" :disabled="loading" @click="loadRules">查询</button>
        </div>
      </div>
    </div>

    <div class="panel">
      <h2>{{ form.id ? '编辑规则' : '新增规则' }}</h2>
      <div class="row">
        <div class="field">
          <label>账号ID(可空，全局规则)</label>
          <input v-model="form.accountId" />
        </div>
        <div class="field">
          <label>规则名称</label>
          <input v-model="form.ruleName" />
        </div>
        <div class="field">
          <label>匹配模式</label>
          <select v-model="form.matchType">
            <option value="CONTAINS">CONTAINS</option>
            <option value="EXACT">EXACT</option>
            <option value="REGEX">REGEX</option>
          </select>
        </div>
      </div>
      <div class="row">
        <div class="field">
          <label>关键词</label>
          <input v-model="form.keyword" />
        </div>
        <div class="field">
          <label>优先级(越小越优先)</label>
          <input v-model="form.priority" type="number" />
        </div>
        <div class="field" style="flex:0.6;min-width:130px;">
          <label>启用</label>
          <select v-model="form.enabled">
            <option :value="true">true</option>
            <option :value="false">false</option>
          </select>
        </div>
      </div>
      <div class="field">
        <label>回复内容</label>
        <textarea v-model="form.replyText"></textarea>
      </div>
      <div class="row" style="margin-top: 10px;">
        <button class="action" :disabled="loading" @click="saveRule">保存</button>
        <button class="action secondary" :disabled="loading" @click="resetForm">重置</button>
      </div>
      <p class="status" :class="statusType">{{ status }}</p>
    </div>

    <div class="panel">
      <h2>规则测试</h2>
      <div class="row">
        <div class="field">
          <label>账号ID（可空）</label>
          <input v-model="matcher.accountId" />
        </div>
      </div>
      <div class="field">
        <label>测试文本</label>
        <textarea v-model="matcher.text"></textarea>
      </div>
      <div class="row" style="margin-top: 10px;">
        <button class="action" :disabled="loading" @click="testMatch">执行匹配</button>
      </div>
      <div class="field" v-if="matcher.result" style="margin-top: 10px;">
        <label>匹配结果(JSON)</label>
        <textarea :value="JSON.stringify(matcher.result, null, 2)" readonly></textarea>
      </div>
    </div>

    <div class="panel">
      <h2>规则列表</h2>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>账号ID</th>
              <th>名称</th>
              <th>关键词</th>
              <th>匹配</th>
              <th>优先级</th>
              <th>启用</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in rules" :key="item.id">
              <td>{{ item.id }}</td>
              <td>{{ item.accountId ?? 'GLOBAL' }}</td>
              <td>{{ item.ruleName || '-' }}</td>
              <td>{{ item.keyword }}</td>
              <td>{{ item.matchType }}</td>
              <td>{{ item.priority }}</td>
              <td>{{ item.enabled ? 'Y' : 'N' }}</td>
              <td>
                <div class="row">
                  <button class="action secondary" @click="editRule(item)">编辑</button>
                  <button class="action warn" @click="removeRule(item.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="rules.length === 0">
              <td colspan="8">暂无规则</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </section>
</template>
