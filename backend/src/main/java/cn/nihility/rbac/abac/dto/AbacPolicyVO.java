package cn.nihility.rbac.abac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ABAC 策略规则视图对象，携带目标资源编码/用户属性条件/时间窗口条件的完整回显。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "ABAC 策略规则")
public class AbacPolicyVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 策略名称。 */
    @Schema(description = "策略名称")
    private String name;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 状态：2000=启用，3000=停用。 */
    @Schema(description = "状态：2000=启用，3000=停用")
    private Integer status;

    /** 显示序号，仅用于列表展示排序，不参与判定优先级。 */
    @Schema(description = "显示序号，仅用于列表展示排序，不参与判定优先级")
    private Integer showOrder;

    /** 目标资源编码列表。 */
    @Schema(description = "目标资源编码列表")
    private List<String> resourceCodes;

    /** 用户属性条件列表。 */
    @Schema(description = "用户属性条件列表")
    private List<AbacPolicyUserAttrVO> userAttrs;

    /** 时间窗口条件列表。 */
    @Schema(description = "时间窗口条件列表")
    private List<AbacPolicyTimeWindowVO> timeWindows;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
