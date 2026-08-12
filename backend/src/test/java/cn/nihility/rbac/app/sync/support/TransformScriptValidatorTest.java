package cn.nihility.rbac.app.sync.support;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * {@link TransformScriptValidator} 的单元测试，覆盖合法/非法 JavaScript 语法两种场景
 * （app-sync-field-mapping change tasks.md 10.1）。
 */
class TransformScriptValidatorTest {

    /**
     * 合法的 JavaScript 语法应通过校验，不抛出任何异常。
     */
    @Test
    void validateSyntax_shouldPass_whenScriptIsValid() {
        String script = "function transform(value) { return value ? value.toUpperCase() : ''; }";

        assertThatCode(() -> TransformScriptValidator.validateSyntax(script)).doesNotThrowAnyException();
    }

    /**
     * 明显的语法错误（缺少右括号）应被拒绝，抛出业务异常。
     */
    @Test
    void validateSyntax_shouldThrowBusinessException_whenScriptHasSyntaxError() {
        String invalidScript = "function transform(value) { return value.toUpperCase(; }";

        assertThatThrownBy(() -> TransformScriptValidator.validateSyntax(invalidScript))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("转换脚本语法错误");
    }
}
