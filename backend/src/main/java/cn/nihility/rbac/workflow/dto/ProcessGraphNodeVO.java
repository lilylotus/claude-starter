package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例详情完整节点图中的单个只读节点：按流程定义快照解析出结构信息（id/类型/名称/坐标），
 * 状态由服务端结合审批轨迹与当前开放任务计算得出（add-approval-remark-and-process-flowchart
 * change design.md Decision 4）。
 */
@Getter
@Setter
@Builder
@Schema(description = "流程实例详情节点")
public class ProcessGraphNodeVO {

    /** 节点标识，同一流程定义内唯一。 */
    private String id;

    /** 节点类型字面量：{@code START}/{@code APPROVAL}/{@code CONDITION}/{@code
     *  PARALLEL_SPLIT}/{@code PARALLEL_JOIN}/{@code CC}/{@code AUTO}/{@code END}。 */
    private String type;

    /** 节点展示名称，开始/结束/条件等节点可为空。 */
    private String name;

    /** 画布横坐标，仅 DSL v2 流程携带，v1 流程为空（前端需自行布局）。 */
    private Double x;

    /** 画布纵坐标，仅 DSL v2 流程携带，v1 流程为空（前端需自行布局）。 */
    private Double y;

    /** 节点状态：{@code COMPLETED}/{@code CURRENT}/{@code PENDING}，
     *  见 {@link cn.nihility.rbac.workflow.constant.ProcessGraphNodeStatus}。 */
    private String status;

    /** 该节点关联的历史审批轨迹条目，仅 {@code status=COMPLETED} 时非空；同一节点可能存在
     *  多条记录（如会签场景多人审批）。 */
    private List<ApprovalRecordVO> records;

    /** 该节点当前候选审批人/已认领处理人，仅 {@code status=CURRENT} 时非空，其余状态恒为空
     *  列表（add-approval-remark-and-process-flowchart change design.md Decision 7）。 */
    private List<CurrentApproverVO> currentApprovers;
}
