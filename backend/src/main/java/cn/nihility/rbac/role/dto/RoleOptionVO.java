package cn.nihility.rbac.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 角色精简选项视图对象，仅供其他模块的角色多选/单选选择器一次性加载全量选项使用，
 * 不包含审计字段等管理视图才需要的信息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色精简选项")
public class RoleOptionVO {

    /** 角色 id。 */
    @Schema(description = "角色 id")
    private Long id;

    /** 角色名称。 */
    @Schema(description = "角色名称")
    private String name;

    /** 角色编码。 */
    @Schema(description = "角色编码")
    private String code;
}
