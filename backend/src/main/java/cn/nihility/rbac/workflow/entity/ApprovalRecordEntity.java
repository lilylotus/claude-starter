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
 * 审批轨迹持久化实体，对应表 {@code tab_wf_approval_record}。完整审批轨迹，"我的已办"查询与
 * {@code WithdrawPolicy} 判断"是否已有人审批过"均以本表为准，不查 Flowable 的
 * {@code ACT_HI_TASKINST}。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_approval_record")
public class ApprovalRecordEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属流程实例 id，关联 {@code tab_wf_process_instance.id}。 */
    private Long processInstanceId;

    /** 关联的审批任务 id，{@code SUBMIT}/{@code TERMINATE} 无关联任务时为空。 */
    private Long taskId;

    /** 节点 id，无关联节点时为空。 */
    private String nodeId;

    /** 节点名称，无关联节点时为空。 */
    private String nodeName;

    /** 操作人用户 id。 */
    private Long operatorId;

    /** 动作类型，{@link cn.nihility.rbac.workflow.constant.ApprovalAction} 字面量。 */
    private String action;

    /** 处理意见/说明。 */
    private String remark;

    /** 转办/委派场景记录的原处理人用户 id，其余场景为空。 */
    private Long fromUserId;

    /** 转办/委派场景记录的新处理人用户 id，其余场景为空。 */
    private Long toUserId;

    /** 创建人。 */
    private String createBy;

    /** 创建时间，即操作发生时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
