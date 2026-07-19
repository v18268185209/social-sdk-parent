<template>
  <div class="message-container">
    <!-- 顶部栏：选择账号 + 同步按钮 -->
    <el-card shadow="never" style="margin-bottom: 12px;">
      <div style="display: flex; justify-content: space-between; align-items: center;">
        <div style="display: flex; gap: 12px; align-items: center;">
          <span style="font-weight: 600;">消息管理</span>
          <el-select v-model="selectedAccount" placeholder="选择账号" @change="onAccountChange" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.displayName" :value="a.id" />
          </el-select>
          <el-button type="primary" size="small" :loading="syncing" @click="handleSyncNow">
            同步消息
          </el-button>
          <el-tag v-if="syncMsg" size="small" :type="syncMsg.includes('成功') ? 'success' : (syncMsg.includes('中') ? 'info' : 'error')" effect="plain">
            {{ syncMsg }}
          </el-tag>
          <el-tag size="small" type="success" effect="plain">实时监听运行中（30s 轮询）</el-tag>
          <el-button type="warning" size="small" @click="openCaptchaPage" v-if="accounts.length > 0">
            打开滑块验证
          </el-button>
        </div>
      </div>
    </el-card>

    <el-row :gutter="12" style="height: calc(100vh - 220px);">
      <!-- 左栏：会话列表 -->
      <el-col :span="7">
        <el-card shadow="never" class="session-list-card" body-style="padding: 0;">
          <div style="padding: 12px 16px; border-bottom: 1px solid #f0f0f0; font-weight: 600;">会话列表</div>
          <div v-if="sessions.length === 0" style="padding: 40px 16px; text-align: center; color: #999;">
            暂无会话，请先选择账号并点击同步
          </div>
          <div
            v-for="s in sessions" :key="s.sessionId"
            class="session-item"
            :class="{ active: s.sessionId === selectedSession }"
            @click="selectSession(s)"
          >
            <div class="session-header">
              <strong>{{ s.counterpartyName }}</strong>
              <span class="time">{{ formatTime(s.lastTime) }}</span>
            </div>
            <div class="session-last-msg" :class="{ mine: s.direction === 'OUTGOING' }">
              {{ s.lastContent || '无消息' }}
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右栏：聊天窗口 -->
      <el-col :span="17">
        <el-card shadow="never" class="chat-card">
          <div style="padding: 12px 16px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center;">
            <span style="font-weight: 600;">{{ selectedSessionData.counterpartyName || (selectedSession || '未选择会话') }}</span>
          </div>

          <div ref="chatBoxRef" class="chat-messages">
            <el-empty v-if="!selectedSession" description="请先选择一个会话" />
            <template v-else>
              <div
                v-for="msg in messages" :key="msg.id"
                class="chat-bubble"
                :class="{ outgoing: msg.direction === 'OUTGOING', incoming: msg.direction === 'INCOMING' }"
              >
                <div class="bubble-meta">
                  <span class="sender">{{ msg.senderName || msg.senderId || (msg.direction === 'OUTGOING' ? '我' : '') }}</span>
                  <span class="time" v-if="msg.messageTime">{{ formatTime(msg.messageTime.toString()) }}</span>
                </div>
                <div class="bubble-body">{{ msg.content || '[空消息]' }}</div>
                <el-tag v-if="msg.autoReply" size="small" type="warning" effect="plain" style="margin-top: 4px;">自动回复</el-tag>
              </div>
            </template>
          </div>

          <div class="chat-input-bar">
            <el-input
              v-model="newMessage"
              type="textarea"
              :rows="2"
              placeholder="输入消息，按 Enter 发送（Shift+Enter 换行）"
              @keydown.enter.prevent="onEnterKey"
            />
            <el-button type="primary" :disabled="!newMessage.trim()" @click="handleSend">发送</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/request'

const accounts = ref([])
const sessions = ref([])
const messages = ref([])
const selectedAccount = ref(null)
const selectedSession = ref('')
const newMessage = ref('')
const syncing = ref(false)
const chatBoxRef = ref(null)
const syncMsg = ref('')
const captchaOpened = ref(false)

const selectedSessionData = ref({ counterpartyName: '', lastContent: '' })

// ==================== 工具函数 ====================

function formatTime(t) {
  if (!t) return ''
  const str = String(t)
  if (str.includes('T')) return str.replace('T', ' ').substring(0, 16)
  return str.substring(0, 16)
}

async function scrollToBottom() {
  await nextTick()
  if (chatBoxRef.value) {
    chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  }
}

// ==================== 数据加载 ====================

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) {}
}

async function loadSessions() {
  if (!selectedAccount.value) return
  sessions.value = []
  selectedSession.value = ''
  selectedSessionData.value = { counterpartyName: '', lastContent: '' }
  try {
    // 调新 /messages/list 按 accountId 拉本地已同步消息，前端按 sessionId 二次分组渲染会话卡片
    const res = await api.get('/messages/list', { params: { accountId: selectedAccount.value, limit: 200 } })
    if (res.success && Array.isArray(res.data)) {
      // 按 sessionId 分组 + 拿每会话最新消息作为 session 摘要
      // 后端返回按 messageTime 倒序，所以遍历时第一次遇到的 sessionId 就是该会话最新消息
      const sessionMap = new Map()
      for (const msg of res.data) {
        const sid = msg.sessionId
        if (!sessionMap.has(sid)) {
          sessionMap.set(sid, {
            sessionId: sid,
            counterpartyName: msg.direction === 'OUTGOING' ? (msg.senderName || '对方') : (msg.senderName || msg.senderId || '对方'),
            lastContent: msg.content || '',
            lastTime: msg.messageTime || '',
            direction: msg.direction,
            unread: 0
          })
        }
      }
      sessions.value = Array.from(sessionMap.values())
      if (sessions.value.length > 0 && !selectedSession.value) {
        selectSession(sessions.value[0])
      }
    } else {
      sessions.value = []
    }
  } catch (e) {
    sessions.value = []
  }
}

async function loadHistory() {
  if (!selectedAccount.value || !selectedSession.value) return
  try {
    const res = await api.get('/messages/history', {
      params: { accountId: selectedAccount.value, sessionId: selectedSession.value, limit: 50 }
    })
    if (res.success) {
      messages.value = Array.isArray(res.data) ? res.data : []
      setTimeout(() => scrollToBottom(), 300)
    }
  } catch (e) {
    messages.value = []
  }
}

// ==================== 事件处理 ====================

function onAccountChange() {
  loadSessions()
}

function selectSession(s) {
  selectedSession.value = s.sessionId
  selectedSessionData.value = s
  loadHistory()
}

async function handleSyncNow() {
  if (!selectedAccount.value) {
    ElMessage.warning('请先选择账号')
    return
  }
  syncing.value = true
  syncMsg.value = '同步中...'
  try {
    // 调单账号同步接口（比 syncNow 全账号更轻量）
    const res = await api.post('/messages/sync', null, { params: { accountId: selectedAccount.value } })
    if (res.success) {
      syncMsg.value = '同步成功'
      ElMessage.success('同步完成')
      await loadSessions()
      if (selectedSession.value) await loadHistory()
    } else {
      syncMsg.value = '同步失败：' + (res.message || '请重试')
      ElMessage.error(syncMsg.value)
    }
  } catch (e) {
    syncMsg.value = '错误: ' + (e?.response?.data?.message || e?.message || '请重试')
    ElMessage.error('同步失败：' + syncMsg.value)
  } finally {
    syncing.value = false
    // 3 秒后清同步状态提示
    setTimeout(() => { syncMsg.value = '' }, 3000)
  }
}

const openCaptchaPage = () => {
  // 打开新窗口到闲鱼主页，让用户手动完成滑块验证
  const url = window.location.origin.replace(':3000', ':9333') + '/json'
  window.open(url, '_blank')
  captchaOpened.value = true
  ElMessage.info('请在浏览器中完成滑块验证')
}

function onEnterKey(e) {
  if (e.shiftKey) return // Shift+Enter 不换行（textarea 默认行为）
  handleSend()
}

async function handleSend() {
  if (!selectedAccount.value || !selectedSession.value || !newMessage.value.trim()) return
  try {
    await api.post('/messages/send', {
      accountId: selectedAccount.value,
      sessionId: selectedSession.value,
      content: newMessage.value.trim()
    })
    ElMessage.success('已发送')
    newMessage.value = ''
    // 本地刷新一下历史，看能不能拿到
    await loadHistory()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '发送失败')
  }
}

onMounted(async () => {
  await loadAccounts()
})
</script>

<style scoped>
.message-container {
  height: calc(100vh - 80px);
  overflow: hidden;
}

.session-list-card {
  height: calc(100vh - 220px);
  overflow-y: auto;
}

.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
  transition: background 0.15s;
}
.session-item:hover {
  background: #f5f7fa;
}
.session-item.active {
  background: #ecf5ff;
}
.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.time {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}
.session-last-msg {
  font-size: 13px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}
.session-last-msg.mine {
  color: #999;
  text-align: right;
}

.chat-card {
  height: calc(100vh - 220px);
  display: flex;
  flex-direction: column;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.chat-bubble {
  max-width: 70%;
  margin-bottom: 12px;
}
.chat-bubble.outgoing {
  margin-left: auto;
}
.chat-bubble.incoming {
  margin-right: auto;
}
.bubble-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}
.bubble-body {
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.5;
  word-break: break-word;
}
.chat-bubble.incoming .bubble-body {
  background: #f0f0f0;
  color: #333;
}
.chat-bubble.outgoing .bubble-body {
  background: #409eff;
  color: #fff;
  margin-left: auto;
}

.chat-input-bar {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding-top: 12px;
  border-top: 1px solid #eee;
}
.chat-input-bar .el-button {
  flex-shrink: 0;
}
</style>
