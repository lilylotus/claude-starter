package cn.nihility.rbac.identity.upstream.entity;

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
 * 上游字段映射持久化实体，对应表 {@code tab_upstream_field_mapping}。方向与
 * {@code tab_app_sync_field_mapping} 相反：源是管理员手工填写的"上游字段名称/编码"
 * （上游 schema 未知，无目录可选），目标是从本系统"元数据字段"目录选择的
 * {@code metadataFieldId}（design.md Decision 5）。只存外键，不落目标字段名称/编码的
 * 快照，查询时实时 JOIN {@code tab_metadata_field} 读取。保存某个数据源某个数据域的
 * 字段映射列表采用整体替换语义（先删后插），本实体不做逻辑删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_upstream_field_mapping")
public class UpstreamFieldMappingEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属上游数据源 id，关联 {@code tab_upstream_source.id}。 */
    private Long sourceId;

    /** 数据域：ORG/USER/POSITION。 */
    private String dataType;

    /** 上游字段名称，管理员手工填写。 */
    private String upstreamFieldName;

    /** 上游字段编码，管理员手工填写，同一数据源同一数据域内不允许重复。 */
    private String upstreamFieldCode;

    /** 目标元数据字段 id，关联 {@code tab_metadata_field.id}。 */
    private Long metadataFieldId;

    /** 转换方式：NO_TRANSFORM/FIXED_VALUE/SCRIPT。 */
    private String transformType;

    /** 转换取值：固定值的具体值，或脚本源码，NO_TRANSFORM 时为空。 */
    private String transformValue;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
