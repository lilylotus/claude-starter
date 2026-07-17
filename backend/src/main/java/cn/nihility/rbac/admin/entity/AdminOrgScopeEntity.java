package cn.nihility.rbac.admin.entity;

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
 * 管理员组织管辖范围持久化实体，对应表 {@code tab_admin_org_scope}，通过 {@code adminId}
 * 关联 {@code tab_admin.id}、{@code orgId} 关联 {@code tab_org.id}（均不建物理外键）。
 * 该表无独立的 {@code status}，随所属管理员创建/更新时"先删后插"整体同步，不做按行 diff，
 * 也不做逻辑删除，直接物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_admin_org_scope")
public class AdminOrgScopeEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属管理员 id，关联 {@code tab_admin.id}。 */
    private Long adminId;

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
