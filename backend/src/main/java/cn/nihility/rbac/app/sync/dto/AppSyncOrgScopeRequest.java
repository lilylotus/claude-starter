package cn.nihility.rbac.app.sync.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 应用同步组织范围保存请求参数（列表单行），随组织/用户/任职三个数据域的同步范围整体替换
 * 接口提交，先删后插，不做按行 diff。
 */
@Getter
@Setter
@Schema(description = "应用同步组织范围保存请求参数（列表单行）")
public class AppSyncOrgScopeRequest {

    /** 组织 id，必填，不校验其指向的组织是否存在且启用。 */
    @NotNull(message = "组织不能为空")
    @Schema(description = "组织 id")
    private Long orgId;

    /** 是否包含递归子组织，必填。 */
    @NotNull(message = "是否包含子组织不能为空")
    @Schema(description = "是否包含递归子组织", defaultValue = "false")
    private Boolean includeChildren = Boolean.FALSE;
}
