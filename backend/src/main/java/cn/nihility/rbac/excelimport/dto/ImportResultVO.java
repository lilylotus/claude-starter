package cn.nihility.rbac.excelimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 批量导入结果：成功导入条数与失败明细列表。整批处理采用逐行独立事务提交，个别行
 * 失败不影响其余行，因此 {@code successCount + failList.size()} 等于本次上传的有效
 * 数据行数（不含表头行、不含空白行）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量导入结果")
public class ImportResultVO {

    /** 成功导入（新增或更新）的条数。 */
    @Schema(description = "成功导入条数")
    private Integer successCount;

    /** 失败明细列表，含行号与失败原因。 */
    @Schema(description = "失败明细列表")
    @Builder.Default
    private List<ImportFailItemVO> failList = new ArrayList<>();
}
