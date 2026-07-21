import api from './request'

// ============== AI 商品文案优化 ==============
// 后端 AiDemoController：@RequestMapping("/api/ai/demo")，全部为 @RequestBody JSON

// 商品标题生成 — POST /api/ai/demo/generate-title  { modelId, keywords:[], category }
// 返回 { keywords, generatedTitle }
export function optimizeTitle(modelId, title, keywords, condition) {
  return api.post('/ai/demo/generate-title', {
    modelId,
    keywords: [title, ...(keywords || [])].filter(Boolean),
    category: condition || ''
  })
}

// 商品描述优化 — POST /api/ai/demo/optimize-description  { modelId, productTitle, rawDescription, tone }
// 返回 { productTitle, rawDescription, optimizedDescription }
export function optimizeDescription(modelId, title, keywords, condition) {
  return api.post('/ai/demo/optimize-description', {
    modelId,
    productTitle: title,
    rawDescription: (keywords || []).join('、'),
    tone: condition || 'professional'
  })
}

// 关键词提取 — POST /api/ai/demo/extract-keywords  { modelId, text }
// 返回 { text, keywords:[] }
export function extractKeywords(modelId, title) {
  return api.post('/ai/demo/extract-keywords', {
    modelId,
    text: title
  })
}
