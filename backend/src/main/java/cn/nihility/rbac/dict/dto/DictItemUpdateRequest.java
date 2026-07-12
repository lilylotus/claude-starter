package cn.nihility.rbac.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新字典项的请求参数。不支持通过本接口修改字典项所属的字典类型，状态字段也不通过
 * 此接口修改，需调用启用/停用专用接口。
 */
@Getter
@Setter
@Schema(description = "更新字典项请求参数")
public class DictItemUpdateRequest {

    /** 字典项标签（展示文案）。 */
    @NotBlank(message = "字典项标签不能为空")
    @Size(max = 64, message = "字典项标签长度不能超过 64 个字符")
    @Schema(description = "字典项标签")
    private String label;

    /** 字典项编码，需在同一字典类型下未删除的字典项中唯一（排除自身）。 */
    @NotBlank(message = "字典项编码不能为空")
    @Size(max = 64, message = "字典项编码长度不能超过 64 个字符")
    @Schema(description = "字典项编码")
    private String code;

    /** 显示序号，值越大越靠前。 */
    @NotNull(message = "显示序号不能为空")
    @Schema(description = "显示序号，值越大越靠前", defaultValue = "0")
    private Integer showOrder = 0;

    /** 备注，可选。 */
    @Size(max = 255, message = "备注长度不能超过 255 个字符")
    @Schema(description = "备注")
    private String remark;
}
