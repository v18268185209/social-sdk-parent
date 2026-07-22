package cn.net.rjnetwork.xianyu.manager.product.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 商品同步进度追踪服务。
 * <p>前端发起同步后立即返回 syncId，后端异步执行，前端按 syncId 轮询进度。</p>
 */
@Service
public class SyncProgressService {

    /** 同步进度状态 */
    public enum Phase {
        PENDING,     // 等待开始
        LISTING,     // 正在拉列表
        DETAILING,   // 正在拉详情（核心阶段）
        COMPLETED,   // 完成
        FAILED       // 失败
    }

    /** 同步进度数据（不可变快照，线程安全） */
    public static class Progress {
        public final String syncId;
        public final Phase phase;
        public final int total;       // 列表拉到的总数
        public final int current;     // 当前处理到第几条
        public final int inserted;
        public final int updated;
        public final int failed;
        public final String message;  // 提示信息 / 错误信息

        public Progress(String syncId, Phase phase, int total, int current,
                        int inserted, int updated, int failed, String message) {
            this.syncId = syncId;
            this.phase = phase;
            this.total = total;
            this.current = current;
            this.inserted = inserted;
            this.updated = updated;
            this.failed = failed;
            this.message = message;
        }

        public static Progress pending(String syncId) {
            return new Progress(syncId, Phase.PENDING, 0, 0, 0, 0, 0, "等待开始...");
        }

        public static Progress listing(String syncId) {
            return new Progress(syncId, Phase.LISTING, 0, 0, 0, 0, 0, "正在拉取商品列表...");
        }

        public static Progress detailing(String syncId, int current, int total) {
            return new Progress(syncId, Phase.DETAILING, total, current, 0, 0, 0,
                    String.format("正在同步商品详情 (%d/%d)", current, total));
        }

        public static Progress completed(String syncId, int inserted, int updated, int failed) {
            return new Progress(syncId, Phase.COMPLETED, inserted + updated + failed,
                    inserted + updated + failed, inserted, updated, failed,
                    String.format("同步完成，新增 %d 条，更新 %d 条，失败 %d 条", inserted, updated, failed));
        }

        public static Progress failed(String syncId, String errorMsg) {
            return new Progress(syncId, Phase.FAILED, 0, 0, 0, 0, 0, "同步失败: " + errorMsg);
        }
    }

    /** 进度缓存，30 分钟过期 */
    private final Cache<String, Progress> progressCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /** 正在运行的同步任务（防重复提交） */
    private final ConcurrentMap<Long, String> runningTasks = new ConcurrentHashMap<>();

    /**
     * 创建同步任务，返回 syncId。
     * 如果该账号已有同步在跑，直接返回已有的 syncId。
     */
    public String createOrGetSyncId(Long accountId) {
        String existing = runningTasks.get(accountId);
        if (existing != null) {
            Progress p = progressCache.getIfPresent(existing);
            if (p != null && (p.phase == Phase.PENDING || p.phase == Phase.LISTING || p.phase == Phase.DETAILING)) {
                return existing;
            }
        }
        String syncId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        runningTasks.put(accountId, syncId);
        progressCache.put(syncId, Progress.pending(syncId));
        return syncId;
    }

    /** 更新进度 */
    public void update(Progress progress) {
        progressCache.put(progress.syncId, progress);
    }

    /** 查询进度 */
    public Progress getProgress(String syncId) {
        return progressCache.getIfPresent(syncId);
    }

    /** 标记任务结束（从 runningTasks 移除） */
    public void finish(Long accountId, String syncId) {
        runningTasks.remove(accountId, syncId);
    }
}
