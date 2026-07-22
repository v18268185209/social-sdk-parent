<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <span>商品管理</span>
          <el-button v-if="activeTab === 'xianyu'" type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 创建商品
          </el-button>
          <el-button v-else type="primary" @click="showLocalCreateDialog = true">
            <el-icon><Plus /></el-icon> 新建本地商品
          </el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" style="margin-bottom: 16px;">
        <el-tab-pane label="闲鱼商品" name="xianyu">
          <el-tag type="info" size="small" style="margin-bottom: 12px;">从闲鱼同步的在线商品，可编辑/改价/上下架</el-tag>
        </el-tab-pane>
        <el-tab-pane label="本地商品" name="local">
          <el-tag type="warning" size="small" style="margin-bottom: 12px;">自建商品（草稿/待发布），发布成功后自动清理</el-tag>
        </el-tab-pane>
      </el-tabs>

      <el-form inline style="margin-bottom: 16px;">
        <el-form-item label="账号">
          <el-select v-model="filters.accountId" placeholder="全部" clearable style="width: 150px;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索标题" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px;">
            <template v-if="activeTab === 'xianyu'">
              <el-option label="在售" value="ON_SALE" />
              <el-option label="下架" value="OFF_SALE" />
              <el-option label="草稿" value="DRAFT" />
            </template>
            <template v-else>
              <el-option label="草稿" value="DRAFT" />
              <el-option label="待发布" value="PENDING" />
              <el-option label="发布中" value="PUBLISHING" />
              <el-option label="失败" value="FAILED" />
            </template>
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeTab === 'xianyu'">
          <el-button
            type="primary"
            :disabled="!filters.accountId"
            :loading="syncing"
            @click="handleSyncFromXianyu"
          >
            <el-icon><Refresh /></el-icon> 同步闲鱼
          </el-button>
        </el-form-item>
        <el-form-item v-if="activeTab === 'xianyu'">
          <el-button type="success" @click="showAiOptimizeDialog = true">
            <el-icon><MagicStick /></el-icon> AI 优化文案
          </el-button>
        </el-form-item>
        <el-form-item v-if="activeTab === 'local'">
          <el-button type="success" :loading="batchPublishing" @click="handleBatchPublish">
            <el-icon><UploadFilled /></el-icon> 批量发布
          </el-button>
        </el-form-item>
        <el-form-item v-if="activeTab === 'local'">
          <el-button type="warning" @click="showImportDialog = true">
            <el-icon><Download /></el-icon> 批量导入
          </el-button>
        </el-form-item>
        <el-form-item v-if="activeTab === 'local'">
          <el-button type="info" @click="handleDownloadTemplate">
            <el-icon><DocumentCopy /></el-icon> 下载 CSV 模板
          </el-button>
        </el-form-item>
      </el-form>

      <el-table :data="activeTab === 'xianyu' ? products : localProducts" stripe v-loading="loading" @selection-change="onLocalSelectionChange">
        <!-- 闲鱼商品列 -->
        <template v-if="activeTab === 'xianyu'">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="80" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="viewCount" label="浏览" width="70" />
          <el-table-column prop="favoriteCount" label="收藏" width="70" />
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="viewDetail(row)">详情</el-button>
              <el-button size="small" @click="editPrice(row)">改价</el-button>
              <el-button size="small" @click="editStock(row)">改库存</el-button>
              <el-button v-if="row.status === 'ON_SALE'" size="small" type="warning" @click="shelfOff(row)">下架</el-button>
              <el-button size="small" type="danger" @click="deleteProduct(row)">删除</el-button>
            </template>
          </el-table-column>
        </template>

        <!-- 本地商品列 -->
        <template v-else>
          <el-table-column type="selection" width="48" />
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column label="库存" width="80">
            <template #default="{ row }">
              <span>{{ row.stock }}</span>
            </template>
          </el-table-column>
          <el-table-column label="发货池" width="90">
            <template #default="{ row }">
              <template v-if="row.goodsType === 'VIRTUAL' && (row.deliverType === 'CARD' || row.deliverType === 'ACCOUNT')">
                <el-tooltip :content="`${poolCount(row)} 个已录入`" placement="top">
                  <el-tag size="small" :type="poolCount(row) >= row.stock ? 'success' : 'warning'">
                    {{ poolCount(row) }}/{{ row.stock }}
                  </el-tag>
                </el-tooltip>
              </template>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="发布账号" width="140">
            <template #default="{ row }">{{ accountName(row.accountId) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="localStatusType(row.status)">{{ localStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="viewDetail(row, 'local')">详情</el-button>
              <el-button size="small" @click="editLocalProduct(row)">编辑</el-button>
              <el-button size="small" type="success" @click="publishLocalProduct(row)" :loading="row._publishing">发布</el-button>
              <el-button size="small" type="danger" @click="deleteLocalProduct(row)">删除</el-button>
            </template>
          </el-table-column>
        </template>
      </el-table>

      <el-pagination
        style="margin-top: 16px; justify-content: center;"
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="activeTab === 'xianyu' ? loadProducts() : loadLocalProducts()"
      />
    </el-card>

    <!-- 本地商品 批量导入对话框 -->
    <el-dialog v-model="showImportDialog" title="批量导入本地商品" width="780px" :close-on-click-modal="false" @close="resetImportDialog">
      <el-steps :active="importStep" finish-status="success" style="margin-bottom: 20px;">
        <el-step title="上传 CSV" />
        <el-step title="预览校验" />
        <el-step title="确认导入" />
      </el-steps>

      <!-- Step 1: 上传 -->
      <div v-if="importStep === 0">
        <el-upload drag :auto-upload="false" :on-change="handleImportFileChange" :show-file-list="false" accept=".csv">
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽 CSV 文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div style="font-size: 12px; color: #909399; margin-top: 8px;">
              列：account_name, title, price, stock, images, goods_type, deliver_type, deliver_content_template
            </div>
          </template>
        </el-upload>
        <div v-if="importFile" style="margin-top: 12px;">
          <el-tag type="success">已选择：{{ importFile.name }}</el-tag>
        </div>
        <el-divider>导入选项</el-divider>
        <el-form :model="importForm" label-width="140px">
          <el-form-item label="默认商品类型">
            <el-radio-group v-model="importForm.defaultGoodsType">
              <el-radio value="PHYSICAL">实物</el-radio>
              <el-radio value="VIRTUAL">虚拟</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="发货内容分隔符">
            <el-input v-model="importForm.deliverContentSeparator" style="width: 200px;" />
            <span style="font-size: 12px; color: #909399; margin-left: 8px;">虚拟商品卡密分隔符（默认 |||）</span>
          </el-form-item>
          <el-form-item label="去重"><el-switch v-model="importForm.deduplicate" /></el-form-item>
          <el-form-item label="重复时覆盖" v-if="importForm.deduplicate">
            <el-switch v-model="importForm.overwriteDuplicate" />
          </el-form-item>
        </el-form>
      </div>

      <!-- Step 2: 预览 -->
      <div v-if="importStep === 1">
        <el-alert v-if="importPreview" type="info" :closable="false" style="margin-bottom: 16px;">
          <template #title>
            共 {{ importPreview.totalRows }} 行，有效 {{ importPreview.validRows }} 行，
            错误 {{ (importPreview.errors || []).length }} 条，重复 {{ importPreview.duplicateCount }} 条
          </template>
        </el-alert>
        <el-table v-if="importPreview && importPreview.items && importPreview.items.length" :data="importPreview.items" stripe max-height="300">
          <el-table-column prop="rowNum" label="行号" width="60" />
          <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
          <el-table-column label="价格" width="80">
            <template #default="{ row }">¥{{ row.price }}</template>
          </el-table-column>
          <el-table-column prop="stock" label="库存" width="60" />
          <el-table-column label="类型" width="70">
            <template #default="{ row }"><el-tag size="small">{{ row.goodsType === 'VIRTUAL' ? '虚拟' : '实物' }}</el-tag></template>
          </el-table-column>
          <el-table-column label="图片" width="60">
            <template #default="{ row }">
              <el-tag v-if="row.images && row.images.length" size="small" type="success">{{ row.images.length }} 张</el-tag>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-alert v-if="importPreview && importPreview.errors && importPreview.errors.length" type="error" style="margin-top: 16px;">
          <template #title>错误详情</template>
          <div style="max-height: 120px; overflow-y: auto;">
            <div v-for="(e, idx) in importPreview.errors" :key="idx" style="font-size: 12px; line-height: 1.8;">
              第 {{ e.row }} 行 / {{ e.field }}：{{ e.message }}
            </div>
          </div>
        </el-alert>
      </div>

      <!-- Step 3: 结果 -->
      <div v-if="importStep === 2 && importResult">
        <el-result icon="success" title="导入完成">
          <template #sub-title>成功 {{ importResult.imported }} 条，跳过 {{ importResult.skipped }} 条，失败 {{ importResult.failed }} 条</template>
        </el-result>
        <el-alert v-if="importResult.errors && importResult.errors.length" type="warning" style="margin-top: 16px;">
          <template #title>失败详情</template>
          <div style="max-height: 120px; overflow-y: auto;">
            <div v-for="(e, idx) in importResult.errors" :key="idx" style="font-size: 12px; line-height: 1.8;">{{ e }}</div>
          </div>
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="importStep > 0 ? importStep-- : showImportDialog = false">{{ importStep > 0 ? '上一步' : '取消' }}</el-button>
        <el-button v-if="importStep === 0" type="primary" :loading="importUploading" :disabled="!importFile" @click="handleImportPreview">预览校验</el-button>
        <el-button v-if="importStep === 1" type="success" :loading="importUploading" @click="handleImportConfirm">确认导入</el-button>
        <el-button v-if="importStep === 2" @click="showImportDialog = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- AI 优化文案对话框 -->
    <el-dialog v-model="showAiOptimizeDialog" title="AI 商品文案优化" width="600px">
      <el-form :model="aiForm" label-width="100px">
        <el-form-item label="AI 模型">
          <el-select v-model="aiForm.modelId" placeholder="选择 AI 模型" style="width: 100%;">
            <el-option v-for="m in aiModels" :key="m.id" :label="`${m.modelName} (${m.providerName})`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名">
          <el-input v-model="aiForm.productTitle" placeholder="如：iPhone 15 Pro 256G 原色钛金属" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="aiForm.keywordsRaw" placeholder="逗号分隔，如：iPhone,15 Pro,256G,原色钛金属" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="成色">
          <el-select v-model="aiForm.condition" style="width: 150px;">
            <el-option label="全新" value="全新" />
            <el-option label="九成新" value="九成新" />
            <el-option label="八成新" value="八成新" />
            <el-option label="七成新" value="七成新" />
          </el-select>
        </el-form-item>
      </el-form>

      <el-divider />
      <el-tabs v-model="aiActiveTab">
        <el-tab-pane label="📝 标题优化" name="title">
          <el-input v-model="aiResult.title" type="textarea" :rows="3" readonly />
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="optimizeTitle" :loading="aiLoading">生成标题</el-button>
        </el-tab-pane>
        <el-tab-pane label="📄 描述优化" name="desc">
          <el-input v-model="aiResult.description" type="textarea" :rows="6" readonly />
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="optimizeDesc" :loading="aiLoading">生成描述</el-button>
        </el-tab-pane>
        <el-tab-pane label="🏷️ 关键词提取" name="kw">
          <div style="min-height: 50px;">
            <el-tag v-for="kw in aiResult.keywords" :key="kw" style="margin: 4px;">{{ kw }}</el-tag>
            <span v-if="!aiResult.keywords.length" style="color: #909399;">暂无关键词</span>
          </div>
          <el-button type="primary" size="small" style="margin-top: 8px;" @click="extractKeywords" :loading="aiLoading">提取关键词</el-button>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="showAiOptimizeDialog = false">关闭</el-button>
        <el-button type="success" @click="useAiResult" v-if="aiResult.title">应用为商品信息</el-button>
      </template>
    </el-dialog>

    <!-- 创建商品对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建商品" width="600px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="账号">
          <el-select v-model="createForm.accountId" placeholder="选择账号" style="width: 100%;" @change="onAccountChange">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="createForm.price" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="原价">
          <el-input-number v-model="createForm.originalPrice" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="库存">
          <el-input-number v-model="createForm.stock" :min="0" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="分类">
          <el-cascader
            v-model="createForm.categoryIdPath"
            :options="categoryTree"
            :props="{ value: 'catId', label: 'catName', children: 'children', checkStrictly: true, emitPath: false }"
            placeholder="留空让闲鱼 AI 按标题自动推荐分类"
            clearable
            filterable
            style="width: 100%;"
            :loading="categoryTreeLoading"
          />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">从闲鱼拉分类树选择，留空走 AI 推荐</div>
        </el-form-item>
        <el-form-item label="运费">
          <el-select v-model="createForm.deliveryChoice" style="width: 100%;">
            <el-option label="按距离计费（闲鱼默认）" value="按距离计费" />
            <el-option label="包邮" value="包邮" />
            <el-option label="一口价运费" value="一口价" />
            <el-option label="无需邮寄（虚拟商品）" value="无需邮寄" />
          </el-select>
          <el-input-number v-if="createForm.deliveryChoice === '一口价'"
            v-model="createForm.postPrice" :min="0" :precision="2"
            style="width: 100%; margin-top: 8px;" placeholder="运费金额" />
        </el-form-item>
        <el-form-item label="所在地">
          <el-input v-model="createForm.location" placeholder="留空走闲鱼账号默认所在地" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">填省市（如「杭州市」），留空走账号默认</div>
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            :file-list="imageFileList"
            list-type="picture-card"
            :limit="9"
            :on-change="(file, list) => handleUploadChange(list, 'images')"
            :on-remove="(file, list) => handleUploadRemove(list, 'images')"
            :http-request="(opts) => customUpload(opts, 'images')"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">最多 9 张图片，单张不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="视频">
          <el-upload
            :file-list="videoFileList"
            :limit="3"
            :on-change="(file, list) => handleUploadChange(list, 'videos')"
            :on-remove="(file, list) => handleUploadRemove(list, 'videos')"
            :http-request="(opts) => customUpload(opts, 'videos')"
            accept="video/*"
          >
            <el-button type="primary" plain>
              <el-icon><UploadFilled /></el-icon> 上传视频
            </el-button>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">最多 3 个视频，单个不超过 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 本地商品 创建/编辑对话框 -->
    <el-dialog v-model="showLocalCreateDialog" :title="localForm.id ? '编辑本地商品' : '新建本地商品'" width="640px">
      <el-form ref="localFormRef" :model="localForm" label-width="90px">
        <el-form-item label="发布账号" required>
          <el-select v-model="localForm.accountId" placeholder="选择要发布的闲鱼账号" style="width: 100%;">
            <el-option v-for="a in accounts" :key="a.id" :label="a.accountName" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="localForm.title" placeholder="商品标题" maxlength="30" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="售价" required>
              <el-input-number v-model="localForm.price" :min="0" :precision="2" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="原价">
              <el-input-number v-model="localForm.originalPrice" :min="0" :precision="2" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="库存" required>
              <el-input-number v-model="localForm.stock" :min="1" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商品类型">
              <el-select v-model="localForm.goodsType" style="width: 100%;">
                <el-option label="实物" value="PHYSICAL" />
                <el-option label="虚拟" value="VIRTUAL" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="发货类型" v-if="localForm.goodsType === 'VIRTUAL'">
          <el-select v-model="localForm.deliverType" style="width: 100%;">
            <el-option label="卡密" value="CARD" />
            <el-option label="账号" value="ACCOUNT" />
            <el-option label="链接" value="LINK" />
            <el-option label="网盘文件" value="FILE" />
          </el-select>
        </el-form-item>
        <el-form-item label="发货内容模板" v-if="localForm.goodsType === 'VIRTUAL'">
          <el-input v-model="localForm.deliverContentTemplate" type="textarea" :rows="3" placeholder="每行一条：卡密 / 账号 / 链接，发布后按顺序交付" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px;">支持变量 {orderNo} / {buyer}，运行时自动替换</div>
        </el-form-item>
        <el-form-item label="商品描述">
          <el-input v-model="localForm.description" type="textarea" :rows="4" placeholder="商品详细描述（可选）" />
        </el-form-item>
        <el-form-item label="图片">
          <el-upload
            :file-list="localImageFileList"
            list-type="picture-card"
            :limit="9"
            :on-change="(file, list) => handleLocalUploadChange(list)"
            :on-remove="(file, list) => handleLocalUploadRemove(list)"
            :http-request="(opts) => customLocalUpload(opts)"
            accept="image/*"
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div style="font-size: 12px; color: #909399;">最多 9 张图片，单张不超过 10MB，首张为主图</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="操作">
          <el-radio-group v-model="localForm.action">
            <el-radio value="DRAFT">保存草稿</el-radio>
            <el-radio value="PENDING">直接提交待发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showLocalCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="localSubmitting" @click="handleLocalSave">保存</el-button>
      </template>
    </el-dialog>

  <!-- 商品详情抽屉 -->
  <el-drawer v-model="showDetailDrawer" :title="detail && detail._source === 'local' ? '本地商品详情' : '商品详情'" size="50%" direction="rtl">
    <div v-if="detail" style="padding: 0 20px 20px;">
      <!-- 主图 + 视频预览 -->
      <el-carousel v-if="detail.images && detail.images.length" height="260px" style="margin-bottom: 16px; border-radius: 8px; overflow: hidden;">
        <el-carousel-item v-for="(img, idx) in detail.images" :key="idx">
          <el-image :src="normalizeImageUrl(img)" fit="contain" style="width: 100%; height: 100%;" :preview-src-list="detail.images.map(normalizeImageUrl)" />
        </el-carousel-item>
      </el-carousel>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detail._source === 'local' ? localStatusType(detail.status) : statusType(detail.status)">
            {{ detail._source === 'local' ? localStatusLabel(detail.status) : statusLabel(detail.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="价格">¥{{ detail.price }}</el-descriptions-item>
        <el-descriptions-item label="原价">¥{{ detail.originalPrice || '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存">{{ detail.stock }}</el-descriptions-item>
        <!-- 闲鱼商品字段 -->
        <template v-if="detail._source !== 'local'">
          <el-descriptions-item label="浏览">{{ detail.viewCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="收藏">{{ detail.favoriteCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="闲鱼 ID">{{ detail.itemId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="品类">{{ detail.categoryId || '-' }}</el-descriptions-item>
        </template>
        <!-- 本地商品字段 -->
        <template v-else>
          <el-descriptions-item label="发布账号">{{ accountName(detail.accountId) }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ detail.updatedAt || '-' }}</el-descriptions-item>
        </template>
        <el-descriptions-item label="商品类型">
          <el-tag size="small">{{ goodsTypeLabel(detail.goodsType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发货类型">
          <el-tag size="small" v-if="detail.deliverType">{{ deliverTypeLabel(detail.deliverType) }}</el-tag>
          <span v-else style="color:#909399;">-</span>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 本地商品：发布失败原因 -->
      <el-alert v-if="detail._source === 'local' && detail.publishError" type="error" :closable="false"
        :title="`发布失败：${detail.publishError}`" style="margin-top: 16px;" show-icon>
        <template #default>
          <el-button size="small" type="primary" :loading="detail._retrying" @click="handleRetryPublish(detail)"
            style="margin-top: 8px;">
            <el-icon><Refresh /></el-icon> 重试发布
          </el-button>
        </template>
      </el-alert>

      <!-- 描述 -->
      <el-card style="margin-top: 16px;" v-if="detail.description">
        <template #header><span>📝 商品描述</span></template>
        <div style="white-space: pre-wrap; line-height: 1.6;">{{ detail.description }}</div>
      </el-card>

      <!-- 发货模板 -->
      <el-card style="margin-top: 16px;" v-if="detail.deliverContentTemplate">
        <template #header><span>📦 发货内容模板</span></template>
        <div style="white-space: pre-wrap; line-height: 1.6; font-family: monospace;">{{ detail.deliverContentTemplate }}</div>
      </el-card>

      <!-- 视频列表 -->
      <el-card style="margin-top: 16px;" v-if="detail.videos && detail.videos.length">
        <template #header><span>🎥 视频列表</span></template>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          <div v-for="(v, idx) in detail.videos" :key="idx" style="display: flex; align-items: center; gap: 12px;">
            <span style="color: #909399; min-width: 50px;">视频 {{ idx + 1 }}</span>
            <el-link :href="v" target="_blank" type="primary" :underline="false">
              <el-icon><VideoPlay /></el-icon> {{ v }}
            </el-link>
          </div>
        </div>
      </el-card>

      <!-- 详情页链接 -->
      <el-card style="margin-top: 16px;" v-if="detail.detailUrl">
        <template #header><span>🔗 闲鱼详情</span></template>
        <el-link :href="detail.detailUrl" target="_blank" type="primary">
          <el-icon><Link /></el-icon> 在闲鱼打开
        </el-link>
      </el-card>
    </div>
    <el-empty v-else description="加载中..." />
  </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, MagicStick, VideoPlay, Link, Plus, UploadFilled, Download, DocumentCopy } from '@element-plus/icons-vue'
import api from '@/api/request'
import { optimizeTitle as optimizeTitleApi, optimizeDescription as optimizeDescriptionApi, extractKeywords as extractKeywordsApi } from '@/api/ai'

const products = ref([])
const localProducts = ref([])
const selectedLocalProducts = ref([])
const accounts = ref([])
const aiModels = ref([])
const loading = ref(false)
const submitting = ref(false)
const localSubmitting = ref(false)
const syncing = ref(false)
const batchPublishing = ref(false)
const showCreateDialog = ref(false)
const showLocalCreateDialog = ref(false)
const showImportDialog = ref(false)
const showAiOptimizeDialog = ref(false)
const showDetailDrawer = ref(false)
const detail = ref(null)
const imageFileList = ref([])
const videoFileList = ref([])
const localImageFileList = ref([])
const aiLoading = ref(false)
const aiActiveTab = ref('title')
const filters = ref({ accountId: null, keyword: '', status: '' })
const pagination = ref({ page: 1, size: 20, total: 0 })
const localFormRef = ref(null)

const localForm = reactive({
  id: null,
  accountId: null,
  title: '',
  price: 0,
  originalPrice: 0,
  stock: 1,
  description: '',
  goodsType: 'PHYSICAL',
  deliverType: '',
  deliverContentTemplate: '',
  images: [],
  action: 'DRAFT'
})

const batchForm = reactive({
  partialSuccess: true,
  maxConcurrency: 3,
  delayMs: 2000,
  retryTimes: 0
})

// ===== 批量导入 =====
const importPreview = ref(null)
const importResult = ref(null)
const importUploading = ref(false)
const importStep = ref(0)
const importFile = ref(null)
const importForm = reactive({
  deduplicate: false,
  overwriteDuplicate: false,
  defaultGoodsType: 'PHYSICAL',
  deliverContentSeparator: '\\|\\|\\|'
})

function handleImportFileChange(uploadFile) {
  importFile.value = uploadFile.raw || uploadFile
  importPreview.value = null
  importResult.value = null
  return false
}

async function handleImportPreview() {
  if (!importFile.value) return ElMessage.warning('请选择 CSV 文件')
  importUploading.value = true
  try {
    const res = await localProductApi.previewLocalProductImport({
      file: importFile.value,
      deduplicate: importForm.deduplicate,
      overwriteDuplicate: importForm.overwriteDuplicate,
      defaultGoodsType: importForm.defaultGoodsType,
      deliverContentSeparator: importForm.deliverContentSeparator
    })
    if (res.success) {
      importPreview.value = res.data
      const total = res.data.totalRows || 0
      const valid = res.data.validRows || 0
      const errs = (res.data.errors || []).length
      ElMessage.success(`预览完成：共 ${total} 行，有效 ${valid} 行，错误 ${errs} 条`)
    } else {
      ElMessage.error(res.message || '预览失败')
    }
  } catch (e) { ElMessage.error('预览失败：' + (e?.message || '')) }
  finally { importUploading.value = false }
}

async function handleImportConfirm() {
  if (!importFile.value) return ElMessage.warning('请选择 CSV 文件')
  try {
    await ElMessageBox.confirm(
      `确认导入？将把 ${importPreview.value?.validRows || 0} 个商品写入本地商品表。`,
      '确认导入', { type: 'warning' }
    )
  } catch { return }
  importUploading.value = true
  try {
    const res = await localProductApi.confirmLocalProductImport({
      file: importFile.value,
      deduplicate: importForm.deduplicate,
      overwriteDuplicate: importForm.overwriteDuplicate,
      defaultGoodsType: importForm.defaultGoodsType,
      deliverContentSeparator: importForm.deliverContentSeparator
    })
    if (res.success) {
      importResult.value = res.data
      ElMessage.success(`导入完成：成功 ${res.data.imported}，跳过 ${res.data.skipped}，失败 ${res.data.failed}`)
      await loadLocalProducts()
    } else {
      ElMessage.error(res.message || '导入失败')
    }
  } catch (e) { ElMessage.error('导入失败：' + (e?.message || '')) }
  finally { importUploading.value = false }
}

function resetImportDialog() {
  importPreview.value = null
  importResult.value = null
  importFile.value = null
}

function handleDownloadTemplate() {
  const header = 'account_name,title,price,stock,images,goods_type,deliver_type,deliver_content_template\n'
  const example1 = '示例账号,iPhone 15 Pro 256G 原色钛金属,6999.00,3,https://cdn.com/1.jpg,PHYSICAL,,\n'
  const example2 = '示例账号,Steam 充值卡 100元,100.00,50,,VIRTUAL,CARD,卡密AAA-111|||卡密BBB-222|||卡密CCC-333\n'
  const example3 = '示例账号,考研资料完整版,9.90,999,,VIRTUAL,FILE,链接: https://pan.baidu.com/xxx 提取码: abcd\n'
  const csv = '﻿' + header + example1 + example2 + example3
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'local_product_import_template.csv'
  link.click()
  URL.revokeObjectURL(url)
}

// ===== 状态 =====
import * as localProductApi from '@/api/localProducts'
const activeTab = ref('xianyu')
const createForm = ref({ accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '', categoryId: '', categoryIdPath: null, deliveryChoice: '按距离计费', postPrice: 0, location: '' })
const categoryTree = ref([])
const categoryTreeLoading = ref(false)
const categoryTreeLoadedAccountId = ref(null)
const aiForm = ref({ modelId: null, productTitle: '', keywordsRaw: '', condition: '九成新' })
const aiResult = ref({ title: '', description: '', keywords: [] })

const localStatusType = (s) => ({ DRAFT: 'info', PENDING: 'primary', PUBLISHING: 'warning', FAILED: 'danger' }[s] || 'info')
const localStatusLabel = (s) => ({ DRAFT: '草稿', PENDING: '待发布', PUBLISHING: '发布中', FAILED: '失败' }[s] || s)
const accountName = (id) => accounts.value.find(a => a.id === id)?.accountName || (id ? `#${id}` : '-')
const poolCount = (row) => {
  if (!row.deliverContentTemplate) return 0
  try {
    const arr = JSON.parse(row.deliverContentTemplate)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return row.deliverContentTemplate.split('|||').filter(s => s.trim()).length
  }
}

watch(activeTab, (tab) => {
  pagination.value.page = 1
  pagination.value.total = 0
  if (tab === 'xianyu') loadProducts()
  else loadLocalProducts()
})

async function loadLocalProducts() {
  loading.value = true
  try {
    const params = { page: pagination.value.page, size: pagination.value.size }
    if (filters.value.accountId) params.accountId = filters.value.accountId
    if (filters.value.keyword) params.keyword = filters.value.keyword
    if (filters.value.status) params.status = filters.value.status
    const res = await localProductApi.listLocalProducts(params)
    if (res.success) {
      localProducts.value = (res.data.records || []).map(p => ({ ...p, _publishing: false }))
      pagination.value.total = res.data.total || 0
    }
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

function resetLocalForm() {
  Object.assign(localForm, {
    id: null, accountId: null, title: '', price: 0, originalPrice: 0, stock: 1,
    description: '', goodsType: 'PHYSICAL', deliverType: '', deliverContentTemplate: '', images: [], action: 'DRAFT'
  })
  localImageFileList.value = []
}

async function handleLocalSave() {
  if (!localForm.accountId) return ElMessage.warning('请选择发布账号')
  if (!localForm.title) return ElMessage.warning('请输入标题')
  if (localForm.price == null || localForm.price <= 0) return ElMessage.warning('请输入有效售价')
  localSubmitting.value = true
  try {
    const images = localImageFileList.value.filter(f => f.status === 'success' && f.url).map(f => denormalizeUploadUrl(f.url))
    const payload = { ...localForm, images }
    let res
    if (localForm.id) {
      res = await localProductApi.updateLocalProduct(localForm.id, payload)
    } else {
      res = await localProductApi.saveLocalProduct(payload)
    }
    if (res.success) {
      ElMessage.success(localForm.id ? '保存成功' : '创建成功')
      showLocalCreateDialog.value = false
      resetLocalForm()
      if (activeTab.value === 'local') await loadLocalProducts()
      else { activeTab.value = 'local' }
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e?.message || ''))
  } finally {
    localSubmitting.value = false
  }
}

function editLocalProduct(row) {
  Object.assign(localForm, {
    id: row.id,
    accountId: row.accountId,
    title: row.title || '',
    price: row.price || 0,
    originalPrice: row.originalPrice || 0,
    stock: row.stock || 1,
    description: row.description || '',
    goodsType: row.goodsType || 'PHYSICAL',
    deliverType: row.deliverType || '',
    deliverContentTemplate: row.deliverContentTemplate || '',
    action: 'DRAFT'
  })
  const imgs = parseJsonArray(row.images) || (row.imageUrl ? [row.imageUrl] : [])
  localForm.images = imgs
  localImageFileList.value = imgs.map((url, idx) => ({ uid: `img-${idx}`, url: normalizeImageUrl(url), name: `图片${idx + 1}`, status: 'success' }))
  showLocalCreateDialog.value = true
}

async function publishLocalProduct(row) {
  if (!row.accountId) return ElMessage.warning('请先编辑并指定发布账号')
  try {
    await ElMessageBox.confirm(`确认发布「${row.title || ('#' + row.id)}」？发布成功后本地记录将自动清理。`, '发布确认', { type: 'info' })
  } catch { return }
  row._publishing = true
  try {
    const res = await localProductApi.publishLocalProduct(row.id)
    if (res.success) {
      ElMessage.success('发布成功，本地记录已清理')
      selectedLocalProducts.value = selectedLocalProducts.value.filter(s => s.id !== row.id)
      await loadLocalProducts()
    } else {
      ElMessage.error(res.message || '发布失败')
    }
  } catch (e) { ElMessage.error('发布失败：' + (e?.message || '')) }
  finally { row._publishing = false }
}

async function handleRetryPublish(row) {
  if (!row.accountId) return ElMessage.warning('请先编辑并指定发布账号')
  row._retrying = true
  try {
    const res = await localProductApi.publishLocalProduct(row.id)
    if (res.success) {
      ElMessage.success('重试发布成功，本地记录已清理')
      detail.value = null
      showDetailDrawer.value = false
      await loadLocalProducts()
    } else {
      // 失败：更新抽屉里的错误信息，不关闭抽屉
      detail.value = { ...detail.value, publishError: res.message || '发布失败' }
      row.publishError = res.message || '发布失败'
      ElMessage.error('重试失败：' + (res.message || '未知错误'))
    }
  } catch (e) {
    const errMsg = e?.message || '未知错误'
    detail.value = { ...detail.value, publishError: errMsg }
    row.publishError = errMsg
    ElMessage.error('重试失败：' + errMsg)
  } finally { row._retrying = false }
}

async function deleteLocalProduct(row) {
  await ElMessageBox.confirm('确认删除该本地商品？此操作不会影响已上架的闲鱼商品。', '提示', { type: 'warning' })
  try {
    const res = await localProductApi.deleteLocalProduct(row.id)
    if (res.success) {
      ElMessage.success('已删除')
      selectedLocalProducts.value = selectedLocalProducts.value.filter(s => s.id !== row.id)
      await loadLocalProducts()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) { if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message || '')) }
}

async function handleBatchPublish() {
  const selected = selectedLocalProducts.value.filter(s => !s._publishing)
  if (!selected.length) return ElMessage.warning('请先勾选要发布的本地商品')
  const missing = selected.find(s => !s.accountId)
  if (missing) return ElMessage.warning(`商品 #${missing.id} 未设置发布账号，请先编辑指定`)
  try {
    await ElMessageBox.confirm(
      `确认批量发布选中的 ${selected.length} 个商品？\n\n` +
      `并发数：${batchForm.maxConcurrency}，账号间隔：${batchForm.delayMs}ms\n` +
      `${batchForm.partialSuccess ? '部分成功的将删除本地记录，失败的保留。' : '全部成功才算部分停止。'}`,
      '批量发布', { type: 'warning' }
    )
  } catch { return }
  batchPublishing.value = true
  try {
    const res = await localProductApi.batchPublishLocalProducts({
      ids: selected.map(s => s.id),
      partialSuccess: batchForm.partialSuccess,
      maxConcurrency: batchForm.maxConcurrency,
      delayMs: batchForm.delayMs,
      retryTimes: batchForm.retryTimes
    })
    if (res.success) {
      const d = res.data || {}
      ElMessage.success(`批量发布完成：成功 ${d.success || 0}，失败 ${d.fail || 0}，跳过 ${d.skip || 0}`)
      if (d.errors && d.errors.length) {
        await ElMessageBox.alert(d.errors.slice(0, 20).join('\n'), `失败详情（共 ${d.errors.length} 条）`, { type: 'warning' })
      }
      selectedLocalProducts.value = []
      await loadLocalProducts()
    } else {
      ElMessage.error(res.message || '批量发布失败')
    }
  } catch (e) { ElMessage.error('批量发布失败：' + (e?.message || '')) }
  finally { batchPublishing.value = false }
}

function onLocalSelectionChange(selection) {
  selectedLocalProducts.value = selection
}

function handleLocalUploadChange(list) { localImageFileList.value = list }
function handleLocalUploadRemove(list) { localImageFileList.value = list }
async function customLocalUpload(options) {
  const file = options.file
  if (!file) return options.onError(new Error('文件为空'))
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('图片不能超过 10MB')
    localImageFileList.value = localImageFileList.value.filter(f => f.uid !== file.uid)
    return options.onError(new Error('文件过大'))
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await api.post('/products/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.success) {
      const normalizedUrl = normalizeImageUrl(res.data.url)
      localImageFileList.value = localImageFileList.value.map(f =>
        f.uid === file.uid ? { ...f, url: normalizedUrl, status: 'success' } : f
      )
      options.onSuccess(normalizedUrl)
    } else {
      throw new Error(res.message || '上传失败')
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.message || ''))
    localImageFileList.value = localImageFileList.value.filter(f => f.uid !== file.uid)
    options.onError(err)
  }
}

const statusType = (s) => ({ ON_SALE: 'success', OFF_SALE: 'warning', DRAFT: 'info' }[s] || 'info')
const statusLabel = (s) => ({ ON_SALE: '在售', OFF_SALE: '下架', DRAFT: '草稿' }[s] || s)
const goodsTypeLabel = (s) => ({ PHYSICAL: '实物', VIRTUAL: '虚拟' }[s] || s || '实物')
const deliverTypeLabel = (s) => ({ CARD: '卡密', ACCOUNT: '账号', LINK: '链接', FILE: '网盘文件' }[s] || s)

async function loadAccounts() {
  try {
    const res = await api.get('/accounts')
    if (res.success) accounts.value = res.data
  } catch (e) { /* ignore */ }
}

// 拉闲鱼分类树 — 创建商品表单分类下拉用，按 accountId 缓存避免重复拉
async function loadCategoryTree(accountId) {
  if (!accountId) { categoryTree.value = []; return }
  if (categoryTreeLoadedAccountId.value === accountId && categoryTree.value.length > 0) return
  categoryTreeLoading.value = true
  try {
    const res = await api.get('/products/category-tree', { params: { accountId } })
    if (res.success) {
      // 闲鱼返回 data.children[] 或 data[] 形式的嵌套树，前端按 catId/catName/children 渲染
      // 兼容多种返回结构：data.children / data / 顶层数组
      let tree = res.data
      if (tree && tree.children) tree = tree.children
      else if (tree && Array.isArray(tree.data)) tree = tree.data
      else if (tree && Array.isArray(tree)) { /* ok */ }
      else tree = []
      categoryTree.value = tree
      categoryTreeLoadedAccountId.value = accountId
    } else {
      categoryTree.value = []
    }
  } catch (e) {
    categoryTree.value = []
  } finally {
    categoryTreeLoading.value = false
  }
}

// 账号切换时清已选分类 + 重拉分类树
async function onAccountChange() {
  createForm.value.categoryIdPath = null
  createForm.value.categoryId = ''
  await loadCategoryTree(createForm.value.accountId)
}

async function loadProducts() {
  loading.value = true
  try {
    const params = { page: pagination.value.page, size: pagination.value.size }
    if (filters.value.accountId) params.accountId = filters.value.accountId
    if (filters.value.keyword) params.keyword = filters.value.keyword
    if (filters.value.status) params.status = filters.value.status
    const res = await api.get('/products', { params })
    if (res.success) {
      products.value = res.data.records || []
      pagination.value.total = res.data.total || 0
    }
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

async function handleSyncFromXianyu() {
  if (!filters.value.accountId) {
    ElMessage.warning('请先选择账号')
    return
  }
  syncing.value = true
  try {
    const res = await api.post('/products/sync', null, { params: { accountId: filters.value.accountId } })
    if (res.success) {
      const r = res.data || {}
      ElMessage.success(`同步完成：新增 ${r.inserted || 0}，更新 ${r.updated || 0}，合计 ${r.synced || 0}`)
      await loadProducts()
    } else if (res.code === 'COOKIE_EXPIRED') {
      ElMessage.error('Cookie 已过期，请重新扫码登录该账号')
    } else {
      ElMessage.error(res.message || '同步失败')
    }
  } catch (e) {
    ElMessage.error('同步请求失败')
  } finally { syncing.value = false }
}

async function handleCreate() {
  if (!createForm.value.accountId || !createForm.value.title) {
    ElMessage.warning('请选择账号并填写标题')
    return
  }
  const images = imageFileList.value
    .filter(f => f.status === 'success' && f.url)
    .map(f => denormalizeUploadUrl(f.url))
  const videos = videoFileList.value
    .filter(f => f.status === 'success' && f.url)
    .map(f => denormalizeUploadUrl(f.url))
  submitting.value = true
  try {
    // el-cascader 选中节点对象（checkStrictly + emitPath:false 时 v-model 拿到 catId 值）
    // 但 el-cascader 的 v-model 在 checkStrictly 模式下拿到的是选中节点 value（catId），不是 path
    const catId = createForm.value.categoryIdPath || ''
    const payload = { ...createForm.value, categoryId: catId, images, videos }
    delete payload.categoryIdPath
    const res = await api.post('/products', payload)
    if (res.success) {
      ElMessage.success('商品创建成功')
      showCreateDialog.value = false
      createForm.value = { accountId: null, title: '', price: 0, originalPrice: 0, stock: 0, description: '', categoryId: '', categoryIdPath: null, deliveryChoice: '按距离计费', postPrice: 0, location: '' }
      imageFileList.value = []
      videoFileList.value = []
      await loadProducts()
    } else {
      // 后端业务校验/发布失败（如缺图片、cookie 过期、闲鱼拒绝）→ 展示具体原因
      ElMessage.error(res.message || '创建失败')
    }
  } catch (e) { /* ignore */ }
  finally { submitting.value = false }
}

async function shelfOn(row) {
  try {
    const res = await api.post(`/products/${row.id}/shelf-on`)
    if (res.success) {
      ElMessage.success('已上架')
      await loadProducts()
    }
  } catch (e) {}
}

async function shelfOff(row) {
  try {
    const res = await api.post(`/products/${row.id}/shelf-off`)
    if (res.success) {
      ElMessage.success('已下架')
      await loadProducts()
    }
  } catch (e) {}
}

function editPrice(row) {
  ElMessageBox.prompt('输入新价格', '修改价格', { inputValue: String(row.price) }).then(async ({ value }) => {
    try {
      const res = await api.put(`/products/${row.id}/price?price=${value}`)
      if (res.success) {
        ElMessage.success('价格已更新')
        await loadProducts()
      } else {
        ElMessage.error(res.message || '改价失败')
      }
    } catch (e) {
      ElMessage.error('改价失败：' + (e?.response?.data?.message || e?.message || '请重试'))
    }
  }).catch(() => {})
}

function editStock(row) {
  ElMessageBox.prompt('输入新库存', '修改库存', { inputValue: String(row.stock) }).then(async ({ value }) => {
    try {
      const res = await api.put(`/products/${row.id}/stock?stock=${parseInt(value)}`)
      if (res.success) {
        ElMessage.success('库存已更新')
        await loadProducts()
      } else {
        ElMessage.error(res.message || '改库存失败')
      }
    } catch (e) {
      ElMessage.error('改库存失败：' + (e?.response?.data?.message || e?.message || '请重试'))
    }
  }).catch(() => {})
}

async function deleteProduct(row) {
  await ElMessageBox.confirm(
    '确认删除该商品？将同时在闲鱼中执行删除。',
    '提示',
    { type: 'warning' }
  )
  try {
    const res = await api.delete(`/products/${row.id}`)
    if (res.success) {
      ElMessage.success('已删除')
      await loadProducts()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e?.message || '请重试'))
  }
}

const parseKeywords = () =>
  (aiForm.value.keywordsRaw || '').split(/[,，、\s]+/).map(s => s.trim()).filter(Boolean)

async function optimizeTitle() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await optimizeTitleApi(
      aiForm.value.modelId,
      aiForm.value.productTitle,
      parseKeywords(),
      aiForm.value.condition
    )
    if (res.success) {
      aiResult.value.title = res.data?.generatedTitle || res.data?.title || res.data || ''
      ElMessage.success('标题已生成')
    }
  } finally { aiLoading.value = false }
}

async function optimizeDesc() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await optimizeDescriptionApi(
      aiForm.value.modelId,
      aiForm.value.productTitle,
      parseKeywords(),
      aiForm.value.condition
    )
    if (res.success) {
      aiResult.value.description = res.data?.optimizedDescription || res.data?.description || res.data || ''
      ElMessage.success('描述已生成')
    }
  } finally { aiLoading.value = false }
}

async function extractKeywords() {
  if (!aiForm.value.productTitle) return ElMessage.warning('请填写商品名')
  aiLoading.value = true
  try {
    const res = await extractKeywordsApi(aiForm.value.modelId, aiForm.value.productTitle)
    if (res.success) {
      aiResult.value.keywords = Array.isArray(res.data) ? res.data : (res.data?.keywords || [])
      ElMessage.success(`已提取 ${aiResult.value.keywords.length} 个关键词`)
    }
  } finally { aiLoading.value = false }
}

function useAiResult() {
  if (aiResult.value.title) createForm.value.title = aiResult.value.title
  if (aiResult.value.description) createForm.value.description = aiResult.value.description
  showAiOptimizeDialog.value = false
  showCreateDialog.value = true
  ElMessage.success('已应用 AI 生成内容')
}

async function loadAiModels() {
  try {
    const res = await api.get('/ai/models', { params: { size: 100 } })
    aiModels.value = (res.data?.records || []).filter(m => m.enabled !== false)
  } catch {}
}

async function viewDetail(row, source) {
  detail.value = null
  showDetailDrawer.value = true
  try {
    const url = source === 'local' ? `/local-products/${row.id}` : `/products/${row.id}`
    const res = await api.get(url)
    if (res.success) {
      const p = res.data
      detail.value = {
        ...p,
        _source: source || 'xianyu',
        images: parseJsonArray(p.images),
        videos: parseJsonArray(p.videos)
      }
    }
  } catch {}
}

function parseJsonArray(raw) {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch { return [] }
}

// 后端返回 /uploads/xxx.jpg，前端需拼 BASE_API_URL 前缀走 /api/uploads 静态映射
function normalizeImageUrl(url) {
  if (!url || typeof url !== 'string') return url
  if (/^(https?:)?\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) return url
  const base = (import.meta.env.BASE_API_URL || '/api').replace(/\/$/, '')
  if (url.startsWith('/api/uploads/')) return url
  if (url.startsWith('/uploads/')) return base + url
  return url
}

function denormalizeUploadUrl(url) {
  if (!url || typeof url !== 'string') return url
  const base = (import.meta.env.BASE_API_URL || '/api').replace(/\/$/, '')
  if (base && url.startsWith(base + '/uploads/')) return url.substring(base.length)
  return url
}

function handleUploadChange(fileList, field) {
  if (field === 'images') imageFileList.value = fileList
  else videoFileList.value = fileList
}

function handleUploadRemove(fileList, field) {
  if (field === 'images') imageFileList.value = fileList
  else videoFileList.value = fileList
}

async function customUpload(options, field) {
  const file = options.file
  if (!file) return options.onError(new Error('文件为空'))
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过 10MB')
    if (field === 'images') imageFileList.value = imageFileList.value.filter(f => f.uid !== file.uid)
    else videoFileList.value = videoFileList.value.filter(f => f.uid !== file.uid)
    return options.onError(new Error('文件过大'))
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await api.post('/products/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.success) {
      const url = res.data.url
      const displayUrl = normalizeImageUrl(url)
      if (field === 'images') {
        imageFileList.value = imageFileList.value.map(f =>
          f.uid === file.uid ? { ...f, url: displayUrl, status: 'success' } : f
        )
      } else {
        videoFileList.value = videoFileList.value.map(f =>
          f.uid === file.uid ? { ...f, url: displayUrl, status: 'success' } : f
        )
      }
      options.onSuccess(displayUrl)
    } else {
      throw new Error(res.message || '上传失败')
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.message || '未知错误'))
    if (field === 'images') imageFileList.value = imageFileList.value.filter(f => f.uid !== file.uid)
    else videoFileList.value = videoFileList.value.filter(f => f.uid !== file.uid)
    options.onError(err)
  }
}

onMounted(() => { loadAccounts(); loadProducts(); loadAiModels() })
</script>
