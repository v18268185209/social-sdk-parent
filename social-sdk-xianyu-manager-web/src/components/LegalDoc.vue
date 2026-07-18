<template>
  <div class="legal-page">
    <div class="bg-layer bg-aurora"></div>
    <div class="bg-grid"></div>

    <div class="legal-card">
      <header class="legal-header">
        <div class="legal-brand">
          <svg class="brand-mark" viewBox="0 0 64 64" width="34" height="34" aria-hidden="true">
            <defs>
              <linearGradient id="markGradLegal" x1="0" y1="0" x2="1" y2="1">
                <stop offset="0" stop-color="#7c6cff" />
                <stop offset="1" stop-color="#22d3ee" />
              </linearGradient>
            </defs>
            <rect x="2" y="2" width="60" height="60" rx="16" fill="url(#markGradLegal)" />
            <g fill="#ffffff">
              <ellipse cx="25" cy="34" rx="15" ry="9" />
              <path d="M40 34 L54 23 L54 45 Z" />
              <circle cx="19" cy="31" r="2.4" fill="#11163a" />
              <path d="M44 13 l2 5.5 5.5 2 -5.5 2 -2 5.5 -2 -5.5 -5.5 -2 5.5 -2 z" fill="#fbbf24" />
            </g>
          </svg>
          <span class="brand-name">AI鱼多宝</span>
        </div>
        <router-link to="/login" class="back-link">
          <el-icon><ArrowLeft /></el-icon> 返回登录
        </router-link>
      </header>

      <div class="legal-body">
        <h1 class="legal-title">{{ title }}</h1>
        <p class="legal-updated">最后更新：{{ updatedAt }}</p>
        <article class="legal-content">
          <slot />
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  updatedAt: { type: String, default: '' }
})
</script>

<style scoped>
.legal-page {
  position: relative;
  min-height: 100vh;
  padding: 40px 16px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  background: #070b1e;
  overflow: hidden;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', Arial, sans-serif;
}
.bg-layer { position: absolute; inset: 0; pointer-events: none; }
.bg-aurora {
  background:
    radial-gradient(55% 50% at 18% 22%, rgba(124, 108, 255, 0.35), transparent 60%),
    radial-gradient(50% 50% at 84% 78%, rgba(34, 211, 238, 0.28), transparent 60%),
    linear-gradient(160deg, #070b1e 0%, #0d1230 50%, #070b1e 100%);
}
.bg-grid {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 46px 46px;
  -webkit-mask-image: radial-gradient(ellipse at center, #000 20%, transparent 80%);
  mask-image: radial-gradient(ellipse at center, #000 20%, transparent 80%);
}
.legal-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 880px;
  border-radius: 22px;
  background: rgba(13, 18, 48, 0.6);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
  overflow: hidden;
}
.legal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 28px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(160deg, rgba(124, 108, 255, 0.16), rgba(34, 211, 238, 0.06));
}
.legal-brand { display: flex; align-items: center; gap: 12px; }
.brand-mark { filter: drop-shadow(0 4px 12px rgba(124, 108, 255, 0.5)); }
.brand-name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1px;
  background: linear-gradient(90deg, #b3a4ff, #5ce0f0, #fbbf24, #b3a4ff);
  background-size: 300% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: gradientFlow 5s linear infinite;
}
@keyframes gradientFlow {
  0% { background-position: 0% 50%; }
  100% { background-position: 300% 50%; }
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #9fb3ff;
  font-size: 14px;
  text-decoration: none;
  transition: color 0.2s;
}
.back-link:hover { color: #5ce0f0; }

.legal-body {
  max-height: calc(100vh - 160px);
  overflow-y: auto;
  padding: 32px 40px 44px;
}
.legal-title {
  color: #fff;
  font-size: 26px;
  font-weight: 700;
}
.legal-updated {
  color: rgba(226, 232, 240, 0.5);
  font-size: 13px;
  margin: 8px 0 24px;
}
.legal-content { color: rgba(226, 232, 240, 0.82); line-height: 1.85; font-size: 14.5px; }
.legal-content :deep(h2) {
  color: #fff;
  font-size: 17px;
  font-weight: 600;
  margin: 26px 0 10px;
  padding-left: 12px;
  border-left: 3px solid #22d3ee;
}
.legal-content :deep(p) { margin: 0 0 12px; }
.legal-content :deep(ul) { margin: 0 0 14px; padding-left: 22px; }
.legal-content :deep(li) { margin-bottom: 6px; }
.legal-content :deep(strong) { color: #fff; }
.legal-content :deep(a) { color: #5ce0f0; }

@media (max-width: 640px) {
  .legal-body { padding: 24px 20px 32px; }
}
</style>
