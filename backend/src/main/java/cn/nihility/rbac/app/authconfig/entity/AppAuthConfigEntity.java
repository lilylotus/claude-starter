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
 * 一对一（app-auth-protocol-config change design.md Decision 1）。{@code casServicePatterns}/
 * {@code oauth2RedirectUriPatterns} 落库为 JSON 字符串数组文本，读写时经 {@code JacksonUtils}
 * 与 {@code List<String>} 互转，本字段本身只存储原始 JSON 文本（同
 * {@code AppConfigEntity#notifyParams} 的既有模式）。
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

    /** CAS service 参数 ANT 匹配规则列表，JSON 字符串数组文本，authProtocol=NONE 时为空。 */
    private String casServicePatterns;

    /** OAuth2 redirect_uri ANT 匹配规则列表，JSON 字符串数组文本，authProtocol=NONE 时为空。 */
    private String oauth2RedirectUriPatterns;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
