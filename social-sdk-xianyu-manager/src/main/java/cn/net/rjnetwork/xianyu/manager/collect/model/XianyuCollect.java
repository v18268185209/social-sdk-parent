package cn.net.rjnetwork.xianyu.manager.collect.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("xianyu_collect")
public class XianyuCollect extends BaseEntity {

    private Long accountId;
    private String targetType; // ITEM, USER, SHOP
    private String targetId;
    private String targetName;
    private LocalDateTime collectedAt;
}
