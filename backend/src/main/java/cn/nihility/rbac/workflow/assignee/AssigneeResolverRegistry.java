package cn.nihility.rbac.workflow.assignee;

import cn.nihility.rbac.workflow.constant.AssigneeType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批人解析器注册表：按 {@link AssigneeType} 汇总全部 {@link AssigneeResolver} 实现，供
 * {@code TaskListener}/{@code ExecutionListener} 统一调用，调用方不需要感知具体有哪些实现类。
 */
@Slf4j
@Component
public class AssigneeResolverRegistry {

    /** 按类型索引的解析器。 */
    private final Map<AssigneeType, AssigneeResolver> resolvers;

    /**
     * 收集全部 {@link AssigneeResolver} 实现并按类型建立索引。
     *
     * @param resolverList 全部已注册为 Spring bean 的解析器实现
     */
    public AssigneeResolverRegistry(List<AssigneeResolver> resolverList) {
        this.resolvers = new EnumMap<>(AssigneeType.class);
        resolverList.forEach(resolver -> resolvers.put(resolver.supportedType(), resolver));
    }

    /**
     * 按类型解析审批人。
     *
     * @param type    审批人来源类型
     * @param context 解析上下文
     * @return 用户 id 集合，类型未注册对应解析器或解析失败时返回空集合，不抛出异常
     */
    public Set<Long> resolve(AssigneeType type, AssigneeResolveContext context) {
        AssigneeResolver resolver = resolvers.get(type);
        if (resolver == null) {
            log.error("未找到 assigneeType={} 对应的 AssigneeResolver 实现，按空审批人处理", type);
            return Set.of();
        }
        try {
            Set<Long> resolved = resolver.resolve(context);
            return resolved == null ? Set.of() : resolved;
        } catch (RuntimeException ex) {
            log.error("审批人解析异常，assigneeType={}，nodeId={}，按空审批人处理", type, context.nodeId(), ex);
            return Set.of();
        }
    }
}
