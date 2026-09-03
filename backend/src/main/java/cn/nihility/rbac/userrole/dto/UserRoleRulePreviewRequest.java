package cn.nihility.rbac.userrole.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户角色规则预览请求参数，不依赖已保存的规则，直接用给定条件现算命中用户分页列表，供
 * 新增/编辑规则表单在保存前预览使用。{@code orgScopes}、{@code userAttrs} 至少配置一类，
 * 均为空时接口 SHALL 拒绝请求。
 */
@Getter
@Setter
@Schema(description = "用户角色规则预览请求参数")
public class UserRoleRulePreviewRequest {

    /** 组织范围条件列表，与用户属性条件至少配置一类。 */
    @Valid
    @Schema(description = "组织范围条件列表，与用户属性条件至少配置一类")
    private List<UserRoleOrgScopeCondition> orgScopes = new ArrayList<>();

    /** 用户属性条件列表，与组织范围条件至少配置一类。 */
    @Valid
    @Schema(description = "用户属性条件列表，与组织范围条件至少配置一类")
    private List<UserRoleUserAttrCondition> userAttrs = new ArrayList<>();

    /** 页码，从 1 开始。 */
    @Schema(description = "页码，默认第 1 页", defaultValue = "1")
    private Integer page = 1;

    /** 每页条数。 */
    @Schema(description = "每页条数，默认 10 条", defaultValue = "10")
    private Integer pageSize = 10;
}
