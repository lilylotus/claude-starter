package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略组织范围条件视图对象，{@code orgName} 关联 {@code tab_org} 实时查询回填。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略组织范围条件")
public class PolicyOrgScopeVO {

    /** 组织 id。 */
    @Schema(description = "组织 id")
    private Long orgId;

    /** 组织名称。 */
    @Schema(description = "组织名称")
    private String orgName;

    /** 是否包含递归子组织。 */
    @Schema(description = "是否包含递归子组织")
    private Boolean includeChildren;

    /** 本条记录最近一次更新时间，供服务层计算"配置是否已变更待重新执行"标记使用。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
