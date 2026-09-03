package cn.nihility.rbac.workflow.dslv2.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Workflow JSON DSL v2 顶层结构（design.md Decision 3）。{@code schemaVersion} 恒为
 * {@code 2}，与流程版本号分离；v1 老定义继续使用原 {@code ProcessModelDsl}/
 * {@code WorkflowModelCompiler}，不原地升级——编辑旧版本必须先复制成 v2 草稿再重新校验。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessModelDslV2 {

    /** DSL schemaVersion，固定为 2。 */
    private Integer schemaVersion;

    /** 业务侧流程编码。 */
    private String processCode;

    /** 流程名称。 */
    private String processName;

    /** 绑定的表单版本 id，关联 {@code tab_wf_form_version.id}；条件字段与节点字段权限均须
     *  落在该表单版本的字段白名单内。 */
    private String formVersionId;

    /** 流程级策略配置。 */
    private PolicyConfigDsl policies;

    /** 节点列表：开始/审批/条件/并行分叉/并行汇合/抄送/自动任务/结束。 */
    private List<ProcessNodeDslV2> nodes;

    /** 连线列表。 */
    private List<EdgeDslV2> edges;

    /** 画布视口状态，仅前端往返使用。 */
    private LayoutDsl layout;
}
