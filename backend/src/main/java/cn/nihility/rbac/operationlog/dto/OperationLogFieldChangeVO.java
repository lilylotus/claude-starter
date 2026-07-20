package cn.nihility.rbac.operationlog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 单个字段的变更详情：字段中文名 + 变更前后的值。新增操作 {@code oldValue} 为
 * {@code null}，删除操作 {@code newValue} 为 {@code null}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字段变更详情")
public class OperationLogFieldChangeVO {

    /** 字段中文名，如"角色名称"。 */
    @Schema(description = "字段中文名")
    private String field;

    /** 变更前的值，新增操作时为 {@code null}。 */
    @Schema(description = "变更前的值，新增操作时为空")
    private String oldValue;

    /** 变更后的值，删除操作时为 {@code null}。 */
    @Schema(description = "变更后的值，删除操作时为空")
    private String newValue;
}
