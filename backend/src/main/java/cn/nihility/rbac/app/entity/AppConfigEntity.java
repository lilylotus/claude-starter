package cn.nihility.rbac.app.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用对外接口凭证配置持久化实体，对应表 {@code tab_app_config}，与 {@code tab_app} 一对一
 * （app-api-credentials-config change design.md Decision 1）。
 *
 * <p>{@code app_id} 列是指向 {@code tab_app.id} 的内部外键，按仓库惯例（{@code orgId}/
 * {@code ownerId}）本应映射为 Java 字段 {@code appId}，但用户可见的"AppId"术语指的是
 * {@code open_app_id} 列（系统生成的对外标识），两者语义不同不能共用字段名：本实体把
 * {@code open_app_id} 映射为 {@code appId}（对齐对外术语），内部外键改名为 {@code appRefId}
 * （design.md Decision 2）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_config")
public class AppConfigEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 内部外键，指向 {@code tab_app.id}，对应数据库列 {@code app_id}。 */
    @TableField("app_id")
    private Long appRefId;

    /** 对外应用标识（AppId），对应数据库列 {@code open_app_id}，系统生成，全局唯一。 */
    @TableField("open_app_id")
    private String appId;

    /** 对外接口 AccessKey，系统生成，全局唯一。 */
    private String accessKey;

    /** 对外接口 SecretKey，经 SM4 对称加密后的 Base64 密文，不存明文。 */
    private String secretKey;

    /** 接口签名算法：{@code SHA256} 或 {@code SM3}。 */
    private String signAlgorithm;

    /**
     * 同步方式：{@code NOTIFY}（通知，本系统主动回调 {@link #notifyUrl}）或 {@code PULL}
     * （拉取，外部系统主动调用本系统接口）。整个应用只有一份，不区分组织/用户/任职/应用/
     * 角色/字典六个数据域。
     */
    private String syncMode;

    /**
     * 是否需要签名/验签校验：出站通知请求与入站拉取请求均按此开关决定是否附加/校验签名参数
     * （app-sync-notify-pull-api change design.md Decision 3）。
     */
    private Boolean needSign;

    /**
     * 同步总开关，默认开启：关闭后该应用不再产生任何新的 {@code tab_app_data_change_log}
     * 记录（不区分组织/用户/任职/应用/角色/字典六个数据域各自是否允许同步，总开关与数据域
     * 开关是"与"的关系）、不再发送通知（因为没有变更记录可供触发，是总开关生效的自然结果）、
     * 拉取接口（按 id / 按序列号）一律返回空结果，即使总开关关闭之前已经产生历史变更记录也
     * 一律屏蔽；历史记录本身不删除，重新打开后自动恢复正常同步能力（app-sync-master-switch
     * change design.md Decision 1/2）。
     */
    private Boolean syncMasterEnabled;

    /** 通知回调接口地址（http/https），{@code syncMode} 为 {@code NOTIFY} 时必填。 */
    private String notifyUrl;

    /**
     * 通知请求自定义参数，落库为 JSON 对象字符串（key-value 均为字符串），{@code syncMode}
     * 为 {@code PULL} 时通常为空。读写时经 {@code JacksonUtils} 与 {@code Map<String, String>}
     * 互转，本字段本身只存储原始 JSON 文本。
     */
    private String notifyParams;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
