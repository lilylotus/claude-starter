package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 流程实例详情完整节点图中的一条只读连线（add-approval-remark-and-process-flowchart change
 * design.md Decision 4）。条件分支不在后端拼接可读文案，只透出结构化条件数据，翻译成"性别
 * 等于 女"这类可读文案的工作交给前端复用已加载的业务对象表单渲染元数据完成（design.md
 * Decision 9）。
 */
@Getter
@Setter
@Builder
@Schema(description = "流程实例详情连线")
public class ProcessGraphEdgeVO {

    /** 连线标识：DSL v2 使用快照中的原始 id，DSL v1（无原生连线 id）由服务端按顺序合成。 */
    private String id;

    /** 起始节点 id。 */
    private String source;

    /** 目标节点 id。 */
    private String target;

    /** 条件分支的结构化条件项列表，无条件（默认/兜底分支）为空列表，不是 {@code null}。 */
    private List<ConditionItemVO> conditions;

    /** 多条件项之间的逻辑连接符：{@code AND}/{@code OR}，{@code conditions.size() <= 1} 时
     *  为空、无实际意义。 */
    private String conditionLogic;
}
