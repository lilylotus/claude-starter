package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 用户角色规则详情视图对象，供角色管理页面"批量规则"列表直接使用（一个角色的规则条数通常
 * 不多，列表接口直接返回完整详情，不需要额外的单条详情接口）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户角色规则详情")
public class UserRoleRuleVO {

    /** 规则 id。 */
    @Schema(description = "规则 id")
    private Long id;

    /** 目标角色 id。 */
    @Schema(description = "目标角色 id")
    private Long roleId;

    /** 规则名称。 */
    @Schema(description = "规则名称")
    private String name;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** 最近一次执行时间，从未执行过为空。 */
    @Schema(description = "最近一次执行时间，从未执行过为空")
    private LocalDateTime lastExecTime;

    /** 最近一次执行人。 */
    @Schema(description = "最近一次执行人")
    private String lastExecBy;

    /** 当前命中人数，现查 {@code COUNT(DISTINCT user_id) FROM tab_user_role_rule_grant}。 */
    @Schema(description = "当前命中人数")
    private Integer hitCount;

    /** 组织范围条件列表。 */
    @Builder.Default
    @Schema(description = "组织范围条件列表")
    private List<UserRoleRuleOrgScopeVO> orgScopes = new ArrayList<>();

    /** 用户属性条件列表。 */
    @Builder.Default
    @Schema(description = "用户属性条件列表")
    private List<UserRoleRuleUserAttrVO> userAttrs = new ArrayList<>();
}
