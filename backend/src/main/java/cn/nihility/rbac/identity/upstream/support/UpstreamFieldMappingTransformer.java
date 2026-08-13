package cn.nihility.rbac.identity.upstream.support;

import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

/**
 * 上游字段映射转换执行器（design.md Decision 3）：按字段映射把一行原始数据
 * （key 为上游字段编码）转换为一行系统字段数据（key 为目标元数据字段的
 * {@code fieldCode}，等价于对应 CreateRequest/UpdateRequest 的 Java 属性名）。方向与
 * {@code cn.nihility.rbac.sync.transform.FieldMappingTransformer} 相反："上游原始值 →
 * 转换 → 系统字段值（入）"，{@code value} 绑定的语义是"待写入的上游原始值"（design.md
 * Decision 5）。契约与超时保护参照该类实现，但独立实现，不跨模块直接依赖（design.md
 * Decision 3：避免为共用几十行的转换求值逻辑引入不必要的模块耦合）。
 */
@Slf4j
@Component
public class UpstreamFieldMappingTransformer {

    /** GraalJS 语言标识，与 {@code TransformScriptValidator} 保持一致。 */
    private static final String JS_LANGUAGE_ID = "js";

    /** 脚本执行时使用的虚拟脚本来源名称，仅用于异常信息定位，不对应真实文件。 */
    private static final String SOURCE_NAME = "upstream-transform.js";

    /** 脚本执行超时（毫秒），超时判定转换失败，该字段跳过。 */
    private static final long SCRIPT_TIMEOUT_MILLIS = 200L;

    /** 脚本执行专用线程池，daemon 线程避免阻塞进程退出。 */
    private static final ExecutorService SCRIPT_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "upstream-transform-script");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 按字段映射转换一行原始数据。未配置任何字段映射的目标字段不会出现在结果 Map 中
     * （与 Excel 导入"只处理已配置字段"的语义一致，不像 {@code FieldMappingTransformer}
     * 那样在无映射时兜底返回原始快照——上游原始数据的 key 是上游字段编码，不是系统字段
     * 编码，没有字段映射就没有任何字段能被识别，兜底返回原始数据没有意义）。
     *
     * @param mappings 该数据源该数据域的字段映射列表
     * @param rawRow   一行原始数据，key 为上游字段编码
     * @return 转换后的一行数据，key 为目标系统字段编码
     */
    public Map<String, Object> transform(List<UpstreamFieldMappingRow> mappings, Map<String, Object> rawRow) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (UpstreamFieldMappingRow mapping : mappings) {
            Object sourceValue = rawRow != null ? rawRow.get(mapping.getUpstreamFieldCode()) : null;
            Object targetValue = switch (mapping.getTransformType()) {
                case TransformType.FIXED_VALUE -> mapping.getTransformValue();
                case TransformType.SCRIPT -> executeScript(mapping.getTransformValue(), sourceValue);
                default -> sourceValue;
            };
            result.put(mapping.getFieldCode(), targetValue);
        }
        return result;
    }

    /**
     * 在最小权限的 GraalVM 沙箱内执行一段转换脚本，超时或异常时返回 {@code null} 并跳过该
     * 字段，不向上抛出异常影响其余字段/记录的转换。
     *
     * @param script      脚本源码
     * @param sourceValue 上游原始值，绑定为脚本内的 {@code value} 全局变量
     * @return 脚本最后一个表达式的求值结果，超时/异常时为 {@code null}
     */
    private Object executeScript(String script, Object sourceValue) {
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
     * 把 GraalVM {@link Value} 转换为普通 Java 对象。
     *
     * @param value GraalVM 求值结果
     * @return 普通 Java 对象，{@code null}/{@code undefined} 时返回 {@code null}
     */
    private Object unwrap(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.as(Object.class);
    }
}
