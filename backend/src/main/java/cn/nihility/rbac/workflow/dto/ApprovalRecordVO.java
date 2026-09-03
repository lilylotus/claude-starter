package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 审批轨迹视图对象。
 */
@Getter
@Setter
@Builder
@Schema(description = "审批轨迹")
public class ApprovalRecordVO {

    /** 主键 id。 */
    private Long id;

    /** 所属流程实例 id。 */
    private Long processInstanceId;

    /** 关联的审批任务 id，可为空。 */
    private Long taskId;

    /** 节点 id，可为空。 */
    private String nodeId;

    /** 节点名称，可为空。 */
    private String nodeName;

    /** 操作人用户 id。 */
    private Long operatorId;

    /** 操作人展示名称。 */
    private String operatorName;

    /** 动作类型。 */
    private String action;

    /** 处理意见/说明。 */
    private String remark;

    /** 转办/委派场景记录的原处理人用户 id，可为空。 */
    private Long fromUserId;

    /** 转办/委派场景记录的原处理人展示名称，可为空。 */
    private String fromUserName;

    /** 转办/委派场景记录的新处理人用户 id，可为空。 */
    private Long toUserId;

    /** 转办/委派场景记录的新处理人展示名称，可为空。 */
    private String toUserName;

    /** 操作发生时间。 */
    private LocalDateTime createTime;
}
