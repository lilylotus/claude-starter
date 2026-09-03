package cn.nihility.rbac.workflow.entity;

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
 * 节点轮次持久化实体，对应表 {@code tab_wf_node_run}。每次节点激活生成一条轮次记录，
 * 承载会签计票（N/A/R）与作用域隔离；重入节点（退回后再次到达）产生新的 {@code roundNo}
 * （production-approval-lifecycle change design.md Decision 9）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_node_run")
public class NodeRunEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程实例 id，关联 {@code tab_wf_process_instance.id}。 */
    private Long instanceId;

    /** 节点 id。 */
    private String nodeId;

    /** Flowable 执行 execution id，MI 场景对应 miBody execution。 */
    private String executionId;

    /** 同一节点第几次激活（退回重建轮次时递增）。 */
    private Integer roundNo;

    /** 总票数 N。 */
    private Integer totalCount;

    /** 同意票数 A。 */
    private Integer agreeCount;

    /** 反对/驳回票数 R。 */
    private Integer rejectCount;

    /** 轮次状态：{@code RUNNING}/{@code COMPLETED}/{@code CANCELLED}。 */
    private String runStatus;

    /** 乐观锁修订号，同实例锁内更新计票。 */
    private Long revision;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
