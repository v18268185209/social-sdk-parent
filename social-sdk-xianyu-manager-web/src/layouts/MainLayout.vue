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
          <el-menu-item index="/reply-logs">
            <span class="menu-icon-box"><el-icon><ChatLineSquare /></el-icon></span>
            <span>自动回复日志</span>
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
        <el-menu-item index="/chrome">
          <span class="menu-icon-box"><el-icon><Setting /></el-icon></span>
          <span>谷歌浏览器配置</span>
        </el-menu-item>
        <el-menu-item index="/proxy">
          <span class="menu-icon-box"><el-icon><Connection /></el-icon></span>
          <span>代理管理</span>
        </el-menu-item>
        <el-menu-item index="/circuit-breaker">
          <span class="menu-icon-box"><el-icon><Warning /></el-icon></span>
          <span>熔断器管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-icon-box">{{ currentIcon }}</span>
          <div class="page-title-area">
            <div class="page-title">{{ currentTitle }}</div>
            <div class="page-breadcrumb">
              <span v-for="(crumb, idx) in currentBreadcrumb" :key="idx">
                <span class="crumb">{{ crumb }}</span>
                <span v-if="idx < currentBreadcrumb.length - 1" class="crumb-sep">/</span>
              </span>
            </div>
          </div>
        </div>
        <div class="header-right">
          <el-button text circle @click="openBrowserConfig" title="浏览器配置">
            <el-icon :size="18"><Setting /></el-icon>
          </el-button>
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
            <span class="avatar">{{ (authStore.user?.displayName || '管').charAt(0) }}</span>
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

      <!-- 浏览器配置可视化表单抽屉 -->
      <el-drawer
        v-model="browserConfigVisible"
        title="浏览器配置"
        direction="rtl"
        size="540px"
        :before-close="handleBrowserConfigClose"
      >
        <div class="browser-config-drawer">
          <!-- 状态总览 -->
          <div class="bc-summary">
            <div class="bc-summary-card" :class="browserSummary.status">
              <div class="bc-summary-icon">
                <el-icon :size="22"><Monitor /></el-icon>
              </div>
              <div class="bc-summary-info">
                <div class="bc-summary-title">{{ browserSummary.title }}</div>
                <div class="bc-summary-desc">{{ browserSummary.desc }}</div>
              </div>
            </div>
            <el-button type="primary" :loading="bcDetecting" @click="handleBrowserDetect">
              <el-icon><Search /></el-icon>
              重新探测
            </el-button>
            <el-button :loading="bcDownloading" @click="handleBrowserDownload">
              <el-icon><Download /></el-icon>
              自动下载
            </el-button>
          </div>

          <!-- 表单 -->
          <el-form
            ref="browserFormRef"
            :model="browserForm"
            label-position="top"
            class="bc-form"
          >
            <el-divider content-position="left">浏览器路径</el-divider>

            <el-form-item>
              <template #label>
                <span>可执行文件路径</span>
                <el-tooltip content="Chrome、Chromium、Edge、Brave 均可。留空则自动探测。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-input-row">
                <el-input v-model="browserForm.executablePath" placeholder="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" clearable />
                <el-button @click="handleBrowserValidate">校验</el-button>
              </div>
              <div class="bc-form-hint" v-if="browserForm.detected.path">
                <el-icon><Check /></el-icon>
                已探测到 {{ browserForm.detected.type }}：{{ browserForm.detected.path }}
                <el-button type="primary" link size="small" @click="useDetected">使用</el-button>
              </div>
              <div class="bc-form-hint muted" v-else>
                未发现浏览器，请手动指定或下载
              </div>
            </el-form-item>

            <el-divider content-position="left">运行模式</el-divider>

            <el-form-item>
              <template #label>
                <span>启动模式</span>
                <el-tooltip content="有界面模式兼容性更好、滑块成功率更高；无头模式适合无显示器服务器。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="browserForm.headless">
                <el-radio :value="false">
                  <span>有界面模式</span>
                  <span class="bc-recommend">(推荐)</span>
                </el-radio>
                <el-radio :value="true">无头模式</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="browserForm.headless">
              <template #label>
                <span>无头模式版本</span>
                <el-tooltip content="Chrome 112+ 推荐新版 headless，旧版兼容性更好。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-radio-group v-model="browserForm.headlessMode">
                <el-radio value="new">新版 headless（推荐）</el-radio>
                <el-radio value="legacy">旧版 --headless</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>窗口尺寸</span>
                <el-tooltip content="模拟屏幕分辨率，影响指纹采集。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-size-row">
                <el-input-number v-model="browserForm.windowWidth" :min="800" :max="3840" />
                <span class="bc-size-sep">×</span>
                <el-input-number v-model="browserForm.windowHeight" :min="600" :max="2160" />
                <span class="bc-form-hint muted">推荐 1366×768</span>
              </div>
            </el-form-item>

            <el-divider content-position="left">高级配置</el-divider>

            <el-form-item>
              <template #label>
                <span>CDP 端口范围</span>
                <el-tooltip content="每个账号独占一个 CDP 端口，段宽需 ≥ 账号数量。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <div class="bc-range-row">
                <el-input-number v-model="browserForm.portRangeStart" :min="1024" :max="65535" />
                <span class="bc-size-sep">至</span>
                <el-input-number v-model="browserForm.portRangeEnd" :min="1024" :max="65535" />
              </div>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>用户数据目录</span>
                <el-tooltip content="Chrome Profile 根目录，每个账号在此下创建独立子目录。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input v-model="browserForm.userDataDirRoot" placeholder="./chrome-profiles" />
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>多账号指纹噪声</span>
                <el-tooltip content="按账号 seed 生成唯一 canvas/WebGL 噪声，避免账号间指纹关联被封。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-switch v-model="browserForm.perAccountSeedNoise" active-text="启用" inactive-text="关闭" />
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>启动超时</span>
                <el-tooltip content="Chrome 容器 IPC 启动最长等待时间，超时触发崩溃恢复。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number v-model="browserForm.launchTimeoutSeconds" :min="5" :max="120" :step="5" />
              <span class="bc-form-hint muted">　秒</span>
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>崩溃恢复次数</span>
                <el-tooltip content="Chrome 异常退出后自动重启的次数上限。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input-number v-model="browserForm.maxCrashRecoveryAttempts" :min="0" :max="10" />
            </el-form-item>

            <el-form-item>
              <template #label>
                <span>自定义启动参数</span>
                <el-tooltip content="每行一个参数，保存后将覆盖默认反检测参数。">
                  <el-icon class="bc-tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </template>
              <el-input
                v-model="browserForm.customLaunchArgsText"
                type="textarea"
                :rows="4"
                placeholder="--disable-gpu&#10;--no-sandbox&#10;--mute-audio"
              />
            </el-form-item>
          </el-form>

          <div class="bc-actions">
            <el-button type="primary" :loading="bcSaving" @click="handleBrowserSave">保存配置</el-button>
            <el-button @click="handleBrowserReset">重置</el-button>
          </div>
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
import { DataAnalysis, User, Goods, ChatDotRound, List, Operation, Money, Star, Cpu, Promotion, Van, UploadFilled, Monitor, Document, Bell, UserFilled, ArrowDown, FullScreen, Shop, Medal, Connection, Service, Sunrise, Switch, Timer, Setting, TrendCharts, Compass, Search, Download, Check, InfoFilled, Warning } from '@element-plus/icons-vue'
import * as notify from '@/api/notification'
import { getChromeConfig, detectChrome, saveChromeConfig, downloadChrome, validateChromePath } from '@/api/chrome'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 面包屑映射
const breadcrumbMap = {
  '/dashboard': ['仪表盘'],
  '/products': ['店铺管理', '商品管理'],
  '/orders': ['店铺管理', '订单管理'],
  '/messages': ['店铺管理', '消息管理'],
  '/collect': ['店铺管理', '收藏关注'],
  '/reviews': ['店铺管理', '评价与信用'],
  '/ai-ops': ['AI 智能', 'AI 运营'],
  '/ai': ['AI 智能', 'AI 厂商'],
  '/ai-cs': ['AI 智能', 'AI 客服'],
  '/polish': ['AI 智能', '商品擦亮'],
  '/virtual-ship': ['发货仓储', '虚拟发货'],
  '/cloud-storage': ['发货仓储', '网盘存储'],
  '/tasks': ['发货仓储', '监控任务'],
  '/rules': ['规则合规', '规则管理'],
  '/notify': ['规则合规', '消息通知'],
  '/audit': ['规则合规', '审计日志'],
  '/wallet': ['数据资产', '钱包资产'],
  '/monitor': ['数据资产', '监控面板'],
  '/market': ['数据资产', '市场情报'],
  '/buyer': ['数据资产', '买家画像'],
  '/accounts': ['账号管理'],
  '/circuit-breaker': ['数据资产', '熔断器管理']
}

const currentBreadcrumb = computed(() => breadcrumbMap[route.path] || ['管理后台'])

// 页面图标映射
const pageIconMap = {
  '/dashboard': '📊',
  '/products': '📦',
  '/orders': '📋',
  '/messages': '💬',
  '/collect': '⭐',
  '/reviews': '🏅',
  '/ai-ops': '🚀',
  '/ai': '🔗',
  '/ai-cs': '🎧',
  '/polish': '✨',
  '/virtual-ship': '🔄',
  '/cloud-storage': '☁️',
  '/tasks': '⏱️',
  '/rules': '⚙️',
  '/notify': '🔔',
  '/audit': '📄',
  '/wallet': '💰',
  '/monitor': '🖥️',
  '/market': '🧭',
  '/buyer': '👤',
  '/circuit-breaker': '⚠️',
  '/accounts': '👤'
}

const currentIcon = computed(() => pageIconMap[route.path] || '📄')

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

// ===== 浏览器配置抽屉 =====
const browserConfigVisible = ref(false)
const bcDetecting = ref(false)
const bcDownloading = ref(false)
const bcSaving = ref(false)
const browserFormRef = ref(null)

const browserForm = ref({
  executablePath: '',
  headless: false,
  headlessMode: 'new',
  portRangeStart: 9222,
  portRangeEnd: 9322,
  userDataDirRoot: './chrome-profiles',
  windowWidth: 1366,
  windowHeight: 768,
  perAccountSeedNoise: true,
  launchTimeoutSeconds: 30,
  maxCrashRecoveryAttempts: 3,
  customLaunchArgsText: '',
  detected: { found: false, path: '', type: '' }
})

const browserSummary = computed(() => {
  const f = browserForm.value
  if (!f.executablePath && !f.detected.path) {
    return { status: 'warning', title: '未配置浏览器', desc: '请指定路径或先探测系统浏览器' }
  }
  if (f.detected.found) {
    return { status: 'success', title: '浏览器可用', desc: `${f.detected.type} · ${f.detected.path}` }
  }
  return { status: 'success', title: '浏览器路径已设置', desc: f.executablePath }
})

function openBrowserConfig() {
  browserConfigVisible.value = true
  if (!browserForm.value.executablePath && !browserForm.value.detected.path) {
    loadBrowserConfig()
  }
}

function handleBrowserConfigClose(done) {
  done()
}

async function loadBrowserConfig() {
  try {
    const res = await getChromeConfig()
    if (res.success && res.data) {
      const d = res.data
      browserForm.value = {
        executablePath: d.executablePath || '',
        headless: d.headless ?? false,
        headlessMode: d.headlessMode || 'new',
        portRangeStart: d.portRangeStart || 9222,
        portRangeEnd: d.portRangeEnd || 9322,
        userDataDirRoot: d.userDataDirRoot || './chrome-profiles',
        windowWidth: d.windowWidth || 1366,
        windowHeight: d.windowHeight || 768,
        perAccountSeedNoise: d.perAccountSeedNoise ?? true,
        launchTimeoutSeconds: d.launchTimeoutSeconds || 30,
        maxCrashRecoveryAttempts: d.maxCrashRecoveryAttempts || 3,
        customLaunchArgsText: (d.customLaunchArgs || []).join('\n'),
        detected: {
          found: d.detected?.found || false,
          path: d.detected?.path || '',
          type: d.detected?.type || ''
        }
      }
    }
  } catch (e) {
    ElMessage.error('加载浏览器配置失败：' + (e.message || e))
  }
}

async function handleBrowserDetect() {
  bcDetecting.value = true
  try {
    const res = await detectChrome()
    if (res.success && res.data) {
      browserForm.value.detected = {
        found: res.data.found,
        path: res.data.path || '',
        type: res.data.type || ''
      }
      if (res.data.found) {
        ElMessage.success(`发现浏览器：${res.data.type}`)
      } else {
        ElMessage.warning('未发现浏览器，请手动指定或下载')
      }
    }
  } catch (e) {
    ElMessage.error('探测失败：' + (e.message || e))
  } finally {
    bcDetecting.value = false
  }
}

function useDetected() {
  if (browserForm.value.detected.path) {
    browserForm.value.executablePath = browserForm.value.detected.path
  }
}

async function handleBrowserValidate() {
  if (!browserForm.value.executablePath) {
    ElMessage.warning('请先输入路径')
    return
  }
  try {
    const res = await validateChromePath(browserForm.value.executablePath)
    if (res.success && res.data) {
      if (res.data.valid) {
        ElMessage.success('路径校验通过，可执行')
      } else {
        ElMessage.warning('路径不可用：' + (res.data.reason || '不存在或无执行权限'))
      }
    }
  } catch (e) {
    ElMessage.error('校验失败：' + (e.message || e))
  }
}

async function handleBrowserDownload() {
  bcDownloading.value = true
  try {
    const res = await downloadChrome()
    if (res.success && res.data) {
      ElMessage.success(res.data.message || '下载完成')
    } else {
      ElMessage.warning(res.message || res.data?.message || '下载失败，请使用系统包管理器安装')
    }
  } catch (e) {
    ElMessage.error('下载失败：' + (e.message || e))
  } finally {
    bcDownloading.value = false
  }
}

async function handleBrowserSave() {
  bcSaving.value = true
  try {
    const args = browserForm.value.customLaunchArgsText
      .split('\n')
      .map(s => s.trim())
      .filter(Boolean)
    const payload = {
      ...browserForm.value,
      customLaunchArgs: args.length ? args : null
    }
    const res = await saveChromeConfig(payload)
    if (res.success) {
      ElMessage.success('浏览器配置已保存')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    bcSaving.value = false
  }
}

async function handleBrowserReset() {
  if (!browserConfigVisible.value) return
  browserConfigVisible.value = false
  await loadBrowserConfig()
  browserConfigVisible.value = true
  ElMessage.info('已重置为当前配置')
}
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
  border-bottom: 1px solid #e4e7ed;
  padding: 0 24px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.page-icon-box {
  width: 28px;
  height: 28px;
  background: #ecf5ff;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409EFF;
  font-size: 14px;
  flex-shrink: 0;
}
.page-title-area {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.page-title {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}
.page-breadcrumb {
  font-size: 11px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}
.crumb-sep {
  color: #c0c4cc;
  margin: 0 2px;
}
.crumb {
  color: #909399;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 13px;
}
.avatar {
  width: 28px;
  height: 28px;
  background: #f0f2f5;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #606266;
  font-size: 12px;
  font-weight: 500;
}
.main-content {
  background: #f5f7fa;
  padding: 16px !important;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

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
  background: #f5f7fa;
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

/* 浏览器配置抽屉 */
.browser-config-drawer {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 16px;
}

.bc-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.bc-summary-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #f5f7fa;
  border: 1px solid #ebeef5;
  flex: 1;
  min-width: 200px;
}

.bc-summary-card.success {
  background: #f0f9eb;
  border-color: #c2e7b0;
  color: #67c23a;
}

.bc-summary-card.warning {
  background: #fdf6ec;
  border-color: #f5dab1;
  color: #e6a23c;
}

.bc-summary-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.bc-summary-info {
  flex: 1;
}

.bc-summary-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.bc-summary-desc {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  word-break: break-all;
}

.bc-form {
  background: #fafbfc;
  border-radius: 8px;
  padding: 16px 16px 4px;
}

.bc-form :deep(.el-form-item__label) {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.bc-tip-icon {
  color: #909399;
  cursor: help;
  font-size: 14px;
}

.bc-form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #67c23a;
  display: flex;
  align-items: center;
  gap: 4px;
}

.bc-form-hint.muted {
  color: #909399;
}

.bc-input-row {
  display: flex;
  gap: 8px;
}

.bc-input-row :deep(.el-input) {
  flex: 1;
}

.bc-recommend {
  font-size: 12px;
  color: #67c23a;
  margin-left: 4px;
}

.bc-size-row,
.bc-range-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bc-size-sep {
  color: #909399;
}

.bc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.bc-divider {
  margin: 12px 0;
}
</style>
