package cn.nihility.rbac.appaccess.policy.entity;

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
 * 策略组织范围条件持久化实体，对应表 {@code tab_app_access_policy_org_scope}，字段形状
 * 对齐 {@code cn.nihility.rbac.admin.entity.AdminOrgScopeEntity}，仅把 {@code adminId}
 * 换成 {@code policyId}。无独立 {@code status}，随所属策略保存时整体替换（先删后插），
 * 物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy_org_scope")
public class PolicyOrgScopeEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_app_access_policy.id}。 */
    private Long policyId;

    /** 组织 id，关联 {@code tab_org.id}。 */
    private Long orgId;

    /** 是否包含递归子组织：{@code false}=否，{@code true}=是。 */
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
