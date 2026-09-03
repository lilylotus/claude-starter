package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "结束"节点，v2 要求明确结果（design.md Decision 3）：{@code APPROVED} 编译为普通结束
 * 事件（等待所有正常 token 完成才算流程完成）；{@code REJECTED} 编译为根流程范围的终止
 * 结束事件（立即取消其余全部开放分支，不把单个分支结束误当流程完成）。
 */
@Getter
@Setter
public class EndNodeDslV2 extends ProcessNodeDslV2 {

    /** 结束结果：{@code APPROVED}/{@code REJECTED}，必填，不接受空值。 */
    private String outcome;
}
