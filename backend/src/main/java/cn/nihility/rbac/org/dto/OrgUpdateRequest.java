package cn.nihility.rbac.org.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新组织的请求参数。状态字段不通过此接口修改，需调用启用/停用专用接口。
 */
@Getter
@Setter
@Schema(description = "更新组织请求参数")
public class OrgUpdateRequest {

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

    /** 预留扩展字段 1，是否展示/必填/正则/唯一由"表单字段定义"配置驱动。 */
    @Size(max = 255, message = "扩展字段 1 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 1")
    private String ext1;

    /** 预留扩展字段 2。 */
    @Size(max = 255, message = "扩展字段 2 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 2")
    private String ext2;

    /** 预留扩展字段 3。 */
    @Size(max = 255, message = "扩展字段 3 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 3")
    private String ext3;

    /** 预留扩展字段 4。 */
    @Size(max = 255, message = "扩展字段 4 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 4")
    private String ext4;

    /** 预留扩展字段 5。 */
    @Size(max = 255, message = "扩展字段 5 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 5")
    private String ext5;

    /** 预留扩展字段 6。 */
    @Size(max = 255, message = "扩展字段 6 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 6")
    private String ext6;

    /** 预留扩展字段 7。 */
    @Size(max = 255, message = "扩展字段 7 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 7")
    private String ext7;

    /** 预留扩展字段 8。 */
    @Size(max = 255, message = "扩展字段 8 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 8")
    private String ext8;

    /** 预留扩展字段 9。 */
    @Size(max = 255, message = "扩展字段 9 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 9")
    private String ext9;

    /** 预留扩展字段 10。 */
    @Size(max = 255, message = "扩展字段 10 长度不能超过 255 个字符")
    @Schema(description = "预留扩展字段 10")
    private String ext10;
}
