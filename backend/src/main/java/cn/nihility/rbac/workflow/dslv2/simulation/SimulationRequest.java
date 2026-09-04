package cn.nihility.rbac.workflow.dslv2.simulation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 快速预演请求体（production-approval-lifecycle change design.md 第 4 节"试运行分两层"第
 * 一层，tasks.md 4.2）。仅做静态路径/人员解析解释，不接触真实引擎实例，不产生任何真实
 * 任务/流程数据。
 */
@Getter
@Setter
public class SimulationRequest {

    /** 按指定已发布版本的 DSL 快照预演；为空则使用流程模型当前草稿（{@code model_json}）。
     *  两者均须是 {@code schemaVersion=2} 的 DSL v2，快速预演不支持 v1 定义。 */
    @Schema(description = "指定已发布版本的流程定义 id，为空则使用当前草稿")
    private Long definitionId;

    /** 模拟表单字段值，供条件节点求值分支使用；未涉及的字段按 {@code null} 处理。 */
    @Schema(description = "模拟表单字段值，key 为表单字段标识")
    private Map<String, Object> formValues;

    /** 模拟申请人用户 id，供"申请人本人"/自审排除/上一节点处理人等解析使用；可为空。 */
    @Schema(description = "模拟申请人用户 id")
    private Long applicantId;

    /** 模拟申请人所属组织 id，供组织负责人类审批人来源解析使用；可为空。 */
    @Schema(description = "模拟申请人所属组织 id")
    private Long applicantOrgId;
}
