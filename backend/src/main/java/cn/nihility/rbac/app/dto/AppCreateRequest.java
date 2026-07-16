package cn.nihility.rbac.app.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建应用的请求参数。
 */
@Getter
@Setter
@Schema(description = "创建应用请求参数")
public class AppCreateRequest {

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
}
