package cn.nihility.rbac.metadata.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 元数据字段详情/列表行视图对象。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "元数据字段详情")
public class MetadataFieldVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    @Schema(description = "业务对象类型：ORG/USER/POSITION/APP")
    private String bizType;

    /** 字段所属表名称，如 tab_org。 */
    @Schema(description = "字段所属表名称")
    private String tableName;

    /** 字段列名（数据库字段定义），如 code、ext6。 */
    @Schema(description = "字段列名（数据库字段定义）")
    private String columnName;

    /** 字段类型（数据库字段类型），如 VARCHAR(255)。 */
    @Schema(description = "字段类型（数据库字段类型）")
    private String columnType;

    /** 字段标识（前端/DTO 使用），如 idCard、showOrder，可编辑，同一业务对象类型下需唯一。 */
    @Schema(description = "字段标识（前端/DTO 使用），可编辑，同一业务对象类型下需唯一")
    private String fieldCode;

    /** 字段名称，如"组织编码"。 */
    @Schema(description = "字段名称")
    private String fieldName;

    /** 状态：2000=启用，3000=停用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，3000=停用，-1000=已删除")
    private Integer status;

    /** 创建人。 */
    @Schema(description = "创建人")
    private String createBy;

    /** 创建时间。 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 更新人。 */
    @Schema(description = "更新人")
    private String updateBy;

    /** 更新时间。 */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
