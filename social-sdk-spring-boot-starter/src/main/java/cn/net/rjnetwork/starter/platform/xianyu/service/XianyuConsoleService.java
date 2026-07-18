package cn.net.rjnetwork.starter.platform.xianyu.service;

import cn.net.rjnetwork.starter.platform.xianyu.config.XianyuConsoleProperties;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountCookieLoginRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountCookieUpdateRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.AccountStatusUpdateRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.KeywordRuleUpsertRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.MessageSendRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.ProductUpsertRequest;
import cn.net.rjnetwork.starter.platform.xianyu.dto.RuleMatchRequest;
import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuAccountEntity;
import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuKeywordRuleEntity;
import cn.net.rjnetwork.starter.platform.xianyu.model.XianyuProductEntity;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuAccountRepository;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuKeywordRuleRepository;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuProductRepository;
import cn.net.rjnetwork.xianyu.api.XianyuLoginApiService;
import cn.net.rjnetwork.xianyu.api.XianyuApiFacade;
import cn.net.rjnetwork.xianyu.service.XianyuSdk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class XianyuConsoleService {

    private static final Logger logger = LoggerFactory.getLogger(XianyuConsoleService.class);

    private final XianyuConsoleProperties properties;
    private final ObjectMapper objectMapper;
    private final XianyuAccountRepository accountRepository;
    private final XianyuProductRepository productRepository;
    private final XianyuKeywordRuleRepository keywordRuleRepository;
    private final XianyuSdk xianyuSdk;

    public XianyuConsoleService(
            XianyuConsoleProperties properties,
            ObjectMapper objectMapper,
            XianyuAccountRepository accountRepository,
            XianyuProductRepository productRepository,
            XianyuKeywordRuleRepository keywordRuleRepository,
            XianyuSdk xianyuSdk) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.keywordRuleRepository = keywordRuleRepository;
        this.xianyuSdk = xianyuSdk;
    }

    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("service", "xianyu-console");
        status.put("enabled", properties.isEnabled());
        status.put("sqlitePath", properties.getSqlitePath());
        status.put("status", "running");
        return status;
    }

    public List<XianyuAccountEntity> listAccounts() {
        return accountRepository.findAll();
    }

    public Optional<XianyuAccountEntity> getAccount(long id) {
        return accountRepository.findById(id);
    }

    public XianyuAccountEntity loginWithCookies(AccountCookieLoginRequest request) {
        String cookie = resolveCookie(request);
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("Cookie is required for login");
        }

        String accountKey = request.getAccountName() != null ? request.getAccountName() : "default";
        XianyuSdk.XianyuAccount acc = xianyuSdk.account(accountKey);
        acc.setCookie(cookie);

        XianyuLoginApiService.LoginStatusResult loginResult = acc.api().checkLoginStatus(cookie);
        if (!loginResult.loggedIn) {
            throw new RuntimeException("Login failed: cookie is invalid or expired");
        }

        String userId = loginResult.userId;
        String displayName = loginResult.nickname;

        XianyuAccountEntity entity = new XianyuAccountEntity();
        entity.setPlatform("xianyu");
        entity.setAccountName(request.getAccountName());
        entity.setUserId(userId);
        entity.setDisplayName(displayName);
        entity.setCookieHeader(cookie);
        entity.setStatus("ACTIVE");
        entity.setRemark(request.getRemark());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        long id = accountRepository.insert(entity);
        entity.setId(id);
        return entity;
    }

    public Map<String, Object> updateAccountCookies(long id, AccountCookieUpdateRequest request) {
        XianyuAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        String cookie = request.getCookieHeader();
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("Cookie is required");
        }

        account.setCookieHeader(cookie);
        account.setUpdatedAt(Instant.now());
        accountRepository.update(account);

        String accountKey = account.getAccountName() != null ? account.getAccountName() : String.valueOf(id);
        XianyuSdk.XianyuAccount acc = xianyuSdk.account(accountKey);
        acc.setCookie(cookie);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", id);
        result.put("updated", true);
        return result;
    }

    public Map<String, Object> updateAccountStatus(long id, AccountStatusUpdateRequest request) {
        XianyuAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        account.setStatus(request.getStatus());
        account.setRemark(request.getRemark());
        account.setUpdatedAt(Instant.now());
        accountRepository.update(account);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", id);
        result.put("status", account.getStatus());
        return result;
    }

    public boolean deleteAccount(long id) {
        XianyuAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        String accountKey = account.getAccountName() != null ? account.getAccountName() : String.valueOf(id);
        xianyuSdk.removeAccount(accountKey);

        accountRepository.deleteById(id);
        return true;
    }

    public Map<String, Object> refreshAccountProfile(long id) {
        XianyuAccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        String accountKey = account.getAccountName() != null ? account.getAccountName() : String.valueOf(id);
        XianyuSdk.XianyuAccount acc = xianyuSdk.account(accountKey);
        XianyuApiFacade facade = acc.api();

        try {
            JsonNode userInfo = facade.getUserInfo();
            Map<String, Object> profile = objectMapper.convertValue(userInfo, Map.class);

            account.setDisplayName(trimToNull((String) profile.get("nickName")));
            account.setUserId(trimToNull((String) profile.get("userId")));
            account.setUpdatedAt(Instant.now());
            accountRepository.update(account);

            profile.put("accountId", id);
            return profile;
        } catch (Exception e) {
            account.setLastError(e.getMessage());
            account.setUpdatedAt(Instant.now());
            accountRepository.update(account);
            throw new RuntimeException("Failed to refresh profile: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listProducts(Long accountId) {
        List<XianyuProductEntity> products;
        if (accountId != null) {
            products = productRepository.findByAccountId(accountId);
        } else {
            products = new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (XianyuProductEntity p : products) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("accountId", p.getAccountId());
            m.put("itemId", p.getItemId());
            m.put("title", p.getTitle());
            m.put("price", p.getPrice());
            m.put("stock", p.getStock());
            m.put("status", p.getStatus());
            m.put("description", p.getDescription());
            m.put("createdAt", p.getCreatedAt());
            m.put("updatedAt", p.getUpdatedAt());
            result.add(m);
        }
        return result;
    }

    public Map<String, Object> createProduct(ProductUpsertRequest request) {
        XianyuSdk.XianyuAccount acc = resolveAccount(request.getAccountId());
        XianyuApiFacade facade = acc.api();

        try {
            JsonNode apiResult = facade.createProduct(
                    request.getTitle(),
                    String.valueOf(request.getPrice()),
                    request.getDescription(),
                    null, null);

            XianyuProductEntity entity = toProductEntity(request, null);
            entity.setAccountId(resolveAccountId(request.getAccountId()));
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            productRepository.insert(entity);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("local", entity);
            response.put("api", objectMapper.convertValue(apiResult, Map.class));
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create product: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> updateProduct(long id, ProductUpsertRequest request) {
        XianyuProductEntity existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        XianyuSdk.XianyuAccount acc = resolveAccount(existing.getAccountId());
        XianyuApiFacade facade = acc.api();

        try {
            if (existing.getItemId() != null) {
                facade.editProduct(
                        existing.getItemId(),
                        firstNonNull(request.getTitle(), existing.getTitle()),
                        firstNonNull(request.getDescription(), existing.getDescription()),
                        request.getPrice() != null ? String.valueOf(request.getPrice()) : String.valueOf(existing.getPrice()),
                        null, null, null);
            }

            XianyuProductEntity updated = toProductEntity(request, existing);
            productRepository.update(updated);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("local", updated);
            response.put("updated", true);
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update product: " + e.getMessage(), e);
        }
    }

    public boolean deleteProduct(long id) {
        XianyuProductEntity product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        try {
            XianyuSdk.XianyuAccount acc = resolveAccount(product.getAccountId());
            acc.api().shelfOff(product.getItemId());
        } catch (Exception e) {
            logger.warn("Failed to shelf-off product {}: {}", product.getItemId(), e.getMessage());
        }

        productRepository.deleteById(id);
        return true;
    }

    public Map<String, Object> sendMessage(MessageSendRequest request) {
        XianyuSdk.XianyuAccount acc = resolveAccount(request.getAccountId());
        XianyuApiFacade facade = acc.api();

        try {
            JsonNode result = facade.sendMessage(
                    request.getSessionId(),
                    request.getContent(),
                    request.getReceiverId());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("apiResponse", objectMapper.convertValue(result, Map.class));
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> getTimeline(long accountId, int limit) {
        XianyuAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        XianyuSdk.XianyuAccount acc = xianyuSdk.account(
                account.getAccountName() != null ? account.getAccountName() : String.valueOf(accountId));
        XianyuApiFacade facade = acc.api();

        try {
            JsonNode sessions = facade.getSessionList();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("accountId", accountId);
            response.put("sessions", objectMapper.convertValue(sessions, List.class));
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get timeline: " + e.getMessage(), e);
        }
    }

    public List<XianyuKeywordRuleEntity> listRules(Long accountId) {
        if (accountId != null) {
            return keywordRuleRepository.findByAccountId(accountId);
        }
        return new ArrayList<>();
    }

    public XianyuKeywordRuleEntity createRule(KeywordRuleUpsertRequest request) {
        XianyuKeywordRuleEntity entity = toRuleEntity(request, null);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        keywordRuleRepository.insert(entity);
        return entity;
    }

    public XianyuKeywordRuleEntity updateRule(long id, KeywordRuleUpsertRequest request) {
        XianyuKeywordRuleEntity existing = keywordRuleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found: " + id));
        return toRuleEntity(request, existing);
    }

    public boolean deleteRule(long id) {
        keywordRuleRepository.deleteById(id);
        return true;
    }

    public Map<String, Object> matchRule(RuleMatchRequest request) {
        List<XianyuKeywordRuleEntity> rules = keywordRuleRepository.findByAccountId(null);
        for (XianyuKeywordRuleEntity rule : rules) {
            if (isRuleMatched(rule, request.getText())) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("matched", true);
                result.put("rule", rule);
                result.put("replyText", rule.getReplyText());
                return result;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matched", false);
        return result;
    }

    private XianyuSdk.XianyuAccount resolveAccount(Long accountId) {
        if (accountId == null) {
            return xianyuSdk.defaultAccount();
        }
        XianyuAccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        String key = account.getAccountName() != null ? account.getAccountName() : String.valueOf(accountId);
        return xianyuSdk.account(key);
    }

    private Long resolveAccountId(Long accountId) {
        if (accountId != null) return accountId;
        List<XianyuAccountEntity> accounts = accountRepository.findAll();
        if (!accounts.isEmpty()) return accounts.get(0).getId();
        return null;
    }

    private String resolveCookie(AccountCookieLoginRequest request) {
        if (request.getCookieHeader() != null && !request.getCookieHeader().isBlank()) {
            return request.getCookieHeader();
        }
        if (request.getCookies() != null && !request.getCookies().isEmpty()) {
            return buildCookieHeader(request.getCookies());
        }
        return null;
    }

    private boolean isRuleMatched(XianyuKeywordRuleEntity rule, String text) {
        if (text == null || rule.getKeyword() == null) return false;
        String matchType = rule.getMatchType();
        if ("EXACT".equals(matchType)) {
            return text.trim().equalsIgnoreCase(rule.getKeyword().trim());
        } else if ("STARTS_WITH".equals(matchType)) {
            return text.trim().toLowerCase().startsWith(rule.getKeyword().trim().toLowerCase());
        } else {
            return text.toLowerCase().contains(rule.getKeyword().toLowerCase());
        }
    }

    private XianyuProductEntity toProductEntity(ProductUpsertRequest request, XianyuProductEntity base) {
        XianyuProductEntity entity = base != null ? base : new XianyuProductEntity();
        entity.setTitle(firstNonNull(request.getTitle(), entity.getTitle()));
        entity.setPrice(request.getPrice() != null ? request.getPrice() : entity.getPrice());
        entity.setStock(request.getStock() != null ? request.getStock() : entity.getStock());
        entity.setStatus(firstNonNull(request.getStatus(), entity.getStatus()));
        entity.setDescription(firstNonNull(request.getDescription(), entity.getDescription()));
        entity.setItemId(firstNonNull(request.getItemId(), entity.getItemId()));
        entity.setDetailUrl(firstNonNull(request.getDetailUrl(), entity.getDetailUrl()));
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private XianyuKeywordRuleEntity toRuleEntity(KeywordRuleUpsertRequest request, XianyuKeywordRuleEntity base) {
        XianyuKeywordRuleEntity entity = base != null ? base : new XianyuKeywordRuleEntity();
        entity.setRuleName(firstNonNull(request.getRuleName(), entity.getRuleName()));
        entity.setKeyword(firstNonNull(request.getKeyword(), entity.getKeyword()));
        entity.setMatchType(request.getMatchType() != null ? request.getMatchType() : (entity.getMatchType() != null ? entity.getMatchType() : "CONTAINS"));
        entity.setReplyText(firstNonNull(request.getReplyText(), entity.getReplyText()));
        entity.setEnabled(request.getEnabled() != null ? request.getEnabled() : entity.isEnabled());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : entity.getPriority());
        entity.setAccountId(request.getAccountId() != null ? request.getAccountId() : entity.getAccountId());
        entity.setUpdatedAt(Instant.now());
        keywordRuleRepository.update(entity);
        return entity;
    }

    private String buildCookieHeader(Map<String, String> cookies) {
        if (cookies == null || cookies.isEmpty()) return "";
        return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        return value.trim();
    }

    private String firstNonNull(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
