package cn.nihility.rbac.userrole.service;

/**
 * 用户角色规则执行引擎业务逻辑接口："整体重建"语义计算规则当前命中用户集合，与既有执行
 * 结果（{@code tab_user_role_rule_grant}）做差集增删，并在收回角色时联动检查是否需要停用
 * 对应自动创建的管理员（add-user-role-batch-assignment change design.md Decision 3/7）。
 */
public interface UserRoleRuleExecutionService {

    /**
     * 重新执行一条规则：加载规则条件 → 用 {@code UserMatchConditionResolver} 计算当前命中
     * 用户集合 → 与该规则既有的执行结果做差集 → 新增命中的补发角色关联、不再命中的收回角色
     * 关联（若该用户该角色还有其他规则命中则不收回）→ 对确实收回的用户检查是否需要联动
     * 停用其自动创建的管理员 → 更新规则的 {@code lastExecTime}/{@code lastExecBy}。整个
     * 过程在一个数据库事务内完成。
     *
     * <p>{@code operator} 必须由调用方显式传入，不能依赖
     * {@code CurrentOperatorService#resolveUserId()}——事件驱动自动重算运行在 Disruptor
     * 消费者线程上，不处于任何 HTTP 请求上下文中，调用该方法会抛异常并被"单条失败不影响
     * 其余"的 try/catch 悄悄吞掉，导致自动重算表面接入成功、实际每次都失败（历史教训见
     * {@code close-sso-log-and-policy-gaps} change 归档记录）。
     *
     * @param ruleId   规则 id，不存在时抛出业务异常
     * @param operator 触发本次执行的操作人（人工保存规则时为操作人自己，事件自动触发时为
     *                 {@code event.getOperator()}），显式传入
     */
    void execute(Long ruleId, String operator);

    /**
     * 收回一条规则产生的全部角色关联（不重新计算命中集合，视同本次命中集合为空），供规则
     * 删除前的级联收回使用（design.md Decision 3a）；对确实收回的用户同样触发"角色收回
     * 联动停用自动创建的管理员"检查。不更新规则的 {@code lastExecTime}/{@code lastExecBy}
     * （规则即将被物理删除）。
     *
     * @param ruleId   规则 id，不存在时静默跳过
     * @param operator 触发本次收回的操作人，显式传入，理由同 {@link #execute}
     */
    void revokeAll(Long ruleId, String operator);
}
