package cn.nihility.rbac.abac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/**
 * ABAC 策略时间窗口条件请求项：{@code dayOfWeek} 可选，不指定代表每天均适用；
 * {@code startTime}/{@code endTime} 必填，结束时间须晚于起始时间（服务层二次校验，见
 * {@code AbacPolicyServiceImpl}）。
 */
@Getter
@Setter
@Schema(description = "ABAC 策略时间窗口条件")
public class AbacPolicyTimeWindowRequestItem {

    /** 星期：1=周一...7=周日，可选，不指定代表每天均适用。 */
    @Min(value = 1, message = "星期取值范围为 1~7")
    @Max(value = 7, message = "星期取值范围为 1~7")
    @Schema(description = "星期：1=周一...7=周日，不指定代表每天均适用")
    private Integer dayOfWeek;

    /** 窗口起始时间，必填。 */
    @NotNull(message = "起始时间不能为空")
    @Schema(description = "窗口起始时间")
    private LocalTime startTime;

    /** 窗口结束时间，必填，须晚于起始时间。 */
    @NotNull(message = "结束时间不能为空")
    @Schema(description = "窗口结束时间，须晚于起始时间")
    private LocalTime endTime;
}
