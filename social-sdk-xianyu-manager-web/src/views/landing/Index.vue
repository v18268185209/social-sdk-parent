<template>
  <div class="landing">
    <!-- 顶部导航：左 logo + 右登录按钮 -->
    <header class="nav">
      <div class="nav-inner">
        <div class="brand">
          <span class="logo">闲鱼管家</span>
          <span class="tag">Xianyu Manager</span>
        </div>
        <button class="login-btn" @click="goLogin">登录控制台 →</button>
      </div>
    </header>

    <!-- Hero 大 banner -->
    <section class="hero">
      <div class="hero-content">
        <h1>闲鱼多账号自动化运营平台</h1>
        <p class="hero-sub">从消息同步到自动发货，从风控解题到 AI 客服，一站式托管你的闲鱼生意</p>
        <div class="hero-cta">
          <button class="cta-primary" @click="goLogin">立即登录</button>
          <a class="cta-link" href="#features">了解功能</a>
        </div>
      </div>
    </section>

    <!-- 核心特性 -->
    <section id="features" class="features">
      <h2 class="section-title">核心能力</h2>
      <div class="feature-grid">
        <div class="feature-card" v-for="f in features" :key="f.title">
          <div class="feature-icon">{{ f.icon }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 使用场景 -->
    <section class="scenes">
      <h2 class="section-title">使用场景</h2>
      <div class="scene-grid">
        <div class="scene-card" v-for="s in scenes" :key="s.title">
          <h3>{{ s.title }}</h3>
          <p>{{ s.desc }}</p>
        </div>
      </div>
    </section>

    <!-- 数据指标 -->
    <section class="stats">
      <div class="stats-inner">
        <div class="stat-item" v-for="s in stats" :key="s.label">
          <div class="stat-num">{{ s.num }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="bottom-cta">
      <h2>开始托管你的闲鱼生意</h2>
      <button class="cta-primary" @click="goLogin">登录控制台</button>
    </section>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 闲鱼管家 · Xianyu Manager</span>
        <div class="footer-links">
          <a href="/service">服务协议</a>
          <a href="/privacy">隐私政策</a>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goLogin = () => {
  router.push('/login')
}

const features = [
  { icon: '🔁', title: '消息自动同步', desc: '定时拉取闲鱼 IM 消息，风控滑块自动解题，多账号会话统一管理' },
  { icon: '🤖', title: 'AI 自动客服', desc: '接入大模型自动回复买家咨询，知识库 + 多轮会话，人工兜底无缝切换' },
  { icon: '📦', title: '虚拟发货', desc: '卡密/网盘类商品支付后自动发货，定时扫描重试，自动确认收货' },
  { icon: '🛡️', title: '风控熔断', desc: '自动识别风控 punish，滑块 CDP 解题，失败自动熔断保护账号' },
  { icon: '🏪', title: '商品运营', desc: '批量上下架、擦亮、市场情报、关键词趋势分析，数据驱动选品' },
  { icon: '👤', title: '买家画像', desc: '从消息行为自动构建买家画像，砍价/成单/售后偏好一目了然' },
  { icon: '🔔', title: '消息通知', desc: '新订单/退款/评价/异常多渠道推送，重试队列保证送达' },
  { icon: '🌐', title: '网盘存储', desc: '内置 OpenList 网盘，自动安装启停，分享链接一键生成' },
  { icon: '📊', title: '监控审计', desc: '定时监控任务、操作审计日志、实时大屏，运营全程可追溯' }
]

const scenes = [
  { title: '多店托管', desc: '一个控制台托管数十个闲鱼账号，消息统一收发，订单统一处理' },
  { title: '虚拟商品自动化', desc: '卡密/网盘商品支付即发货，无需人工值守，7×24 自动运营' },
  { title: 'AI 客服降本', desc: '常见咨询 AI 自动回复，复杂问题人工接管，客服成本降 80%' },
  { title: '数据驱动运营', desc: '市场情报 + 买家画像 + 订单趋势，用数据指导选品和定价' }
]

const stats = [
  { num: '20+', label: '业务模块闭环' },
  { num: '7×24', label: '自动运营托管' },
  { num: '80%', label: '客服成本下降' },
  { num: '2min', label: '订单同步延迟' }
]
</script>

<style scoped>
.landing {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', sans-serif;
  color: #1a1a1a;
  background: #fff;
  min-height: 100vh;
}

/* 顶部导航 */
.nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid #f0f0f0;
}
.nav-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.logo {
  font-size: 20px;
  font-weight: 700;
  color: #1a1a1a;
}
.tag {
  font-size: 12px;
  color: #999;
  letter-spacing: 0.5px;
}
.login-btn {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #4f46e5;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.login-btn:hover {
  background: #4338ca;
}

/* Hero */
.hero {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 50%, #2563eb 100%);
  padding: 120px 24px 100px;
  text-align: center;
  color: #fff;
}
.hero-content {
  max-width: 800px;
  margin: 0 auto;
}
.hero h1 {
  font-size: 44px;
  font-weight: 800;
  margin: 0 0 20px;
  letter-spacing: -0.5px;
}
.hero-sub {
  font-size: 18px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.88);
  margin: 0 0 36px;
}
.hero-cta {
  display: flex;
  gap: 16px;
  justify-content: center;
  align-items: center;
}
.cta-primary {
  padding: 14px 36px;
  font-size: 16px;
  font-weight: 600;
  color: #4f46e5;
  background: #fff;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;
}
.cta-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}
.cta-link {
  color: #fff;
  font-size: 15px;
  text-decoration: none;
  opacity: 0.9;
}
.cta-link:hover {
  opacity: 1;
}

/* 通用 section 标题 */
.section-title {
  font-size: 32px;
  font-weight: 700;
  text-align: center;
  margin: 0 0 48px;
  color: #1a1a1a;
}

/* 核心特性 */
.features {
  max-width: 1200px;
  margin: 0 auto;
  padding: 80px 24px;
}
.feature-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 24px;
}
.feature-card {
  padding: 32px 24px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 16px;
  transition: transform 0.15s, box-shadow 0.15s;
}
.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  border-color: #e5e5e5;
}
.feature-icon {
  font-size: 36px;
  margin-bottom: 16px;
}
.feature-card h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 10px;
}
.feature-card p {
  font-size: 14px;
  line-height: 1.6;
  color: #666;
  margin: 0;
}

/* 使用场景 */
.scenes {
  background: #f9fafb;
  padding: 80px 24px;
}
.scene-grid {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}
.scene-card {
  padding: 28px 24px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #f0f0f0;
}
.scene-card h3 {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 10px;
  color: #4f46e5;
}
.scene-card p {
  font-size: 14px;
  line-height: 1.6;
  color: #666;
  margin: 0;
}

/* 数据指标 */
.stats {
  background: #1a1a2e;
  padding: 72px 24px;
}
.stats-inner {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  text-align: center;
}
.stat-num {
  font-size: 40px;
  font-weight: 800;
  color: #fff;
  margin-bottom: 8px;
}
.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.65);
}

/* 底部 CTA */
.bottom-cta {
  text-align: center;
  padding: 100px 24px;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
}
.bottom-cta h2 {
  font-size: 32px;
  font-weight: 700;
  color: #fff;
  margin: 0 0 28px;
}

/* 页脚 */
.footer {
  background: #111;
  padding: 24px;
  border-top: 1px solid #222;
}
.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #888;
}
.footer-links {
  display: flex;
  gap: 20px;
}
.footer-links a {
  color: #888;
  text-decoration: none;
}
.footer-links a:hover {
  color: #fff;
}

/* 响应式 */
@media (max-width: 768px) {
  .hero h1 { font-size: 28px; }
  .hero-sub { font-size: 15px; }
  .section-title { font-size: 24px; }
  .stats-inner { grid-template-columns: repeat(2, 1fr); }
  .footer-inner { flex-direction: column; gap: 10px; }
}
</style>
