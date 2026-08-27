package cn.nihility.rbac.plugin.support;

import cn.nihility.rbac.plugin.annotation.PluginOverride;
import cn.nihility.rbac.plugin.config.PluginProperties;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

/**
 * 插件 Bean 定义注册处理器（plugin-jar-upgrade change design.md Decision 1）：在主程序自身
 * {@code @ComponentScan} 产生的全部 Bean 定义注册完成之后、任何 Bean 开始实例化之前，扫描
 * {@code plugins/} 目录下的插件 jar，将其中的 Controller/Service/Component/Configuration
 * 类解析为 Bean 定义注册进主 {@link BeanDefinitionRegistry}。
 * <p>
 * <b>关于执行时机</b>：本类实现 {@link PriorityOrdered} 且 {@link #getOrder()} 返回
 * {@link Ordered#LOWEST_PRECEDENCE}（最低优先级）。design.md 原描述是"返回一个数值大于
 * {@code ConfigurationClassPostProcessor} 的值"，但 {@code ConfigurationClassPostProcessor}
 * 自身的 {@code getOrder()} 默认就返回 {@code Ordered.LOWEST_PRECEDENCE}（已是最大值，不存在
 * 比它更大的候选值）。实际验证 Spring 5/6（本项目基于 Spring Boot 3.5）的
 * {@code PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors} 源码可知，真正
 * 保证执行顺序的不是数值比较，而是"发现时机"：{@code ConfigurationClassPostProcessor}
 * 在容器创建时就以固定 bean name 预注册，是第一轮 {@code PriorityOrdered} 扫描中唯一能找到的
 * 候选者，它的 {@code postProcessBeanDefinitionRegistry} 执行的正是主程序
 * {@code @ComponentScan}/{@code @Configuration} 的完整处理过程——本类作为
 * {@code cn.nihility.rbac} 包内的 {@code @Component}，只有在那一轮扫描完成后才会作为 Bean
 * 定义出现，因此必然只能在后续批次（Ordered 批次，因为 {@link PriorityOrdered} 是 {@link
 * Ordered} 的子接口，同样会被该批次的类型匹配检查命中）中被发现和调用，天然晚于主程序自身
 * 组件扫描，且仍在同一次 {@code refresh()} 的 {@code invokeBeanFactoryPostProcessors}
 * 阶段（早于任何 Bean 实例化）。这是对 design.md "决策 1" 时机约束的等价工程实现，未改变
 * 该决策的核心架构。
 */
@Slf4j
@Component
public class PluginBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, PriorityOrdered, EnvironmentAware {

    /** 内存态插件登记表在容器中的 bean name，供 {@code plugin.service} 层查询注入。 */
    public static final String PLUGIN_REGISTRY_BEAN_NAME = "pluginRegistry";

    /** 主程序自身 Bean 在冲突提示中的来源标识。 */
    private static final String MAIN_PROGRAM_OWNER = "主程序";

    /** 当前 {@link Environment}，用于早于常规 Bean 生命周期读取 {@code rbac.plugin.*} 配置。 */
    private Environment environment;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!(registry instanceof ConfigurableListableBeanFactory beanFactory)) {
            log.warn("当前 BeanDefinitionRegistry [{}] 不是 ConfigurableListableBeanFactory 实现，跳过插件加载", registry);
            return;
        }

        PluginRegistry pluginRegistry = new PluginRegistry();
        beanFactory.registerSingleton(PLUGIN_REGISTRY_BEAN_NAME, pluginRegistry);

        PluginProperties properties = bindProperties();
        Path directory = Path.of(properties.getDirectory());
        List<Path> jarFiles = PluginJarScanner.scan(directory);
        if (jarFiles.isEmpty()) {
            return;
        }

        ClassLoader appClassLoader = beanFactory.getBeanClassLoader();
        List<Candidate> candidates = new ArrayList<>(jarFiles.size());
        for (Path jarPath : jarFiles) {
            candidates.add(readMetadata(jarPath));
        }
        // design.md Decision 4：按 priority 升序处理（数值越大越晚处理，同优先级退回文件名字典序）。
        candidates.sort(Comparator.<Candidate>comparingInt(candidate -> candidate.metadata().priority())
                .thenComparing(candidate -> candidate.jarPath().getFileName().toString()));

        ControllerPathRegistry pathRegistry = new ControllerPathRegistry();
        seedMainProgramPaths(registry, pathRegistry, appClassLoader);

        OverrideDenyListChecker denyListChecker =
                new OverrideDenyListChecker(new LinkedHashSet<>(properties.getOverride().getDenyList()));
        Map<String, String> overrideOwners = new HashMap<>();

        for (Candidate candidate : candidates) {
            PluginInfo pluginInfo = processPlugin(candidate, beanFactory, appClassLoader, pathRegistry, denyListChecker,
                    overrideOwners, pluginRegistry);
            pluginRegistry.register(pluginInfo);
        }
    }

    /**
     * {@inheritDoc} 全部工作已在 {@link #postProcessBeanDefinitionRegistry} 阶段完成，本方法
     * 无需额外处理，仅满足接口契约。
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    /**
     * 预登记主程序自身已注册的 Controller 请求路径，作为插件路径冲突比对的基线（design.md
     * Decision 2 "已知路径集合（主程序 + 已处理插件）"）。只处理拥有直接类名的 Bean 定义
     * （常规 {@code @Component} 扫描产生的定义均满足），避免触碰 {@code @Bean} 工厂方法定义
     * 可能引发的提前实例化风险；用 {@code Class.forName(name, false, loader)} 仅加载类元数据、
     * 不触发静态初始化，不会破坏"本处理器运行期间不实例化任何 Bean"的约束。
     *
     * @param registry     当前 Bean 定义注册表
     * @param pathRegistry 路径登记表
     * @param classLoader  主程序类加载器
     */
    private void seedMainProgramPaths(BeanDefinitionRegistry registry, ControllerPathRegistry pathRegistry,
            ClassLoader classLoader) {
        for (String beanName : registry.getBeanDefinitionNames()) {
            String className = registry.getBeanDefinition(beanName).getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> clazz;
            try {
                clazz = Class.forName(className, false, classLoader);
            } catch (Throwable ex) {
                continue;
            }
            if (!AnnotatedElementUtils.hasAnnotation(clazz, Controller.class)) {
                continue;
            }
            Set<String> paths = RequestMappingPathResolver.resolvePaths(clazz);
            if (!paths.isEmpty()) {
                pathRegistry.tryRegister(MAIN_PROGRAM_OWNER, paths);
            }
        }
    }

    /**
     * 读取单个插件 jar 的元信息；元信息读取失败（通常意味着 jar 本身已损坏）不在此处立即判定
     * 插件失败，而是用缺省元信息继续，实际的加载结果留给 {@link #processPlugin} 统一判定并
     * 记录到 {@link PluginInfo}，避免在元信息读取与正式处理两处重复维护失败判定逻辑。
     *
     * @param jarPath 插件 jar 路径
     * @return 插件候选信息
     */
    private Candidate readMetadata(Path jarPath) {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return new Candidate(jarPath, PluginMetadataReader.read(jarFile, jarPath));
        } catch (Exception ex) {
            log.warn("插件 [{}] 元信息读取失败（jar 可能已损坏），先使用缺省元信息排序，实际加载结果由后续处理阶段判定：{}",
                    jarPath.getFileName(), ex.toString());
            String fallbackName = jarPath.getFileName().toString();
            return new Candidate(jarPath,
                    new PluginMetadata(fallbackName, PluginMetadata.UNKNOWN_VERSION, PluginMetadata.DEFAULT_PRIORITY));
        }
    }

    /**
     * 处理单个插件：构建专属类加载器、扫描组件是"基础设施步骤"，整体用 try/catch 隔离
     * （design.md Decision 1）——任一步骤失败，该插件标记为 {@link PluginStatus#FAILED}，
     * 不注册该插件任何 Bean 定义；基础设施步骤成功后，逐个候选类独立注册（覆盖目标解析不到、
     * 命中覆盖黑名单、请求路径冲突等），单个类失败只跳过该类，不影响插件内其他类，插件整体
     * 仍保持 {@link PluginStatus#REGISTERED}（plugin-jar-management capability spec "插件
     * Bean 定义注册阶段的失败隔离"）。
     *
     * @param candidate       插件候选信息
     * @param beanFactory     当前 Bean 工厂
     * @param appClassLoader  主程序类加载器
     * @param pathRegistry    路径登记表
     * @param denyListChecker 覆盖黑名单校验器
     * @param overrideOwners  覆盖目标 bean name -&gt; 当前生效插件名称，跨插件共享用于冲突记录
     * @param pluginRegistry  内存态插件登记表，用于记录覆盖冲突
     * @return 该插件的状态记录
     */
    private PluginInfo processPlugin(Candidate candidate, ConfigurableListableBeanFactory beanFactory,
            ClassLoader appClassLoader, ControllerPathRegistry pathRegistry, OverrideDenyListChecker denyListChecker,
            Map<String, String> overrideOwners, PluginRegistry pluginRegistry) {
        PluginMetadata metadata = candidate.metadata();
        PluginInfo info = new PluginInfo(metadata.name(), candidate.jarPath().getFileName().toString(), metadata.version(),
                metadata.priority());

        List<Class<?>> components;
        URLClassLoader pluginClassLoader = null;
        try {
            pluginClassLoader = PluginClassLoaderFactory.create(candidate.jarPath(), metadata.name(), appClassLoader);
            components = PluginComponentScanner.scan(candidate.jarPath(), pluginClassLoader);
        } catch (Exception | LinkageError ex) {
            info.markFailed("插件加载失败：" + ex);
            log.warn("插件 [{}]（来源 [{}]）Bean 定义注册阶段失败：{}", metadata.name(), candidate.jarPath().getFileName(), ex.toString());
            closeQuietly(pluginClassLoader, metadata.name());
            return info;
        }

        for (Class<?> clazz : components) {
            try {
                registerComponent(clazz, metadata.name(), beanFactory, pathRegistry, denyListChecker, overrideOwners, info,
                        pluginRegistry);
            } catch (Exception ex) {
                info.addSkippedClass(new PluginSkippedClass(clazz.getName(), "注册异常：" + ex));
                log.warn("插件 [{}] 中的类 [{}] Bean 定义注册失败：{}", metadata.name(), clazz.getName(), ex.toString());
            }
        }
        log.info("插件 [{}]（来源 [{}]）处理完成：成功注册 {} 个 Bean 定义，跳过 {} 个候选类", metadata.name(),
                candidate.jarPath().getFileName(), info.getOverrides().size() + info.getRegisteredBeanNames().size(),
                info.getSkippedClasses().size());
        return info;
    }

    /**
     * 按候选类是否标注 {@link PluginOverride} 分派到覆盖注册或新建 Bean 注册流程。
     *
     * @param clazz           候选类
     * @param pluginName      插件名称
     * @param beanFactory     当前 Bean 工厂
     * @param pathRegistry    路径登记表
     * @param denyListChecker 覆盖黑名单校验器
     * @param overrideOwners  覆盖目标 bean name -&gt; 当前生效插件名称
     * @param info            插件状态记录
     * @param pluginRegistry  内存态插件登记表
     */
    private void registerComponent(Class<?> clazz, String pluginName, ConfigurableListableBeanFactory beanFactory,
            ControllerPathRegistry pathRegistry, OverrideDenyListChecker denyListChecker,
            Map<String, String> overrideOwners, PluginInfo info, PluginRegistry pluginRegistry) {
        PluginOverride overrideAnnotation = clazz.getAnnotation(PluginOverride.class);
        if (overrideAnnotation != null) {
            registerOverride(clazz, overrideAnnotation, pluginName, beanFactory, denyListChecker, overrideOwners, info,
                    pluginRegistry);
            return;
        }
        registerNewBean(clazz, pluginName, beanFactory, pathRegistry, info);
    }

    /**
     * 处理显式声明 {@link PluginOverride} 的候选类（plugin-bean-override capability spec
     * "显式声明覆盖目标"）：先查覆盖黑名单，再按目标类型在当前 {@link BeanDefinitionRegistry}
     * 中定位主程序（或此前插件）已注册的唯一 bean name，用同一 bean name 重新注册以生效覆盖
     * （design.md Decision 3，依赖 {@code spring.main.allow-bean-definition-overriding:
     * true}）。多个插件覆盖同一目标时，处理顺序决定生效顺序，记录冲突到 {@link PluginRegistry}
     * （design.md Decision 4）。
     *
     * @param clazz           候选类
     * @param annotation      覆盖声明注解
     * @param pluginName      插件名称
     * @param beanFactory     当前 Bean 工厂
     * @param denyListChecker 覆盖黑名单校验器
     * @param overrideOwners  覆盖目标 bean name -&gt; 当前生效插件名称
     * @param info            插件状态记录
     * @param pluginRegistry  内存态插件登记表
     */
    private void registerOverride(Class<?> clazz, PluginOverride annotation, String pluginName,
            ConfigurableListableBeanFactory beanFactory, OverrideDenyListChecker denyListChecker,
            Map<String, String> overrideOwners, PluginInfo info, PluginRegistry pluginRegistry) {
        Class<?> targetType = annotation.target();
        String targetClassName = targetType.getName();

        if (denyListChecker.isDenied(targetClassName)) {
            info.addSkippedClass(
                    new PluginSkippedClass(clazz.getName(), "覆盖目标 [" + targetClassName + "] 命中覆盖黑名单，拒绝覆盖"));
            return;
        }

        String[] candidateNames = beanFactory.getBeanNamesForType(targetType, true, false);
        if (candidateNames.length == 0) {
            info.addSkippedClass(
                    new PluginSkippedClass(clazz.getName(), "覆盖目标 [" + targetClassName + "] 在主程序中未找到对应 Bean 定义"));
            return;
        }
        if (candidateNames.length > 1) {
            info.addSkippedClass(new PluginSkippedClass(clazz.getName(), "覆盖目标 [" + targetClassName
                    + "] 对应多个候选 Bean（" + String.join(",", candidateNames) + "），无法确定覆盖对象"));
            return;
        }

        String beanName = candidateNames[0];
        AnnotatedGenericBeanDefinition beanDefinition = new AnnotatedGenericBeanDefinition(clazz);
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        // registerBeanDefinition 只声明在 BeanDefinitionRegistry 接口上，ConfigurableListableBeanFactory
        // 本身不含该方法；Spring Boot 实际使用的 DefaultListableBeanFactory 同时实现两个接口，此处安全强转。
        ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(beanName, beanDefinition);

        String previousOwner = overrideOwners.put(beanName, pluginName);
        if (previousOwner != null) {
            pluginRegistry.recordConflict(new PluginOverrideConflict(targetClassName, previousOwner, pluginName, beanName));
            log.info("插件覆盖冲突：目标 [{}] 此前由插件 [{}] 生效，现由插件 [{}] 覆盖生效（bean name [{}]）", targetClassName, previousOwner,
                    pluginName, beanName);
        }
        info.addOverride(new PluginBeanOverride(clazz.getName(), targetClassName, beanName));
    }

    /**
     * 处理未声明覆盖的候选类：使用 {@code plugin.<pluginName>.<SimpleClassName>} 命名空间前缀
     * 生成 bean name（design.md Decision 1），Controller 类额外做请求路径去重校验
     * （design.md Decision 2）。
     *
     * @param clazz        候选类
     * @param pluginName   插件名称
     * @param beanFactory  当前 Bean 工厂
     * @param pathRegistry 路径登记表
     * @param info         插件状态记录
     */
    private void registerNewBean(Class<?> clazz, String pluginName, ConfigurableListableBeanFactory beanFactory,
            ControllerPathRegistry pathRegistry, PluginInfo info) {
        String beanName = "plugin." + pluginName + "." + clazz.getSimpleName();
        if (beanFactory.containsBeanDefinition(beanName)) {
            info.addSkippedClass(new PluginSkippedClass(clazz.getName(), "生成的 bean name [" + beanName + "] 已被占用"));
            return;
        }

        if (AnnotatedElementUtils.hasAnnotation(clazz, Controller.class)) {
            Set<String> paths = RequestMappingPathResolver.resolvePaths(clazz);
            if (!paths.isEmpty()) {
                Optional<String> conflict = pathRegistry.tryRegister(pluginName, paths);
                if (conflict.isPresent()) {
                    info.addSkippedClass(new PluginSkippedClass(clazz.getName(), conflict.get()));
                    return;
                }
            }
        }

        AnnotatedGenericBeanDefinition beanDefinition = new AnnotatedGenericBeanDefinition(clazz);
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        // registerBeanDefinition 只声明在 BeanDefinitionRegistry 接口上，ConfigurableListableBeanFactory
        // 本身不含该方法；Spring Boot 实际使用的 DefaultListableBeanFactory 同时实现两个接口，此处安全强转。
        ((BeanDefinitionRegistry) beanFactory).registerBeanDefinition(beanName, beanDefinition);
        info.addRegisteredBeanName(beanName);
    }

    /**
     * 从 {@link Environment} 绑定 {@code rbac.plugin} 配置。不通过常规 {@code @Autowired}
     * 注入 {@link PluginProperties} Bean——本处理器运行阶段早于
     * {@code ConfigurationPropertiesBindingPostProcessor} 就绪，此时取到的会是字段未绑定的
     * 裸对象，因此直接用 {@link Binder} 从同一份 {@link Environment} 读取，与常规注入拿到的
     * 是同一数据源。
     *
     * @return 插件配置
     */
    private PluginProperties bindProperties() {
        return Binder.get(environment).bind("rbac.plugin", Bindable.of(PluginProperties.class)).orElseGet(PluginProperties::new);
    }

    /**
     * 安静关闭插件类加载器（仅用于基础设施步骤失败、未注册任何该插件 Bean 定义的场景，此时
     * 提前释放 jar 文件句柄是安全的）。
     *
     * @param classLoader 待关闭的类加载器，可能为 {@code null}
     * @param pluginName  插件名称，仅用于日志
     */
    private void closeQuietly(URLClassLoader classLoader, String pluginName) {
        if (classLoader == null) {
            return;
        }
        try {
            classLoader.close();
        } catch (Exception ex) {
            log.debug("关闭插件 [{}] 类加载器失败（可忽略）：{}", pluginName, ex.toString());
        }
    }

    /**
     * 插件候选信息：来源 jar 路径 + 元信息，仅用于本类内部排序与传参。
     *
     * @param jarPath  插件 jar 路径
     * @param metadata 插件元信息
     */
    private record Candidate(Path jarPath, PluginMetadata metadata) {
    }
}
