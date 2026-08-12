package cn.nihility.rbac.app.sync.entity;

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
 * 应用同步字段映射持久化实体，对应表 {@code tab_app_sync_field_mapping}。只存
 * {@code metadataFieldId} 外键，源字段名称/编码不在此落快照，查询时实时 JOIN
 * {@code tab_metadata_field} 读取（app-sync-field-mapping change design.md Decision 4）。
 * 保存某个应用某个数据域的字段映射列表采用整体替换语义（先删后插），本实体不做逻辑删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_sync_field_mapping")
public class AppSyncFieldMappingEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 数据域：ORG/USER/APP/ROLE（不含字典）。 */
    private String syncDomain;

    /** 同步的源字段 id，关联 {@code tab_metadata_field.id}。 */
    private Long metadataFieldId;

    /** 应用侧目标字段名称，管理员手工填写。 */
    private String appFieldName;

    /** 应用侧目标字段编码，管理员手工填写。 */
    private String appFieldCode;

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
