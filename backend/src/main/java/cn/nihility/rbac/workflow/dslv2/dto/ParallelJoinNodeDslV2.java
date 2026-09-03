package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "并行汇合"节点，与配对的 {@link ParallelSplitNodeDslV2} 共同构成一个并行块
 * （design.md Decision 3/7）。
 */
@Getter
@Setter
public class ParallelJoinNodeDslV2 extends ProcessNodeDslV2 {

    /** 配对的并行分叉节点 id，必填，须与对应分叉节点的 {@code joinNodeId} 互相指向一致。 */
    private String splitNodeId;
}
