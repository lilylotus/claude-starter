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

    /**
     * 标注版错误文件的 Base64 编码内容：仅包含失败的行（末尾追加一列"错误原因"、标红
     * 文字），成功导入的行不出现在这份文件里，仅当 {@link #failList} 非空时才会填充，
     * 全部成功时保持 {@code null}。
     */
    @Schema(description = "标注版错误文件的 Base64 编码内容，仅包含失败行，仅 failList 非空时填充")
    private String errorFileBase64;

    /**
     * 标注版错误文件的建议文件名：业务对象类型中文名 + 生成时刻时间戳（如
     * {@code 任职-202607252318.xlsx}），仅当 {@link #failList} 非空时才会填充，全部成功
     * 时保持 {@code null}。
     */
    @Schema(description = "标注版错误文件的建议文件名，格式为业务对象类型中文名-时间戳.xlsx，仅 failList 非空时填充")
    private String errorFileName;
}
