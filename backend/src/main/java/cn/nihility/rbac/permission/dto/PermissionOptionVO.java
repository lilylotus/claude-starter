package cn.nihility.rbac.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 权限点精简选项视图对象，供其他模块的权限点勾选控件一次性加载全量选项使用，也用于
 * 角色详情接口回填"已分配权限点"列表，不包含审计字段等管理视图才需要的信息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限点精简选项")
public class PermissionOptionVO {

    /** 权限点 id。 */
    @Schema(description = "权限点 id")
    private Long id;

    /** 权限点名称。 */
    @Schema(description = "权限点名称")
    private String name;

    /** 权限点编码。 */
    @Schema(description = "权限点编码")
    private String code;
}
