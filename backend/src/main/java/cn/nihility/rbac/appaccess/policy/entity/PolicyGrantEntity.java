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
 * 策略计算结果（POLICY 来源授权记录）持久化实体，对应表
 * {@code tab_app_access_policy_grant}。点击"执行"后按 {@code policyId} 整体重建（先按
 * {@code policyId} 物理删除全部旧记录，再批量插入本次计算得到的新集合），SHALL NOT 影响
 * {@code tab_app_access_manual_override} 表的任何记录（design.md Decision 2）。同一
 * {@code userId+appId} 组合可能出现在多条不同 {@code policyId} 下。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy_grant")
public class PolicyGrantEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产生该授权记录的策略 id，关联 {@code tab_app_access_policy.id}。 */
    private Long policyId;

    /** 用户 id，关联 {@code tab_user.id}。 */
    private Long userId;

    /** 应用 id，关联 {@code tab_app.id}。 */
    private Long appId;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
