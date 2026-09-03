package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * "并行分叉"节点，与配对的 {@link ParallelJoinNodeDslV2} 共同构成一个并行块，通过
 * {@code joinNodeId} 显式声明配对关系，禁止跨块连接（design.md Decision 3/7：嵌套并行只
 * 允许配对块，全部分支正常完成才继续，任一分支终止拒绝必须结束整个实例）。设计器的
 * "添加并行块"操作同时生成一对分叉/汇合节点，降低死锁配置风险。
 */
@Getter
@Setter
public class ParallelSplitNodeDslV2 extends ProcessNodeDslV2 {

    /** 配对的并行汇合节点 id，必填。 */
    private String joinNodeId;
}
