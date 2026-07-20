package cn.net.rjnetwork.xianyu.manager.notify.model;

import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通知模板（按场景）。标题/正文支持 {var} 占位符，由 TemplateRenderer 渲染。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notify_template")
public class NotifyTemplate extends BaseEntity {

    private String scenario;  // 见 NotifyScenario
    private String titleTpl;
    private String bodyTpl;
    private Boolean enabled = true;
}
