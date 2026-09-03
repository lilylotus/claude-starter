package cn.nihility.rbac.workflow.engine.flowable.support;

import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.Context;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 应用上下文持有者，供 Flowable 通过 {@code class} 属性反射实例化的
 * {@code TaskListener}/{@code ExecutionListener} 获取 Spring 管理的 Bean。BPMN 里
 * {@code flowable:taskListener class="..."}/{@code flowable:executionListener class="..."}
 * 引用的类由 Flowable 通过无参构造器反射创建，不经过 Spring 容器，因此无法使用构造器注入；
 * 这是 Flowable+Spring 集成场景下常见的解决方式（workflow-approval-engine change
 * design.md Decision 4 的实现细节）。
 * <p>
 * {@link #getBean(Class)} 优先从"当前正在执行的 Flowable 命令所绑定的
 * {@code ProcessEngineConfiguration}"（{@link Context#getProcessEngineConfiguration()}）
 * 取其 {@link SpringProcessEngineConfiguration#getApplicationContext()}，而不是直接读
 * {@link #applicationContext} 静态字段：本类作为 {@code @Component} 在每个 Spring 容器
 * 启动/刷新时都会被重新实例化并覆盖该静态字段一次，在单个 JVM 内先后创建了多个
 * {@code ApplicationContext} 的场景下（典型地——测试套件里既有 Spring 测试上下文缓存命中
 * 复用旧容器、又穿插着为不同 {@code @MockBean}/属性组合新建容器的用例），该静态字段最终会
 * 停留在"整个 JVM 生命周期内最后一次被刷新的容器"，而不一定是"当前正在执行的这个流程实例
 * 所属的容器"，导致监听器内经由该字段取到的 Mapper/Service 绑定到错误容器自己的数据源连接，
 * 看不见同一个 Spring 事务里刚插入但尚未提交的数据。而 Flowable 的
 * {@code ProcessEngineConfiguration} 本身是随每个 Spring 容器各自创建一份、和该容器一一
 * 对应的 Bean，通过命令上下文取到的必然是"当前正在执行的这次调用所属"的那个容器，因此没有
 * 这个问题。仅当命令上下文不可用时（理论上不会发生在 {@code TaskListener}/
 * {@code ExecutionListener} 回调内，保留作为兜底）才退回读取静态字段。
 */
@Component
public class WorkflowSpringContext implements ApplicationContextAware {

    /** Spring 应用上下文兜底缓存，命令上下文不可用时使用。 */
    private static ApplicationContext applicationContext;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        WorkflowSpringContext.applicationContext = context;
    }

    /**
     * 按类型获取 Spring 管理的 Bean，优先绑定到当前正在执行的 Flowable 命令所属的容器。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例
     * @throws IllegalStateException 应用上下文尚未初始化完成时抛出
     */
    public static <T> T getBean(Class<T> type) {
        ApplicationContext context = resolveApplicationContext();
        if (context == null) {
            throw new IllegalStateException("Spring ApplicationContext 尚未初始化，无法获取 Bean：" + type.getName());
        }
        return context.getBean(type);
    }

    /**
     * 优先取当前 Flowable 命令上下文绑定的容器，取不到时退回静态缓存的容器。
     */
    private static ApplicationContext resolveApplicationContext() {
        ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
        if (configuration instanceof SpringProcessEngineConfiguration springConfiguration) {
            return springConfiguration.getApplicationContext();
        }
        return applicationContext;
    }
}
