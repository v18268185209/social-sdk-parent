import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Index.vue'),
    meta: { public: true }
  },
  {
    path: '/service',
    name: 'Service',
    component: () => import('@/views/agreement/Service.vue'),
    meta: { public: true, title: '服务协议' }
  },
  {
    path: '/privacy',
    name: 'Privacy',
    component: () => import('@/views/privacy/Privacy.vue'),
    meta: { public: true, title: '隐私政策' }
  },
  {
    path: '/data-board',
    name: 'DataBoard',
    component: () => import('@/views/data-board/Index.vue'),
    meta: { title: '实时大屏', fullscreen: true }
  },
  {
    // 默认主页 = 企业介绍页（公开），右上角「登录」按钮跳 /login
    path: '/',
    name: 'Landing',
    component: () => import('@/views/landing/Index.vue'),
    meta: { public: true, title: '闲鱼管家' }
  },
  {
    // 兼容直接访问 /dashboard，重定向到管理后台
    path: '/dashboard',
    redirect: '/app/dashboard'
  },
  {
    // 管理后台路由树挪到 /app，避免和介绍页根路径冲突
    path: '/app',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/app/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '仪表盘' }
      },
      {
        path: 'accounts',
        name: 'Accounts',
        component: () => import('@/views/accounts/Index.vue'),
        meta: { title: '账号管理' }
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('@/views/products/Index.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: 'messages',
        name: 'Messages',
        component: () => import('@/views/messages/Index.vue'),
        meta: { title: '消息管理' }
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/Index.vue'),
        meta: { title: '订单管理' }
      },
      {
        path: 'rules',
        name: 'Rules',
        component: () => import('@/views/rules/Index.vue'),
        meta: { title: '规则管理' }
      },
      {
        path: 'wallet',
        name: 'Wallet',
        component: () => import('@/views/wallet/Index.vue'),
        meta: { title: '钱包资产' }
      },
      {
        path: 'collect',
        name: 'Collect',
        component: () => import('@/views/collect/Index.vue'),
        meta: { title: '收藏关注' }
      },
      {
        path: 'ai',
        name: 'Ai',
        component: () => import('@/views/ai/Index.vue'),
        meta: { title: 'AI 厂商' }
      },
      {
        path: 'ai-ops',
        name: 'AiOps',
        component: () => import('@/views/aiOps/Index.vue'),
        meta: { title: 'AI 运营' }
      },
      {
        path: 'cloud-storage',
        name: 'CloudStorage',
        component: () => import('@/views/cloudStorage/Index.vue'),
        meta: { title: '网盘存储' }
      },
      {
        path: 'virtual-ship',
        name: 'VirtualShip',
        component: () => import('@/views/virtualShip/Index.vue'),
        meta: { title: '虚拟发货' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('@/views/monitor/Index.vue'),
        meta: { title: '监控面板' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/Index.vue'),
        meta: { title: '审计日志' }
      },
      {
        path: 'notify',
        name: 'Notify',
        component: () => import('@/views/notify/Index.vue'),
        meta: { title: '消息通知' }
      },
      {
        path: 'market',
        name: 'Market',
        component: () => import('@/views/market/Index.vue'),
        meta: { title: '市场情报' }
      },
      {
        path: 'buyer',
        name: 'Buyer',
        component: () => import('@/views/buyer/Index.vue'),
        meta: { title: '买家画像' }
      },
      {
        path: 'ai-cs',
        name: 'AiCs',
        component: () => import('@/views/aiCs/Index.vue'),
        meta: { title: 'AI 客服' }
      },
      {
        path: 'tasks',
        name: 'Tasks',
        component: () => import('@/views/tasks/Index.vue'),
        meta: { title: '监控任务' }
      },
      {
        path: 'reviews',
        name: 'Reviews',
        component: () => import('@/views/reviews/Index.vue'),
        meta: { title: '评价与信用' }
      },
      {
        path: 'reply-logs',
        name: 'ReplyLogs',
        component: () => import('@/views/replyLogs/Index.vue'),
        meta: { title: '自动回复日志' }
      },
      {
        path: 'polish',
        name: 'Polish',
        component: () => import('@/views/polish/Index.vue'),
        meta: { title: '商品擦亮' }
      },
      {
        path: 'chrome',
        name: 'ChromeConfig',
        component: () => import('@/views/chrome/Index.vue'),
        meta: { title: '谷歌浏览器配置' }
      },
      {
        path: 'proxy',
        name: 'Proxy',
        component: () => import('@/views/proxy/Index.vue'),
        meta: { title: '代理管理' }
      },
      {
        path: 'circuit-breaker',
        name: 'CircuitBreaker',
        component: () => import('@/views/circuitBreaker/Index.vue'),
        meta: { title: '熔断器管理' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  if (to.meta.requiresAuth && !token) {
    next('/login')
  } else {
    next()
  }
})

export default router
