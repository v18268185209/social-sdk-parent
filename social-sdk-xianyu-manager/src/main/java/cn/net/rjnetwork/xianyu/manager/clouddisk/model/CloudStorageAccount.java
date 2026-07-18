package cn.net.rjnetwork.xianyu.manager.clouddisk.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.net.rjnetwork.xianyu.manager.common.BaseEntity;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cloud_storage_account")
public class CloudStorageAccount extends BaseEntity {

    private Long accountId;

    /** BAIDU_NETDISK / QUARK_NETDISK / ALIYUN_DRIVE */
    private String provider;

    private String accessToken;

    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    private String uid;

    private Long totalSpace;

    private Long usedSpace;

    private Boolean isActive;
}
