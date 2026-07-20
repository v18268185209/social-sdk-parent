^package cn.net.rjnetwork.xianyu.manager.auth.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

/**
 * 管理员用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user")
public class AdminUser extends BaseEntity {

    private String username;

    private String passwordHash;

    private String displayName;

    private String email;

    private String phone;

    private Integer roleLevel; // 1=普通管理员, 9=超级管理员
}
