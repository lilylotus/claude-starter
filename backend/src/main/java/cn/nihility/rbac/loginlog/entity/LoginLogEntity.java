package cn.nihility.rbac.loginlog.entity;

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
 * 登录日志持久化实体，对应表 {@code tab_login_log}，与 {@code tab_operation_log}
 * （操作日志）完全独立的一张表，语义上不复用——登录事件没有字段级 diff 快照，失败时
 * 也往往没有已认证身份可作为操作人。日志只追加不更新不删除，{@code updateBy}/
 * {@code updateTime} 写入时与 {@code createBy}/{@code createTime} 一次性赋值为
 * 相同的值，之后不再变更。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_login_log")
public class LoginLogEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 本次登录尝试提交的账号，解密失败时为 {@code null}。 */
    private String loginAccount;

    /** 关联的 {@code tab_user.id}，账号不存在/解密失败时为 {@code null}。 */
    private Long userId;

    /** 用户姓名快照，账号不存在/解密失败时为 {@code null}。 */
    private String userName;

    /** 登录结果：1=成功，2=失败。 */
    private Integer loginResult;

    /** 失败原因文案，登录成功时为 {@code null}。 */
    private String failReason;

    /** 登录发起 IP，取不到时为空。 */
    private String loginIp;

    /** 登录终端类型，从 User-Agent 解析，解析不出时为空。 */
    private String loginTerminal;

    /** 登录操作系统，从 User-Agent 解析，解析不出时为空。 */
    private String loginOs;

    /** 登录浏览器，从 User-Agent 解析，解析不出时为空。 */
    private String loginBrowser;

    /** 原始 User-Agent 请求头，取不到时为空。 */
    private String loginUserAgent;

    /**
     * 本次登录建立的 SSO 会话标识（SSO 会话令牌的 SHA-256 摘要，非原始令牌），仅 SSO
     * 登录成功时填充；登录失败、或管理端口令登录（不产生 SSO 会话）时为 {@code null}。
     */
    private String sessionId;

    /**
     * 登录方式：{@code PASSWORD}=口令，{@code SMS}=短信验证码，{@code QRCODE}=扫码，
     * 见 {@code LoginMethod}（add-sso-login-methods change design.md Decision 7）。
     */
    private String loginMethod;

    /** 创建人，即本次登录尝试提交的账号，为空时存固定值 {@code "unknown"}。 */
    private String createBy;

    /** 创建时间，即本次登录尝试发生时间。 */
    private LocalDateTime createTime;

    /** 更新人，恒等于 {@code createBy}。 */
    private String updateBy;

    /** 更新时间，恒等于 {@code createTime}。 */
    private LocalDateTime updateTime;
}
