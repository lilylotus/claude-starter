package cn.nihility.rbac.app.sync.support;

import cn.nihility.rbac.common.exception.BusinessException;
import java.io.IOException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;

/**
 * 转换脚本语法校验工具类（app-sync-field-mapping change design.md Decision 6）：用 GraalJS
 * {@code Context.parse} 对脚本文本做静态语法解析（构建语法树），不调用任何执行/求值方法，
 * 因此不会执行脚本里的任何代码，只校验语法是否合法。无状态，风格对齐
 * {@link cn.nihility.rbac.app.support.AppCredentialGenerator}，不注册为 Spring bean。
 */
public final class TransformScriptValidator {

    /** GraalJS 语言标识。 */
    private static final String JS_LANGUAGE_ID = "js";

    /** 语法解析时使用的虚拟脚本来源名称，仅用于异常信息定位，不对应真实文件。 */
    private static final String SOURCE_NAME = "sync-transform.js";

    /**
     * 工具类不允许实例化。
     */
    private TransformScriptValidator() {
    }

    /**
     * 校验一段 JavaScript 源码的语法是否合法，只做静态解析，不执行脚本内容。
     *
     * @param script 待校验的脚本源码
     * @throws BusinessException 脚本语法不合法时抛出
     */
    public static void validateSyntax(String script) {
        try (Context context = Context.newBuilder(JS_LANGUAGE_ID).build()) {
            context.parse(Source.newBuilder(JS_LANGUAGE_ID, script, SOURCE_NAME).build());
        } catch (PolyglotException e) {
            throw new BusinessException("转换脚本语法错误：" + e.getMessage());
        } catch (IOException e) {
            throw new BusinessException("转换脚本语法错误：" + e.getMessage());
        }
    }
}
