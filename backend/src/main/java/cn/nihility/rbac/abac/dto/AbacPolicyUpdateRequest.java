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
 * ABAC 策略规则编辑请求，语义与 {@link AbacPolicyCreateRequest} 一致，目标资源编码/
 * 用户属性条件/时间窗口条件均为整体替换语义（先删后插）；用户属性条件、时间窗口条件两者
 * 中至少配置一类非空（abac-policy-engine change design.md Decision 5）。
 */
@Getter
@Setter
@Schema(description = "ABAC 策略规则编辑请求")
public class AbacPolicyUpdateRequest {

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

    /** 目标资源编码列表，至少一个，整体替换。 */
    @NotEmpty(message = "目标资源编码不能为空")
    @Schema(description = "目标资源编码列表，至少一个，整体替换")
    private List<String> resourceCodes;

    /**
     * 用户属性条件，可选，与 {@link #timeWindows} 可以同时配置，两者中至少一类非空，
     * 整体替换。
     */
    @Valid
    @Schema(description = "用户属性条件，可选，可与时间窗口条件同时配置，两者中至少一类非空，整体替换")
    private List<AbacPolicyUserAttrRequestItem> userAttrs;

    /**
     * 时间窗口条件，可选，与 {@link #userAttrs} 可以同时配置，两者中至少一类非空，
     * 整体替换。
     */
    @Valid
    @Schema(description = "时间窗口条件，可选，可与用户属性条件同时配置，两者中至少一类非空，整体替换")
    private List<AbacPolicyTimeWindowRequestItem> timeWindows;
}
