<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import { xianyuApi } from '../api/xianyu'

const loading = ref(false)
const status = ref('')
const statusType = ref('ok')
const sendResult = ref(null)
const timelineResult = ref(null)
const takeoverStatus = ref(null)
const chatEvents = ref([])
let eventSource = null

const takeoverForm = reactive({
  accountId: '',
  autoReply: true
})

const sendForm = reactive({
  accountId: '',
  toUserId: '',
  itemId: '',
  cid: '',
  text: '',
  imageUrl: '',
  imagePath: '',
  imageWidth: 800,
  imageHeight: 600,
  useRealtime: true
})

const timelineForm = reactive({
  accountId: '',
  limit: 20
})

function setStatus(message, type = 'ok') {
  status.value = message
  statusType.value = type
}

function appendChatEvent(event) {
  chatEvents.value.unshift(event)
  if (chatEvents.value.length > 200) {
    chatEvents.value = chatEvents.value.slice(0, 200)
  }
  fillReplyTarget(event, false)
}

function isReplyableEvent(event) {
  return event && (event.type === 'message' || event.type === 'conversation_hint') && !event.outgoing
}

function fillReplyTarget(event, overwriteText = true) {
  if (!isReplyableEvent(event)) {
    return
  }
  sendForm.accountId = String(event.accountId || sendForm.accountId || '')
  sendForm.toUserId = event.senderUserId || sendForm.toUserId
  sendForm.cid = event.chatId || sendForm.cid
  sendForm.itemId = event.itemId || sendForm.itemId
  if (overwriteText) {
    sendForm.text = ''
    setStatus('已回填回复对象，可直接输入内容发送', 'ok')
  }
}

function stopStreamOnly() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

function openStream(accountId) {
  stopStreamOnly()
  eventSource = new EventSource(`/api/social-sdk/xianyu/chats/stream/${accountId}`)
  const handler = (event) => {
    try {
      appendChatEvent(JSON.parse(event.data))
    } catch (error) {
      appendChatEvent({ type: 'parse_error', message: error.message, raw: event.data, timestamp: new Date().toISOString() })
    }
  }
  eventSource.addEventListener('message', handler)
  eventSource.addEventListener('conversation_hint', handler)
  eventSource.addEventListener('system', handler)
  eventSource.addEventListener('error', handler)
  eventSource.addEventListener('auto_reply', handler)
  eventSource.addEventListener('auto_reply_error', handler)
  eventSource.addEventListener('status', handler)
  eventSource.onerror = () => {
    setStatus('聊天接管 SSE 连接异常，后端重连中或接管已停止', 'error')
  }
}

async function startTakeover() {
  if (!takeoverForm.accountId) {
    setStatus('请填写要接管的账号ID', 'error')
    return
  }
  loading.value = true
  try {
    const accountId = Number(takeoverForm.accountId)
    takeoverStatus.value = await xianyuApi.startChatTakeover({
      accountId,
      autoReply: !!takeoverForm.autoReply
    })
    sendForm.accountId = String(accountId)
    timelineForm.accountId = String(accountId)
    await loadChatEvents(false)
    openStream(accountId)
    setStatus('聊天已接管，正在监听闲鱼实时消息', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function stopTakeover() {
  if (!takeoverForm.accountId) {
    setStatus('请填写账号ID', 'error')
    return
  }
  loading.value = true
  try {
    takeoverStatus.value = await xianyuApi.stopChatTakeover(Number(takeoverForm.accountId))
    stopStreamOnly()
    setStatus('聊天接管已停止', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function refreshTakeoverStatus() {
  loading.value = true
  try {
    takeoverStatus.value = await xianyuApi.getChatTakeoverStatus(takeoverForm.accountId ? Number(takeoverForm.accountId) : undefined)
    setStatus('接管状态已刷新', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loadChatEvents(showStatus = true) {
  if (!takeoverForm.accountId) {
    if (showStatus) setStatus('请填写账号ID', 'error')
    return
  }
  try {
    const events = await xianyuApi.listChatEvents(Number(takeoverForm.accountId))
    chatEvents.value = [...events].reverse()
    if (showStatus) setStatus(`已加载 ${events.length} 条聊天事件`, 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  }
}

async function sendMessage() {
  if (!sendForm.accountId || !sendForm.toUserId.trim()) {
    setStatus('accountId 和 toUserId 为必填项', 'error')
    return
  }
  if (!sendForm.text.trim() && !sendForm.imageUrl.trim() && !sendForm.imagePath.trim()) {
    setStatus('文本、图片URL、本地图片路径至少填写一个', 'error')
    return
  }

  loading.value = true
  try {
    sendResult.value = await xianyuApi.sendMessage({
      accountId: Number(sendForm.accountId),
      toUserId: sendForm.toUserId,
      itemId: sendForm.itemId,
      cid: sendForm.cid,
      text: sendForm.text,
      imageUrl: sendForm.imageUrl,
      imagePath: sendForm.imagePath,
      imageWidth: Number(sendForm.imageWidth) || 800,
      imageHeight: Number(sendForm.imageHeight) || 600,
      useRealtime: !!sendForm.useRealtime
    })
    setStatus('消息发送完成', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

async function loadTimeline() {
  if (!timelineForm.accountId) {
    setStatus('请填写账号ID', 'error')
    return
  }
  loading.value = true
  try {
    timelineResult.value = await xianyuApi.getTimeline(Number(timelineForm.accountId), Number(timelineForm.limit) || 20)
    setStatus('时间线拉取完成', 'ok')
  } catch (error) {
    setStatus(error.message, 'error')
  } finally {
    loading.value = false
  }
}

function eventClass(event) {
  if (event.type === 'error' || event.type === 'auto_reply_error') return 'danger'
  if (event.type === 'auto_reply') return 'ok'
  if (event.type === 'system') return 'warn'
  return event.outgoing ? 'ok' : 'warn'
}

onBeforeUnmount(stopStreamOnly)
</script>

<template>
  <section class="grid">
    <div class="panel">
      <h2>闲鱼聊天接管</h2>
      <div class="row">
        <div class="field">
          <label>账号ID</label>
          <input v-model="takeoverForm.accountId" placeholder="要接管的账号ID" />
        </div>
        <div class="field compact">
          <label>自动匹配规则回复</label>
          <select v-model="takeoverForm.autoReply">
            <option :value="true">true</option>
            <option :value="false">false</option>
          </select>
        </div>
        <div class="field compact" style="align-self:flex-end;">
          <button class="action" :disabled="loading" @click="startTakeover">开始接管</button>
        </div>
        <div class="field compact" style="align-self:flex-end;">
          <button class="action warn" :disabled="loading" @click="stopTakeover">停止接管</button>
        </div>
        <div class="field compact" style="align-self:flex-end;">
          <button class="action secondary" :disabled="loading" @click="refreshTakeoverStatus">刷新状态</button>
        </div>
      </div>
      <p class="status" :class="statusType">{{ status }}</p>
      <div class="field" v-if="takeoverStatus" style="margin-top: 10px;">
        <label>接管状态(JSON)</label>
        <textarea :value="JSON.stringify(takeoverStatus, null, 2)" readonly></textarea>
      </div>
    </div>

    <div class="panel">
      <h2>实时聊天事件</h2>
      <div class="row" style="margin-bottom: 10px;">
        <button class="action secondary" :disabled="loading" @click="loadChatEvents(true)">拉取最近事件</button>
        <button class="action secondary" :disabled="!takeoverForm.accountId" @click="openStream(Number(takeoverForm.accountId))">重连SSE</button>
      </div>
      <div class="chat-list">
        <div
          v-for="(event, index) in chatEvents"
          :key="index"
          class="chat-item"
          :class="{ clickable: isReplyableEvent(event) }"
          @click="fillReplyTarget(event)"
        >
          <div class="row">
            <span class="tag" :class="eventClass(event)">{{ event.type }}</span>
            <strong>{{ event.outgoing ? '我方' : (event.senderNick || event.senderUserId || '系统') }}</strong>
            <span>{{ event.timestamp || '-' }}</span>
            <span v-if="event.chatId">CID: {{ event.chatId }}</span>
            <span v-if="isReplyableEvent(event)" class="status">点击回复</span>
          </div>
          <p v-if="event.content">{{ event.content }}</p>
          <p v-else-if="event.imageUrl || event.imagePath">图片消息：{{ event.imageUrl || event.imagePath }}</p>
          <pre v-else>{{ JSON.stringify(event, null, 2) }}</pre>
        </div>
        <p v-if="chatEvents.length === 0" class="status">暂无聊天事件。开始接管后，新消息会实时出现在这里。</p>
      </div>
    </div>

    <div class="panel">
      <h2>发送消息（文本 / 图片）</h2>
      <div class="row">
        <div class="field">
          <label>账号ID</label>
          <input v-model="sendForm.accountId" placeholder="已登录账号ID" />
        </div>
        <div class="field">
          <label>接收方 UserId</label>
          <input v-model="sendForm.toUserId" placeholder="必填；点击上方消息会自动回填" />
        </div>
        <div class="field">
          <label>商品 ItemId</label>
          <input v-model="sendForm.itemId" placeholder="可选" />
        </div>
        <div class="field">
          <label>会话 CID</label>
          <input v-model="sendForm.cid" placeholder="可选，不填则自动创建/解析" />
        </div>
      </div>
      <div class="field" style="margin-top: 10px;">
        <label>文本内容</label>
        <textarea v-model="sendForm.text" placeholder="要发送的文本"></textarea>
      </div>
      <div class="row" style="margin-top: 10px;">
        <div class="field">
          <label>图片 URL</label>
          <input v-model="sendForm.imageUrl" placeholder="https://..." />
        </div>
        <div class="field">
          <label>本地图片路径</label>
          <input v-model="sendForm.imagePath" placeholder="/tmp/demo.jpg" />
        </div>
        <div class="field compact">
          <label>宽</label>
          <input v-model="sendForm.imageWidth" type="number" />
        </div>
        <div class="field compact">
          <label>高</label>
          <input v-model="sendForm.imageHeight" type="number" />
        </div>
        <div class="field compact">
          <label>优先实时通道</label>
          <select v-model="sendForm.useRealtime">
            <option :value="true">true</option>
            <option :value="false">false</option>
          </select>
        </div>
      </div>
      <div class="row" style="margin-top: 10px;">
        <button class="action" :disabled="loading" @click="sendMessage">发送</button>
      </div>
      <div class="field" v-if="sendResult" style="margin-top: 10px;">
        <label>发送结果(JSON)</label>
        <textarea :value="JSON.stringify(sendResult, null, 2)" readonly></textarea>
      </div>
    </div>

    <div class="panel">
      <h2>时间线 / 最近会话拉取</h2>
      <div class="row">
        <div class="field">
          <label>账号ID</label>
          <input v-model="timelineForm.accountId" />
        </div>
        <div class="field compact">
          <label>数量</label>
          <input v-model="timelineForm.limit" type="number" />
        </div>
        <div class="field compact" style="align-self:flex-end;">
          <button class="action secondary" :disabled="loading" @click="loadTimeline">拉取</button>
        </div>
      </div>
      <div class="field" v-if="timelineResult" style="margin-top: 10px;">
        <label>时间线结果(JSON)</label>
        <textarea :value="JSON.stringify(timelineResult, null, 2)" readonly></textarea>
      </div>
    </div>
  </section>
</template>
