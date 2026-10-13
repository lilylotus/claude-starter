package cn.nihility.rbac.abac.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * ABAC 策略规则新增请求。目标资源编码不能为空（abac-policy-engine change design.md
 * Decision 2）；用户属性条件、时间窗口条件 SHALL NOT 同时为空，至少配置其中一类才允许
 * 保存（design.md Decision 5），服务层做二次校验，不完全依赖 Bean Validation。
 */
@Getter
@Setter
@Schema(description = "ABAC 策略规则新增请求")
public class AbacPolicyCreateRequest {

    /** 策略名称，必填。 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    @Schema(description = "策略名称")
    private String name;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注")
    private String remark;

    /** 显示序号，可选，未提供时默认 {@code 0}；仅用于列表展示排序，不参与判定优先级。 */
    @Schema(description = "显示序号，仅用于列表展示排序，不参与判定优先级，未提供时默认 0")
    private Integer showOrder;

    /** 目标资源编码列表，至少一个，与 {@code menu} 运行时鉴权使用的三段式权限编码口径一致。 */
    @NotEmpty(message = "目标资源编码不能为空")
    @Schema(description = "目标资源编码列表，至少一个")
    private List<String> resourceCodes;

    /**
     * 用户属性条件，可选，与 {@link #timeWindows} 可以同时配置，两者中至少一类非空。
     */
    @Valid
    @Schema(description = "用户属性条件，可选，可与时间窗口条件同时配置，两者中至少一类非空")
    private List<AbacPolicyUserAttrRequestItem> userAttrs;

    /**
     * 时间窗口条件，可选，与 {@link #userAttrs} 可以同时配置，两者中至少一类非空。
     */
    @Valid
    @Schema(description = "时间窗口条件，可选，可与用户属性条件同时配置，两者中至少一类非空")
    private List<AbacPolicyTimeWindowRequestItem> timeWindows;
}
