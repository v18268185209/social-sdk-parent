package cn.net.rjnetwork.starter.platform.xianyu.config;

import cn.net.rjnetwork.starter.platform.common.web.StarterGlobalExceptionHandler;
import cn.net.rjnetwork.starter.platform.xianyu.controller.XianyuConsoleController;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuAccountRepository;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuKeywordRuleRepository;
import cn.net.rjnetwork.starter.platform.xianyu.repository.XianyuProductRepository;
import cn.net.rjnetwork.starter.platform.xianyu.service.XianyuConsoleService;
import cn.net.rjnetwork.xianyu.service.XianyuSdk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Configuration
@ConditionalOnClass({XianyuSdk.class, JdbcTemplate.class, SQLiteDataSource.class})
@ConditionalOnProperty(prefix = "social-sdk.console.xianyu", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(XianyuConsoleProperties.class)
public class XianyuConsoleAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(XianyuConsoleAutoConfiguration.class);

    @Bean(name = "xianyuConsoleDataSource")
    @ConditionalOnMissingBean(name = "xianyuConsoleDataSource")
    public DataSource xianyuConsoleDataSource(XianyuConsoleProperties properties) {
        Path dbPath = prepareDbPath(properties.getSqlitePath());
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath.toAbsolutePath());
        logger.info("Xianyu console sqlite datasource initialized: {}", dbPath.toAbsolutePath());
        return dataSource;
    }

    @Bean(name = "xianyuConsoleJdbcTemplate")
    @ConditionalOnMissingBean(name = "xianyuConsoleJdbcTemplate")
    public JdbcTemplate xianyuConsoleJdbcTemplate(@Qualifier("xianyuConsoleDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public XianyuConsoleSchemaInitializer xianyuConsoleSchemaInitializer(
            @Qualifier("xianyuConsoleJdbcTemplate") JdbcTemplate jdbcTemplate,
            XianyuConsoleProperties properties) {
        return new XianyuConsoleSchemaInitializer(jdbcTemplate, properties.isAutoInitSchema());
    }

    @Bean
    public XianyuAccountRepository xianyuAccountRepository(
            @Qualifier("xianyuConsoleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new XianyuAccountRepository(jdbcTemplate);
    }

    @Bean
    public XianyuProductRepository xianyuProductRepository(
            @Qualifier("xianyuConsoleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new XianyuProductRepository(jdbcTemplate);
    }

    @Bean
    public XianyuKeywordRuleRepository xianyuKeywordRuleRepository(
            @Qualifier("xianyuConsoleJdbcTemplate") JdbcTemplate jdbcTemplate) {
        return new XianyuKeywordRuleRepository(jdbcTemplate);
    }

    @Bean
    public XianyuSdk xianyuSdk(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new XianyuSdk();
    }

    @Bean
    public XianyuConsoleService xianyuConsoleService(
            XianyuConsoleProperties properties,
            XianyuAccountRepository accountRepository,
            XianyuProductRepository productRepository,
            XianyuKeywordRuleRepository keywordRuleRepository,
            XianyuSdk xianyuSdk,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new XianyuConsoleService(
                properties,
                objectMapper,
                accountRepository,
                productRepository,
                keywordRuleRepository,
                xianyuSdk);
    }

    @Bean
    public XianyuConsoleController xianyuConsoleController(XianyuConsoleService service) {
        return new XianyuConsoleController(service);
    }

    @Bean
    @ConditionalOnMissingBean
    public StarterGlobalExceptionHandler starterGlobalExceptionHandler() {
        return new StarterGlobalExceptionHandler();
    }

    private Path prepareDbPath(String sqlitePath) {
        String raw = sqlitePath == null || sqlitePath.isBlank() ? "./data/social-sdk-xianyu.db" : sqlitePath;
        Path path = Paths.get(raw).toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("Create sqlite directory failed: " + parent, e);
            }
        }
        return path;
    }

    public static class XianyuConsoleSchemaInitializer {

        private final JdbcTemplate jdbcTemplate;
        private final boolean enabled;

        public XianyuConsoleSchemaInitializer(JdbcTemplate jdbcTemplate, boolean enabled) {
            this.jdbcTemplate = jdbcTemplate;
            this.enabled = enabled;
            initialize();
        }

        private void initialize() {
            if (!enabled) {
                return;
            }

            List<String> ddlList = List.of(
                    "PRAGMA journal_mode=WAL",
                    "CREATE TABLE IF NOT EXISTS xianyu_accounts ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "platform TEXT NOT NULL DEFAULT 'xianyu',"
                            + "account_name TEXT,"
                            + "user_id TEXT,"
                            + "display_name TEXT,"
                            + "cookie_header TEXT,"
                            + "cookies_json TEXT,"
                            + "status TEXT NOT NULL DEFAULT 'ACTIVE',"
                            + "remark TEXT,"
                            + "last_error TEXT,"
                            + "created_at TEXT NOT NULL,"
                            + "updated_at TEXT NOT NULL"
                            + ")",
                    "CREATE INDEX IF NOT EXISTS idx_xianyu_accounts_user_id ON xianyu_accounts(user_id)",
                    "CREATE TABLE IF NOT EXISTS xianyu_products ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "account_id INTEGER NOT NULL,"
                            + "item_id TEXT,"
                            + "title TEXT NOT NULL,"
                            + "price REAL,"
                            + "stock INTEGER,"
                            + "status TEXT,"
                            + "detail_url TEXT,"
                            + "description TEXT,"
                            + "created_at TEXT NOT NULL,"
                            + "updated_at TEXT NOT NULL"
                            + ")",
                    "CREATE INDEX IF NOT EXISTS idx_xianyu_products_account_id ON xianyu_products(account_id)",
                    "CREATE TABLE IF NOT EXISTS xianyu_keyword_rules ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "account_id INTEGER,"
                            + "rule_name TEXT,"
                            + "keyword TEXT NOT NULL,"
                            + "match_type TEXT NOT NULL DEFAULT 'CONTAINS',"
                            + "reply_text TEXT NOT NULL,"
                            + "enabled INTEGER NOT NULL DEFAULT 1,"
                            + "priority INTEGER NOT NULL DEFAULT 100,"
                            + "created_at TEXT NOT NULL,"
                            + "updated_at TEXT NOT NULL"
                            + ")",
                    "CREATE INDEX IF NOT EXISTS idx_xianyu_rules_account_id ON xianyu_keyword_rules(account_id)",
                    "CREATE INDEX IF NOT EXISTS idx_xianyu_rules_priority ON xianyu_keyword_rules(priority)"
            );

            ddlList.forEach(jdbcTemplate::execute);
        }
    }
}
