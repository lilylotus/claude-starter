package cn.nihility.rbac.abac.entity;

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
 * ABAC 策略目标资源编码持久化实体，对应表 {@code tab_abac_policy_resource}。
 * {@code permissionCode} 取值与 {@code IdentityAuthFilter#resolveRequiredPermission()}
 * 最终解析出的权限编码口径完全一致，精确匹配，不支持通配符（abac-policy-engine change
 * design.md Decision 2）。无独立 {@code status}，随所属策略保存时整体替换（先删后插），
 * 物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_abac_policy_resource")
public class AbacPolicyResourceEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_abac_policy.id}。 */
    private Long policyId;

    /** 目标资源编码（三段式权限编码），与 {@code menu} 运行时鉴权口径一致。 */
    private String permissionCode;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
