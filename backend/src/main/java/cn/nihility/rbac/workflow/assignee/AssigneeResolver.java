package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.Set;

/**
 * 审批人解析器接口，按 {@link AssigneeType} 各自实现一种审批人来源的解析算法，返回值统一为
 * {@code tab_user.id} 集合，不返回角色编码等间接标识，调用方（{@code TaskListener}/
 * {@code ExecutionListener}）拿到的始终是可以直接设置为 Flowable {@code assignee}/
 * {@code candidateUsers} 的用户 id（workflow-approval-engine change design.md Decision 4）。
 * 实现类 SHALL NOT 抛出未捕获异常导致 Flowable 事务回滚，解析失败时返回空集合，交由调用方按
 * 节点配置的空审批人策略处理。
 */
public interface AssigneeResolver {

    /**
     * 声明本解析器支持的审批人来源类型。
     *
     * @return 审批人来源类型
     */
    AssigneeType supportedType();

    /**
     * 解析出实际审批人用户 id 集合。
     *
     * @param context 解析上下文
     * @return 用户 id 集合，无法解析出任何审批人时返回空集合，不返回 {@code null}
     */
    Set<Long> resolve(AssigneeResolveContext context);
}
