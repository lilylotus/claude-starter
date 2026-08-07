package cn.nihility.rbac.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 当前登录用户权限编码集合响应，供前端过滤菜单/按钮展示使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前用户权限编码集合响应")
public class PermissionCodesVO {

    /** 当前用户拥有的全部权限编码（三段式，如 {@code OrgManagement:org:add}）。 */
    @Schema(description = "当前用户拥有的全部权限编码集合")
    private Set<String> codes;

    /**
     * 当前用户的管辖组织范围解析结果是否受限，供前端在权限编码之外收紧组织相关选择器的
     * 可选范围（如组织管理"新增/编辑组织"弹窗的"上级组织"选择器，见 org-scope-write-guard
     * change design.md Decision 6）。
     */
    @Schema(description = "当前用户的管辖组织范围是否受限")
    private boolean orgScopeRestricted;
}
