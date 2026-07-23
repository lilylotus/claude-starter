package cn.nihility.rbac.excelimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批量导入单行失败明细：Excel 行号（从 1 开始，含表头行，与 Excel 客户端里看到的
 * 行号一致）与失败原因。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量导入失败明细")
public class ImportFailItemVO {

    /** Excel 行号（从 1 开始，含表头行）。 */
    @Schema(description = "Excel 行号（从 1 开始，含表头行）")
    private Integer rowNo;

    /** 失败原因。 */
    @Schema(description = "失败原因")
    private String reason;
}
