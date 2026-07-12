package cn.nihility.rbac.org.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建组织的请求参数。
 */
@Getter
@Setter
@Schema(description = "创建组织请求参数")
public class OrgCreateRequest {

    /** 组织名称。 */
    @NotBlank(message = "组织名称不能为空")
    @Size(max = 64, message = "组织名称长度不能超过 64 个字符")
    @Schema(description = "组织名称")
    private String name;

    /** 组织编码，需在未删除的组织中保持唯一。 */
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 64, message = "组织编码长度不能超过 64 个字符")
    @Schema(description = "组织编码")
    private String code;

    /** 上级组织 id，0 表示顶级/根节点。 */
    @NotNull(message = "上级组织不能为空")
    @Schema(description = "上级组织 id，0 表示顶级", defaultValue = "0")
    private Long parentId = 0L;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;
}
