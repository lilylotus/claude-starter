package cn.nihility.rbac.app.authconfig.entity;

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
 * 应用单点登录协议配置持久化实体，对应表 {@code tab_app_auth_config}，与 {@code tab_app}
 * 一对一（app-auth-protocol-config change design.md Decision 1）。{@code servicePatterns}
 * 落库为 JSON 字符串数组文本，读写时经 {@code JacksonUtils} 与 {@code List<String>} 互转，
 * 本字段本身只存储原始 JSON 文本（同 {@code AppConfigEntity#notifyParams} 的既有模式）。
 * CAS/OAuth2.0 及未来新增的单点登录协议共用同一份存储，不再按协议类型分别维护独立列
 * （unify-app-auth-service-patterns change design.md Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_auth_config")
public class AppAuthConfigEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属应用 id，关联 {@code tab_app.id}，对应数据库列 {@code app_id}。 */
    @TableField("app_id")
    private Long appRefId;

    /** 单点登录协议类型：NONE/CAS/OAUTH2。 */
    private String authProtocol;

    /**
     * 回跳地址 ANT 匹配规则列表，JSON 字符串数组文本，CAS/OAuth2.0 等协议共用同一份存储，
     * authProtocol=NONE 时为空。
     */
    private String servicePatterns;

    /**
     * 允许的登录认证方式，JSON 字符串数组文本，取值 {@code PASSWORD}/{@code SMS}/
     * {@code QRCODE} 的子集，{@code PASSWORD} 恒定包含（add-sso-login-methods change
     * design.md Decision 1），读写模式与 {@link #servicePatterns} 一致。
     */
    private String loginMethods;

    /**
     * 登出通知回调地址：单点登出触发登出主流程时，系统按此地址以 POST +
     * {@code application/x-www-form-urlencoded} 方式回调通知该应用登出事件（add-sso-single-logout
     * change design.md Decision 2/3），未配置（{@code null}）时登出不通知该应用。
     */
    private String logoutNotifyUrl;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
