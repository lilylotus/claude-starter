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
 * 应用同步组织范围持久化实体，对应表 {@code tab_app_sync_org_scope}，字段形状对齐
 * {@code cn.nihility.rbac.admin.entity.AdminOrgScopeEntity}，仅把 {@code adminId} 换成
 * {@code appRefId}。按 {@code (appRefId, syncDomain)} 关联，零行=全部数据，>=1 行=指定组织
 * 范围（app-sync-org-scope-and-app-change-log change design.md Decision 2）。该表无独立
 * {@code status}，随应用某数据域的同步范围保存时"先删后插"整体同步，不做按行 diff，也不做
 * 逻辑删除，直接物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_sync_org_scope")
public class AppSyncOrgScopeEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属应用 id，关联 {@code tab_app.id}。 */
    private Long appRefId;

    /** 数据域：ORG/USER/POSITION（应用层校验，不允许 APP/ROLE/DICT）。 */
    private String syncDomain;

    /** 组织 id，关联 {@code tab_org.id}。 */
    private Long orgId;

    /** 是否包含递归子组织：0=否，1=是。 */
    private Boolean includeChildren;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
