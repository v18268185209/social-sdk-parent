<template>
  <el-container class="layout">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <div class="logo-icon">AI</div>
        <div class="logo-text">
          <div class="logo-title">AI鱼多宝</div>
          <div class="logo-subtitle">智能运营平台</div>
        </div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="sidebar-menu"
      >
        <el-menu-item index="/dashboard">
          <span class="menu-icon-box"><el-icon><DataAnalysis /></el-icon></span>
          <span>仪表盘</span>
        </el-menu-item>

        <el-menu-item-group title="店铺管理">
          <el-menu-item index="/products">
            <span class="menu-icon-box"><el-icon><Goods /></el-icon></span>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/orders">
            <span class="menu-icon-box"><el-icon><List /></el-icon></span>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/messages">
            <span class="menu-icon-box"><el-icon><ChatDotRound /></el-icon></span>
            <span>消息管理</span>
          </el-menu-item>
          <el-menu-item index="/collect">
            <span class="menu-icon-box"><el-icon><Star /></el-icon></span>
            <span>收藏关注</span>
          </el-menu-item>
          <el-menu-item index="/reviews">
            <span class="menu-icon-box"><el-icon><Medal /></el-icon></span>
            <span>评价与信用</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="AI 智能">
          <el-menu-item index="/ai-ops">
            <span class="menu-icon-box"><el-icon><Promotion /></el-icon></span>
            <span>AI 运营</span>
          </el-menu-item>
          <el-menu-item index="/ai">
            <span class="menu-icon-box"><el-icon><Connection /></el-icon></span>
            <span>AI 厂商</span>
          </el-menu-item>
          <el-menu-item index="/ai-cs">
            <span class="menu-icon-box"><el-icon><Service /></el-icon></span>
            <span>AI 客服</span>
          </el-menu-item>
          <el-menu-item index="/polish">
            <span class="menu-icon-box"><el-icon><Sunrise /></el-icon></span>
            <span>商品擦亮</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="发货仓储">
          <el-menu-item index="/virtual-ship">
            <span class="menu-icon-box"><el-icon><Switch /></el-icon></span>
            <span>虚拟发货</span>
          </el-menu-item>
          <el-menu-item index="/cloud-storage">
            <span class="menu-icon-box"><el-icon><UploadFilled /></el-icon></span>
            <span>网盘存储</span>
          </el-menu-item>
          <el-menu-item index="/tasks">
            <span class="menu-icon-box"><el-icon><Timer /></el-icon></span>
            <span>监控任务</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="规则合规">
          <el-menu-item index="/rules">
            <span class="menu-icon-box"><el-icon><Setting /></el-icon></span>
            <span>规则管理</span>
          </el-menu-item>
          <el-menu-item index="/notify">
            <span class="menu-icon-box"><el-icon><Bell /></el-icon></span>
            <span>消息通知</span>
          </el-menu-item>
          <el-menu-item index="/audit">
            <span class="menu-icon-box"><el-icon><Document /></el-icon></span>
            <span>审计日志</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="数据资产">
          <el-menu-item index="/wallet">
            <span class="menu-icon-box"><el-icon><Money /></el-icon></span>
            <span>钱包资产</span>
          </el-menu-item>
          <el-menu-item index="/monitor">
            <span class="menu-icon-box"><el-icon><Monitor /></el-icon></span>
            <span>监控面板</span>
          </el-menu-item>
          <el-menu-item index="/market">
            <span class="menu-icon-box"><el-icon><Compass /></el-icon></span>
            <span>市场情报</span>
          </el-menu-item>
          <el-menu-item index="/buyer">
            <span class="menu-icon-box"><el-icon><User /></el-icon></span>
            <span>买家画像</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item index="/accounts">
          <span class="menu-icon-box"><el-icon><UserFilled /></el-icon></span>
          <span>账号管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="page-title">{{ currentTitle }}</span>
        <div class="header-right">
          <el-button text circle @click="openDataBoard" title="实时大屏（新窗口）">
            <el-icon :size="18"><FullScreen /></el-icon>
          </el-button>
          <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-badge">
            <el-button text circle @click="openInbox" title="站内通知">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>
          <el-dropdown @command="handleCommand">
          <span class="user-info">
            <el-icon><UserFilled /></el-icon>
            {{ authStore.user?.displayName || '管理员' }}
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
      <el-footer class="app-footer" height="auto">
        <span class="coop-label">商务合作</span>
        <button class="coop-item" type="button" @click="openWechatQr" title="点击查看二维码">
          <svg class="coop-ico wechat" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M8.5 3C4.9 3 2 5.6 2 8.8c0 1.8 1 3.4 2.6 4.4L4 15.5l2.7-1.4c.8.2 1.6.3 2.5.3h.4c-.2-.7-.3-1.4-.3-2.1 0-3.1 2.9-5.5 6.5-5.5h.5C15.6 4.9 12.4 3 8.5 3z" />
            <path d="M21.5 13.2c0-2.5-2.5-4.5-5.6-4.5S10.3 10.7 10.3 13.2s2.5 4.5 5.6 4.5c.7 0 1.4-.1 2-.3l2.4 1.2-.5-1.9c1.2-.7 1.8-1.8 1.8-3z" />
          </svg>
          worker_680
        </button>
        <a class="coop-item" href="tel:18268185209">
          <svg class="coop-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2 4.1 2 2 0 0 1 4 2h3a2 2 0 0 1 2 1.7c.1 1 .3 1.9.7 2.8a2 2 0 0 1-.5 2.1L8 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7A2 2 0 0 1 22 16.9z" />
          </svg>
          18268185209
        </a>
        <a class="coop-item" href="https://aius.autos" target="_blank" rel="noopener noreferrer">
          <svg class="coop-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1" />
            <path d="M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1" />
          </svg>
          aius.autos
        </a>
        <span class="footer-divider">·</span>
        <span class="copyright">© 2026 AI鱼多宝</span>
      </el-footer>

      <el-dialog v-model="qrVisible" title="扫码添加微信" width="360px" align-center class="qr-dialog">
        <div class="qr-wrap">
          <el-image :src="qrSrc" fit="contain" class="qr-img" :preview-src-list="[qrSrc]" />
          <p class="qr-tip">请使用微信「扫一扫」添加：<strong>worker_680</strong></p>
          <el-button type="primary" class="qr-copy" @click="copyWechat">复制微信号</el-button>
        </div>
      </el-dialog>

      <!-- 站内通知收件箱 -->
      <el-drawer v-model="inboxVisible" title="站内通知" direction="rtl" size="380px">
        <div class="inbox-head">
          <span v-if="unread > 0">有 <strong>{{ unread }}</strong> 条未读</span>
          <span v-else>全部已读</span>
          <el-button text type="primary" :disabled="unread === 0" @click="readAll">全部已读</el-button>
        </div>
        <el-empty v-if="!messages.length" description="暂无通知" />
        <div
          v-for="m in messages"
          :key="m.id"
          class="inbox-item"
          :class="{ unread: !m.isRead }"
          @click="readMsg(m)"
        >
          <div class="inbox-title">
            <span class="inbox-dot" v-if="!m.isRead" />
            {{ m.title }}
          </div>
          <div class="inbox-content">{{ m.content }}</div>
          <div class="inbox-meta">{{ scenarioLabel(m.scenario) }} · {{ m.createdAt }}</div>
        </div>
      </el-drawer>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessageBox, ElMessage } from 'element-plus'
import { DataAnalysis, User, Goods, ChatDotRound, List, Operation, Money, Star, Cpu, Promotion, Van, UploadFilled, Monitor, Document, Bell, UserFilled, ArrowDown, FullScreen, Shop, Medal, Connection, Service, Sunrise, Switch, Timer, Setting, TrendCharts, Compass } from '@element-plus/icons-vue'
import * as notify from '@/api/notification'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const titleMap = {
  '/dashboard': '仪表盘',
  '/accounts': '账号管理',
  '/products': '商品管理',
  '/messages': '消息管理',
  '/orders': '订单管理',
  '/rules': '规则管理',
  '/wallet': '钱包资产',
  '/collect': '收藏关注',
  '/ai': 'AI 厂商',
  '/ai-ops': 'AI 运营',
  '/virtual-ship': '虚拟发货',
  '/cloud-storage': '网盘存储',
  '/monitor': '监控面板',
  '/audit': '审计日志',
  '/notify': '消息通知',
  '/market': '市场情报',
  '/buyer': '买家画像',
  '/ai-cs': 'AI 客服',
  '/tasks': '监控任务',
  '/reviews': '评价与信用',
  '/polish': '商品擦亮'
}

const currentTitle = computed(() => titleMap[route.path] || '管理后台')

function openDataBoard() {
  window.open('/data-board', '_blank')
}

function handleCommand(cmd) {
  if (cmd === 'logout') {
    ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' }).then(() => {
      authStore.logout()
      router.push('/login')
    })
  }
}

const wechatId = 'worker_680'
const qrSrc = '/wechat.jpg'
const qrVisible = ref(false)
function openWechatQr() {
  qrVisible.value = true
}
function copyWechat() {
  const ok = () => ElMessage.success('微信号已复制：' + wechatId)
  const fallback = () => {
    const ta = document.createElement('textarea')
    ta.value = wechatId
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ok()
    } catch (e) {
      ElMessage.error('复制失败，请手动复制：' + wechatId)
    }
    document.body.removeChild(ta)
  }
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(wechatId).then(ok).catch(fallback)
  } else {
    fallback()
  }
}

// ===== 站内通知收件箱 =====
const inboxVisible = ref(false)
const messages = ref([])
const unread = ref(0)
const scenarioMap = ref({})

function scenarioLabel(s) { return scenarioMap.value[s] || s }

async function loadScenarios() {
  try {
    const res = await notify.listScenarios()
    if (res.success) {
      scenarioMap.value = {}
      ;(res.data || []).forEach(it => { scenarioMap.value[it.scenario] = it.label })
    }
  } catch (e) {}
}

async function loadUnread() {
  try {
    const res = await notify.unreadCount()
    if (res.success) unread.value = res.data?.unread || 0
  } catch (e) {}
}

async function loadMessages() {
  try {
    const res = await notify.listMessages({ page: 1, size: 30 })
    if (res.success) messages.value = res.data?.records || []
  } catch (e) {}
}

async function openInbox() {
  inboxVisible.value = true
  await loadMessages()
}

async function readMsg(m) {
  if (!m.isRead) {
    try {
      await notify.markRead(m.id)
      m.isRead = true
      await loadUnread()
    } catch (e) {}
  }
}

async function readAll() {
  try {
    const res = await notify.markAllRead()
    if (res.success) {
      messages.value.forEach(m => { m.isRead = true })
      unread.value = 0
      ElMessage.success('已全部标记为已读')
    }
  } catch (e) {}
}

let pollTimer = null
onMounted(() => {
  loadScenarios()
  loadUnread()
  pollTimer = setInterval(loadUnread, 30000)
})
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<style scoped>
.layout { height: 100vh; }
.sidebar {
  background: #fff;
  overflow-y: auto;
  border-right: 1px solid #e4e7ed;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid #f0f2f5;
  gap: 10px;
}
.logo-icon {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #409EFF 0%, #1D9E75 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}
.logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}
.logo-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.logo-subtitle {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,.1);
}
.page-title { font-size: 16px; font-weight: 600; }
.user-info {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #606266;
}
.main-content { background: #f0f2f5; padding: 0 !important; overflow: hidden; display: flex; flex-direction: column; }

/* 侧边栏菜单样式 — 浅色主题 */
.sidebar-menu {
  border-right: none !important;
  padding: 8px 0;
}
.sidebar-menu :deep(.el-menu-item) {
  margin: 0 8px;
  border-radius: 6px;
  height: 40px;
  line-height: 40px;
  color: #606266;
  font-size: 13px;
  padding: 0 12px !important;
}
.sidebar-menu :deep(.el-menu-item:hover) {
  background: #f5f7fa !important;
  color: #303133;
}
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: #ecf5ff !important;
  color: #409EFF !important;
  font-weight: 500;
}
.sidebar-menu :deep(.el-menu-item-group__title) {
  padding: 12px 20px 6px;
  font-size: 11px;
  color: #909399;
  font-weight: 500;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}
.menu-icon-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 8px;
  border-radius: 4px;
  background: #f0f2f5;
  color: #606266;
  font-size: 14px;
  flex-shrink: 0;
}
.sidebar-menu :deep(.el-menu-item.is-active) .menu-icon-box {
  background: #409EFF;
  color: #fff;
}

/* 业务合作页脚 */
.app-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px 18px;
  padding: 12px 16px;
  background: #f0f2f5;
  border-top: 1px solid #e4e7ed;
  color: #909399;
  font-size: 13px;
}
.coop-label {
  color: #606266;
  font-weight: 600;
  letter-spacing: 1px;
}
.coop-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 13px;
  text-decoration: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 8px;
  transition: color 0.2s, background 0.2s;
}
.coop-item:hover {
  color: #409eff;
  background: rgba(64, 158, 255, 0.08);
}
.coop-ico { width: 15px; height: 15px; }
.coop-ico.wechat { color: #07c160; }
.footer-divider { color: #c0c4cc; }
.copyright { color: #a8abb2; }

/* 二维码弹窗 */
.qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 6px 0 2px;
}
.qr-img {
  width: 220px;
  height: 280px;
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  background: #fff;
  cursor: zoom-in;
}
.qr-tip {
  color: #606266;
  font-size: 14px;
  margin: 0;
}
.qr-tip strong { color: #303133; }

/* 顶部右侧：通知铃铛 + 用户 */
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.bell-badge { margin-right: 4px; }
.bell-badge :deep(.el-button) {
  color: #606266;
}

/* 站内通知收件箱 */
.inbox-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: #606266;
  font-size: 13px;
}
.inbox-item {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  margin-bottom: 10px;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.inbox-item:hover { background: #f5f7fa; }
.inbox-item.unread { border-color: #b3d8ff; background: #ecf5ff; }
.inbox-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 6px;
}
.inbox-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #409eff;
  flex: 0 0 auto;
}
.inbox-content {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.inbox-meta {
  margin-top: 6px;
  color: #a8abb2;
  font-size: 12px;
}
</style>
