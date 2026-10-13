package cn.nihility.rbac.abac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ABAC 策略时间窗口条件视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ABAC 策略时间窗口条件")
public class AbacPolicyTimeWindowVO {

    /** 星期：1=周一...7=周日，{@code null} 表示每天均适用。 */
    @Schema(description = "星期：1=周一...7=周日，null 表示每天均适用")
    private Integer dayOfWeek;

    /** 窗口起始时间。 */
    @Schema(description = "窗口起始时间")
    private LocalTime startTime;

    /** 窗口结束时间。 */
    @Schema(description = "窗口结束时间")
    private LocalTime endTime;
}
