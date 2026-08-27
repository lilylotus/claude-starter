package cn.nihility.rbac.plugin.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 插件覆盖声明注解：插件 jar 中的类显式标注本注解后，其 Bean 定义会以 {@link #target()}
 * 在主程序中已注册的 bean name 重新注册，从而替换主程序原有实现（plugin-bean-override
 * capability spec "显式声明覆盖目标"）。未标注本注解的插件类一律作为独立 Bean 处理，
 * 不做任何基于类名/包名的隐式覆盖，避免误覆盖主程序已有实现。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginOverride {

    /**
     * 声明要覆盖的主程序类（通常是接口或已被主程序 {@code @Service}/{@code @Component}/
     * {@code @Controller} 等注解标注的实现类）。{@code PluginBeanDefinitionRegistrar}
     * 按该类型在当前 {@code BeanDefinitionRegistry} 中定位主程序已注册的 bean name，
     * 定位不到时该插件类的 Bean 定义注册失败（隔离，不影响其他插件/类）。
     *
     * @return 覆盖目标类
     */
    Class<?> target();
}
