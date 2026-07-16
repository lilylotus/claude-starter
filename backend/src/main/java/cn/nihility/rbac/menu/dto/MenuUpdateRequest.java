package cn.nihility.rbac.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新资源的请求参数。状态字段不通过此接口修改，需调用启用/停用专用接口。
 */
@Getter
@Setter
@Schema(description = "更新资源请求参数")
public class MenuUpdateRequest {

    /** 资源名称。 */
    @NotBlank(message = "资源名称不能为空")
    @Size(max = 64, message = "资源名称长度不能超过 64 个字符")
    @Schema(description = "资源名称")
    private String name;

    /** 资源编码，需在未删除的资源中保持唯一。 */
    @NotBlank(message = "资源编码不能为空")
    @Size(max = 64, message = "资源编码长度不能超过 64 个字符")
    @Schema(description = "资源编码")
    private String code;

    /** 上级资源 id，0 表示顶级/根节点。 */
    @NotNull(message = "上级资源不能为空")
    @Schema(description = "上级资源 id，0 表示顶级", defaultValue = "0")
    private Long parentId = 0L;

    /** 资源类型：1=菜单，2=按钮，3=API。 */
    @NotNull(message = "资源类型不能为空")
    @Schema(description = "资源类型：1=菜单，2=按钮，3=API")
    private Integer resourceType;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;
}
