package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例详情视图对象，含完整审批轨迹。
 */
@Getter
@Setter
@Builder
@Schema(description = "流程实例详情")
public class ProcessInstanceDetailVO {

    /** 流程实例 id。 */
    private Long id;

    /** Flowable 流程实例 id。 */
    private String flowableInstanceId;

    /** 业务对象类型。 */
    private String businessType;

    /** 业务对象 id。 */
    private Long businessId;

    /** 流程标题。 */
    private String title;

    /** 发起人用户 id。 */
    private Long applicantId;

    /** 发起人展示名称。 */
    private String applicantName;

    /** 流程实例状态。 */
    private String status;

    /** 当前所在节点 id，结束后为空。 */
    private String currentNodeId;

    /** 当前所在节点名称，结束后为空。 */
    private String currentNodeName;

    /** 启动时间。 */
    private LocalDateTime startedTime;

    /** 结束时间，运行中为空。 */
    private LocalDateTime finishedTime;

    /** 完整审批轨迹，按发生时间升序排列。 */
    private List<ApprovalRecordVO> records;
}
