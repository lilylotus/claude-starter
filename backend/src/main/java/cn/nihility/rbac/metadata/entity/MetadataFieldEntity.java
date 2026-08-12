package cn.nihility.rbac.metadata.entity;

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
 * 元数据字段配置持久化实体，对应表 {@code tab_metadata_field}。记录组织/用户/任职/
 * 应用四类业务对象"可开放配置"的表字段目录，{@code tableName}/{@code columnName}/
 * {@code columnType}/{@code bizType} 一经迁移写入不可通过接口修改，
 * {@code fieldName}/{@code fieldCode}/{@code status} 均可编辑；{@code fieldCode}
 * 需在同一 {@code bizType} 下保持唯一。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_metadata_field")
public class MetadataFieldEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务对象类型：ORG/USER/POSITION/APP/ROLE，创建后不可修改。 */
    private String bizType;

    /** 字段所属表名称，如 tab_org，创建后不可修改。 */
    private String tableName;

    /** 字段列名（数据库字段定义），如 code、ext6，创建后不可修改。 */
    private String columnName;

    /** 字段类型（数据库字段类型），如 VARCHAR(255)，创建后不可修改。 */
    private String columnType;

    /** 字段标识（前端/DTO 使用），如 idCard、showOrder，可编辑，同一 bizType 下需唯一。 */
    private String fieldCode;

    /** 字段名称，如"组织编码"，可编辑。 */
    private String fieldName;

    /** 状态：2000=启用，3000=停用，-1000=已删除（当前无接口可写入该状态）。 */
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
