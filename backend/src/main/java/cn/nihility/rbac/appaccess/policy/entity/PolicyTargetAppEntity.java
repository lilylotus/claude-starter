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
 * 策略目标应用持久化实体，对应表 {@code tab_app_access_policy_target_app}。无独立
 * {@code status}，随所属策略保存时整体替换（先删后插），物理删除。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy_target_app")
public class PolicyTargetAppEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_app_access_policy.id}。 */
    private Long policyId;

    /** 目标应用 id，关联 {@code tab_app.id}。 */
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
