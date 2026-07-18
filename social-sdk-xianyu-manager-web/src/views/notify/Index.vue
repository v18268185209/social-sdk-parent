<template>
  <div>
    <el-card>
      <template #header>
        <span>消息通知</span>
      </template>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="通知通道" name="channels" />
        <el-tab-pane label="通知模板" name="templates" />
        <el-tab-pane label="订阅规则" name="subscriptions" />
        <el-tab-pane label="投递日志" name="logs" />
        <el-tab-pane label="每日摘要" name="digest" />
      </el-tabs>

      <!-- ===================== 通道 ===================== -->
      <div v-show="activeTab === 'channels'">
        <div style="margin-bottom: 12px; text-align: right;">
          <el-button type="primary" @click="openChannelDialog()">
            <el-icon><Plus /></el-icon> 新增通道
          </el-button>
        </div>
        <el-table :data="channels" stripe v-loading="loadingChannels">
          <el-table-column prop="name" label="名称" min-width="140" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="row.type === 'EMAIL' ? 'primary' : row.type === 'SMS' ? 'success' : 'warning'" size="small">
                {{ channelTypeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="通道种类" width="120" v-if="false" />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" @change="(v) => toggleChannel(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220">
            <template #default="{ row }">
              <el-button size="small" @click="openChannelDialog(row)">编辑</el-button>
              <el-button size="small" type="success" @click="openTestDialog(row)">测试</el-button>
              <el-button size="small" type="danger" @click="removeChannel(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ===================== 模板 ===================== -->
      <div v-show="activeTab === 'templates'">
        <div style="margin-bottom: 12px; text-align: right;">
          <el-button type="primary" @click="openTemplateDialog()">
            <el-icon><Plus /></el-icon> 新增/覆盖模板
          </el-button>
        </div>
        <el-table :data="templates" stripe v-loading="loadingTemplates">
          <el-table-column label="场景" width="200">
            <template #default="{ row }">{{ scenarioLabel(row.scenario) }}</template>
          </el-table-column>
          <el-table-column prop="titleTpl" label="标题模板" min-width="160" show-overflow-tooltip />
          <el-table-column prop="bodyTpl" label="正文模板" min-width="200" show-overflow-tooltip />
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" @change="(v) => toggleTemplate(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="openTemplateDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeTemplate(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ===================== 订阅 ===================== -->
      <div v-show="activeTab === 'subscriptions'">
        <div style="margin-bottom: 12px; text-align: right;">
          <el-button type="primary" @click="openSubDialog()">
            <el-icon><Plus /></el-icon> 新增订阅
          </el-button>
        </div>
        <el-table :data="subscriptions" stripe v-loading="loadingSubs">
          <el-table-column label="场景" width="200">
            <template #default="{ row }">{{ scenarioLabel(row.scenario) }}</template>
          </el-table-column>
          <el-table-column label="通道" width="160">
            <template #default="{ row }">{{ channelName(row.channelId) }}</template>
          </el-table-column>
          <el-table-column label="接收范围" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.recipientScope === 'ALL' ? 'info' : 'success'">
                {{ row.recipientScope === 'ALL' ? '全部接收人' : '指定接收人' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="账号范围" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.accountScope === 'ALL' ? 'info' : 'success'">
                {{ row.accountScope === 'ALL' ? '全部账号' : '指定账号' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="启用" width="90">
            <template #default="{ row }">
              <el-switch :model-value="row.enabled" @change="(v) => toggleSub(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button size="small" @click="openSubDialog(row)">编辑</el-button>
              <el-button size="small" type="danger" @click="removeSub(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- ===================== 日志 ===================== -->
      <div v-show="activeTab === 'logs'">
        <el-form inline style="margin-bottom: 12px;">
          <el-form-item label="场景">
            <el-select v-model="logScenario" clearable placeholder="全部场景" style="width: 200px;" @change="loadLogs(1)">
              <el-option v-for="s in scenarios" :key="s.scenario" :label="s.label" :value="s.scenario" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-table :data="logs" stripe v-loading="loadingLogs">
          <el-table-column label="场景" width="200">
            <template #default="{ row }">{{ scenarioLabel(row.scenario) }}</template>
          </el-table-column>
          <el-table-column prop="channelType" label="通道" width="110" />
          <el-table-column prop="recipient" label="接收人" width="160" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'SENT' ? 'success' : 'danger'">
                {{ row.status === 'SENT' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="error" label="错误信息" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.error" style="color:#f56c6c">{{ row.error }}</span>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="180" />
        </el-table>
        <el-pagination
          style="margin-top: 12px; justify-content: flex-end;"
          layout="total, prev, pager, next"
          :total="logTotal"
          :current-page="logPage"
          :page-size="logSize"
          @current-change="loadLogs" />
      </div>

      <!-- ===================== 每日摘要 ===================== -->
      <div v-show="activeTab === 'digest'">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px;"
          title="每天定时把前一天的站内通知按场景汇总，经指定通道（邮件/短信）一次性发送。">
        </el-alert>
        <el-form label-width="120px" style="max-width: 640px;" v-loading="loadingDigest">
          <el-form-item label="启用">
            <el-switch v-model="digestForm.enabled" />
          </el-form-item>
          <el-form-item label="发送通道">
            <el-select v-model="digestForm.channelId" style="width: 100%;" placeholder="选择通道">
              <el-option v-for="c in channels" :key="c.id" :label="c.name + '（' + channelTypeLabel(c.type) + '）'" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="接收人">
            <el-input v-model="digestForm.recipients" placeholder="邮箱/手机号，逗号分隔（留空则用通道默认）" />
          </el-form-item>
          <el-form-item label="触发时间">
            <el-time-picker v-model="digestTime" value-format="HH:mm" format="HH:mm" placeholder="每日发送时间" />
          </el-form-item>
          <el-form-item label="纳入场景">
            <el-select v-model="digestForm.scenarios" multiple clearable collapse-tags
              style="width: 100%;" placeholder="全部场景">
              <el-option v-for="s in scenarios" :key="s.scenario" :label="s.label" :value="s.scenario" />
            </el-select>
          </el-form-item>
          <el-form-item label="站内留痕">
            <el-switch v-model="digestForm.includeInApp" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="savingDigest" @click="saveDigest">保存配置</el-button>
            <el-button :loading="sendingDigest" @click="sendDigestNow">立即发送一次</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 通道编辑 -->
    <el-dialog v-model="channelVisible" :title="channelForm.id ? '编辑通道' : '新增通道'" width="560px">
      <el-form :model="channelForm" label-width="100px">
        <el-form-item label="名称">
          <el-input v-model="channelForm.name" placeholder="如：运营告警邮箱" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="channelForm.type" @change="onChannelTypeChange">
            <el-radio-button value="EMAIL">邮件(SMTP)</el-radio-button>
            <el-radio-button value="WEBHOOK">Webhook</el-radio-button>
            <el-radio-button value="SMS">短信(SMS)</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="channelForm.enabled" />
        </el-form-item>

        <!-- 邮件配置 -->
        <template v-if="channelForm.type === 'EMAIL'">
          <el-form-item label="SMTP 主机">
            <el-input v-model="channelForm.config.smtpHost" placeholder="smtp.qq.com" />
          </el-form-item>
          <el-form-item label="端口">
            <el-input-number v-model="channelForm.config.smtpPort" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="SSL/TLS">
            <el-switch v-model="channelForm.config.useSsl" />
          </el-form-item>
          <el-form-item label="用户名">
            <el-input v-model="channelForm.config.username" placeholder="发件邮箱" />
          </el-form-item>
          <el-form-item label="密码/授权码">
            <el-input v-model="channelForm.config.password" type="password" show-password placeholder="邮箱授权码" />
          </el-form-item>
          <el-form-item label="发件人">
            <el-input v-model="channelForm.config.from" placeholder="显示发件人" />
          </el-form-item>
          <el-form-item label="默认收件人">
            <el-input v-model="channelForm.config.defaultTo" placeholder="逗号分隔多个收件人" />
          </el-form-item>
        </template>

        <!-- Webhook 配置 -->
        <template v-else-if="channelForm.type === 'WEBHOOK'">
          <el-form-item label="Webhook 类型">
            <el-select v-model="channelForm.config.webhookType" style="width: 100%;">
              <el-option label="企业微信机器人" value="WECHAT_WORK" />
              <el-option label="钉钉机器人" value="DINGTALK" />
              <el-option label="飞书机器人" value="FEISHU" />
              <el-option label="通用(JSON)" value="GENERIC" />
            </el-select>
          </el-form-item>
          <el-form-item label="Webhook 地址">
            <el-input v-model="channelForm.config.webhookUrl" placeholder="https://..." />
          </el-form-item>
          <el-form-item label="加签密钥">
            <el-input v-model="channelForm.config.secret" placeholder="机器人加签 secret（可选）" />
          </el-form-item>
        </template>

        <!-- 短信配置 -->
        <template v-if="channelForm.type === 'SMS'">
          <el-form-item label="短信服务商">
            <el-select v-model="channelForm.config.provider" style="width: 100%;">
              <el-option label="通用 HTTP 网关" value="GENERIC" />
              <el-option label="阿里云短信" value="ALIYUN" />
            </el-select>
          </el-form-item>
          <el-form-item label="默认手机号">
            <el-input v-model="channelForm.config.phones" placeholder="逗号分隔，订阅未指定接收人时回退" />
          </el-form-item>
          <el-form-item label="签名">
            <el-input v-model="channelForm.config.signName" placeholder="短信签名" />
          </el-form-item>
          <el-form-item label="模板 Code">
            <el-input v-model="channelForm.config.templateCode" placeholder="如 SMS_123456" />
          </el-form-item>
          <el-form-item label="模板参数">
            <el-input v-model="channelForm.config.templateParam" type="textarea" :rows="2"
              placeholder='阿里云：{"name":"{accountName}"} 或整段 {"content":"{body}"}' />
            <div style="color:#909399;font-size:12px;margin-top:4px;line-height:1.6">
              支持任意模板变量：{accountName} {orderId} {itemTitle} {amount} {status} {counterparty} {content} {balance} {threshold} {bizType}；{body} 兜底为完整正文。
            </div>
          </el-form-item>
          <template v-if="channelForm.config.provider === 'GENERIC'">
            <el-form-item label="网关地址">
              <el-input v-model="channelForm.config.url" placeholder="https://your-gateway/send" />
            </el-form-item>
            <el-form-item label="负载模板">
              <el-input v-model="channelForm.config.bodyTemplate" type="textarea" :rows="2"
                placeholder='可选，支持 {phones}{body}{signName}{templateCode}{templateParam} 及任意 {var}' />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="AccessKeyId">
              <el-input v-model="channelForm.config.accessKeyId" />
            </el-form-item>
            <el-form-item label="AccessKeySecret">
              <el-input v-model="channelForm.config.accessKeySecret" type="password" show-password />
            </el-form-item>
            <el-form-item label="RegionId">
              <el-input v-model="channelForm.config.regionId" placeholder="cn-hangzhou" />
            </el-form-item>
          </template>
          <el-form-item label="每分钟限频">
            <el-input-number v-model="channelForm.config.rateLimitPerMinute" :min="0" :max="1000" />
            <span style="margin-left:8px;color:#909399;font-size:12px">0=不限制</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="channelVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveChannel">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试通道 -->
    <el-dialog v-model="testVisible" title="发送测试消息" width="460px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="testForm.title" />
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="testForm.body" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testVisible = false">取消</el-button>
        <el-button type="primary" :loading="testing" @click="sendTest">发送</el-button>
      </template>
    </el-dialog>

    <!-- 模板编辑 -->
    <el-dialog v-model="tplVisible" :title="tplForm.id ? '编辑模板' : '新增模板'" width="560px">
      <el-form :model="tplForm" label-width="100px">
        <el-form-item label="场景">
          <el-select v-model="tplForm.scenario" style="width: 100%;" :disabled="!!tplForm.id">
            <el-option v-for="s in scenarios" :key="s.scenario" :label="s.label" :value="s.scenario" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题模板">
          <el-input v-model="tplForm.titleTpl" placeholder="支持 {accountName} {orderId} 等占位符" />
        </el-form-item>
        <el-form-item label="正文模板">
          <el-input v-model="tplForm.bodyTpl" type="textarea" :rows="5" placeholder="支持 {var} 占位符" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="tplForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tplVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 订阅编辑 -->
    <el-dialog v-model="subVisible" :title="subForm.id ? '编辑订阅' : '新增订阅'" width="560px">
      <el-form :model="subForm" label-width="100px">
        <el-form-item label="场景">
          <el-select v-model="subForm.scenario" style="width: 100%;">
            <el-option v-for="s in scenarios" :key="s.scenario" :label="s.label" :value="s.scenario" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知通道">
          <el-select v-model="subForm.channelId" style="width: 100%;" placeholder="选择通道">
            <el-option v-for="c in channels" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="接收范围">
          <el-radio-group v-model="subForm.recipientScope">
            <el-radio value="ALL">全部接收人</el-radio>
            <el-radio value="CUSTOM">指定接收人</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="subForm.recipientScope === 'CUSTOM'" label="接收人">
          <el-input v-model="subForm.recipients" type="textarea" :rows="2" placeholder="邮箱/手机号，逗号分隔" />
        </el-form-item>
        <el-form-item label="账号范围">
          <el-radio-group v-model="subForm.accountScope">
            <el-radio value="ALL">全部账号</el-radio>
            <el-radio value="CUSTOM">指定账号</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="subForm.accountScope === 'CUSTOM'" label="账号ID">
          <el-input v-model="subForm.accountIds" type="textarea" :rows="2" placeholder="账号 ID，逗号分隔" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="subForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSub">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import * as notify from '@/api/notification'

const activeTab = ref('channels')

// ===== 公共：场景 & 通道选项 =====
const scenarios = ref([])
const scenarioMap = ref({})
const channels = ref([])
const channelMap = ref({})
function scenarioLabel(s) {
  if (s === 'DAILY_DIGEST') return '每日摘要'
  if (s === 'DIGEST') return '摘要发送'
  if (s === 'TEST') return '测试'
  return scenarioMap.value[s] || s
}
function channelTypeLabel(t) {
  if (t === 'EMAIL') return '邮件(SMTP)'
  if (t === 'SMS') return '短信(SMS)'
  if (t === 'WEBHOOK') return 'Webhook'
  return t || '-'
}
function channelName(id) { return channelMap.value[id] || (id ? 'ID:' + id : '-') }

// ===== 通道 =====
const loadingChannels = ref(false)
const channelVisible = ref(false)
const saving = ref(false)
const testing = ref(false)
const channelForm = reactive({
  id: null, name: '', type: 'EMAIL', enabled: true, config: {}
})
const testVisible = ref(false)
const testForm = reactive({ id: null, title: '测试通知', body: '这是一条来自 AI鱼多宝 的测试消息。' })

function emptyEmailConfig() {
  return { smtpHost: '', smtpPort: 465, useSsl: true, username: '', password: '', from: '', defaultTo: '' }
}
function emptyWebhookConfig() {
  return { webhookUrl: '', webhookType: 'WECHAT_WORK', secret: '' }
}
function emptySmsConfig() {
  return { provider: 'GENERIC', phones: '', signName: '', templateCode: '', templateParam: '',
    url: '', bodyTemplate: '', accessKeyId: '', accessKeySecret: '', regionId: 'cn-hangzhou',
    rateLimitPerMinute: 0 }
}

function onChannelTypeChange() {
  if (channelForm.type === 'EMAIL') channelForm.config = emptyEmailConfig()
  else if (channelForm.type === 'SMS') channelForm.config = emptySmsConfig()
  else channelForm.config = emptyWebhookConfig()
}

function openChannelDialog(row) {
  if (row) {
    channelForm.id = row.id
    channelForm.name = row.name
    channelForm.type = row.type
    channelForm.enabled = !!row.enabled
    try {
      channelForm.config = row.configJson ? JSON.parse(row.configJson) : (row.type === 'EMAIL' ? emptyEmailConfig() : emptyWebhookConfig())
    } catch (e) {
      channelForm.config = row.type === 'EMAIL' ? emptyEmailConfig()
        : row.type === 'SMS' ? emptySmsConfig() : emptyWebhookConfig()
    }
  } else {
    channelForm.id = null
    channelForm.name = ''
    channelForm.type = 'EMAIL'
    channelForm.enabled = true
    channelForm.config = emptyEmailConfig()
  }
  channelVisible.value = true
}

async function saveChannel() {
  if (!channelForm.name) { ElMessage.warning('请填写名称'); return }
  saving.value = true
  const payload = {
    name: channelForm.name,
    type: channelForm.type,
    enabled: channelForm.enabled,
    configJson: JSON.stringify(channelForm.config || {})
  }
  try {
    const fn = channelForm.id
      ? notify.updateChannel(channelForm.id, payload)
      : notify.createChannel(payload)
    const res = await fn
    if (res.success) {
      ElMessage.success('已保存')
      channelVisible.value = false
      await loadChannels()
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {}
  finally { saving.value = false }
}

async function toggleChannel(row, v) {
  try {
    await notify.toggleChannel(row.id, v)
    row.enabled = v
    ElMessage.success('已更新')
  } catch (e) {}
}

function openTestDialog(row) {
  testForm.id = row.id
  testForm.title = '测试通知'
  testForm.body = '这是一条来自 AI鱼多宝 的测试消息。'
  testVisible.value = true
}

async function sendTest() {
  testing.value = true
  try {
    const res = await notify.testChannel(testForm.id, { title: testForm.title, body: testForm.body })
    if (res.success) ElMessage.success('测试消息已发送，请检查接收端')
    else ElMessage.error(res.message || '发送失败')
  } catch (e) {}
  finally {
    testing.value = false
    testVisible.value = false
  }
}

async function removeChannel(row) {
  await ElMessageBox.confirm(`确认删除通道「${row.name}」？`, '提示', { type: 'warning' })
  try {
    const res = await notify.deleteChannel(row.id)
    if (res.success) { ElMessage.success('已删除'); await loadChannels() }
  } catch (e) {}
}

// ===== 模板 =====
const loadingTemplates = ref(false)
const templates = ref([])
const tplVisible = ref(false)
const tplForm = reactive({ id: null, scenario: '', titleTpl: '', bodyTpl: '', enabled: true })

function openTemplateDialog(row) {
  if (row) {
    tplForm.id = row.id
    tplForm.scenario = row.scenario
    tplForm.titleTpl = row.titleTpl
    tplForm.bodyTpl = row.bodyTpl
    tplForm.enabled = !!row.enabled
  } else {
    tplForm.id = null
    tplForm.scenario = scenarios.value[0]?.scenario || ''
    tplForm.titleTpl = ''
    tplForm.bodyTpl = ''
    tplForm.enabled = true
  }
  tplVisible.value = true
}

async function saveTemplate() {
  if (!tplForm.scenario) { ElMessage.warning('请选择场景'); return }
  saving.value = true
  const payload = { scenario: tplForm.scenario, titleTpl: tplForm.titleTpl, bodyTpl: tplForm.bodyTpl, enabled: tplForm.enabled }
  try {
    const res = await notify.upsertTemplate(payload)
    if (res.success) { ElMessage.success('已保存'); tplVisible.value = false; await loadTemplates() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) {}
  finally { saving.value = false }
}

async function toggleTemplate(row, v) {
  // 复用 upsert 覆盖 enabled
  try {
    const res = await notify.upsertTemplate({ scenario: row.scenario, titleTpl: row.titleTpl, bodyTpl: row.bodyTpl, enabled: v })
    if (res.success) { row.enabled = v; ElMessage.success('已更新') }
  } catch (e) {}
}

async function removeTemplate(row) {
  await ElMessageBox.confirm('确认删除该模板？', '提示', { type: 'warning' })
  try {
    const res = await notify.deleteTemplate(row.id)
    if (res.success) { ElMessage.success('已删除'); await loadTemplates() }
  } catch (e) {}
}

// ===== 订阅 =====
const loadingSubs = ref(false)
const subscriptions = ref([])
const subVisible = ref(false)
const subForm = reactive({
  id: null, scenario: '', channelId: null,
  recipientScope: 'ALL', recipients: '',
  accountScope: 'ALL', accountIds: '', enabled: true
})

function openSubDialog(row) {
  if (row) {
    subForm.id = row.id
    subForm.scenario = row.scenario
    subForm.channelId = row.channelId
    subForm.recipientScope = row.recipientScope || 'ALL'
    subForm.recipients = row.recipients || ''
    subForm.accountScope = row.accountScope || 'ALL'
    subForm.accountIds = row.accountIds || ''
    subForm.enabled = !!row.enabled
  } else {
    subForm.id = null
    subForm.scenario = scenarios.value[0]?.scenario || ''
    subForm.channelId = channels.value[0]?.id || null
    subForm.recipientScope = 'ALL'
    subForm.recipients = ''
    subForm.accountScope = 'ALL'
    subForm.accountIds = ''
    subForm.enabled = true
  }
  subVisible.value = true
}

async function saveSub() {
  if (!subForm.scenario || !subForm.channelId) { ElMessage.warning('请选择场景与通道'); return }
  saving.value = true
  const payload = {
    scenario: subForm.scenario,
    channelId: subForm.channelId,
    recipientScope: subForm.recipientScope,
    recipients: subForm.recipientScope === 'CUSTOM' ? subForm.recipients : '',
    accountScope: subForm.accountScope,
    accountIds: subForm.accountScope === 'CUSTOM' ? subForm.accountIds : '',
    enabled: subForm.enabled
  }
  try {
    const fn = subForm.id ? notify.updateSubscription(subForm.id, payload) : notify.createSubscription(payload)
    const res = await fn
    if (res.success) { ElMessage.success('已保存'); subVisible.value = false; await loadSubs() }
    else ElMessage.error(res.message || '保存失败')
  } catch (e) {}
  finally { saving.value = false }
}

async function toggleSub(row, v) {
  try {
    await notify.toggleSubscription(row.id, v)
    row.enabled = v
    ElMessage.success('已更新')
  } catch (e) {}
}

async function removeSub(row) {
  await ElMessageBox.confirm('确认删除该订阅？', '提示', { type: 'warning' })
  try {
    const res = await notify.deleteSubscription(row.id)
    if (res.success) { ElMessage.success('已删除'); await loadSubs() }
  } catch (e) {}
}

// ===== 日志 =====
const loadingLogs = ref(false)
const logs = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logSize = ref(20)
const logScenario = ref('')

async function loadLogs(page = 1) {
  logPage.value = page
  loadingLogs.value = true
  try {
    const res = await notify.listLogs({ scenario: logScenario.value || undefined, page, size: logSize.value })
    if (res.success) {
      logs.value = res.data?.records || []
      logTotal.value = res.data?.total || 0
    }
  } catch (e) {}
  finally { loadingLogs.value = false }
}

// ===== 每日摘要 =====
const loadingDigest = ref(false)
const savingDigest = ref(false)
const sendingDigest = ref(false)
const digestTime = ref('09:00')
const digestForm = reactive({
  id: 1, enabled: false, channelId: null, recipients: '',
  hour: 9, minute: 0, scenarios: [], includeInApp: true
})

async function loadDigest() {
  loadingDigest.value = true
  try {
    const res = await notify.getDigestConfig()
    if (res.success && res.data) {
      Object.assign(digestForm, res.data)
      digestForm.scenarios = Array.isArray(res.data.scenarios)
        ? res.data.scenarios
        : (res.data.scenarios ? JSON.parse(res.data.scenarios) : [])
      const h = res.data.hour != null ? res.data.hour : 9
      const m = res.data.minute != null ? res.data.minute : 0
      digestTime.value = String(h).padStart(2, '0') + ':' + String(m).padStart(2, '0')
    }
  } catch (e) {}
  finally { loadingDigest.value = false }
}

async function saveDigest() {
  if (!digestForm.channelId) { ElMessage.warning('请选择发送通道'); return }
  savingDigest.value = true
  const payload = {
    enabled: digestForm.enabled,
    channelId: digestForm.channelId,
    recipients: digestForm.recipients,
    hour: Number(digestTime.value.split(':')[0]),
    minute: Number(digestTime.value.split(':')[1]),
    scenarios: JSON.stringify(digestForm.scenarios || []),
    includeInApp: digestForm.includeInApp
  }
  try {
    const res = await notify.saveDigestConfig(payload)
    if (res.success) ElMessage.success('已保存')
    else ElMessage.error(res.message || '保存失败')
  } catch (e) {}
  finally { savingDigest.value = false }
}

async function sendDigestNow() {
  sendingDigest.value = true
  try {
    const res = await notify.sendDigestNow()
    if (res.success) ElMessage.success('已触发发送，请查看投递日志')
    else ElMessage.error(res.message || '发送失败')
  } catch (e) {}
  finally { sendingDigest.value = false }
}

// ===== 加载 =====
async function loadScenarios() {
  try {
    const res = await notify.listScenarios()
    if (res.success) {
      scenarios.value = res.data || []
      scenarioMap.value = {}
      scenarios.value.forEach(s => { scenarioMap.value[s.scenario] = s.label })
    }
  } catch (e) {}
}
async function loadChannels() {
  loadingChannels.value = true
  try {
    const res = await notify.listChannels()
    if (res.success) {
      channels.value = res.data || []
      channelMap.value = {}
      channels.value.forEach(c => { channelMap.value[c.id] = c.name })
    }
  } catch (e) {}
  finally { loadingChannels.value = false }
}
async function loadTemplates() {
  loadingTemplates.value = true
  try {
    const res = await notify.listTemplates()
    if (res.success) templates.value = res.data || []
  } catch (e) {}
  finally { loadingTemplates.value = false }
}
async function loadSubs() {
  loadingSubs.value = true
  try {
    const res = await notify.listSubscriptions()
    if (res.success) subscriptions.value = res.data || []
  } catch (e) {}
  finally { loadingSubs.value = false }
}

onMounted(async () => {
  await loadScenarios()
  await loadChannels()
  await loadTemplates()
  await loadSubs()
  await loadLogs(1)
  await loadDigest()
})
</script>
