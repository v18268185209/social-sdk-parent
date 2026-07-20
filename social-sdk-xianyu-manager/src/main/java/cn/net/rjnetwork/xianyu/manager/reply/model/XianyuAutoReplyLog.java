^package cn.net.rjnetwork.xianyu.manager.reply.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@TableName("xianyu_auto_reply_log")
public class XianyuAutoReplyLog {
    private Long id;
    private Long accountId;
    private Long ruleId;
    private String ruleName;
    private String replyType; // KEYWORD, AI, AUTO
    private String keyword;
    private String buyerMessage;
    private String replyText;
    private Boolean matched;
    private LocalDateTime createdAt;
}
