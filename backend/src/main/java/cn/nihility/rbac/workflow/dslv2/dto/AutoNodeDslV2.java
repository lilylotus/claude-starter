package cn.nihility.rbac.workflow.dslv2.dto;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * "自动任务"节点，只允许调用预注册的白名单 {@code actionCode}，不开放任意 URL/脚本节点
 * （design.md Decision 3/10：首轮仅内置可幂等动作，外部通用 HTTP 节点不开放）。编译期只
 * 校验 {@code actionCode} 是否在白名单注册表中存在及参数是否满足其 schema，具体执行逻辑由
 * 运行时的 Outbox 消费者完成，不在编译阶段调用。
 */
@Getter
@Setter
public class AutoNodeDslV2 extends ProcessNodeDslV2 {

    /** 预注册的动作编码，编译期须能在白名单注册表中查到。 */
    private String actionCode;

    /** 动作参数，按 {@code actionCode} 对应的参数 schema 校验。 */
    private Map<String, Object> params;
}
