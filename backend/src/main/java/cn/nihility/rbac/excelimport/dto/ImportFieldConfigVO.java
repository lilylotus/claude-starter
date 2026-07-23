package cn.nihility.rbac.excelimport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Excel 导入字段配置详情/列表行视图对象。{@code locked} 并非该实体的持久化列，而是
 * 根据 {@code (bizType, fieldCode)} 是否命中
 * {@code cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs} 白名单计算
 * 得出，供前端渲染"系统保护"标记、隐藏编辑必填/删除入口使用。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Excel 导入字段配置详情")
public class ImportFieldConfigVO {

    /** 主键 id。 */
    @Schema(description = "主键 id")
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    @Schema(description = "业务对象类型：ORG/USER/POSITION/APP")
    private String bizType;

    /** 关联的表单字段定义 id，可空。 */
    @Schema(description = "关联的表单字段定义 id")
    private Long formFieldDefinitionId;

    /** 字段标识。 */
    @Schema(description = "字段标识")
    private String fieldCode;

    /** Excel 表头文字。 */
    @Schema(description = "Excel 表头文字")
    private String excelHeaderName;

    /** 是否作为匹配已有记录的主键列之一。 */
    @Schema(description = "是否作为匹配已有记录的主键列之一")
    private Boolean isPrimaryKey;

    /** 导入语义下的必填。 */
    @Schema(description = "导入语义下的必填")
    private Boolean isRequired;

    /** 显示序号，值越大越靠前。 */
    @Schema(description = "显示序号，值越大越靠前")
    private Integer showOrder;

    /** 状态：2000=启用，-1000=已删除。 */
    @Schema(description = "状态：2000=启用，-1000=已删除")
    private Integer status;

    /**
     * 是否为锁定（系统保护）配置，根据 {@code (bizType, fieldCode)} 是否命中
     * {@code LockedImportFieldConfigs} 白名单计算得出，不落库。锁定的配置不可删除，
     * 也不可把主键/必填标记改为否。
     */
    @Schema(description = "是否为锁定（系统保护）配置，计算得出，不可删除/取消主键或必填标记")
    private Boolean locked;

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
