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
 * 应用访问授权策略规则持久化实体，对应表 {@code tab_app_access_policy}。组织范围/
 * 用户属性条件/目标应用分别落在 {@link PolicyOrgScopeEntity}/{@link PolicyUserAttrEntity}/
 * {@link PolicyTargetAppEntity} 三张子表，整体替换语义（先删后插）；{@code lastExecTime}/
 * {@code lastExecBy} 记录最近一次"执行"操作的时间与操作人，未执行过为空。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_app_access_policy")
public class PolicyEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 策略名称。 */
    private String name;

    /** 备注。 */
    private String remark;

    /** 状态：2000=启用，3000=停用。 */
    private Integer status;

    /**
     * 显示序号，数值越小优先级越高：最终生效权限判定（考虑请求上下文）按该字段升序排列
     * 候选策略并取排在最前的一条计算结果，与 {@code tab_role.show_order}"数值越大越靠前"的
     * 展示排序语义方向相反，使用时需注意区分（policy-condition-exclusive-priority change
     * design.md Decision）。新增/编辑时可指定，未指定默认 {@code 0}。
     */
    private Integer showOrder;

    /** 最近一次执行时间，从未执行过为空。 */
    private LocalDateTime lastExecTime;

    /** 最近一次执行人。 */
    private String lastExecBy;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
