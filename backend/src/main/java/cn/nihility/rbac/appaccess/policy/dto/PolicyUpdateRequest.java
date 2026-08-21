package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 策略规则编辑请求，语义与 {@link PolicyCreateRequest} 一致，组织范围/用户属性条件/
 * 目标应用/请求控制条件（浏览器白名单/IP 白名单）均为整体替换语义（先删后插）；组织范围、
 * 用户属性条件、浏览器白名单、IP 白名单四者不能同时为空，允许"仅配置请求控制"的策略
 * （close-sso-log-and-policy-gaps change design.md Decision 3）。
 */
@Getter
@Setter
@Schema(description = "策略规则编辑请求")
public class PolicyUpdateRequest {

    /** 策略名称，必填。 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    @Schema(description = "策略名称")
    private String name;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注")
    private String remark;

    /**
     * 组织范围条件，可选，与 {@link #userAttrs}/{@link #browserRules}/{@link #ipRules}
     * 不能同时为空，整体替换。
     */
    @Valid
    @Schema(description = "组织范围条件，可选，整体替换")
    private List<PolicyOrgScopeRequestItem> orgScopes;

    /**
     * 用户属性条件，可选，与 {@link #orgScopes}/{@link #browserRules}/{@link #ipRules}
     * 不能同时为空，整体替换。
     */
    @Valid
    @Schema(description = "用户属性条件，可选，整体替换")
    private List<PolicyUserAttrRequestItem> userAttrs;

    /** 目标应用 id 列表，至少一个，整体替换。 */
    @NotEmpty(message = "目标应用不能为空")
    @Schema(description = "目标应用 id 列表，整体替换")
    private List<Long> targetAppIds;

    /**
     * 请求控制条件-浏览器白名单编码列表，可选，与 {@link #orgScopes}/{@link #userAttrs}/
     * {@link #ipRules} 不能同时为空，整体替换。
     */
    @Schema(description = "请求控制条件-浏览器白名单编码列表，可选，整体替换")
    private List<String> browserRules;

    /**
     * 请求控制条件-IP/网段白名单列表，可选，与 {@link #orgScopes}/{@link #userAttrs}/
     * {@link #browserRules} 不能同时为空，整体替换。
     */
    @Schema(description = "请求控制条件-IP/网段白名单列表，可选，整体替换")
    private List<String> ipRules;
}
