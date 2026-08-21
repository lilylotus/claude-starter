package cn.nihility.rbac.ssoprotocollog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * SSO 协议调用记录列表行视图对象，不拆分详情接口——字段均为简单标量，不存在需要在列表接口
 * 中省略的"可能很大的结构化字段"。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 协议调用记录列表行")
public class SsoProtocolLogVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 解析出的应用 id，解析不到应用时为空。 */
    @Schema(description = "解析出的应用 id，解析不到应用时为空")
    private Long appRefId;

    /** 原始 appId/client_id 参数值。 */
    @Schema(description = "原始 appId/client_id 参数值")
    private String appId;

    /** 协议类型：CAS/OAUTH2/NONE。 */
    @Schema(description = "协议类型：CAS/OAUTH2/NONE")
    private String protocol;

    /** 事件类型。 */
    @Schema(description = "事件类型：LOGIN/SERVICE_VALIDATE/LOGOUT/AUTHORIZE/TOKEN/USERINFO")
    private String eventType;

    /** 关联的用户 id，处理链路尚未解析出用户身份时为空。 */
    @Schema(description = "关联的用户 id，处理链路尚未解析出用户身份时为空")
    private Long userId;

    /** 调用结果：1=成功，2=失败。 */
    @Schema(description = "调用结果：1=成功，2=失败")
    private Integer result;

    /** 调用结果中文文案。 */
    @Schema(description = "调用结果中文文案")
    private String resultLabel;

    /** 失败原因文案，成功时为空。 */
    @Schema(description = "失败原因文案，成功时为空")
    private String failReason;

    /** 客户端 IP，可为空。 */
    @Schema(description = "客户端 IP")
    private String clientIp;

    /**
     * 本次调用所属 SSO 会话标识（会话令牌 SHA-256 摘要，非原始令牌），处理链路尚未解析出
     * 会话时为空；供登录日志页面下钻查看时与登录记录的会话标识核对展示。
     */
    @Schema(description = "SSO 会话标识（会话令牌摘要），处理链路尚未解析出会话时为空")
    private String sessionId;

    /** 原始 User-Agent 请求头，可为空。 */
    @Schema(description = "原始 User-Agent 请求头")
    private String userAgent;

    /** 调用发生时间。 */
    @Schema(description = "调用发生时间")
    private LocalDateTime createTime;
}
