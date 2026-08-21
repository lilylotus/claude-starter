package cn.nihility.rbac.appaccess.support;

/**
 * "考虑请求上下文"的最终生效权限判定结果（policy-condition-exclusive-priority change
 * design.md Decision）：{@code authorized} 表示是否放行；{@code deniedByPolicyId} 仅在
 * "存在候选策略、排在最前的一条身份命中但请求控制条件不满足"这一具体分支下非空，供
 * {@code sso-protocol-access-log} 能力记录拒绝来源的策略 id。候选为空、或由 {@code DENY}/
 * {@code GRANT} 人工例外决定的判定分支，{@code deniedByPolicyId} 均为 {@code null}。
 *
 * @param authorized        是否放行
 * @param deniedByPolicyId  拒绝来源的策略 id，非"排在最前的候选策略请求控制不满足"这一分支
 *                          时为 {@code null}
 */
public record AppAccessAuthorizationDecision(boolean authorized, Long deniedByPolicyId) {

    /**
     * 构造一个"放行"的判定结果，{@code deniedByPolicyId} 恒为 {@code null}。名称避免与
     * record 自动生成的 {@code authorized()} 访问器同名（返回类型不同会导致编译错误）。
     *
     * @return 放行的判定结果
     */
    public static AppAccessAuthorizationDecision allow() {
        return new AppAccessAuthorizationDecision(true, null);
    }

    /**
     * 构造一个"不可访问、且并非由具体策略造成"的判定结果（候选为空、或由人工例外决定）。
     *
     * @return 不可访问的判定结果
     */
    public static AppAccessAuthorizationDecision denyWithoutPolicy() {
        return new AppAccessAuthorizationDecision(false, null);
    }

    /**
     * 构造一个"被指定策略拒绝"的判定结果。
     *
     * @param policyId 拒绝来源的策略 id
     * @return 不可访问的判定结果
     */
    public static AppAccessAuthorizationDecision denyByPolicy(Long policyId) {
        return new AppAccessAuthorizationDecision(false, policyId);
    }
}
