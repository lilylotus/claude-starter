package cn.nihility.rbac.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 按字典类型编码查询启用字典项时返回的精简视图对象，仅供下拉框等业务场景使用，
 * 不包含 id、审计字段等管理视图才需要的信息。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典项精简选项")
public class DictItemOptionVO {

    /** 字典项编码。 */
    @Schema(description = "字典项编码")
    private String code;

    /** 字典项标签（展示文案）。 */
    @Schema(description = "字典项标签")
    private String label;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;
}
