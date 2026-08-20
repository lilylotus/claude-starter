package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略目标应用视图对象，{@code appName} 关联 {@code tab_app} 实时查询回填。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略目标应用")
public class PolicyTargetAppVO {

    /** 应用 id。 */
    @Schema(description = "应用 id")
    private Long appId;

    /** 应用名称。 */
    @Schema(description = "应用名称")
    private String appName;

    /** 本条记录最近一次更新时间，供服务层计算"配置是否已变更待重新执行"标记使用。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
