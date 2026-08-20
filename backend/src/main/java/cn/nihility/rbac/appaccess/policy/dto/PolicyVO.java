package cn.nihility.rbac.appaccess.policy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 策略规则视图对象，携带组织范围/用户属性条件/目标应用/请求控制条件的完整回显，以及
 * 最近一次执行信息与"配置是否已变更待重新执行"标记（比较组织范围/用户属性条件/目标应用
 * 最新 {@code updateTime} 是否晚于 {@code lastExecTime}，纯查询判断，不新增状态字段，见
 * design.md Decision 4；请求控制条件不参与该判断，见 app-access-request-control change
 * design.md/tasks.md 3.4——请求控制是运行时校验，不参与策略"执行"的批量身份计算）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "策略规则")
public class PolicyVO {

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

    /** 最近一次执行时间，从未执行过为空。 */
    @Schema(description = "最近一次执行时间，从未执行过为空")
    private LocalDateTime lastExecTime;

    /** 最近一次执行人。 */
    @Schema(description = "最近一次执行人")
    private String lastExecBy;

    /** 组织范围条件列表。 */
    @Schema(description = "组织范围条件列表")
    private List<PolicyOrgScopeVO> orgScopes;

    /** 用户属性条件列表。 */
    @Schema(description = "用户属性条件列表")
    private List<PolicyUserAttrVO> userAttrs;

    /** 目标应用列表。 */
    @Schema(description = "目标应用列表")
    private List<PolicyTargetAppVO> targetApps;

    /** 请求控制条件-浏览器白名单列表，均可选（app-access-request-control change）。 */
    @Schema(description = "请求控制条件-浏览器白名单列表，可选")
    private List<PolicyBrowserRuleVO> browserRules;

    /** 请求控制条件-IP/网段白名单列表，均可选（app-access-request-control change）。 */
    @Schema(description = "请求控制条件-IP/网段白名单列表，可选")
    private List<PolicyIpRuleVO> ipRules;

    /** 配置是否已变更待重新执行：{@code lastExecTime} 非空且组织范围/用户属性条件/
     *  目标应用中任一条最新更新时间晚于 {@code lastExecTime} 时为 {@code true}。 */
    @Schema(description = "配置是否已变更待重新执行")
    private Boolean pendingReExecute;
}
