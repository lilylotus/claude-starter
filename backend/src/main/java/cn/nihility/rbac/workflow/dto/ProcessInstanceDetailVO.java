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

    /** 当前所在节点 id，结束后为空；并行分叉场景下只反映最近一次被写入的某一分支节点，
     *  不代表全部并行分支，需要完整开放节点集合请使用 {@link #openNodes}。 */
    private String currentNodeId;

    /** 当前所在节点名称，结束后为空；并行分叉场景下同 {@link #currentNodeId} 的局限。 */
    private String currentNodeName;

    /** 当前全部开放节点集合：按 {@code tab_wf_approval_task} 状态为 {@code PENDING}/
     *  {@code CLAIMED} 的记录聚合、按 {@code (nodeId, nodeName)} 去重，并行分叉场景下可能
     *  同时包含多个节点；流程已结束时为空列表。与 {@link #currentNodeId}/
     *  {@link #currentNodeName} 共存，不替代原有单值字段。 */
    private List<OpenNodeVO> openNodes;

    /** 启动时间。 */
    private LocalDateTime startedTime;

    /** 结束时间，运行中为空。 */
    private LocalDateTime finishedTime;

    /** 完整审批轨迹，按发生时间升序排列。 */
    private List<ApprovalRecordVO> records;
}
