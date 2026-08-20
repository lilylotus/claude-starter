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
 * 策略请求控制条件-IP/网段白名单持久化实体，对应表
 * {@code tab_app_access_policy_ip_rule}（app-access-request-control change design.md
 * Decision 1）。无独立 {@code status}，随所属策略保存时整体替换（先删后插），物理删除；
 * 不参与策略"执行"的批量身份计算，只在最终生效权限判定时读取（见
 * {@code AppAccessEffectivePermissionService}）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy_ip_rule")
public class PolicyIpRuleEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属策略 id，关联 {@code tab_app_access_policy.id}。 */
    private Long policyId;

    /** 单个 IP 地址或 CIDR 网段，如 {@code 192.168.1.100} 或 {@code 192.168.1.0/24}。 */
    private String ipCidr;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
