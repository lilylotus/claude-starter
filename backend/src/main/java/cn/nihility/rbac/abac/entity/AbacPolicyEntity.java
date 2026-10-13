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
 * ABAC 策略规则持久化实体，对应表 {@code tab_abac_policy}。目标资源编码/用户属性条件/
 * 时间窗口条件分别落在 {@link AbacPolicyResourceEntity}/{@link AbacPolicyUserAttrEntity}/
 * {@link AbacPolicyTimeWindowEntity} 三张子表，整体替换语义（先删后插）；本模块只有
 * 限制（DENY-only）语义，没有 {@code effect} 字段（abac-policy-engine change design.md
 * Decision 1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_abac_policy")
public class AbacPolicyEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略名称。 */
    private String name;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用。 */
    private Integer status;

    /** 显示序号，仅用于列表展示排序，不参与判定优先级（design.md Decision 6）。 */
    private Integer showOrder;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
