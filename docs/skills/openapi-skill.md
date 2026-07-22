# OpenAPI Integration Skill

## Description

Use this skill to help users integrate with an OpenAPI-based HTTP service that uses app credentials, Bearer access tokens, a unified response envelope, account-level access scope, and rate limiting.

This skill is designed for public use. It focuses on client integration, request examples, error handling, endpoint discovery, and safe troubleshooting. It intentionally avoids project-internal implementation details such as source paths, database migrations, framework classes, private interceptors, build commands, and internal stack traces.

## When to Use

Use this skill when the user asks to:

- Integrate with an OpenAPI / public API / developer platform.
- Obtain or refresh an access token from `appKey` and `appSecret`.
- Generate curl, JavaScript, Python, Postman, or SDK examples.
- Call account, product, order, message, wallet, notification, AI, cloud, or monitoring endpoints.
- Diagnose API errors such as 401, 403, 429, invalid token, forbidden account access, or rate limit responses.
- Explain response envelopes and error codes.
- Build a lightweight client wrapper around an OpenAPI service.
- Review whether a public API response is safe and does not expose secrets.

## Inputs to Collect

Before generating concrete requests, ask for or infer these values:

| Input | Required | Example | Notes |
|---|---:|---|---|
| `baseUrl` | Yes | `https://api.example.com` | Do not hard-code localhost for user-facing examples unless the user is local testing. |
| `appKey` | Usually | `ak_xxxxxxxx` | Public application identifier. |
| `appSecret` | Usually | `********` | Secret credential. Never print a real secret back unless the user supplied it and explicitly wants it included. |
| `accessToken` | Optional | `eyJ...` | If already available, skip token exchange examples. |
| `accountId` | Optional | `123` | Used by account-scoped endpoints. |
| endpoint/domain | Optional | `orders`, `products`, `messages` | Use to choose focused examples. |
| language/tool | Optional | `curl`, `Python`, `Node.js`, `Postman` | Default to curl plus one SDK-style example if unspecified. |

## Public API Conventions

Assume these conventions unless the user provides a different OpenAPI specification:

1. Business endpoints are under `/openapi/v1/**`.
2. Token endpoint is public and does not require a Bearer token:

   ```http
   POST /openapi/v1/oauth/token
   Content-Type: application/json
   ```

3. Business endpoints require:

   ```http
   Authorization: Bearer <accessToken>
   ```

4. Responses use a unified envelope:

   ```json
   {
     "code": "OK",
     "message": "Success",
     "data": {},
     "timestamp": 1721365800
   }
   ```

5. Treat `code === "OK"` as success. Do not rely only on HTTP status 200.
6. Tokens may expire. If the API returns invalid-token errors, refresh the token and retry once.
7. Some endpoints are account-scoped. If the app is bound to specific account IDs, requests for other accounts should fail with a forbidden error.
8. API responses must not expose secrets such as app secrets, cookies, session tokens, private credentials, cloud drive tokens, or raw stack traces.

## Authentication Flow

### Step 1: Exchange app credentials for an access token

```http
POST /openapi/v1/oauth/token
Content-Type: application/json

{
  "appKey": "ak_xxxxxxxx",
  "appSecret": "xxxxxxxxxxxxxxxx"
}
```

Expected response:

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "accessToken": "xxxxxxxxxxxxxxxx",
    "tokenType": "Bearer",
    "expiresIn": 7200
  },
  "timestamp": 1721365800
}
```

### Step 2: Call a business endpoint

```http
GET /openapi/v1/accounts
Authorization: Bearer <accessToken>
```

### Step 3: Refresh token when needed

When the API returns an invalid-token or expired-token response, call `/openapi/v1/oauth/token` again. Then retry the failed request once. Avoid infinite retry loops.

## Error Handling

| Code | Typical HTTP Status | Meaning | Client Action |
|---|---:|---|---|
| `OPEN_UNAUTHORIZED` | 401 | Missing access token | Add `Authorization: Bearer <token>`. |
| `OPEN_INVALID_TOKEN` | 401 | Token invalid, expired, or no longer recognized | Refresh token and retry once. |
| `OPEN_APP_DISABLED` | 403 | Application disabled | Ask platform admin to enable the app. |
| `OPEN_APP_EXPIRED` | 403 | Application credentials expired | Ask platform admin to renew or recreate the app. |
| `OPEN_RATE_LIMIT` | 429 | Rate limit exceeded | Back off and retry later. Add client-side throttling. |
| `OPEN_ACCOUNT_FORBIDDEN` | 403 | App is not allowed to access the target account | Use an allowed account ID or update app account bindings. |
| `OPEN_INVALID_PARAM` | 400 | Invalid query/path/body parameter | Validate request parameters. |
| `OPEN_NOT_FOUND` | 404 | Resource not found or not visible to this app | Check ID and account scope. |
| `OPEN_INTERNAL` | 500 | Server-side failure | Preserve request ID/log context if available and contact the service owner. |

If the actual API uses different error codes, adapt the table to the OpenAPI JSON or official docs.

## Endpoint Catalog Pattern

When documenting or generating examples, group endpoints by business domain. Use tables with method, path, parameters, and purpose.

### OAuth

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/openapi/v1/oauth/token` | None | Exchange `appKey` + `appSecret` for Bearer token. |

### Accounts

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/accounts` | `accountId?` | List accounts visible to the application. |
| GET | `/openapi/v1/accounts/{id}` | `id` | Get account detail with scope checks. |

### Products

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/products` | `accountId?`, `status?`, `keyword?` | List products. |
| GET | `/openapi/v1/products/{id}` | `id` | Get product detail. |
| GET | `/openapi/v1/local-products` | `accountId?`, `status?`, `keyword?` | List local/draft products if supported. |
| GET | `/openapi/v1/local-products/{id}` | `id` | Get local product detail. |

### Messages and Orders

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/messages` | `accountId?` | List messages. |
| GET | `/openapi/v1/messages/{id}` | `id` | Get message detail. |
| GET | `/openapi/v1/orders` | `accountId?` | List orders. |
| GET | `/openapi/v1/orders/{id}` | `id` | Get order detail. |

### Wallets

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/wallets` | `accountId?` | List wallet summaries. |
| GET | `/openapi/v1/wallets/{accountId}` | `accountId` | Get wallet by account. |
| GET | `/openapi/v1/wallets/{accountId}/transactions` | `accountId` | List wallet transactions. |

### Notifications

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/notify/messages` | `accountId?` | List notification messages. |
| GET | `/openapi/v1/notify/messages/{id}` | `id` | Get notification message detail. |
| GET | `/openapi/v1/notify/templates` | None | List notification templates. |
| GET | `/openapi/v1/notify/channels` | None | List notification channels with sensitive config redacted. |
| GET | `/openapi/v1/notify/logs` | `accountId?`, `scenario?`, `status?`, `from?`, `to?` | List notification send logs. |
| GET | `/openapi/v1/notify/subscriptions` | `scenario?`, `channelId?` | List notification subscriptions. |
| GET | `/openapi/v1/notify/subscriptions/{id}` | `id` | Get notification subscription detail. |

### AI, Monitoring, Market, Cloud, and Fulfillment

| Method | Path | Common Parameters | Purpose |
|---|---|---|---|
| GET | `/openapi/v1/ai/providers` | `enabled?` | List AI providers with credentials redacted. |
| GET | `/openapi/v1/ai/models` | None | List AI models. |
| GET | `/openapi/v1/ai/ops/tasks` | `accountId?` | List AI operation tasks. |
| GET | `/openapi/v1/ai/ops/suggestions` | `accountId?` | List AI operation suggestions. |
| GET | `/openapi/v1/ai/cs/sessions` | `accountId?` | List AI customer-service sessions. |
| GET | `/openapi/v1/ai/cs/knowledge` | `accountId?` | List AI knowledge entries. |
| GET | `/openapi/v1/ai/cs/policies` | `accountId?` | List AI customer-service policies. |
| GET | `/openapi/v1/monitor/tasks` | `accountId?`, `status?` | List monitoring tasks. |
| GET | `/openapi/v1/monitor/results` | `accountId?`, `taskId?`, `from?`, `to?` | List monitoring results. |
| GET | `/openapi/v1/market/daily-stats` | `keyword?`, `date?` | List market daily statistics. |
| GET | `/openapi/v1/market/sellers` | `accountId?`, `keyword?` | List seller profiles. |
| GET | `/openapi/v1/market/price-history` | `accountId?`, `itemId?`, `keyword?` | List price history. |
| GET | `/openapi/v1/buyer/profiles` | `accountId?` | List buyer profiles. |
| GET | `/openapi/v1/virtual-ship/configs` | `accountId?` | List virtual fulfillment configs. |
| GET | `/openapi/v1/virtual-ship/tasks` | `accountId?` | List virtual fulfillment tasks. |
| GET | `/openapi/v1/cloud/accounts` | `accountId?` | List cloud accounts with tokens redacted. |
| GET | `/openapi/v1/cloud/files` | `accountId?` | List cloud files with private sharing data redacted. |

## curl Example

```bash
BASE_URL="https://api.example.com"
APP_KEY="ak_xxxxxxxx"
APP_SECRET="xxxxxxxxxxxxxxxx"

TOKEN=$(curl -s -X POST "$BASE_URL/openapi/v1/oauth/token" \
  -H 'Content-Type: application/json' \
  -d "{\"appKey\":\"$APP_KEY\",\"appSecret\":\"$APP_SECRET\"}" \
  | jq -r '.data.accessToken')

curl -s "$BASE_URL/openapi/v1/accounts" \
  -H "Authorization: Bearer $TOKEN" | jq
```

If `jq` is unavailable, return the full JSON and manually copy `data.accessToken`.

## JavaScript Client Example

```js
class OpenApiClient {
  constructor({ baseUrl, appKey, appSecret, accessToken }) {
    this.baseUrl = baseUrl.replace(/\/$/, '')
    this.appKey = appKey
    this.appSecret = appSecret
    this.accessToken = accessToken || null
  }

  async token() {
    const res = await fetch(`${this.baseUrl}/openapi/v1/oauth/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ appKey: this.appKey, appSecret: this.appSecret })
    })
    const json = await res.json()
    if (json.code !== 'OK') throw new Error(`${json.code}: ${json.message}`)
    this.accessToken = json.data.accessToken
    return this.accessToken
  }

  async request(path, { method = 'GET', params, body, retry = true } = {}) {
    if (!this.accessToken) await this.token()

    const url = new URL(`${this.baseUrl}/openapi/v1${path}`)
    Object.entries(params || {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, value)
      }
    })

    const res = await fetch(url, {
      method,
      headers: {
        Authorization: `Bearer ${this.accessToken}`,
        ...(body ? { 'Content-Type': 'application/json' } : {})
      },
      body: body ? JSON.stringify(body) : undefined
    })

    const json = await res.json()
    if (json.code === 'OK') return json.data

    if (retry && json.code === 'OPEN_INVALID_TOKEN') {
      await this.token()
      return this.request(path, { method, params, body, retry: false })
    }

    throw new Error(`${json.code}: ${json.message}`)
  }
}

const client = new OpenApiClient({
  baseUrl: 'https://api.example.com',
  appKey: 'ak_xxxxxxxx',
  appSecret: 'xxxxxxxxxxxxxxxx'
})

const accounts = await client.request('/accounts')
const products = await client.request('/products', {
  params: { accountId: accounts[0]?.id }
})

console.log({ accounts, products })
```

## Python Client Example

```python
import requests

class OpenApiClient:
    def __init__(self, base_url, app_key, app_secret, access_token=None):
        self.base_url = base_url.rstrip('/')
        self.app_key = app_key
        self.app_secret = app_secret
        self.access_token = access_token

    def _unwrap(self, response):
        payload = response.json()
        if payload.get('code') != 'OK':
            raise RuntimeError(f"{payload.get('code')}: {payload.get('message')}")
        return payload.get('data')

    def token(self):
        data = self._unwrap(requests.post(
            f'{self.base_url}/openapi/v1/oauth/token',
            json={'appKey': self.app_key, 'appSecret': self.app_secret},
            timeout=30,
        ))
        self.access_token = data['accessToken']
        return self.access_token

    def get(self, path, params=None, retry=True):
        if not self.access_token:
            self.token()
        response = requests.get(
            f'{self.base_url}/openapi/v1{path}',
            headers={'Authorization': f'Bearer {self.access_token}'},
            params=params or {},
            timeout=30,
        )
        payload = response.json()
        if payload.get('code') == 'OK':
            return payload.get('data')
        if retry and payload.get('code') == 'OPEN_INVALID_TOKEN':
            self.token()
            return self.get(path, params=params, retry=False)
        raise RuntimeError(f"{payload.get('code')}: {payload.get('message')}")

client = OpenApiClient(
    base_url='https://api.example.com',
    app_key='ak_xxxxxxxx',
    app_secret='xxxxxxxxxxxxxxxx',
)

orders = client.get('/orders', params={'accountId': 123})
print(orders)
```

## Postman Setup

Create an environment with:

| Variable | Example |
|---|---|
| `baseUrl` | `https://api.example.com` |
| `appKey` | `ak_xxxxxxxx` |
| `appSecret` | `xxxxxxxxxxxxxxxx` |
| `accessToken` | empty initially |

Token request:

```http
POST {{baseUrl}}/openapi/v1/oauth/token
Content-Type: application/json

{
  "appKey": "{{appKey}}",
  "appSecret": "{{appSecret}}"
}
```

Tests tab:

```js
const json = pm.response.json()
pm.test('token response OK', function () {
  pm.expect(json.code).to.eql('OK')
})
pm.environment.set('accessToken', json.data.accessToken)
```

Business request header:

```http
Authorization: Bearer {{accessToken}}
```

## Safe Output Rules

When generating examples or diagnostics:

1. Mask real secrets unless the user explicitly asks for a runnable local snippet and understands the risk.
2. Prefer placeholders:
   - `https://api.example.com`
   - `ak_xxxxxxxx`
   - `xxxxxxxxxxxxxxxx`
   - `<accessToken>`
3. Do not include cookies, session headers, raw private tokens, database credentials, or stack traces in public examples.
4. Do not suggest disabling authentication, bypassing scope checks, or increasing rate limits as a first fix.
5. For logs, recommend redacting `Authorization`, `appSecret`, cookies, and any vendor API keys.

## Troubleshooting Playbook

### 401 Unauthorized

Checklist:

1. Does the request include `Authorization: Bearer <accessToken>`?
2. Is there an accidental extra quote, missing space, or `BearerBearer` typo?
3. Has the token expired?
4. Was the token generated against the same `baseUrl` being called?
5. Retry token exchange once, then retry the business request.

### 403 Forbidden / Account Forbidden

Checklist:

1. Confirm the target `accountId`.
2. Confirm the application is allowed to access that account.
3. For detail endpoints, confirm the resource belongs to an allowed account.
4. If the app should access all accounts, ask the platform admin to update account bindings.

### 429 Rate Limited

Checklist:

1. Reduce request concurrency.
2. Add client-side rate limiting by app key.
3. Use exponential backoff with jitter.
4. Cache stable reference data such as accounts, AI models, templates, and provider lists.

Example backoff strategy:

```text
retry delays: 1s, 2s, 4s, 8s, then stop or queue
add random jitter: 0-500ms
```

### 400 Invalid Parameter

Checklist:

1. Validate required path parameters.
2. Check date/time format. Prefer ISO-8601, for example `2026-07-22T10:00:00`.
3. Check enum values such as `status`, `replyType`, or `scenario`.
4. Avoid sending empty strings when the parameter should be omitted.

### 404 Not Found

Checklist:

1. Confirm the resource ID exists.
2. Confirm the resource is visible to the app's account scope.
3. Confirm you are using the correct environment (`dev`, `staging`, `prod`).

### 500 Internal Error

Checklist:

1. Capture method, path, query params, sanitized request body, timestamp, and response code.
2. Do not expose secrets in bug reports.
3. If the API provides a request ID, include it.
4. Ask the service owner to inspect server logs.

## Common Client Design

For production integrations:

- Store `appSecret` in a secret manager or environment variable.
- Cache `accessToken` until near expiration.
- Refresh token proactively, for example 5 minutes before `expiresIn`.
- Retry once on invalid token.
- Use idempotent retries only for safe methods like GET unless the API explicitly supports idempotency keys.
- Add request timeouts.
- Add structured logs with secrets redacted.
- Add client-side throttling to avoid 429.
- Validate `code` in the response envelope.

## Response Review Checklist

When reviewing or designing public OpenAPI responses, check that:

- [ ] Response uses the documented envelope.
- [ ] Success code is stable and documented.
- [ ] Error codes are actionable.
- [ ] Account-scoped resources include enough IDs for clients to correlate data.
- [ ] Secret fields are absent or redacted.
- [ ] Timestamps use a consistent format.
- [ ] Pagination is documented if list responses can grow large.
- [ ] Nullable fields are documented.
- [ ] Enum values are documented.

## How to Answer Users

When a user asks for help using this OpenAPI:

1. Identify the target endpoint/domain.
2. Confirm `baseUrl` and whether they already have an `accessToken`.
3. Provide a minimal curl example first.
4. Provide a language-specific wrapper if requested.
5. Explain how to detect success using `code === "OK"`.
6. Include error handling for token refresh and rate limits.
7. Avoid exposing or echoing real secrets.

When a user reports an error:

1. Ask for method, path, sanitized headers, sanitized request body/query, HTTP status, and JSON response.
2. Map `code` to the troubleshooting playbook.
3. Provide a concrete next request to run.
4. If server-side inspection is required, say what sanitized context to give the service owner.
