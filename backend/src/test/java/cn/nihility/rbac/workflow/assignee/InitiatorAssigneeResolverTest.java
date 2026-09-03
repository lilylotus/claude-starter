package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link InitiatorAssigneeResolver} 单元测试。
 */
class InitiatorAssigneeResolverTest {

    private final InitiatorAssigneeResolver resolver = new InitiatorAssigneeResolver();

    /** 应返回流程实例快照记录的发起人。 */
    @Test
    void resolve_shouldReturnApplicant() {
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", null, 100L, 10L);

        assertThat(resolver.resolve(context)).containsExactly(100L);
    }

    /** 发起人上下文缺失时返回空集合，不抛出异常。 */
    @Test
    void resolve_shouldReturnEmptyWhenApplicantMissing() {
        AssigneeResolveContext context = new AssigneeResolveContext(null, "node1", null, null, null);

        assertThat(resolver.resolve(context)).isEmpty();
    }
}
