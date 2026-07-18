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
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
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
