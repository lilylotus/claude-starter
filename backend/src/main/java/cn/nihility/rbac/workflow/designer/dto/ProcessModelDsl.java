package cn.nihility.rbac.workflow.designer.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Workflow JSON DSL 顶层结构，对应流程设计器画布的完整保存内容
 * （{@code tab_wf_process_model.model_json}/{@code tab_wf_process_definition.
 * model_json_snapshot}，workflow-approval-engine change design.md Decision 9）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessModelDsl {

    /** 业务侧流程编码，如 {@code MASTER_DATA_APPROVAL}。 */
    private String processCode;

    /** 流程名称。 */
    private String processName;

    /** 节点列表，仅包含"开始/审批/条件/结束"四种业务语言节点。 */
    private List<ProcessNodeDsl> nodes;

    /** 连线列表。 */
    private List<EdgeDsl> edges;
}
