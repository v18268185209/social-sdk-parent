<template>
  <div class="page-root">
  <!-- 顶部工具栏：账号、同步按钮始终可见 -->
  <div class="top-toolbar">
    <el-select v-model="selectedAccount" placeholder="选择账号" @change="onAccountChange" style="width: 180px;" size="default">
      <el-option v-for="a in accounts" :key="a.id" :label="a.accountName || a.displayName" :value="a.id" />
    </el-select>
    <el-button type="primary" :loading="syncing" @click="handleSyncNow">
      {{ syncing ? '同步中...' : '同步消息' }}
    </el-button>
    <el-tag v-if="selectedAccount" type="success" effect="plain" class="countdown-tag">
      <el-icon><Loading /></el-icon>
      {{ countdown }}s
    </el-tag>
  </div>

  <div class="message-container">
    <el-row :gutter="0" class="chat-row">
      <!-- 左栏：会话列表 -->
      <el-col :span="6">
        <div class="session-list">
          <div class="session-list-header">
            <span class="title">会话列表</span>
            <el-badge :value="unreadSet.size" :hidden="unreadSet.size === 0" />
          </div>
          <div class="session-search">
            <el-input v-model="searchText" placeholder="搜索会话" size="small" clearable :prefix-icon="Search" />
          </div>
          <div class="session-list-body">
            <div v-if="filteredSessions.length === 0" class="empty-tip">
              暂无会话
            </div>
            <div
              v-for="s in filteredSessions" :key="s.sessionId"
              class="session-item"
              :class="{ active: s.sessionId === selectedSession, unread: unreadSet.has(s.sessionId) }"
              @click="selectSession(s)"
            >
              <el-badge :is-dot="unreadSet.has(s.sessionId)">
                <el-avatar :size="44" :src="s.counterpartyAvatar || ''">
                  {{ avatarText(s.counterpartyName || s.counterpartyId) }}
                </el-avatar>
              </el-badge>
              <div class="session-info">
                <div class="session-row-1">
                  <span class="name">{{ s.counterpartyName || s.counterpartyId || '未知用户' }}</span>
                  <span class="time">{{ formatTime(s.lastTime) }}</span>
                </div>
                <div class="session-row-2">
                  <span class="last-msg">
                    <template v-if="s.contentType === 'IMAGE'">[图片]</template>
                    <template v-else-if="s.contentType === 'VIDEO'">[视频]</template>
                    <template v-else-if="s.contentType === 'CARD' || s.contentType === 'JSON'">[卡片]</template>
                    <template v-else>{{ s.lastContent || '无消息' }}</template>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右栏：聊天窗口 -->
      <el-col :span="18">
        <div class="chat-panel">
          <!-- 聊天头部 -->
          <div class="chat-header" v-if="selectedSession">
            <div class="chat-title">
              <span class="name">{{ selectedSessionData.counterpartyName || '未选择会话' }}</span>
              <span class="status" v-if="selectedSessionData.lastTime">最后消息 {{ formatTime(selectedSessionData.lastTime) }}</span>
            </div>
          </div>
          <div class="chat-header empty" v-else>
            <span>请从左侧选择会话</span>
          </div>

          <!-- 消息列表 -->
          <div ref="chatBoxRef" class="chat-messages">
            <div v-if="!selectedSession" class="chat-empty">
              <el-empty description="请先选择一个会话开始聊天" />
            </div>
            <div v-else class="messages-list">
              <div
                v-for="msg in messages" :key="msg.id"
                class="msg-wrapper"
                :class="{ mine: msg.direction === 'OUTGOING' }"
              >
                <div class="msg-time" v-if="msg.messageTime">
                  <span>{{ formatTime(msg.messageTime.toString()) }}</span>
                </div>
                <div class="msg-row">
                  <el-avatar v-if="msg.direction === 'INCOMING'" :size="32">
                    {{ avatarText(selectedSessionData.counterpartyName || '') }}
                  </el-avatar>
                  <div class="bubble">
                    <div v-if="isJsonCard(msg.content)" class="bubble-card">
                      <div class="card-title">{{ parseJsonCard(msg.content).title }}</div>
                      <div class="card-subtitle">{{ parseJsonCard(msg.content).subtitle }}</div>
                      <img v-if="parseJsonCard(msg.content).leftImg" :src="parseJsonCard(msg.content).leftImg" class="card-left-img" />
                      <el-button
                        v-if="parseJsonCard(msg.content).buttonText"
                        size="small"
                        :color="parseJsonCard(msg.content).buttonBgColor || '#409eff'"
                        class="card-btn"
                        @click="parseJsonCard(msg.content).targetUrl && window.open(parseJsonCard(msg.content).targetUrl, '_blank')"
                      >{{ parseJsonCard(msg.content).buttonText }}</el-button>
                      <div v-if="parseJsonCard(msg.content).subTitle" class="card-desc">{{ parseJsonCard(msg.content).subTitle }}</div>
                    </div>
                    <div class="bubble-text" v-else-if="msg.msgType === 'TEXT'">{{ msg.content || '[空消息]' }}</div>
                    <img v-else-if="msg.msgType === 'IMAGE'" :src="msg.content" alt="图片" class="bubble-img" @click="openMedia(msg.content)" />
                    <video v-else-if="msg.msgType === 'VIDEO'" :src="msg.content" controls class="bubble-img"></video>
                    <div class="bubble-text" v-else>{{ msg.content || '[空消息]' }}</div>
                    <el-tag v-if="msg.autoReply" size="small" type="warning" effect="plain" class="auto-reply-tag">自动回复</el-tag>
                  </div>
                  <el-avatar v-if="msg.direction === 'OUTGOING'" :size="32">我</el-avatar>
                </div>
              </div>
            </div>
          </div>

          <!-- 输入框 -->
          <div class="chat-input">
            <el-input
              v-model="newMessage"
              type="textarea"
              :rows="3"
              placeholder="输入消息，按 Enter 发送（Shift+Enter 换行）"
              @keydown.enter.prevent="onEnterKey"
              :disabled="!selectedSession"
            />
            <div class="input-footer">
              <span class="tip">Enter 发送 | Shift+Enter 换行</span>
              <el-button type="primary" :disabled="!newMessage.trim() || !selectedSession" @click="handleSend">发送</el-button>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch, onUnmounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Search } from '@element-plus/icons-vue'
import api from '@/api/request'

const accounts = ref([])
const sessions = ref([])
const messages = ref([])
const selectedAccount = ref(null)
const selectedSession = ref('')
const newMessage = ref('')
const syncing = ref(false)
const chatBoxRef = ref(null)
const searchText = ref('')

// 搜索过滤会话
const filteredSessions = computed(() => {
  if (!searchText.value) return sessions.value
  const kw = searchText.value.toLowerCase()
  return sessions.value.filter(s =>
    (s.counterpartyName || '').toLowerCase().includes(kw) ||
    (s.counterpartyId || '').toLowerCase().includes(kw) ||
    (s.lastContent || '').toLowerCase().includes(kw)
  )
})

function openMedia(url) {
  if (url) window.open(url, '_blank')
}

function isJsonCard(content) {
  if (!content || typeof content !== 'string') return false
  try {
    const obj = JSON.parse(content)
    return obj && (obj.dxCard || obj.contentType)
  } catch {
    return false
  }
}

function parseJsonCard(content) {
  const fallback = { title: '', subtitle: '', buttonText: '', buttonBgColor: '', targetUrl: '', leftImg: '', subTitle: '' }
  if (!content) return fallback
  try {
    const obj = typeof content === 'string' ? JSON.parse(content) : content
    const dxCard = obj?.dxCard || obj?.dxcard
    const main = dxCard?.item?.main || dxCard?.item || {}
    const exContent = main?.exContent || main?.excontent || {}
    const button = exContent?.button || {}
    return {
      title: exContent?.title || main?.title || '',
      subtitle: exContent?.subtitle || main?.subtitle || '',
      subTitle: exContent?.subTitle || main?.subTitle || '',
      buttonText: button?.text || '',
      buttonBgColor: button?.bgColor || '',
      targetUrl: button?.targetUrl || main?.targetUrl || dxCard?.item?.main?.targetUrl || '',
      leftImg: button?.leftImg || ''
    }
  } catch {
    return fallback
  }
}
const syncMsg = ref('')

const selectedSessionData = ref({ counterpartyName: '', lastContent: '' })

// 会话最后消息时间缓存（用于检测新消息到达 -> 未读标红）
const sessionLastTimeCache = ref({})
// 未读会话 id 集合
const unreadSet = ref(new Set())

// ==================== 工具函数 ====================

function formatTime(t) {
  if (!t) return ''
  const str = String(t)
  if (str.includes('T')) return str.replace('T', ' ').substring(0, 16)
  return str.substring(0, 16)
}

function avatarText(name) {
  const str = String(name || '').trim()
  return str ? str.slice(0, 1) : '?'
}

function scrollToBottom() {
  nextTick(() => {
    if (chatBoxRef.value) {
      chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
    }
  })
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
  const prevSelected = selectedSession.value
  try {
    // 会话摘要由后端按 sessionId 聚合，并且优先返回对方(INCOMING)用户信息；
    // 不能在前端用最新消息 senderName 推断，否则最后一条是自己发出时会展示自己的账号名。
    const res = await api.get('/messages/sessions', { params: { accountId: selectedAccount.value } })
    if (res.success && Array.isArray(res.data)) {
      let list = res.data
      // 按最后消息时间倒序（最新的在前面）
      list.sort((a, b) => {
        const ta = a.lastTime || ''
        const tb = b.lastTime || ''
        return tb.localeCompare(ta)
      })
      // 检测新消息 -> 未读标红（当前选中会话不标红）
      const newUnread = new Set(unreadSet.value)
      for (const s of list) {
        const sid = s.sessionId
        const newTime = s.lastTime || ''
        const cached = sessionLastTimeCache.value[sid]
        if (cached !== undefined && newTime > cached && sid !== selectedSession.value) {
          newUnread.add(sid)
        }
        sessionLastTimeCache.value[sid] = newTime
      }
      unreadSet.value = newUnread
      sessions.value = list
      // 保持之前选中的会话，或选第一个
      if (prevSelected && list.find(s => s.sessionId === prevSelected)) {
        const found = list.find(s => s.sessionId === prevSelected)
        selectedSession.value = prevSelected
        selectedSessionData.value = found
      } else if (list.length > 0) {
        selectSession(list[0])
      } else {
        selectedSession.value = ''
        selectedSessionData.value = { counterpartyName: '', lastContent: '' }
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
      // 后端 selectBySession 已按 message_time ASC 返回（最老在前、最新在后），
      // 不再 reverse，直接渲染：最新消息在底部，与闲鱼客户端顺序一致。
      const list = Array.isArray(res.data) ? [...res.data] : []
      // 兜底：再按 messageTime 升序排一次，避免同时间戳乱序
      list.sort((a, b) => {
        const ta = a.messageTime ? String(a.messageTime) : ''
        const tb = b.messageTime ? String(b.messageTime) : ''
        if (ta === tb) return (a.id || 0) - (b.id || 0)
        return ta.localeCompare(tb)
      })
      messages.value = list
      setTimeout(() => scrollToBottom(), 100)
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
  // 清除该会话未读标记
  if (unreadSet.value.has(s.sessionId)) {
    const n = new Set(unreadSet.value)
    n.delete(s.sessionId)
    unreadSet.value = n
  }
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

// ==================== 实时监听倒计时 ====================
const POLL_INTERVAL = 30 // 秒
const countdown = ref(POLL_INTERVAL)
let pollTimer = null
let countdownTimer = null

function startPolling() {
  stopPolling()
  countdown.value = POLL_INTERVAL
  // 每秒减一显示倒计时
  countdownTimer = setInterval(() => {
    countdown.value = countdown.value > 0 ? countdown.value - 1 : POLL_INTERVAL
  }, 1000)
  // 每 30s 触发真实消息同步（MTOP 拉远端最新消息入库），再刷新列表 + 当前聊天
  pollTimer = setInterval(async () => {
    if (!selectedAccount.value) return
    try {
      // 真正触发同步：走 MTOP 拉最新消息写库
      await api.post('/messages/sync', null, { params: { accountId: selectedAccount.value } })
    } catch (e) {
      // 同步失败（如风控）不阻塞后续刷新
    }
    await loadSessions()
    if (selectedSession.value) await loadHistory()
    countdown.value = POLL_INTERVAL
  }, POLL_INTERVAL * 1000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
}

// 选中账号后启动轮询
watch(selectedAccount, (val) => {
  if (val) {
    loadSessions()
    startPolling()
  } else {
    stopPolling()
  }
})

onMounted(async () => {
  await loadAccounts()
  // 自动选中第一个账号 → 触发 watch(selectedAccount) → 启动轮询 + 倒计时
  if (accounts.value.length > 0 && !selectedAccount.value) {
    selectedAccount.value = accounts.value[0].id
  }
})

// 暴露给父级 layout 用：确保切回消息页时倒计时已启动
watch(() => selectedSession.value, () => { if (selectedSession.value) loadHistory() })

onUnmounted(() => {
  stopPolling()
})
</script>

<style scoped>
/* ==================== 单屏根容器：占满 el-main，自身不滚动，内部区域滚动 ==================== */
.page-root {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
}

/* ==================== 顶部工具栏 ==================== */
.top-toolbar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  background: #fcfcfc;
  border-bottom: 1px solid #e5e5e5;
}
.countdown-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* ==================== 主区域撑满剩余高度 ==================== */
.message-container {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.chat-row { height: 100%; flex: 1; }
.chat-row .el-col { height: 100%; }

/* ==================== 会话列表 ==================== */
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f7f7f7;
  border-right: 1px solid #e5e5e5;
}
.session-list-header {
  padding: 16px;
  font-weight: 600;
  font-size: 16px;
  border-bottom: 1px solid #e5e5e5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.session-search {
  padding: 8px 12px;
  background: #f7f7f7;
  border-bottom: 1px solid #e5e5e5;
}
.session-list-body {
  flex: 1;
  overflow-y: auto;
}
.empty-tip {
  text-align: center;
  color: #999;
  padding: 40px 0;
  font-size: 13px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #ececec;
  transition: background 0.1s;
}
.session-item:hover { background: #ebebeb; }
.session-item.active { background: #e0e0e0; }
.unread .name { color: #e74c3c; font-weight: 600; }
.unread .last-msg { color: #e74c3c; }
.session-info {
  flex: 1;
  min-width: 0;
}
.session-row-1 {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.name {
  font-size: 14px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.session-row-1 .time {
  font-size: 11px;
  color: #b0b0b0;
  flex-shrink: 0;
  margin-left: 8px;
}
.session-row-2 .last-msg {
  font-size: 12px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 底部账号选择 */
.session-list-footer {
  padding: 12px;
  border-top: 1px solid #e5e5e5;
  background: #f3f3f3;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.footer-btns {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* ==================== 聊天面板 ==================== */
.chat-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}
.chat-header {
  padding: 14px 20px;
  background: #fcfcfc;
  border-bottom: 1px solid #e5e5e5;
}
.chat-header.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 14px;
  flex: 1;
}
.chat-title .name {
  font-weight: 600;
  font-size: 15px;
}
.chat-title .status {
  font-size: 12px;
  color: #999;
  margin-left: 12px;
}

/* 消息列表 */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.chat-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.messages-list {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}
.msg-wrapper {
  margin-bottom: 16px;
}
.msg-time {
  text-align: center;
  font-size: 11px;
  color: #b0b0b0;
  margin-bottom: 8px;
}
.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.msg-wrapper.mine .msg-row {
  flex-direction: row-reverse;
}
.bubble {
  max-width: 65%;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.msg-wrapper:not(.mine) .bubble {
  background: #fff;
  color: #333;
  border-top-left-radius: 0;
}
.msg-wrapper.mine .bubble {
  background: #95ec69;
  color: #333;
  border-top-right-radius: 0;
}
.bubble-text {
  white-space: pre-wrap;
}
.bubble-img {
  max-width: 140px;
  max-height: 140px;
  border-radius: 4px;
  cursor: zoom-in;
  display: block;
}
.auto-reply-tag {
  margin-top: 4px;
}
.bubble-card {
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e5e5e5;
  min-width: 200px;
  max-width: 280px;
}
.card-title {
  font-weight: 600;
  font-size: 15px;
  color: #333;
  margin-bottom: 4px;
}
.card-subtitle {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}
.card-desc {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}
.card-left-img {
  width: 24px;
  height: 24px;
  margin-bottom: 8px;
  display: block;
}
.card-btn {
  color: #fff !important;
  border: none !important;
  font-weight: 500;
}

/* 输入框 */
.chat-input {
  padding: 12px 16px;
  border-top: 1px solid #e5e5e5;
  background: #fcfcfc;
}
.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.input-footer .tip {
  font-size: 11px;
  color: #b0b0b0;
}
</style>
