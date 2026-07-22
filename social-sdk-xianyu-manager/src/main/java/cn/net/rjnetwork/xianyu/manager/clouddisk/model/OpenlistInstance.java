package cn.net.rjnetwork.xianyu.manager.clouddisk.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("openlist_instance")
public class OpenlistInstance {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer port;

    private String url;

    private String dataDir;

    /** 首次启动时自动生成的初始账号 */
    private String initialUsername;

    /** 首次启动时自动生成的初始密码 */
    private String initialPassword;

    private String installPath;

    private String osName;

    private String arch;

    private Integer installed;

    private Integer running;

    private LocalDateTime firstStartedAt;

    private LocalDateTime lastStartedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
