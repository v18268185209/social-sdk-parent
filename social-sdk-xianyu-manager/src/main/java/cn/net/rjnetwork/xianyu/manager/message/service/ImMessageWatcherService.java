package cn.net.rjnetwork.xianyu.manager.message.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 兼容旧监听入口。
 * <p>旧实现仍调用已失效的 mtop.taobao.idlemessage.pc.session.sync，导致“自动监听失败”。
 * 真实消息链路已统一收敛到 {@link MessageService}：WSS /r/Conversation/listNewestPagination
 * 拉会话，再用 /r/MessageManager/listUserMessages 拉历史并按 msgId 增量入库。</p>
 */
@Service
public class ImMessageWatcherService {

    private static final Logger log = LoggerFactory.getLogger(ImMessageWatcherService.class);

    @PostConstruct
    public void init() {
        log.info("[IM] watcher service initialized; active polling is handled by MessageService");
    }
}
