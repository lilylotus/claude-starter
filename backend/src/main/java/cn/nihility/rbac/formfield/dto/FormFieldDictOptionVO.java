package cn.nihility.rbac.formfield.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 渲染元数据中，字典下拉控件内嵌的可选项，来源于所关联字典类型下的启用字典项。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典下拉可选项")
public class FormFieldDictOptionVO {

    /** 选项展示文案，取自字典项标签。 */
    @Schema(description = "选项展示文案")
    private String label;

    /** 选项取值，取自字典项编码。 */
    @Schema(description = "选项取值")
    private String value;
}
