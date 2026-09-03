package cn.nihility.rbac.workflow.dslv2.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * "抄送"节点，编译为写抄送记录与通知 Outbox 的内部 ServiceTask，非 {@code userTask}，
 * 不阻塞流程（design.md Decision 3/10）。
 */
@Getter
@Setter
public class CcNodeDslV2 extends ProcessNodeDslV2 {

    /** 抄送接收人来源类型：{@code USER}/{@code ROLE}/{@code INITIATOR}/
     *  {@code PREVIOUS_APPROVER}，与审批节点的审批人来源类型枚举同源，复用现有解析器。 */
    private String recipientType;

    /** 抄送接收人来源取值，按 {@code recipientType} 解释。 */
    private String recipientValue;

    /** 抄送范围内可见的字段标识列表，为空表示仅通知不携带详情。 */
    private List<String> visibleFields;
}
