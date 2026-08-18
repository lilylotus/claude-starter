package cn.nihility.rbac.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ScriptTransformExecutor} 的单元测试（add-sso-userinfo-field-mapping change
 * tasks.md 9.1），覆盖脚本正常执行、执行异常两种场景（spec.md "backend-common-utilities"
 * 能力对应 Scenario）。超时场景依赖真实的 200ms 阻塞，不在单元测试中覆盖以避免测试整体
 * 变慢/不稳定，行为由本类直接迁移自 {@code FieldMappingTransformer} 既有实现保证不变。
 */
class ScriptTransformExecutorTest {

    /**
     * 合法脚本正常执行，应返回脚本最后一个表达式的求值结果。
     */
    @Test
    void execute_shouldReturnResult_whenScriptValid() {
        Object result = ScriptTransformExecutor.execute("value ? value.toUpperCase() : ''", "abc");

        assertThat(result).isEqualTo("ABC");
    }

    /**
     * 脚本执行期间抛出异常（访问未定义变量）时应返回 {@code null}，不向上抛出异常。
     */
    @Test
    void execute_shouldReturnNull_whenScriptThrows() {
        Object result = ScriptTransformExecutor.execute("undefinedVariable.toUpperCase()", "abc");

        assertThat(result).isNull();
    }

    /**
     * {@code value} 全局变量应正确绑定为传入的源字段值。
     */
    @Test
    void execute_shouldBindValueAsGlobalVariable() {
        Object result = ScriptTransformExecutor.execute("value + 1", 41);

        assertThat(result).isEqualTo(42);
    }
}
