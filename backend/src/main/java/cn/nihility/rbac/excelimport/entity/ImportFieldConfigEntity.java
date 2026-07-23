package cn.nihility.rbac.excelimport.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Excel 导入字段配置持久化实体，对应表 {@code tab_import_field_config}。按
 * {@code bizType}（ORG/USER/POSITION/APP）维护一组导入列配置，驱动导入模板生成与
 * 批量导入的表头匹配、主键匹配、必填校验。{@code bizType} 一经创建不可修改；
 * POSITION 的 {@code __userCode}/{@code __orgCode} 两条固定标识列是否为锁定
 * （系统保护）配置不落库，由
 * {@code cn.nihility.rbac.excelimport.constant.LockedImportFieldConfigs} 在
 * 读取/更新/删除时按 {@code (bizType, fieldCode)} 计算得出。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_import_field_config")
public class ImportFieldConfigEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP，创建后不可变。 */
    private String bizType;

    /**
     * 关联的表单字段定义 id，关联 {@code tab_form_field_definition.id}，可空。非空时
     * {@code fieldCode} 取自该定义的当前 {@code fieldCode}；为空时（如 POSITION 的
     * 固定标识列）{@code fieldCode} 由创建时直接指定，不随任何表单字段定义变化。
     */
    private Long formFieldDefinitionId;

    /**
     * 字段标识：关联表单字段定义时取自其 {@code fieldCode}，冗余落库而非仅运行时
     * join，避免表单字段定义被删除后导入配置失联；POSITION 的固定标识列取值为
     * {@code __userCode}/{@code __orgCode}，同 {@code bizType} 下唯一。
     */
    private String fieldCode;

    /** Excel 表头文字，可与表单展示名称不同。 */
    private String excelHeaderName;

    /** 是否作为匹配已有记录的主键列之一，同 {@code bizType} 下可有多列勾选组成复合匹配键。 */
    private Boolean isPrimaryKey;

    /** 导入语义下的必填，独立于所绑定表单字段定义的必填开关。 */
    private Boolean isRequired;

    /** 显示序号，值越大越靠前，决定模板表头列顺序。 */
    private Integer showOrder;

    /** 状态：2000=启用，-1000=已删除（逻辑删除）。 */
    private Integer status;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
