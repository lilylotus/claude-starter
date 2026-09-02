package cn.nihility.rbac.loginlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录日志分页查询的筛选参数，全部字段均可选。
 */
@Getter
@Setter
@Schema(description = "登录日志分页查询参数")
public class LoginLogQueryRequest {

    /** 登录账号，精确匹配，可选。 */
    @Schema(description = "登录账号，精确匹配")
    private String loginAccount;

    /** 登录结果，精确匹配，可选。 */
    @Schema(description = "登录结果：1=成功，2=失败")
    private Integer loginResult;

    /** 登录方式，精确匹配，可选：PASSWORD=口令，SMS=短信验证码，QRCODE=扫码。 */
    @Schema(description = "登录方式：PASSWORD=口令，SMS=短信验证码，QRCODE=扫码")
    private String loginMethod;

    /** 登录时间范围起点（含），可选。 */
    @Schema(description = "登录时间范围起点（含）")
    private LocalDateTime startTime;

    /** 登录时间范围终点（含），可选。 */
    @Schema(description = "登录时间范围终点（含）")
    private LocalDateTime endTime;

    /** 页码，默认第 1 页。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数，默认 10 条。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
