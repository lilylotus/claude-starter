package cn.nihility.rbac.workflow.dslv2.compiler;

import java.util.Set;

/**
 * "自动任务"节点 {@code actionCode} 白名单注册表：编译期只校验 {@code actionCode} 是否已注册，
 * 不在编译阶段执行；具体执行逻辑属于运行时 Outbox 消费者范畴（production-approval-lifecycle
 * change 第 7 节，本轮范围外）。首轮不内置任何动作，注册表为空，任何 {@code AUTO} 节点在当前
 * 阶段都会因 {@code actionCode} 不在白名单而拒绝发布，直到后续批次注册真正可幂等执行的动作
 * （design.md Decision 10"首轮仅内置可幂等动作，外部通用 HTTP 节点不开放"）。
 */
public final class AutoActionRegistry {

    /** 已注册的 actionCode 白名单，当前为空集合。 */
    private static final Set<String> REGISTERED_ACTION_CODES = Set.of();

    /** 工具类不允许实例化。 */
    private AutoActionRegistry() {
    }

    /**
     * 判断 {@code actionCode} 是否已在白名单注册表中。
     *
     * @param actionCode 动作编码
     * @return 是否已注册
     */
    public static boolean isRegistered(String actionCode) {
        return REGISTERED_ACTION_CODES.contains(actionCode);
    }
}
