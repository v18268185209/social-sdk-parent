package cn.net.rjnetwork.xianyu.manager.message.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_message")
public class XianyuMessage extends BaseEntity {

    private Long accountId;
    private String sessionId;
    private String senderId;
    private String senderName;
    private String content;
    private String msgType; // TEXT, IMAGE, SYSTEM
    private String direction; // INCOMING, OUTGOING
    private Boolean autoReply;
    private LocalDateTime messageTime;
}
