package cn.nihility.rbac.app.authconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 应用用户信息响应字段映射持久化实体，对应表 {@code tab_app_userinfo_field_mapping}，
 * CAS/OAuth2.0 协议共用（add-sso-userinfo-field-mapping change design.md Decision 2）。
 * 只存 {@code metadataFieldId} 外键（可为空，为空表示固定的"用户ID"伪字段），本地字段
 * 名称/编码不在此落快照，查询时实时 LEFT JOIN {@code tab_metadata_field} 读取。整体
 * 替换语义（先删后插），不做逻辑删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_userinfo_field_mapping")
public class AppUserinfoFieldMappingEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属应用 id，关联 {@code tab_app.id}，对应数据库列 {@code app_id}。 */
    @TableField("app_id")
    private Long appRefId;

    /** 本地字段，关联 {@code tab_metadata_field.id}；为空表示固定的"用户ID"伪字段。 */
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
