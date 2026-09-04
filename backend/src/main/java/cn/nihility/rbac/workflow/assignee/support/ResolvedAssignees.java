package cn.nihility.rbac.workflow.assignee.support;

import java.util.Set;

/**
 * 节点审批人解析结果。{@code kind} 区分"直接解析出候选人"与"空审批人策略兜底后的三种结果"。
 *
 * @param userIds      用户 id 集合；{@code kind=AUTO_SKIP}/{@code REJECT} 时恒为空集合
 * @param kind         结果类型
 * @param resolveBasis 解析依据说明文本（如"角色 SECURITY_ADMIN 命中管理员 3 人"），供审计与
 *                      运维排查落库到 {@code tab_wf_approval_task_candidate.resolve_basis}
 *                      （production-approval-lifecycle change tasks.md 5.4）
 */
public record ResolvedAssignees(Set<Long> userIds, Kind kind, String resolveBasis) {

    /**
     * 结果类型。
     */
    public enum Kind {
        /** 正常解析出候选人（未触发空审批人策略）。 */
        DIRECT,
        /** 解析为空，按 {@code TO_WORKFLOW_ADMIN} 策略转配置的流程管理员角色。 */
        WORKFLOW_ADMIN,
        /** 解析为空，按 {@code AUTO_SKIP} 策略自动完成该节点。 */
        AUTO_SKIP,
        /** 解析为空，按 {@code REJECT} 策略终止流程。 */
        REJECT,

        /** 解析为空，按 {@code BLOCK}/{@code FALLBACK_ROLE}（兜底仍为空）策略阻塞待运维
         *  重分配（DSL v2 专用）。 */
        BLOCKED
    }

    /**
     * 构造"直接解析出候选人"结果。
     *
     * @param userIds      用户 id 集合，不应为空
     * @param resolveBasis 解析依据说明
     * @return 解析结果
     */
    public static ResolvedAssignees direct(Set<Long> userIds, String resolveBasis) {
        return new ResolvedAssignees(userIds, Kind.DIRECT, resolveBasis);
    }

    /**
     * 构造"转流程管理员"结果。
     *
     * @param userIds      流程管理员关联用户 id 集合，可能仍为空（配置错误的极端场景）
     * @param resolveBasis 解析依据说明
     * @return 解析结果
     */
    public static ResolvedAssignees toWorkflowAdmin(Set<Long> userIds, String resolveBasis) {
        return new ResolvedAssignees(userIds, Kind.WORKFLOW_ADMIN, resolveBasis);
    }

    /**
     * 构造"自动跳过"结果。
     *
     * @param resolveBasis 解析依据说明
     * @return 解析结果
     */
    public static ResolvedAssignees autoSkip(String resolveBasis) {
        return new ResolvedAssignees(Set.of(), Kind.AUTO_SKIP, resolveBasis);
    }

    /**
     * 构造"终止流程"结果。
     *
     * @param resolveBasis 解析依据说明
     * @return 解析结果
     */
    public static ResolvedAssignees reject(String resolveBasis) {
        return new ResolvedAssignees(Set.of(), Kind.REJECT, resolveBasis);
    }

    /**
     * 构造"阻塞待运维重分配"结果（DSL v2 专用）。
     *
     * @param resolveBasis 解析依据说明
     * @return 解析结果
     */
    public static ResolvedAssignees blocked(String resolveBasis) {
        return new ResolvedAssignees(Set.of(), Kind.BLOCKED, resolveBasis);
    }

    /**
     * 是否解析出了至少一个候选人（无论是否触发了空审批人策略）。
     *
     * @return 是否非空
     */
    public boolean hasAssignees() {
        return userIds != null && !userIds.isEmpty();
    }
}
