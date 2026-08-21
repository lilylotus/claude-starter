package cn.nihility.rbac.ssoprotocollog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSO 协议调用记录持久化实体，对应表 {@code tab_sso_protocol_log}，与
 * {@code tab_login_log}（登录日志）物理隔离、互不影响——本表覆盖 CAS/OAuth2.0 协议全部
 * 运行时端点（含全局单点登出接口）的每一次调用，不局限于"用户输入账号密码"这一个动作
 * （add-sso-protocol-access-log change design.md Decision 1）。日志只追加不更新不删除，
 * {@code updateBy}/{@code updateTime} 写入时与 {@code createBy}/{@code createTime}
 * 一次性赋值为相同的值，之后不再变更。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_sso_protocol_log")
public class SsoProtocolLogEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 解析出的应用 id，关联 {@code tab_app.id}，appId/client_id 解析不到应用时为 {@code null}。 */
    private Long appRefId;

    /** 原始 appId/client_id 参数值，即使解析不到 {@code appRefId} 也保留，便于排查。 */
    private String appId;

    /** 协议类型：{@code CAS}/{@code OAUTH2}，应用不存在或未开启协议时兜底为 {@code NONE}。 */
    private String protocol;

    /** 事件类型，见 {@code SsoProtocolLogEventType}。 */
    private String eventType;

    /** 关联的用户 id，处理链路尚未解析出用户身份时为 {@code null}。 */
    private Long userId;

    /**
     * 本次调用所属 SSO 会话标识（SSO 会话令牌的 SHA-256 摘要，非原始令牌），处理链路尚未
     * 解析出会话时为 {@code null}。
     */
    private String sessionId;

    /** 调用结果：1=成功，2=失败。 */
    private Integer result;

    /** 失败原因文案，成功时为 {@code null}。 */
    private String failReason;

    /** 客户端 IP，取不到时为空。 */
    private String clientIp;

    /** 原始 User-Agent 请求头，取不到时为空。 */
    private String userAgent;

    /** 创建人，为空时存固定值 {@code "unknown"}。 */
    private String createBy;

    /** 创建时间，即本次调用发生时间。 */
    private LocalDateTime createTime;

    /** 更新人，恒等于 {@code createBy}。 */
    private String updateBy;

    /** 更新时间，恒等于 {@code createTime}。 */
    private LocalDateTime updateTime;
}
