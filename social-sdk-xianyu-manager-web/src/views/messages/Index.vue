<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>消息管理</span>
          <el-button type="primary" @click="showSendDialog = true">
            <el-icon><Promotion /></el-icon> 发送消息
          </el-button>
        </div>
      </template>

      <el-form inline style="margin-bottom: 16px;">
        <el-form-item label="账号">
          <el-select v-model="selectedAccount" placeholder="选择账号" @change="loadSessions" style="width: 200px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话">
          <el-select v-model="selectedSession" placeholder="选择会话" @change="loadHistory" style="width: 200px;" clearable>
            <el-option v-for="s in sessions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-timeline v-if="selectedSession">
        <el-timeline-item
          v-for="msg in messages"
          :key="msg.id"
          :timestamp="formatTime(msg.messageTime)"
          :type="msg.direction === 'INCOMING' ? 'primary' : 'success'"
          placement="top"
        >
          <el-card>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <strong>{{ msg.senderName || msg.senderId }}</strong>
              <el-tag v-if="msg.autoReply" size="small" type="warning">自动回复</el-tag>
            </div>
            <p style="margin-top: 8px;">{{ msg.content }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="请选择账号和会话查看消息历史" />
    </el-card>

    <!-- 发送消息对话框 -->
    <el-dialog v-model="showSendDialog" title="发送消息" width="500px">
      <el-form :model="sendForm" label-width="80px">
        <el-form-item label="账号">
          <el-select v-model="sendForm.accountId" placeholder="选择账号" style="width: 100%;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="会话">
          <el-input v-model="sendForm.sessionId" placeholder="会话 ID" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="sendForm.content" type="textarea" :rows="4" placeholder="消息内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSend">发送</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api/request'

const accounts = ref([])
const sessions = ref([])
const messages = ref([])
const selectedAccount = ref(null)
const selectedSession = ref('')
const showSendDialog = ref(false)
const sendForm = ref({ accountId: null, sessionId: '', content: '' })

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

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
  messages.value = []
  try {
    const res = await api.get('/messages/sessions', { params: { accountId: selectedAccount.value } })
    if (res.success) sessions.value = res.data
  } catch (e) {}
}

async function loadHistory() {
  if (!selectedAccount.value || !selectedSession.value) return
  try {
    const res = await api.get('/messages/history', {
      params: { accountId: selectedAccount.value, sessionId: selectedSession.value, limit: 50 }
    })
    if (res.success) messages.value = res.data
  } catch (e) {}
}

async function handleSend() {
  if (!sendForm.value.accountId || !sendForm.value.sessionId || !sendForm.value.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    const res = await api.post('/messages/send', sendForm.value)
    if (res.success) {
      ElMessage.success('消息已发送')
      showSendDialog.value = false
      sendForm.value = { accountId: null, sessionId: '', content: '' }
    }
  } catch (e) {}
}

onMounted(loadAccounts)
</script>
