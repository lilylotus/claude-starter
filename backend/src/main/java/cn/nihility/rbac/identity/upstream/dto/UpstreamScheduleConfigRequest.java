package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新上游数据源调度配置的请求参数：按 {@link #scheduleType} 二选一，条件必填字段由
 * 服务层校验（{@code INTERVAL} 要求 {@link #intervalUnit}/{@link #intervalValue}
 * 非空，{@code FIXED_TIME} 要求 {@link #fixedTime} 非空且格式为 {@code HH:mm}），
 * spec.md Requirement：定时调度触发。
 */
@Getter
@Setter
@Schema(description = "更新上游数据源调度配置请求参数")
public class UpstreamScheduleConfigRequest {

    /** 调度方式，必填，只能是 INTERVAL/FIXED_TIME。 */
    @NotBlank(message = "调度方式不能为空")
    @Pattern(regexp = "^(INTERVAL|FIXED_TIME)$", message = "调度方式只能是 INTERVAL 或 FIXED_TIME")
    @Schema(description = "调度方式：INTERVAL（按间隔）/FIXED_TIME（按每日固定时间点）")
    private String scheduleType;

    /** 间隔单位：MINUTE/HOUR，scheduleType=INTERVAL 时必填。 */
    @Pattern(regexp = "^$|^(MINUTE|HOUR)$", message = "间隔单位只能是 MINUTE 或 HOUR")
    @Schema(description = "间隔单位：MINUTE/HOUR，scheduleType=INTERVAL 时必填")
    private String intervalUnit;

    /** 间隔取值（正整数），scheduleType=INTERVAL 时必填。 */
    @Schema(description = "间隔取值（正整数），scheduleType=INTERVAL 时必填")
    private Integer intervalValue;

    /** 每日固定时间点，HH:mm 文本，scheduleType=FIXED_TIME 时必填。 */
    @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "固定时间点格式不正确，应为 HH:mm")
    @Schema(description = "每日固定时间点，HH:mm 文本，scheduleType=FIXED_TIME 时必填")
    private String fixedTime;
}
