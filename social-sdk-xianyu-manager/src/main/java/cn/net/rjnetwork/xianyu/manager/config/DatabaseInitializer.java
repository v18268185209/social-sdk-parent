package cn.net.rjnetwork.xianyu.manager.config;

import cn.net.rjnetwork.xianyu.manager.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据库初始化配置
 * 启动时自动执行 schema.sql 初始化数据库表结构
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final DataSource dataSource;
    private final AuthService authService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    public DatabaseInitializer(DataSource dataSource, AuthService authService) {
        this.dataSource = dataSource;
        this.authService = authService;
    }

    @PostConstruct
    public void init() {
        // 确保数据目录存在
        try {
            String dbPath = System.getProperty("user.dir") + "/data";
            File dbDir = new File(dbPath);
            if (!dbDir.exists()) {
                dbDir.mkdirs();
            }

            // 执行 schema 初始化
            ClassPathResource resource = new ClassPathResource("db/schema.sql");
            if (resource.exists()) {
                ScriptUtils.executeSqlScript(dataSource.getConnection(), resource);
                logger.info("Database schema initialized successfully");
            }
        } catch (Exception e) {
            logger.warn("Database initialization skipped (may already exist): {}", e.getMessage());
        }

        // 初始化默认管理员账户
        try {
            authService.initDefaultAdmin("admin", "admin123");
            logger.info("Default admin account initialized (username: admin, password: admin123)");
        } catch (Exception e) {
            logger.warn("Admin initialization skipped: {}", e.getMessage());
        }
    }
}
