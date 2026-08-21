package cn.nihility.rbac.appaccess.policy.service;

import cn.nihility.rbac.appaccess.policy.dto.PolicyVO;

/**
 * 策略规则手动执行业务逻辑接口（spec.md"策略规则的手动执行"需求）。
 */
public interface PolicyExecutionService {

    /**
     * 执行指定策略（管理员在"策略规则"页面点击"执行"按钮的路径）：按组织范围/用户属性条件
     * 匹配命中用户，与目标应用集合做笛卡尔积，整体重建该策略产生的策略授权记录（先删后插），
     * SHALL NOT 影响任何人工例外记录，同时记录本次执行时间与执行人。执行人从
     * {@code CurrentOperatorService} 解析当前登录用户，要求调用方处于已认证的 HTTP 请求
     * 上下文中，否则按该服务的约定抛出 {@link IllegalStateException}。
     *
     * @param policyId 策略 id
     * @return 执行后的策略规则详情（含最新 {@code lastExecTime}/{@code lastExecBy}）
     */
    PolicyVO execute(Long policyId);

    /**
     * 执行指定策略，执行人由调用方显式传入而不是从 {@code CurrentOperatorService} 解析——
     * 供组织/用户/任职变更触发的自动重新执行场景使用（见
     * {@code cn.nihility.rbac.sync.event.support.DomainChangeEventProcessor}）：该场景运行在
     * Disruptor 消费者线程上，不处于任何 HTTP 请求上下文中，{@code CurrentOperatorService}
     * 无法解析当前登录用户，必须改用触发本次自动重算的原始领域变更事件所携带的操作人
     * （{@code DomainChangeEvent#getOperator()}）作为本次执行的执行人
     * （close-sso-log-and-policy-gaps change design.md Decision 6，修复自动重新执行因无法
     * 解析操作人而每次都抛异常、被外层 catch 悄悄吞掉的问题）。{@code operator} 允许为
     * {@code null}（对应 {@code tab_app_access_policy_grant}/{@code tab_app_access_policy}
     * 的 {@code create_by}/{@code update_by}/{@code last_exec_by} 均为可空列），不会像
     * {@link #execute(Long)} 那样在解析不到操作人时抛异常。
     *
     * @param policyId 策略 id
     * @param operator 执行人（用户 id 文本），允许为 {@code null}
     * @return 执行后的策略规则详情（含最新 {@code lastExecTime}/{@code lastExecBy}）
     */
    PolicyVO execute(Long policyId, String operator);
}
