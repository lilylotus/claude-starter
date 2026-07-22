package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新应用的请求参数，状态字段不通过本接口修改。
 */
@Getter
@Setter
@Schema(description = "更新应用请求参数")
public class AppUpdateRequest {

    /** 应用名称，必填。 */
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 64, message = "应用名称长度不能超过 64 个字符")
    @Schema(description = "应用名称")
    private String name;

    /** 应用编码，必填，需在未删除的应用中保持唯一。 */
    @NotBlank(message = "应用编码不能为空")
    @Size(max = 64, message = "应用编码长度不能超过 64 个字符")
    @Schema(description = "应用编码")
    private String code;

    /** 负责人用户 id，必填，须为已存在的用户。 */
    @NotNull(message = "负责人不能为空")
    @Schema(description = "负责人用户 id")
    private Long ownerId;

    /** 所属组织 id，必填。 */
    @NotNull(message = "所属组织不能为空")
    @Schema(description = "所属组织 id")
    private Long orgId;

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
