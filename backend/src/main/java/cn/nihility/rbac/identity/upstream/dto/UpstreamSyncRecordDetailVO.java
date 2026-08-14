package cn.nihility.rbac.identity.upstream.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 上游数据同步执行记录明细视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上游数据同步执行记录明细")
public class UpstreamSyncRecordDetailVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 本次执行内该行的序号，从 1 开始。 */
    @Schema(description = "本次执行内该行的序号，从 1 开始")
    private Integer rowNo;

    /** 该行的原始上游数据（JSON 文本）。 */
    @Schema(description = "该行的原始上游数据，JSON 文本")
    private String rowData;

    /** 该行处理状态：SUCCESS（成功）/FAILED（失败）。 */
    @Schema(description = "该行处理状态：SUCCESS（成功）/FAILED（失败）")
    private String status;

    /** 失败原因，仅 {@code status=FAILED} 时有值。 */
    @Schema(description = "失败原因，仅 status=FAILED 时有值")
    private String failReason;
}
