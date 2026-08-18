package cn.nihility.rbac.common.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * 可复用的转换脚本执行工具类（add-sso-userinfo-field-mapping change design.md
 * Decision 5）：从 {@code FieldMappingTransformer} 抽出，在最小权限的 GraalVM 沙箱内执行
 * 一段 JavaScript 转换脚本（脚本以 {@code value} 全局变量读入源字段值，脚本最后一个表达式
 * 的值作为结果），限制权限（不注入宿主对象、不给网络/文件系统访问能力）并加执行超时保护，
 * 供后端各处需要"转换脚本"能力的字段映射功能共同复用（当前为应用同步字段映射
 * {@code FieldMappingTransformer} 与用户信息字段映射 {@code SsoUserinfoAttributesResolver}），
 * 避免各自重复维护脚本沙箱执行细节（超时、权限限制等安全相关逻辑）。无状态，风格对齐
 * {@code TransformScriptValidator}，不注册为 Spring bean。
 */
@Slf4j
public final class ScriptTransformExecutor {

    /** GraalJS 语言标识，与 {@code TransformScriptValidator} 保持一致。 */
    private static final String JS_LANGUAGE_ID = "js";

    /** 脚本执行时使用的虚拟脚本来源名称，仅用于异常信息定位，不对应真实文件。 */
    private static final String SOURCE_NAME = "transform.js";

    /** 脚本执行超时（毫秒），超时判定转换失败，该字段跳过。 */
    private static final long SCRIPT_TIMEOUT_MILLIS = 200L;

    /** 脚本执行专用线程池，独立于业务线程，daemon 线程避免阻塞进程退出。 */
    private static final ExecutorService SCRIPT_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "script-transform-executor");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 工具类不允许实例化。
     */
    private ScriptTransformExecutor() {
    }

    /**
     * 在最小权限的 GraalVM 沙箱内执行一段转换脚本，超时或异常时返回 {@code null} 并记录 WARN
     * 日志，不向上抛出异常影响调用方后续处理。
     *
     * @param script      脚本源码
     * @param sourceValue 源字段值，绑定为脚本内的 {@code value} 全局变量
     * @return 脚本最后一个表达式的求值结果，超时/异常时为 {@code null}
     */
    public static Object execute(String script, Object sourceValue) {
        AtomicReference<Context> contextRef = new AtomicReference<>();
        try {
            return SCRIPT_EXECUTOR.submit(() -> {
                try (Context context = Context.newBuilder(JS_LANGUAGE_ID).allowAllAccess(false).build()) {
                    contextRef.set(context);
                    context.getBindings(JS_LANGUAGE_ID).putMember("value", sourceValue);
                    Value result = context.eval(Source.newBuilder(JS_LANGUAGE_ID, script, SOURCE_NAME).build());
                    return unwrap(result);
                } finally {
                    contextRef.set(null);
                }
            }).get(SCRIPT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Context context = contextRef.get();
            if (context != null) {
                try {
                    context.close(true);
                } catch (Exception closeException) {
                    log.warn("强制关闭超时的转换脚本执行上下文失败", closeException);
                }
            }
            log.warn("转换脚本执行超时（{}ms），已跳过该字段", SCRIPT_TIMEOUT_MILLIS);
            return null;
        } catch (Exception e) {
            log.warn("转换脚本执行失败，已跳过该字段：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 把 GraalVM {@link Value} 转换为普通 Java 对象（{@code String}/{@code Number}/
     * {@code Boolean} 等），供 JSON 序列化使用。
     *
     * @param value GraalVM 求值结果
     * @return 普通 Java 对象，{@code null}/{@code undefined} 时返回 {@code null}
     */
    private static Object unwrap(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.as(Object.class);
    }
}
