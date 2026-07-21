<template>
  <div class="login-page">
    <!-- 动态背景 -->
    <div class="bg-layer bg-aurora"></div>
    <div class="bg-layer bg-grid"></div>
    <div class="stars stars-1"></div>
    <div class="stars stars-2"></div>
    <div class="stars stars-3"></div>
    <div class="shooting"></div>
    <div class="orb orb-1"></div>
    <div class="orb orb-2"></div>
    <div class="orb orb-3"></div>

    <div class="login-split">
      <!-- 旋转流光边框 -->
      <span class="glow-border"></span>
      <span class="card-shine"></span>

      <!-- 品牌展示区 -->
      <section class="brand-panel">
        <div class="brand-top">
          <div class="brand-logo">
            <div class="logo-halo">
              <svg class="brand-mark" viewBox="0 0 64 64" width="52" height="52" aria-hidden="true">
                <defs>
                  <linearGradient id="markGrad" x1="0" y1="0" x2="1" y2="1">
                    <stop offset="0" stop-color="#7c6cff" />
                    <stop offset="1" stop-color="#22d3ee" />
                  </linearGradient>
                </defs>
                <rect x="2" y="2" width="60" height="60" rx="16" fill="url(#markGrad)" />
                <g fill="#ffffff">
                  <ellipse cx="25" cy="34" rx="15" ry="9" />
                  <path d="M40 34 L54 23 L54 45 Z" />
                  <circle cx="19" cy="31" r="2.4" fill="#11163a" />
                  <path d="M44 13 l2 5.5 5.5 2 -5.5 2 -2 5.5 -2 -5.5 -5.5 -2 5.5 -2 z" fill="#fbbf24" />
                </g>
              </svg>
            </div>
            <span class="brand-name">AI鱼多宝</span>
          </div>

          <h1 class="brand-title">AI 驱动的<br />闲鱼经营智能平台</h1>
          <p class="brand-sub">
            统一管理多账号、AI 自动客服与实时数据看板，<br />
            让闲置交易更高效、更省心。
          </p>
        </div>

        <ul class="feature-list">
          <li>
            <el-icon class="feature-icon"><Cpu /></el-icon>
            <div>
              <div class="feature-title">AI 智能客服</div>
              <div class="feature-desc">自动回复买家咨询，7×24 小时不间断</div>
            </div>
          </li>
          <li>
            <el-icon class="feature-icon"><Connection /></el-icon>
            <div>
              <div class="feature-title">多账号统一管控</div>
              <div class="feature-desc">一站式管理所有闲鱼店铺与风控状态</div>
            </div>
          </li>
          <li>
            <el-icon class="feature-icon"><DataLine /></el-icon>
            <div>
              <div class="feature-title">实时数据看板</div>
              <div class="feature-desc">经营、消息、订单动态尽在掌握</div>
            </div>
          </li>
        </ul>

        <div class="brand-footer">© 2026 AI鱼多宝 · 让每一笔交易更省心</div>
      </section>

      <!-- 登录表单区 -->
      <section class="form-panel">
        <div class="login-card">
          <div class="form-header">
            <h2>欢迎回来</h2>
            <p>请输入账号信息登录管理系统</p>
          </div>

          <el-form :model="form" @submit.prevent="handleLogin">
            <el-form-item>
              <el-input
                v-model="form.username"
                placeholder="用户名 / 手机号"
                prefix-icon="User"
                size="large"
              />
            </el-form-item>
            <el-form-item>
              <el-input
                v-model="form.password"
                type="password"
                placeholder="密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keyup.enter="handleLogin"
              />
            </el-form-item>
            <el-form-item>
              <el-button
                class="login-btn"
                type="primary"
                size="large"
                :loading="loading"
                style="width: 100%"
                @click="handleLogin"
              >
                <span class="btn-text">登 录</span>
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            登录即代表同意
            <router-link to="/service" class="legal-link">《服务协议》</router-link>
            与
            <router-link to="/privacy" class="legal-link">《隐私政策》</router-link>
          </div>
        </div>
      </section>

      <div class="page-footer">
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
      </div>

      <el-dialog v-model="qrVisible" title="扫码添加微信" width="360px" align-center class="qr-dialog">
        <div class="qr-wrap">
          <el-image :src="qrSrc" fit="contain" class="qr-img" :preview-src-list="[qrSrc]" />
          <p class="qr-tip">请使用微信「扫一扫」添加：<strong>worker_680</strong></p>
          <el-button type="primary" class="qr-copy" @click="copyWechat">复制微信号</el-button>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const form = ref({ username: '', password: '' })
const loading = ref(false)

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const ok = await authStore.login(form.value.username, form.value.password)
    if (ok) {
      ElMessage.success('登录成功')
      router.push('/app/dashboard')
    }
  } catch (e) {
    ElMessage.error('登录失败')
  } finally {
    loading.value = false
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
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding-bottom: 64px;
  overflow: hidden;
  background: #070b1e;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', Arial, sans-serif;
}

/* ===== 动态背景 ===== */
.bg-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.bg-aurora {
  background:
    radial-gradient(55% 50% at 18% 22%, rgba(124, 108, 255, 0.45), transparent 60%),
    radial-gradient(50% 50% at 84% 78%, rgba(34, 211, 238, 0.35), transparent 60%),
    radial-gradient(42% 42% at 72% 16%, rgba(251, 191, 36, 0.16), transparent 60%),
    linear-gradient(160deg, #070b1e 0%, #0d1230 50%, #070b1e 100%);
  animation: auroraShift 18s ease-in-out infinite alternate;
}
@keyframes auroraShift {
  0% { transform: scale(1) translate(0, 0); }
  100% { transform: scale(1.12) translate(-2%, -2%); }
}
.bg-grid {
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.04) 1px, transparent 1px);
  background-size: 46px 46px;
  -webkit-mask-image: radial-gradient(ellipse at center, #000 25%, transparent 78%);
  mask-image: radial-gradient(ellipse at center, #000 25%, transparent 78%);
}

/* ===== 闪烁星星 ===== */
.stars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.stars::before,
.stars::after {
  content: '';
  position: absolute;
  inset: -50%;
  background-repeat: repeat;
}
.stars-1 {
  background-image:
    radial-gradient(1.5px 1.5px at 12% 18%, #fff, transparent),
    radial-gradient(1.5px 1.5px at 28% 62%, #cfe9ff, transparent),
    radial-gradient(1px 1px at 45% 30%, #fff, transparent),
    radial-gradient(2px 2px at 62% 75%, #fff, transparent),
    radial-gradient(1.5px 1.5px at 78% 44%, #d8c9ff, transparent),
    radial-gradient(1px 1px at 88% 12%, #fff, transparent),
    radial-gradient(1.5px 1.5px at 8% 82%, #bff3ff, transparent),
    radial-gradient(1px 1px at 55% 92%, #fff, transparent);
  animation: twinkle 3.5s ease-in-out infinite;
}
.stars-2 {
  background-image:
    radial-gradient(1px 1px at 20% 40%, #fff, transparent),
    radial-gradient(1.5px 1.5px at 35% 78%, #a9d8ff, transparent),
    radial-gradient(1px 1px at 52% 20%, #fff, transparent),
    radial-gradient(1.5px 1.5px at 70% 58%, #fff, transparent),
    radial-gradient(1px 1px at 83% 84%, #e2d4ff, transparent),
    radial-gradient(1.5px 1.5px at 95% 34%, #fff, transparent);
  animation: twinkle 4.8s ease-in-out infinite 0.8s;
}
.stars-3 {
  background-image:
    radial-gradient(1px 1px at 15% 55%, #fff, transparent),
    radial-gradient(1px 1px at 40% 10%, #bcefff, transparent),
    radial-gradient(1.5px 1.5px at 66% 36%, #fff, transparent),
    radial-gradient(1px 1px at 90% 66%, #fff, transparent),
    radial-gradient(1px 1px at 5% 30%, #d9caff, transparent);
  animation: twinkle 6s ease-in-out infinite 1.6s;
}
@keyframes twinkle {
  0%, 100% { opacity: 0.25; }
  50% { opacity: 1; }
}

/* ===== 流星 ===== */
.shooting {
  position: absolute;
  top: -10%;
  left: -10%;
  width: 160px;
  height: 2px;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.9), transparent);
  border-radius: 2px;
  filter: drop-shadow(0 0 6px #fff);
  transform: rotate(35deg);
  opacity: 0;
  animation: shoot 7s ease-in-out infinite 2s;
  pointer-events: none;
}
@keyframes shoot {
  0% { transform: translate(0, 0) rotate(35deg); opacity: 0; }
  6% { opacity: 1; }
  16% { transform: translate(120vw, 84vh) rotate(35deg); opacity: 0; }
  100% { opacity: 0; }
}

.orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(50px);
  opacity: 0.5;
  pointer-events: none;
}
.orb-1 {
  width: 240px; height: 240px;
  background: radial-gradient(circle, #7c6cff, transparent 70%);
  top: 8%; left: 6%;
  animation: float1 15s ease-in-out infinite;
}
.orb-2 {
  width: 200px; height: 200px;
  background: radial-gradient(circle, #22d3ee, transparent 70%);
  bottom: 10%; right: 10%;
  animation: float2 19s ease-in-out infinite;
}
.orb-3 {
  width: 160px; height: 160px;
  background: radial-gradient(circle, #fbbf24, transparent 70%);
  top: 55%; left: 42%;
  opacity: 0.3;
  animation: float3 22s ease-in-out infinite;
}
@keyframes float1 {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(30px, 40px); }
}
@keyframes float2 {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-36px, -28px); }
}
@keyframes float3 {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(24px, -30px); }
}

/* ===== 分屏容器 ===== */
.login-split {
  position: relative;
  z-index: 2;
  width: 94%;
  max-width: 1040px;
  display: flex;
  border-radius: 26px;
  overflow: hidden;
  background: rgba(13, 18, 48, 0.55);
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.55);
  animation: splitIn 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes splitIn {
  from { opacity: 0; transform: translateY(26px) scale(0.985); }
  to { opacity: 1; transform: none; }
}

/* 旋转流光边框 */
.glow-border {
  position: absolute;
  inset: -2px;
  border-radius: 28px;
  padding: 2px;
  background: conic-gradient(
    from 0deg,
    transparent 0deg,
    #7c6cff 60deg,
    #22d3ee 130deg,
    transparent 200deg,
    #fbbf24 300deg,
    transparent 360deg
  );
  -webkit-mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  -webkit-mask-composite: xor;
  mask: linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0);
  mask-composite: exclude;
  opacity: 0.85;
  pointer-events: none;
  z-index: 5;
  animation: spinBorder 6s linear infinite;
}
@keyframes spinBorder {
  to { transform: rotate(360deg); }
}

/* 顶部斜向高光扫过 */
.card-shine {
  position: absolute;
  top: 0;
  left: -60%;
  width: 45%;
  height: 100%;
  background: linear-gradient(
    100deg,
    transparent,
    rgba(255, 255, 255, 0.14),
    transparent
  );
  transform: skewX(-18deg);
  pointer-events: none;
  z-index: 4;
  animation: cardShine 6.5s ease-in-out infinite 1.2s;
}
@keyframes cardShine {
  0% { left: -60%; }
  35%, 100% { left: 130%; }
}

/* ===== 品牌区 ===== */
.brand-panel {
  position: relative;
  z-index: 3;
  flex: 1.15;
  padding: 54px 48px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background: linear-gradient(160deg, rgba(124, 108, 255, 0.2), rgba(34, 211, 238, 0.08));
  animation: fadeRight 0.8s ease both 0.1s;
}
@keyframes fadeRight {
  from { opacity: 0; transform: translateX(-26px); }
  to { opacity: 1; transform: none; }
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 14px;
}
.logo-halo {
  position: relative;
  display: inline-flex;
  border-radius: 16px;
}
.logo-halo::before {
  content: '';
  position: absolute;
  inset: -8px;
  border-radius: 20px;
  background: radial-gradient(circle, rgba(124, 108, 255, 0.6), transparent 70%);
  filter: blur(10px);
  z-index: -1;
  animation: haloPulse 2.6s ease-in-out infinite;
}
@keyframes haloPulse {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 1; transform: scale(1.12); }
}
.brand-mark {
  filter: drop-shadow(0 6px 16px rgba(124, 108, 255, 0.6));
  animation: markFloat 4s ease-in-out infinite;
}
@keyframes markFloat {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-5px) rotate(-3deg); }
}
.brand-name {
  font-size: 26px;
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
.brand-title {
  font-size: 30px;
  font-weight: 700;
  color: #fff;
  line-height: 1.35;
  margin-top: 30px;
  text-shadow: 0 2px 24px rgba(124, 108, 255, 0.35);
}
.brand-sub {
  color: rgba(226, 232, 240, 0.72);
  margin-top: 16px;
  line-height: 1.75;
  font-size: 15px;
}
.feature-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin: 34px 0;
}
.feature-list li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.feature-icon {
  color: #5ce0f0;
  font-size: 22px;
  background: rgba(34, 211, 238, 0.12);
  padding: 10px;
  border-radius: 12px;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.2), 0 0 18px rgba(34, 211, 238, 0.25);
}
.feature-title {
  color: #fff;
  font-weight: 600;
  font-size: 15px;
}
.feature-desc {
  color: rgba(226, 232, 240, 0.6);
  font-size: 13px;
  margin-top: 3px;
}
.brand-footer {
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

/* ===== 表单区 ===== */
.form-panel {
  position: relative;
  z-index: 3;
  flex: 0.85;
  padding: 54px 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 11, 30, 0.55);
  animation: fadeLeft 0.8s ease both 0.1s;
}
@keyframes fadeLeft {
  from { opacity: 0; transform: translateX(26px); }
  to { opacity: 1; transform: none; }
}
.login-card {
  width: 100%;
  max-width: 360px;
}
.form-header h2 {
  color: #fff;
  font-size: 24px;
  font-weight: 700;
}
.form-header p {
  color: rgba(226, 232, 240, 0.6);
  margin-top: 8px;
  font-size: 14px;
  margin-bottom: 28px;
}

/* Element Plus 暗色适配 */
.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
  border-radius: 12px;
  padding: 2px 14px;
  transition: box-shadow 0.25s ease;
}
.login-card :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(34, 211, 238, 0.4) inset;
}
.login-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #22d3ee inset, 0 0 0 3px rgba(34, 211, 238, 0.18), 0 0 22px rgba(34, 211, 238, 0.28);
}
.login-card :deep(.el-input__inner) {
  color: #e2e8f0;
  height: 46px;
}
.login-card :deep(.el-input__inner::placeholder) {
  color: rgba(226, 232, 240, 0.4);
}
.login-card :deep(.el-input__prefix) {
  color: rgba(226, 232, 240, 0.5);
}

.login-btn {
  position: relative;
  overflow: hidden;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #7c6cff, #22d3ee) !important;
  background-size: 200% 100% !important;
  box-shadow: 0 12px 30px rgba(124, 108, 255, 0.42), 0 0 20px rgba(34, 211, 238, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  animation: btnGradient 4s ease infinite;
}
@keyframes btnGradient {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}
.login-btn::after {
  content: '';
  position: absolute;
  top: 0;
  left: -80%;
  width: 50%;
  height: 100%;
  background: linear-gradient(100deg, transparent, rgba(255, 255, 255, 0.5), transparent);
  transform: skewX(-20deg);
  animation: btnSheen 3.2s ease-in-out infinite 1s;
}
@keyframes btnSheen {
  0% { left: -80%; }
  40%, 100% { left: 140%; }
}
.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 46px rgba(34, 211, 238, 0.55), 0 0 28px rgba(124, 108, 255, 0.45);
}
.login-btn:active {
  transform: translateY(0);
}
.btn-text { position: relative; z-index: 1; }

.form-footer {
  text-align: center;
  color: rgba(226, 232, 240, 0.4);
  font-size: 12px;
  margin-top: 18px;
}
.legal-link {
  color: #5ce0f0;
  text-decoration: none;
  transition: color 0.2s, text-shadow 0.2s;
}
.legal-link:hover {
  color: #7fe9f5;
  text-shadow: 0 0 10px rgba(34, 211, 238, 0.6);
}

/* 页脚业务合作 */
.page-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16px;
  z-index: 6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px 22px;
  padding: 0 16px;
  pointer-events: none;
  color: rgba(226, 232, 240, 0.5);
  font-size: 13px;
}
.coop-label {
  color: rgba(255, 255, 255, 0.55);
  font-weight: 600;
  letter-spacing: 1px;
}
.coop-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: rgba(226, 232, 240, 0.62);
  font-size: 13px;
  text-decoration: none;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 9px;
  pointer-events: auto;
  transition: color 0.2s, background 0.2s, text-shadow 0.2s;
}
.coop-item:hover {
  color: #5ce0f0;
  background: rgba(34, 211, 238, 0.08);
  text-shadow: 0 0 10px rgba(34, 211, 238, 0.5);
}
.coop-ico {
  width: 15px;
  height: 15px;
}
.coop-ico.wechat {
  color: #07c160;
}

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

/* ===== 响应式：小屏改为上下堆叠，输入框占满整行 ===== */
@media (max-width: 1024px) {
  .login-split {
    flex-direction: column;
    width: 92%;
    max-width: 460px;
    border-radius: 22px;
  }
  .brand-panel {
    flex: none;
    padding: 30px 32px 18px;
  }
  .feature-list { display: none; }
  .brand-footer { display: none; }
  .brand-title { font-size: 22px; margin-top: 16px; }
  .brand-sub { margin-top: 10px; font-size: 14px; }
  .form-panel {
    flex: none;
    padding: 24px 32px 34px;
  }
  /* 小屏：页脚改为正常流，避免与协议链接重叠 */
  .page-footer {
    position: static;
    margin-top: 18px;
  }
  .login-page {
    padding-bottom: 28px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .bg-aurora, .stars, .shooting, .orb, .glow-border, .card-shine,
  .logo-halo::before, .brand-mark, .brand-name, .login-btn, .login-btn::after {
    animation: none !important;
  }
}
</style>
