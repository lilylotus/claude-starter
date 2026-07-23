package cn.nihility.rbac.operationlog.context;

import cn.nihility.rbac.operationlog.constant.OperationSource;

/**
 * 线程级"当前操作来源"标记工具类：批量导入引擎在调用组织/人员/任职/应用四个模块既有
 * 的创建/更新 service 方法前通过 {@link #mark(int)} 声明"当前操作来自 Excel 导入"，
 * {@code OperationLogRecorderImpl} 写库时通过 {@link #currentOrDefault()} 读取该标记
 * 决定 {@code operate_source} 取值，未被标记时视为界面手动操作
 * （fix-excel-import-followups design.md 决策 3）。调用方必须在 {@code finally} 块中
 * 调用 {@link #clear()}，避免同一线程后续处理其他请求时误继承标记。
 */
public final class OperationSourceContext {

    /** 线程级操作来源标记，未标记时为 {@code null}。 */
    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();

    /**
     * 工具类不允许实例化。
     */
    private OperationSourceContext() {
    }

    /**
     * 标记当前线程接下来的操作来源。
     *
     * @param source 操作来源码值，见 {@link OperationSource}
     */
    public static void mark(int source) {
        HOLDER.set(source);
    }

    /**
     * 读取当前线程的操作来源标记，未标记时返回 {@link OperationSource#MANUAL}。
     *
     * @return 操作来源码值
     */
    public static int currentOrDefault() {
        Integer source = HOLDER.get();
        return source != null ? source : OperationSource.MANUAL;
    }

    /**
     * 清除当前线程的操作来源标记。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
