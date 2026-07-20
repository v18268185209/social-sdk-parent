^package cn.net.rjnetwork.xianyu.manager.rule.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_keyword_rule")
public class XianyuKeywordRule extends BaseEntity {

    private Long accountId;
    private String ruleName;
    private String replyType = "KEYWORD"; // KEYWORD, AI, AUTO
    private String keyword;
    private String matchType; // CONTAINS, EXACT, STARTS_WITH (仅 KEYWORD 类型使用)
    private String replyText;
    private Boolean enabled;
    private Integer priority;
}
