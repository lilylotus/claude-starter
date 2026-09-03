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
 * 抄送记录持久化实体，对应表 {@code tab_wf_cc_record}。非 {@code userTask}，不阻塞流程
 * （production-approval-lifecycle change design.md Decision 9/10）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_cc_record")
public class CcRecordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程实例 id，关联 {@code tab_wf_process_instance.id}。 */
    private Long instanceId;

    /** 产生该抄送的节点轮次 id，关联 {@code tab_wf_node_run.id}。 */
    private Long nodeRunId;

    /** 抄送接收人用户 id。 */
    private Long recipientId;

    /** 接收人查看时间，未读为空。 */
    private LocalDateTime readTime;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
