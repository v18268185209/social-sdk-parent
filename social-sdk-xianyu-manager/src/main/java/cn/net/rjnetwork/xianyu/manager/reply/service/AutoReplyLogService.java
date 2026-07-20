^package cn.net.rjnetwork.xianyu.manager.reply.service;

import cn.net.rjnetwork.xianyu.manager.reply.mapper.AutoReplyLogMapper;
import cn.net.rjnetwork.xianyu.manager.reply.model.XianyuAutoReplyLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AutoReplyLogService {

    private final AutoReplyLogMapper logMapper;

    public AutoReplyLogService(AutoReplyLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 记录一次自动回复触发
     */
    public XianyuAutoReplyLog log(Long accountId, Long ruleId, String ruleName, String replyType,
                                  String keyword, String buyerMessage, String replyText, boolean matched) {
        XianyuAutoReplyLog log = new XianyuAutoReplyLog();
        log.setAccountId(accountId);
        log.setRuleId(ruleId);
        log.setRuleName(ruleName);
        log.setReplyType(replyType);
        log.setKeyword(keyword);
        log.setBuyerMessage(buyerMessage);
        log.setReplyText(replyText);
        log.setMatched(matched);
        log.setCreatedAt(LocalDateTime.now());
        logMapper.insert(log);
        return log;
    }

    /**
     * 分页查询日志
     */
    public Page<XianyuAutoReplyLog> listPage(int pageNum, int pageSize, Long accountId, String replyType, Boolean matched) {
        Page<XianyuAutoReplyLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<XianyuAutoReplyLog> wrapper = new LambdaQueryWrapper<>();
        if (accountId != null) wrapper.eq(XianyuAutoReplyLog::getAccountId, accountId);
        if (replyType != null && !replyType.isEmpty()) wrapper.eq(XianyuAutoReplyLog::getReplyType, replyType);
        if (matched != null) wrapper.eq(XianyuAutoReplyLog::getMatched, matched);
        wrapper.orderByDesc(XianyuAutoReplyLog::getCreatedAt);
        logMapper.selectPage(page, wrapper);
        return page;
    }
}
