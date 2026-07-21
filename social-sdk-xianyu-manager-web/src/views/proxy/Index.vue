<template>
  <div class="page-root">
    <!-- 页头 -->
    <div class="page-header">
      <h2>代理管理</h2>
      <p class="page-desc">多账号多代理统一管理，支持阿布云/快代理/青果/Smartproxy 全量配置</p>
    </div>

    <!-- 错误提示 -->
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      closable
      style="margin-bottom: 16px;"
      @close="error = ''"
    />

    <!-- 指标 + 控制 -->
    <el-card style="margin-bottom: 16px;">
      <div class="metrics-bar">
        <span class="metric">
          <el-icon><Connection /></el-icon>
          已注册 <strong>{{ status.metrics?.registeredProviders ?? 0 }}</strong> 个供应商
        </span>
        <span class="metric">
          <el-icon><Pointer /></el-icon>
          活跃租约 <strong>{{ status.metrics?.activeLeases ?? 0 }}</strong>
        </span>
        <span class="metric">
          <el-icon><Link /></el-icon>
          绑定 <strong>{{ status.metrics?.bindings ?? 0 }}</strong> 个账号
        </span>
        <span class="metric">
          <el-icon><ColdDrink /></el-icon>
          冷名单 <strong>{{ status.metrics?.coolingDown ?? 0 }}</strong>
        </span>
        <span class="metric">
          <el-icon><TrendCharts /></el-icon>
          成功率 <strong>{{ status.metrics?.successRate ?? '100.0%' }}</strong>
        </span>
        <span class="metric">
          累计 <strong>{{ status.metrics?.totalAcquire ?? 0 }}</strong> 次
        </span>
        <span class="metric" v-if="!status.available">
          <el-tag type="warning" size="small">池未就绪</el-tag>
        </span>
      </div>
      <div class="actions">
        <el-button type="primary" :loading="reloading" @click="handleReload">
          <el-icon><Refresh /></el-icon>
          应用配置
        </el-button>
        <el-button :loading="checking" @click="handleHealthCheck">
          <el-icon><FirstAidKit /></el-icon>
          手动检查
        </el-button>
        <el-button text type="primary" :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button text type="danger" @click="showAddDialog = true">
          <el-icon><Plus /></el-icon>
          新增供应商
        </el-button>
      </div>
    </el-card>

    <!-- 供应商编辑 Tab -->
    <el-card>
      <el-tabs v-model="activeTab">
        <el-tab-pane
          v-for="cfg in configs"
          :key="cfg.providerType"
          :label="providerLabel(cfg.providerType)"
          :name="cfg.providerType"
        >
          <div v-if="cfg.providerType === 'global'">
            <el-form :model="cfg.config" label-width="180px" size="default" style="max-width: 600px;">
              <el-form-item label="复用已绑定 IP">
                <el-switch v-model="cfg.config.reuseBoundIp" />
              </el-form-item>
              <el-form-item label="最大复用次数">
                <el-input-number v-model="cfg.config.maxBindingUseCount" :min="1" :max="10000" />
              </el-form-item>
              <el-form-item label="直连兜底">
                <el-switch v-model="cfg.config.directModeAutoFallback" />
                <el-text v-if="cfg.config.directModeAutoFallback" size="small" type="info" style="margin-left: 12px;">
                  无代理时不报错，直接走本地网络
                </el-text>
                <el-text v-else size="small" type="warning" style="margin-left: 12px;">
                  无代理时抛异常，必须走代理
                </el-text>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveConfig(cfg)">
                  保存全局配置
                </el-button>
              </el-form-item>
            </el-form>
          </div>

          <div v-else>
            <el-form :model="cfg.config" label-width="140px" size="default" style="max-width: 700px;">
              <el-form-item label="启用">
                <el-switch v-model="cfg.enabled" />
              </el-form-item>

              <template v-if="cfg.providerType === 'abuyun'">
                <el-form-item label="用户名"><el-input v-model="cfg.config.username" /></el-form-item>
                <el-form-item label="密码"><el-input v-model="cfg.config.password" show-password /></el-form-item>
                <el-form-item label="Host"><el-input v-model="cfg.config.host" placeholder="http-dyn.abuyun.com" /></el-form-item>
                <el-form-item label="Port"><el-input-number v-model="cfg.config.port" :min="1" :max="65535" /></el-form-item>
              </template>

              <template v-else-if="cfg.providerType === 'smartproxy'">
                <el-form-item label="用户名"><el-input v-model="cfg.config.username" /></el-form-item>
                <el-form-item label="密码"><el-input v-model="cfg.config.password" show-password /></el-form-item>
                <el-form-item label="Host"><el-input v-model="cfg.config.host" placeholder="gate.smartproxy.com" /></el-form-item>
                <el-form-item label="Port"><el-input-number v-model="cfg.config.port" :min="1" :max="65535" /></el-form-item>
                <el-form-item label="城市"><el-input v-model="cfg.config.city" placeholder="Shanghai" /></el-form-item>
              </template>

              <template v-else-if="cfg.providerType === 'qg_tunnel'">
                <el-form-item label="ApiKey"><el-input v-model="cfg.config.apiKey" show-password /></el-form-item>
                <el-form-item label="用户名"><el-input v-model="cfg.config.authKey" /></el-form-item>
                <el-form-item label="密码"><el-input v-model="cfg.config.authPwd" show-password /></el-form-item>
                <el-form-item label="Host"><el-input v-model="cfg.config.host" /></el-form-item>
                <el-form-item label="Port"><el-input-number v-model="cfg.config.port" /></el-form-item>
                <el-form-item label="地区编码"><el-input v-model="cfg.config.areaCode" placeholder="110100" /></el-form-item>
                <el-form-item label="协议">
                  <el-radio-group v-model="cfg.config.protocol">
                    <el-radio-button value="http">HTTP</el-radio-button>
                    <el-radio-button value="socks5">SOCKS5</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </template>

              <template v-else-if="cfg.providerType === 'qg_short_lived'">
                <el-form-item label="ApiKey"><el-input v-model="cfg.config.apiKey" show-password /></el-form-item>
                <el-form-item label="提取模式">
                  <el-select v-model="cfg.config.plan">
                    <el-option value="extract_by_count" label="按量" />
                    <el-option value="elastic" label="弹性" />
                    <el-option value="uniform" label="均匀" />
                    <el-option value="channel" label="渠道" />
                  </el-select>
                </el-form-item>
                <el-form-item label="地区编码"><el-input v-model="cfg.config.areaCode" /></el-form-item>
                <el-form-item label="运营商">
                  <el-select v-model="cfg.config.isp">
                    <el-option :value="0" label="不筛选" />
                    <el-option :value="1" label="电信" />
                    <el-option :value="2" label="移动" />
                    <el-option :value="3" label="联通" />
                  </el-select>
                </el-form-item>
                <el-form-item label="去重提取"><el-switch v-model="cfg.config.distinct" /></el-form-item>
                <el-form-item label="存活秒数"><el-input-number v-model="cfg.config.keepAliveSec" :min="1" /></el-form-item>
                <el-form-item label="最大延迟(ms)"><el-input-number v-model="cfg.config.maxLatencyMs" :min="0" /></el-form-item>
              </template>

              <template v-else-if="cfg.providerType === 'kuaidaili_tunnel'">
                <el-form-item label="SecretId"><el-input v-model="cfg.config.secretId" show-password /></el-form-item>
                <el-form-item label="SecretKey"><el-input v-model="cfg.config.secretKey" show-password /></el-form-item>
                <el-form-item label="鉴权方式">
                  <el-radio-group v-model="cfg.config.authType">
                    <el-radio-button value="token">TOKEN</el-radio-button>
                    <el-radio-button value="hmacsha1">HMAC-SHA1</el-radio-button>
                  </el-radio-group>
                </el-form-item>
              </template>

              <template v-else-if="cfg.providerType === 'kuaidaili_private'">
                <el-form-item label="SecretId"><el-input v-model="cfg.config.secretId" show-password /></el-form-item>
                <el-form-item label="SecretKey"><el-input v-model="cfg.config.secretKey" show-password /></el-form-item>
                <el-form-item label="鉴权方式">
                  <el-radio-group v-model="cfg.config.authType">
                    <el-radio-button value="token">TOKEN</el-radio-button>
                    <el-radio-button value="hmacsha1">HMAC-SHA1</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="地区编码"><el-input v-model="cfg.config.areaCode" /></el-form-item>
                <el-form-item label="运营商">
                  <el-select v-model="cfg.config.isp">
                    <el-option :value="0" label="不筛选" />
                    <el-option :value="1" label="电信" />
                    <el-option :value="2" label="移动" />
                    <el-option :value="3" label="联通" />
                  </el-select>
                </el-form-item>
                <el-form-item label="去重"><el-switch v-model="cfg.config.distinct" /></el-form-item>
                <el-form-item label="存活(秒)"><el-input-number v-model="cfg.config.keepAliveSec" :min="1" /></el-form-item>
              </template>

              <template v-else>
                <el-text type="warning">未识别供应商类型：{{ cfg.providerType }}</el-text>
              </template>

              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveConfig(cfg)">
                  保存配置
                </el-button>
                <el-button text type="danger" :loading="deleting" @click="deleteConfig(cfg)">
                  删除
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 余额、绑定列表 -->
      <el-row :gutter="16" style="margin-top: 16px;">
        <el-col :span="12">
          <el-card>
            <template #header><strong>供应商余额</strong></template>
            <el-empty v-if="!status.balances || !Object.keys(status.balances).length" />
            <div v-for="(bal, k) in status.balances" :key="k" class="balance-row">
              <el-tag size="small">{{ k }}</el-tag>
              <el-text size="small">{{ bal }}</el-text>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header><strong>账号绑定</strong></template>
            <el-empty v-if="!status.bindings || !Object.keys(status.bindings).length" description="无绑定" />
            <div v-for="(info, accountId) in status.bindings" :key="accountId" class="binding-row">
              <el-tag size="small">账号 {{ accountId }}</el-tag>
              <el-text size="small">{{ info.host }}:{{ info.port }}</el-text>
              <el-button text type="warning" size="small" @click="handleUnbind(accountId)">解绑</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 新增供应商弹窗 -->
    <el-dialog v-model="showAddDialog" title="新增供应商" width="520">
      <el-form :model="newProvider" label-width="120px" size="default">
        <el-form-item label="供应商类型">
          <el-select v-model="newProvider.providerType" placeholder="请选择...">
            <el-option value="abuyun" label="阿布云" />
            <el-option value="smartproxy" label="Smartproxy" />
            <el-option value="qg_tunnel" label="青果隧道" />
            <el-option value="qg_short_lived" label="青果短效" />
            <el-option value="kuaidaili_tunnel" label="快代理隧道" />
            <el-option value="kuaidaili_private" label="快代理私密" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="newProvider.providerType" label="启用">
          <el-switch v-model="newProvider.enabled" />
        </el-form-item>
        <el-form-item v-if="newProvider.providerType" label="排序">
          <el-input-number v-model="newProvider.sortOrder" :min="0" :max="100" />
          <el-text size="small" type="info" style="margin-left: 8px;">数字越小优先级越高</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmAdd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Connection, Pointer, Link, ColdDrink, TrendCharts, Refresh,
  FirstAidKit, Plus
} from '@element-plus/icons-vue'
import * as proxyApi from '@/api/proxy'

const activeTab = ref('global')
const configs = ref([])
const status = ref({})
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const reloading = ref(false)
const checking = ref(false)
const error = ref('')
const showAddDialog = ref(false)
const newProvider = reactive({ providerType: '', enabled: true, sortOrder: 10 })

async function loadAll() {
  try {
    loading.value = true
    const [{ data: cs }, { data: st }] = await Promise.all([
      proxyApi.listProxyConfigs(),
      proxyApi.getProxyStatus()
    ])
    configs.value = cs
    status.value = st
    // activate first tab if global not present
    if (cs.length && !cs.find(c => c.providerType === 'global')) {
      activeTab.value = cs[0].providerType
    }
  } catch (e) {
    error.value = `加载失败: ${e.message}`
  } finally {
    loading.value = false
  }
}

async function saveConfig(cfg) {
  try {
    saving.value = true
    await proxyApi.saveProxyConfig(cfg.providerType, cfg.config)
    ElMessage.success('配置已保存，请点击「应用配置」使其生效')
    await loadAll()
  } catch (e) {
    ElMessage.error(`保存失败: ${e.message}`)
  } finally {
    saving.value = false
  }
}

async function deleteConfig(cfg) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${providerLabel(cfg.providerType)}」？这会导致使用该供应商的代理回到直连模式或上游供应商。`,
      '删除确认', { type: 'warning' }
    )
    deleting.value = true
    await proxyApi.deleteProxyConfig(cfg.providerType)
    ElMessage.success('已删除')
    await loadAll()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`删除失败: ${e.message}`)
  } finally {
    deleting.value = false
  }
}

async function handleReload() {
  try {
    reloading.value = true
    await proxyApi.reloadProxy()
    ElMessage.success('代理池已重新加载')
    await loadAll()
  } catch (e) {
    ElMessage.error(`重载失败: ${e.message}`)
  } finally {
    reloading.value = false
  }
}

async function handleHealthCheck() {
  try {
    checking.value = true
    await proxyApi.triggerHealthCheck()
    ElMessage.success('已触发健康检查')
    await loadAll()
  } catch (e) {
    ElMessage.error(`检查失败: ${e.message}`)
  } finally {
    checking.value = false
  }
}

async function handleUnbind(accountId) {
  try {
    await ElMessageBox.confirm(`确认解绑账号 ${accountId}？解绑后下次 acquire 将走供应商策略重新分配。`, '解绑确认', { type: 'warning' })
    await proxyApi.unbindAccount(accountId)
    ElMessage.success('已解绑')
    await loadAll()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(`解绑失败: ${e.message}`)
  }
}

async function confirmAdd() {
  if (!newProvider.providerType) {
    ElMessage.warning('请选择供应商类型')
    return
  }
  try {
    saving.value = true
    const cfg = {
      providerType: newProvider.providerType,
      config: defaultConfigFor(newProvider.providerType),
      enabled: newProvider.enabled,
      sortOrder: newProvider.sortOrder,
    }
    await proxyApi.saveProxyConfig(cfg.providerType, cfg.config)
    ElMessage.success('已添加')
    showAddDialog.value = false
    newProvider.providerType = ''
    newProvider.enabled = true
    newProvider.sortOrder = 10
    await loadAll()
    activeTab.value = cfg.providerType
  } catch (e) {
    ElMessage.error(`添加失败: ${e.message}`)
  } finally {
    saving.value = false
  }
}

function defaultConfigFor(type) {
  switch (type) {
    case 'abuyun': return { username: '', password: '', host: 'http-dyn.abuyun.com', port: 9020 }
    case 'smartproxy': return { username: '', password: '', host: 'gate.smartproxy.com', port: 7000, city: '' }
    case 'qg_tunnel': return { apiKey: '', authKey: '', authPwd: '', host: '', port: 0, areaCode: '', keepAliveSec: 60, protocol: 'http' }
    case 'qg_short_lived': return { apiKey: '', plan: 'extract_by_count', areaCode: '', isp: 0, distinct: true, num: 1, keepAliveSec: 60, maxLatencyMs: 3000 }
    case 'kuaidaili_tunnel': return { secretId: '', secretKey: '', authType: 'token', num: 1, format: 'json' }
    case 'kuaidaili_private': return { secretId: '', secretKey: '', authType: 'token', num: 1, pt: 1, distinct: true, format: 'json', areaCode: '', isp: 0, keepAliveSec: 120 }
    default: return {}
  }
}

function providerLabel(type) {
  const m = {
    global: '全局策略', abuyun: '阿布云', smartproxy: 'Smartproxy',
    qg_tunnel: '青果·隧道', qg_short_lived: '青果·短效',
    kuaidaili_tunnel: '快代理·隧道', kuaidaili_private: '快代理·私密',
  }
  return m[type] || type
}

onMounted(loadAll)
</script>

<style scoped>
.page-root { padding: 20px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0 0 4px; }
.page-desc { margin: 0; color: #909399; font-size: 13px; }
.metrics-bar { display: flex; flex-wrap: wrap; gap: 24px; margin-bottom: 12px; }
.metric { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #606266; }
.actions { display: flex; gap: 8px; align-items: center; border-top: 1px solid #ebeef5; padding-top: 12px; }
.balance-row, .binding-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 4px 0; border-bottom: 1px solid #f5f7fa; }
.balance-row:last-child, .binding-row:last-child { border-bottom: none; }
</style>
