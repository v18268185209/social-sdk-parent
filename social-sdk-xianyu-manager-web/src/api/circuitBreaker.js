import api from './request'

// ===== 熔断器管理 — CircuitBreakerController (/api/circuit-breaker) =====
export function listCircuitBreakers() {
  return api.get('/circuit-breaker')
}

export function getCircuitBreaker(accountId, serviceName) {
  return api.get(`/circuit-breaker/${accountId}/${serviceName}`)
}

export function resetCircuitBreaker(accountId, serviceName) {
  return api.post(`/circuit-breaker/${accountId}/${serviceName}/reset`)
}

export function resetGlobalCircuitBreaker(serviceName) {
  return api.post(`/circuit-breaker/global/${serviceName}/reset`)
}
