<template>
  <div class="page-container">
    <div class="page-header">
      <h2>谷歌浏览器配置</h2>
      <p class="page-desc">管理 Chrome 浏览器路径、启动模式与反检测参数</p>
    </div>

    <!-- 浏览器探测卡片 -->
    <div class="card">
      <div class="card-header">
        <h3>浏览器探测</h3>
        <div class="card-actions">
          <el-button type="primary" :loading="detecting" @click="handleDetect">
            <el-icon><Search /></el-icon>
            重新探测
          </el-button>
          <el-button :loading="downloading" @click="handleDownload">
            <el-icon><Download /></el-icon>
            自动下载
          </el-button>
        </div>
      </div>

      <div class="detect-result" v-if="detectResult">
        <el-alert
          :title="detectResult.found ? `已发现 ${detectResult.type}: ${detectResult.path}` : '未发现 Chrome'"
          :type="detectResult.found ? 'success' : 'warning'"
          :closable="false"
          show-icon
        >
          <template #default>
            <div v-if="detectResult.found" class="detect-info">
              <p><strong>路径：</strong>{{ detectResult.path }}</p>
              <p><strong>类型：</strong>{{ detectResult.type }}</p>
              <el-button type="primary" size="small" @click="useDetectedPath">使用此路径</el-button>
            </div>
            <div v-else class="detect-info">
              <p>系统未安装 Chrome，可点击"自动下载"按钮获取。</p>
              <p class="muted">已搜索 {{ detectResult.searched ? detectResult.searched.length : 0 }} 个路径</p>
            </div>
          </template>
        </el-alert>
      </div>

      <div class="form-row">
        <label>可执行文件路径</label>
        <div class="input-group">
          <el-input v-model="form.executablePath" placeholder="输入 Chrome 路径" clearable />
          <el-button @click="handleValidate">校验</el-button>
        </div>
        <p class="hint">支持 Chrome、Chromium、Edge、Brave 等基于 Chromium 的浏览器</p>
      </div>
    </div>

    <!-- 启动模式卡片 -->
    <div class="card">
      <div class="card-header">
        <h3>启动模式</h3>
      </div>

      <div class="form-row">
        <label>运行模式</label>
        <el-radio-group v-model="form.headless">
          <el-radio :value="false">有界面模式</el-radio>
          <el-radio :value="true">无头模式</el-radio>
        </el-radio-group>
        <p class="hint">有界面模式兼容性与滑块成功率更高；无头模式适合服务器环境</p>
      </div>

      <div class="form-row" v-if="form.headless">
        <label>无头模式版本</label>
        <el-radio-group v-model="form.headlessMode">
          <el-radio value="new">新版 headless（推荐，Chrome 112+）</el-radio>
          <el-radio value="legacy">旧版 --headless</el-radio>
        </el-radio-group>
      </div>

      <div class="form-row">
        <label>窗口尺寸</label>
        <div class="input-row">
          <el-input-number v-model="form.windowWidth" :min="800" :max="3840" />
          <span class="sep">×</span>
          <el-input-number v-model="form.windowHeight" :min="600" :max="2160" />
        </div>
      </div>
    </div>

    <!-- 高级配置卡片 -->
    <div class="card">
      <div class="card-header">
        <h3>高级配置</h3>
      </div>

      <div class="form-row">
        <label>CDP 端口范围</label>
        <div class="input-row">
          <el-input-number v-model="form.portRangeStart" :min="1024" :max="65535" />
          <span class="sep">至</span>
          <el-input-number v-model="form.portRangeEnd" :min="1024" :max="65535" />
        </div>
        <p class="hint">每个账号独占一个 CDP 端口，端口段需足够容纳所有账号</p>
      </div>

      <div class="form-row">
        <label>用户数据目录</label>
        <el-input v-model="form.userDataDirRoot" placeholder="./chrome-profiles" />
        <p class="hint">每个账号会在此目录下创建独立子目录</p>
      </div>

      <div class="form-row">
        <label>多账号指纹噪声</label>
        <el-switch v-model="form.perAccountSeedNoise" />
        <p class="hint">按账号 seed 生成唯一 canvas/WebGL 噪声，避免指纹关联</p>
      </div>

      <div class="form-row">
        <label>启动超时（秒）</label>
        <el-input-number v-model="form.launchTimeoutSeconds" :min="5" :max="120" />
      </div>

      <div class="form-row">
        <label>崩溃恢复次数</label>
        <el-input-number v-model="form.maxCrashRecoveryAttempts" :min="0" :max="10" />
      </div>

      <div class="form-row">
        <label>自定义启动参数</label>
        <el-input
          v-model="customLaunchArgsText"
          type="textarea"
          :rows="4"
          placeholder="--disable-gpu&#10;--no-sandbox&#10;每行一个参数"
        />
        <p class="hint">这些参数会覆盖默认反检测参数（可选）</p>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div class="card">
      <el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 校验结果 -->
    <div class="card" v-if="validateResult">
      <h3>路径校验结果</h3>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="路径">{{ validateResult.path }}</el-descriptions-item>
        <el-descriptions-item label="存在">
          <el-tag :type="validateResult.exists ? 'success' : 'danger'">
            {{ validateResult.exists ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="可执行">
          <el-tag :type="validateResult.canExecute ? 'success' : 'danger'">
            {{ validateResult.canExecute ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatSize(validateResult.sizeBytes) }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import {
  getChromeConfig,
  detectChrome,
  saveChromeConfig,
  downloadChrome,
  validateChromePath
} from '@/api/chrome'

const form = reactive({
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
  customLaunchArgs: []
})

const customLaunchArgsText = ref('')

const detecting = ref(false)
const downloading = ref(false)
const saving = ref(false)

const detectResult = ref(null)
const validateResult = ref(null)

onMounted(() => {
  loadConfig()
})

async function loadConfig() {
  try {
    const res = await getChromeConfig()
    if (res.success && res.data) {
      const d = res.data
      Object.assign(form, {
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
        customLaunchArgs: d.customLaunchArgs || []
      })
      if (d.customLaunchArgs && d.customLaunchArgs.length) {
        customLaunchArgsText.value = d.customLaunchArgs.join('\n')
      }
      if (d.detected) {
        detectResult.value = d.detected
      }
    }
  } catch (e) {
    ElMessage.error('加载配置失败：' + (e.message || e))
  }
}

async function handleDetect() {
  detecting.value = true
  try {
    const res = await detectChrome()
    if (res.success && res.data) {
      detectResult.value = res.data
      if (res.data.found) {
        ElMessage.success(`发现浏览器：${res.data.type}`)
      } else {
        ElMessage.warning('未发现浏览器，请尝试手动下载')
      }
    }
  } catch (e) {
    ElMessage.error('探测失败：' + (e.message || e))
  } finally {
    detecting.value = false
  }
}

function useDetectedPath() {
  if (detectResult.value && detectResult.value.path) {
    form.executablePath = detectResult.value.path
    ElMessage.success('已填入路径，请点击"保存配置"保存')
  }
}

async function handleValidate() {
  if (!form.executablePath) {
    ElMessage.warning('请先输入路径')
    return
  }
  try {
    const res = await validateChromePath(form.executablePath)
    if (res.success && res.data) {
      validateResult.value = res.data
      if (res.data.valid) {
        ElMessage.success('路径校验通过')
      } else {
        ElMessage.warning('路径不可用：' + (res.data.reason || '未知原因'))
      }
    }
  } catch (e) {
    ElMessage.error('校验失败：' + (e.message || e))
  }
}

async function handleDownload() {
  downloading.value = true
  try {
    const res = await downloadChrome()
    if (res.success && res.data) {
      ElMessage.success(res.data.message || '下载完成')
      if (res.data.path) {
        form.executablePath = res.data.path
      }
    } else {
      ElMessage.error(res.message || '下载失败')
    }
  } catch (e) {
    ElMessage.error('下载失败：' + (e.message || e))
  } finally {
    downloading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    // 处理自定义参数文本 -> 数组
    const args = customLaunchArgsText.value
      .split('\n')
      .map(s => s.trim())
      .filter(Boolean)

    const payload = {
      ...form,
      customLaunchArgs: args.length ? args : null
    }
    const res = await saveChromeConfig(payload)
    if (res.success) {
      ElMessage.success('配置已保存')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

function handleReset() {
  loadConfig()
  ElMessage.info('已重置为当前配置')
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let n = bytes
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024
    i++
  }
  return n.toFixed(1) + ' ' + units[i]
}
</script>

<style scoped>
.page-container {
  padding: 20px;
  max-width: 900px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  color: #888;
  font-size: 13px;
}

.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.card-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.form-row {
  margin-bottom: 18px;
}

.form-row:last-child {
  margin-bottom: 0;
}

.form-row label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 6px;
  color: #333;
}

.hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: #999;
}

.muted {
  color: #aaa;
}

.input-group {
  display: flex;
  gap: 8px;
}

.input-group :deep(.el-input) {
  flex: 1;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sep {
  color: #aaa;
}

.detect-result {
  margin-bottom: 16px;
}

.detect-info p {
  margin: 4px 0;
  font-size: 13px;
}
</style>
