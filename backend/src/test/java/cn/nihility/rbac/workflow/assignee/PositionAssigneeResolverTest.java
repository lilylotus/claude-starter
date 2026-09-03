package cn.nihility.rbac.workflow.assignee;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import org.junit.jupiter.api.Test;

/**
 * {@link PositionAssigneeResolver} 单元测试：当前无岗位数据源，解析结果恒为空
 * （workflow-approval-engine change spec.md "指定岗位类型当前恒返回空" Scenario）。
 */
class PositionAssigneeResolverTest {

    private final PositionAssigneeResolver resolver = new PositionAssigneeResolver();

    /** 解析结果恒为空集合，不抛出异常。 */
    @Test
    void resolve_shouldAlwaysReturnEmpty() {
        AssigneeResolveContext context = new AssigneeResolveContext(1L, "node1", null, 100L, 10L);

        assertThat(resolver.resolve(context)).isEmpty();
    }

    /** 支持的类型应为 POSITION。 */
    @Test
    void supportedType_shouldBePosition() {
        assertThat(resolver.supportedType()).isEqualTo(AssigneeType.POSITION);
    }
}
