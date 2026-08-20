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
 * 策略规则新增请求。组织范围与用户属性条件均可选（列表可为空），但服务层校验两者
 * SHALL NOT 同时为空；目标应用不能为空（spec.md"策略规则的定义与维护"需求）。请求控制
 * 条件（浏览器白名单/IP 白名单）完全可选，两者都为空表示该策略不做请求控制限制，不受
 * 组织范围/用户属性条件"至少一项非空"约束的影响（app-access-request-control change）。
 */
@Getter
@Setter
@Schema(description = "策略规则新增请求")
public class PolicyCreateRequest {

    /** 策略名称，必填。 */
    @NotBlank(message = "策略名称不能为空")
    @Size(max = 128, message = "策略名称长度不能超过 128")
    @Schema(description = "策略名称")
    private String name;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255")
    @Schema(description = "备注")
    private String remark;

    /** 组织范围条件，可选，与 {@link #userAttrs} 不能同时为空。 */
    @Valid
    @Schema(description = "组织范围条件，可选")
    private List<PolicyOrgScopeRequestItem> orgScopes;

    /** 用户属性条件，可选，与 {@link #orgScopes} 不能同时为空。 */
    @Valid
    @Schema(description = "用户属性条件，可选")
    private List<PolicyUserAttrRequestItem> userAttrs;

    /** 目标应用 id 列表，至少一个。 */
    @NotEmpty(message = "目标应用不能为空")
    @Schema(description = "目标应用 id 列表")
    private List<Long> targetAppIds;

    /** 请求控制条件-浏览器白名单编码列表，完全可选。 */
    @Schema(description = "请求控制条件-浏览器白名单编码列表，可选")
    private List<String> browserRules;

    /** 请求控制条件-IP/网段白名单列表，完全可选。 */
    @Schema(description = "请求控制条件-IP/网段白名单列表，可选")
    private List<String> ipRules;
}
