package cn.nihility.rbac.ssoprotocollog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * SSO 协议调用记录分页查询的筛选参数，全部字段均可选。
 */
@Getter
@Setter
@Schema(description = "SSO 协议调用记录分页查询参数")
public class SsoProtocolLogQueryRequest {

    /** 应用 id（{@code tab_app.id}），精确匹配，可选。 */
    @Schema(description = "应用 id，精确匹配")
    private Long appRefId;

    /** 协议类型，精确匹配，可选。 */
    @Schema(description = "协议类型：CAS/OAUTH2/NONE")
    private String protocol;

    /** 事件类型，精确匹配，可选。 */
    @Schema(description = "事件类型：LOGIN/SERVICE_VALIDATE/LOGOUT/AUTHORIZE/TOKEN/USERINFO")
    private String eventType;

    /** 调用结果，精确匹配，可选。 */
    @Schema(description = "调用结果：1=成功，2=失败")
    private Integer result;

    /**
     * SSO 会话标识（会话令牌 SHA-256 摘要），精确匹配，可选；主要供"登录日志"页面按某条
     * 登录记录的 {@code sessionId} 查询该会话之后触发的全部 CAS/OAuth2 协议调用记录使用。
     */
    @Schema(description = "SSO 会话标识（会话令牌摘要），精确匹配，可选")
    private String sessionId;

    /** 调用时间范围起点（含），可选。 */
    @Schema(description = "调用时间范围起点（含）")
    private LocalDateTime startTime;

    /** 调用时间范围终点（含），可选。 */
    @Schema(description = "调用时间范围终点（含）")
    private LocalDateTime endTime;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
