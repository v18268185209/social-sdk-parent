package cn.net.rjnetwork.xianyu.chrome.core;

import cn.net.rjnetwork.xianyu.chrome.config.ChromeConfig;
import cn.net.rjnetwork.xianyu.chrome.exception.ChromeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.BitSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CDP 端口池管理器。
 *
 * <p>管理 {@link ChromeConfig#getPortRangeStart()} ~ {@link ChromeConfig#getPortRangeEnd()} 之间的端口分配/释放。
 * 使用 {@link BitSet} 跟踪分配状态，线程安全。
 */
@Component
public class ChromePortPool {

    private static final Logger log = LoggerFactory.getLogger(ChromePortPool.class);

    private final BitSet portBits;
    private final int rangeStart;
    private final int rangeEnd;
    private final ReentrantLock lock = new ReentrantLock();

    public ChromePortPool(ChromeConfig config) {
        this.rangeStart = config.getPortRangeStart();
        this.rangeEnd = config.getPortRangeEnd();
        int total = rangeEnd - rangeStart + 1;
        if (total <= 0) {
            throw new IllegalArgumentException("无效的 CDP 端口范围: " + rangeStart + " ~ " + rangeEnd);
        }
        this.portBits = new BitSet(total);
    }

    /**
     * 分配一个空闲端口。
     *
     * @return 可用端口号
     * @throws ChromeException 无可用端口
     */
    public int acquirePort() {
        lock.lock();
        try {
            int clearIdx = portBits.nextClearBit(0);
            if (clearIdx >= portBits.size()) {
                throw ChromeException.noAvailablePort();
            }
            int port = rangeStart + clearIdx;
            portBits.set(clearIdx);
            log.debug("[PORT_POOL] 分配端口: {}", port);
            return port;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 释放端口（端口重新回到池中）。
     *
     * @param port 要释放的端口号
     */
    public void releasePort(int port) {
        lock.lock();
        try {
            int idx = port - rangeStart;
            if (idx < 0 || idx >= portBits.size()) {
                log.warn("[PORT_POOL] 释放端口超出范围: {}", port);
                return;
            }
            if (portBits.get(idx)) {
                portBits.clear(idx);
                log.debug("[PORT_POOL] 释放端口: {}", port);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 强制占用指定端口（适用于数据库中有记录的 profile 恢复）。
     *
     * @param port 要占用的端口
     * @return true 成功（端口当时空闲）；false 端口已被占用
     */
    public boolean occupyPort(int port) {
        lock.lock();
        try {
            int idx = port - rangeStart;
            if (idx < 0 || idx >= portBits.size()) {
                throw new IllegalArgumentException("端口超出管理范围: " + port);
            }
            if (portBits.get(idx)) {
                return false;
            }
            portBits.set(idx);
            log.debug("[PORT_POOL] 强制占用端口: {}", port);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询当前空闲端口数量。
     */
    public int availableCount() {
        lock.lock();
        try {
            return portBits.size() - portBits.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询已分配端口数量。
     */
    public int allocatedCount() {
        lock.lock();
        try {
            return portBits.cardinality();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 指定端口是否在使用中。
     */
    public boolean isAllocated(int port) {
        lock.lock();
        try {
            int idx = port - rangeStart;
            return idx >= 0 && idx < portBits.size() && portBits.get(idx);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询端口范围总容量。
     */
    public int totalCapacity() {
        return rangeEnd - rangeStart + 1;
    }
}
