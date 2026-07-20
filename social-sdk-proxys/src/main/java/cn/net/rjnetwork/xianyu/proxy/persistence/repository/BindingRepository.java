package cn.net.rjnetwork.xianyu.proxy.persistence.repository;

import cn.net.rjnetwork.xianyu.proxy.persistence.entity.ProxyAccountBinding;

import java.util.List;
import java.util.Optional;

/**
 * 账号-IP 绑定持久化接口。可由不同存储 backend 实现：
 * <ul>
 *   <li>{@code SqliteBindingRepository} — 默认实现，使用应用同库</li>
 *   <li>{@code RedisBindingRepository} — 高并发/多实例部署</li>
 *   <li>自定义实现 — 业务方可 {@code @Bean} 覆盖</li>
 * </ul>
 *
 * <p>所有方法都是 synchronous。调用方应在 IO 线程池执行，避免阻塞业务线程。</p>
 */
public interface BindingRepository {

    /**
     * 保存或更新一条绑定。accountId 是业务主键，重复调用会 upsert。
     *
     * @param binding 绑定信息，accountId 不可为 null
     * @return 保存后的实体（含生成的 id）
     */
    ProxyAccountBinding save(ProxyAccountBinding binding);

    /**
     * 根据账号 ID 查询绑定。
     *
     * @param accountId 账号 ID
     * @return 绑定信息，未找到返回 empty
     */
    Optional<ProxyAccountBinding> findByAccountId(Long accountId);

    /**
     * 查询所有未删除的绑定（用于启动时还原内存状态）。
     */
    List<ProxyAccountBinding> findAllActive();

    /**
     * 根据账号 ID 标记逻辑删除。
     *
     * @param accountId 账号 ID
     * @return 是否删到了记录
     */
    boolean deleteByAccountId(Long accountId);

    /**
     * 根据出口 IP 查询所有绑定该 IP 的记录（IP 被封时批量解绑）。
     *
     * @param exitIp 出口 IP
     * @return 绑定列表
     */
    List<ProxyAccountBinding> findByExitIp(String exitIp);

    /**
     * 删除过期绑定（超过 {@code expireBefore} 的未使用记录）。
     *
     * @param expireBefore 截止时间
     * @return 删除条数
     */
    int deleteExpired(LocalDateTime expireBefore);

    /**
     * 统计当前有效绑定数（监控用）。
     */
    long countActive();
}
