import api from './request'

// ============== AI 商品文案优化 ==============
export function optimizeTitle(modelId, title, keywords, condition) {
  return api.post('/ai/demo/title', null, {
    params: { modelId, title, keywords: keywords?.join(','), condition }
  })
}
export function optimizeDescription(modelId, title, keywords, condition) {
  return api.post('/ai/demo/description', null, {
    params: { modelId, title, keywords: keywords?.join(','), condition }
  })
}
export function extractKeywords(modelId, title) {
  return api.post('/ai/demo/keywords', null, {
    params: { modelId, title }
  })
}
export function optimizeListing(modelId, title, keywords, condition) {
  return api.post('/ai/demo/listing', null, {
    params: { modelId, title, keywords: keywords?.join(','), condition }
  })
}
