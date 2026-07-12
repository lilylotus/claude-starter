package cn.nihility.rbac.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建字典类型的请求参数。
 */
@Getter
@Setter
@Schema(description = "创建字典类型请求参数")
public class DictTypeCreateRequest {

    /** 字典类型名称。 */
    @NotBlank(message = "字典类型名称不能为空")
    @Size(max = 64, message = "字典类型名称长度不能超过 64 个字符")
    @Schema(description = "字典类型名称")
    private String name;

    /** 字典类型编码，需在未删除的字典类型中全局唯一。 */
    @NotBlank(message = "字典类型编码不能为空")
    @Size(max = 64, message = "字典类型编码长度不能超过 64 个字符")
    @Schema(description = "字典类型编码")
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
