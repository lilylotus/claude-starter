package cn.nihility.rbac.sync.transform;

import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.dto.AppSyncFieldMappingRow;
import cn.nihility.rbac.app.sync.mapper.AppSyncFieldMappingMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

/**
 * 拉取结果字段映射转换执行器（app-sync-notify-pull-api change design.md Decision 9）：
 * {@code NO_TRANSFORM}/{@code FIXED_VALUE} 直接取值，{@code SCRIPT} 用 GraalVM
 * {@link Context} 沙箱执行（绑定方式与 {@code TransformScriptValidator} 现有语法校验的
 * 执行方言保持一致：脚本以 {@code value} 全局变量读入源字段值，脚本最后一个表达式的值
 * 作为结果），限制权限（不注入宿主对象、不给网络/文件系统访问能力）并加执行超时保护，
 * 超时判定该字段转换失败、跳过（design.md Risks）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FieldMappingTransformer {

    /** GraalJS 语言标识，与 {@code TransformScriptValidator} 保持一致。 */
    private static final String JS_LANGUAGE_ID = "js";

    /** 脚本执行时使用的虚拟脚本来源名称，仅用于异常信息定位，不对应真实文件。 */
    private static final String SOURCE_NAME = "sync-transform.js";

    /** 脚本执行超时（毫秒），超时判定转换失败，该字段跳过（design.md Risks）。 */
    private static final long SCRIPT_TIMEOUT_MILLIS = 200L;

    /** 脚本执行专用线程池，独立于 Disruptor 消费者线程/HTTP 请求线程，daemon 线程避免阻塞进程退出。 */
    private static final ExecutorService SCRIPT_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "sync-transform-script");
        thread.setDaemon(true);
        return thread;
    });

    /** 应用同步字段映射数据访问接口，复用 {@code app.sync} 模块既有 Mapper（跨模块只读查询）。 */
    private final AppSyncFieldMappingMapper appSyncFieldMappingMapper;

    /**
     * 按应用、数据域的字段映射配置转换一条变更记录的字段快照。未配置任何字段映射时原样
     * 返回全部字段（design.md Decision 9 兜底策略）。
     *
     * @param appRefId   应用 id
     * @param syncDomain 数据域
     * @param snapshot   变更记录的原始字段快照（key 为实体属性名）
     * @return 转换后的字段 Map（key 为应用字段编码），或原始快照（未配置字段映射时）
     */
    public Map<String, Object> transform(Long appRefId, String syncDomain, Map<String, Object> snapshot) {
        List<AppSyncFieldMappingRow> mappings = appSyncFieldMappingMapper.selectByAppRefIdAndDomain(appRefId,
                syncDomain);
        if (mappings.isEmpty()) {
            return snapshot;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (AppSyncFieldMappingRow mapping : mappings) {
            Object sourceValue = snapshot != null ? snapshot.get(mapping.getFieldCode()) : null;
            Object targetValue = switch (mapping.getTransformType()) {
                case TransformType.FIXED_VALUE -> mapping.getTransformValue();
                case TransformType.SCRIPT -> executeScript(mapping.getTransformValue(), sourceValue);
                default -> sourceValue;
            };
            result.put(mapping.getAppFieldCode(), targetValue);
        }
        return result;
    }

    /**
     * 在最小权限的 GraalVM 沙箱内执行一段转换脚本，超时或异常时返回 {@code null} 并跳过该
     * 字段，不向上抛出异常影响其余字段/记录的转换。
     *
     * @param script      脚本源码
     * @param sourceValue 源字段值，绑定为脚本内的 {@code value} 全局变量
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
     * 把 GraalVM {@link Value} 转换为普通 Java 对象（{@code String}/{@code Number}/
     * {@code Boolean} 等），供 JSON 序列化使用。
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
