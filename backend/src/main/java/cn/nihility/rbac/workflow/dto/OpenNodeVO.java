package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例当前开放节点视图对象：并行分叉场景下同一时刻可能有多个节点各自存在待处理任务，
 * 聚合自 {@code tab_wf_approval_task} 状态为 {@code PENDING}/{@code CLAIMED} 的记录，按
 * {@code (nodeId, nodeName)} 去重（production-approval-lifecycle change tasks.md 6.9）。
 */
@Getter
@Setter
@Builder
@Schema(description = "流程实例当前开放节点")
public class OpenNodeVO {

    /** 节点 id。 */
    private String nodeId;

    /** 节点名称。 */
    private String nodeName;
}
