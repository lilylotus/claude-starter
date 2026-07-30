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
}
