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
 * 策略规则新增请求。组织范围、用户属性、请求控制（浏览器白名单+IP 白名单合并算一类）三者
 * 中 SHALL 至少配置一类，允许同时配置多类，仅三者均为空时拒绝保存（spec.md"策略规则的定义
 * 与维护"需求，close-sso-log-and-policy-gaps change design.md Decision 3）；目标应用不能
 * 为空（同需求）。
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

    /**
     * 显示序号，可选，未提供时默认 {@code 0}；数值越小优先级越高，运行时最终生效权限判定
     * （考虑请求上下文）按该字段升序取排在最前的候选策略计算结果（policy-condition-exclusive
     * -priority change design.md Decision）。
     */
    @Schema(description = "显示序号，数值越小优先级越高，未提供时默认 0")
    private Integer showOrder;

    /**
     * 组织范围条件，可选，与 {@link #userAttrs}、{@link #browserRules}/{@link #ipRules}
     * 可以同时配置，三者中至少一类非空。
     */
    @Valid
    @Schema(description = "组织范围条件，可选，可与用户属性、请求控制条件同时配置")
    private List<PolicyOrgScopeRequestItem> orgScopes;

    /**
     * 用户属性条件，可选，与 {@link #orgScopes}、{@link #browserRules}/{@link #ipRules}
     * 可以同时配置，三者中至少一类非空。
     */
    @Valid
    @Schema(description = "用户属性条件，可选，可与组织范围、请求控制条件同时配置")
    private List<PolicyUserAttrRequestItem> userAttrs;

    /** 目标应用 id 列表，至少一个。 */
    @NotEmpty(message = "目标应用不能为空")
    @Schema(description = "目标应用 id 列表")
    private List<Long> targetAppIds;

    /**
     * 请求控制条件-浏览器白名单编码列表，可选，与 {@link #ipRules} 合并算一类，可与
     * {@link #orgScopes}/{@link #userAttrs} 同时配置。
     */
    @Schema(description = "请求控制条件-浏览器白名单编码列表，可选，与 IP 白名单合并算一类，可与组织范围、用户属性同时配置")
    private List<String> browserRules;

    /**
     * 请求控制条件-IP/网段白名单列表，可选，与 {@link #browserRules} 合并算一类，可与
     * {@link #orgScopes}/{@link #userAttrs} 同时配置。
     */
    @Schema(description = "请求控制条件-IP/网段白名单列表，可选，与浏览器白名单合并算一类，可与组织范围、用户属性同时配置")
    private List<String> ipRules;
}
