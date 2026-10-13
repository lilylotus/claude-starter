package cn.nihility.rbac.abac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ABAC 策略时间窗口条件持久化实体，对应表 {@code tab_abac_policy_time_window}。
 * {@code dayOfWeek} 为 {@code null} 表示每天均适用，否则取 1（周一）~7（周日）中的某一天；
 * {@code startTime}/{@code endTime} 须满足结束晚于起始。同一策略内多条时间窗口之间取 OR
 * 语义（判定时在 {@code cn.nihility.rbac.abac.support.AbacTimeWindowEvaluator} 中实现，
 * abac-policy-engine change design.md Decision 4）。无独立 {@code status}，随所属策略
 * 保存时整体替换（先删后插），物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_abac_policy_time_window")
public class AbacPolicyTimeWindowEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_abac_policy.id}。 */
    private Long policyId;

    /** 星期：1=周一...7=周日，{@code null} 表示每天均适用。 */
    private Integer dayOfWeek;

    /** 窗口起始时间。 */
    private LocalTime startTime;

    /** 窗口结束时间，须晚于起始时间。 */
    private LocalTime endTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
