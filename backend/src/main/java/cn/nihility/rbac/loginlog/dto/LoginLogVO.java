package cn.nihility.rbac.loginlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 登录日志列表行视图对象，不拆分详情接口——{@code loginUserAgent} 最长也就几百字符，
 * 不存在需要在列表接口中省略的"可能很大的结构化字段"。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录日志列表行")
public class LoginLogVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 本次登录尝试提交的账号，解密失败时为空。 */
    @Schema(description = "登录账号，解密失败时为空")
    private String loginAccount;

    /** 关联的用户 id，账号不存在/解密失败时为空。 */
    @Schema(description = "关联的用户 id，账号不存在/解密失败时为空")
    private Long userId;

    /** 用户姓名快照，账号不存在/解密失败时为空。 */
    @Schema(description = "用户姓名快照，账号不存在/解密失败时为空")
    private String userName;

    /** 登录结果：1=成功，2=失败。 */
    @Schema(description = "登录结果：1=成功，2=失败")
    private Integer loginResult;

    /** 登录结果中文文案。 */
    @Schema(description = "登录结果中文文案")
    private String loginResultLabel;

    /** 失败原因文案，登录成功时为空。 */
    @Schema(description = "失败原因文案，登录成功时为空")
    private String failReason;

    /** 登录发起 IP，可为空。 */
    @Schema(description = "登录发起 IP")
    private String loginIp;

    /** 登录终端类型，可为空。 */
    @Schema(description = "登录终端类型")
    private String loginTerminal;

    /** 登录操作系统，可为空。 */
    @Schema(description = "登录操作系统")
    private String loginOs;

    /** 登录浏览器，可为空。 */
    @Schema(description = "登录浏览器")
    private String loginBrowser;

    /** 原始 User-Agent 请求头，可为空。 */
    @Schema(description = "原始 User-Agent 请求头")
    private String loginUserAgent;

    /** 登录发起时间。 */
    @Schema(description = "登录发起时间")
    private LocalDateTime createTime;
}
