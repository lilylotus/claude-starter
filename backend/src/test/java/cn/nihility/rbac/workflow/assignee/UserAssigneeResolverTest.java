package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import org.junit.jupiter.api.Test;

/**
 * {@link UserAssigneeResolver} 单元测试。
 */
class UserAssigneeResolverTest {

    private final UserAssigneeResolver resolver = new UserAssigneeResolver();

    /** 单个用户 id 应正确解析。 */
    @Test
    void resolve_shouldParseSingleUserId() {
        assertThat(resolver.resolve(context("100"))).containsExactly(100L);
    }

    /** 多个逗号分隔的用户 id 均应解析。 */
    @Test
    void resolve_shouldParseMultipleUserIds() {
        assertThat(resolver.resolve(context("100,200,300"))).containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    /** 非法值应被忽略，不抛出异常。 */
    @Test
    void resolve_shouldIgnoreInvalidValues() {
        assertThat(resolver.resolve(context("100,abc,200"))).containsExactlyInAnyOrder(100L, 200L);
    }

    /** 空值应返回空集合。 */
    @Test
    void resolve_shouldReturnEmptyWhenBlank() {
        assertThat(resolver.resolve(context(null))).isEmpty();
    }

    /** 支持的类型应为 USER。 */
    @Test
    void supportedType_shouldBeUser() {
        assertThat(resolver.supportedType()).isEqualTo(AssigneeType.USER);
    }

    /** 构造解析上下文。 */
    private AssigneeResolveContext context(String assigneeValue) {
        return new AssigneeResolveContext(1L, "node1", assigneeValue, 100L, 10L, null, null);
    }
}
