## Context

主程序是单体 Spring Boot 3.5 应用（`RbacApplication`），使用标准 `@SpringBootApplication` + `@ComponentScan`，MVC 路由由 `RequestMappingHandlerMapping` 在主 `ApplicationContext` 内统一扫描注册。目前没有任何插件/扩展点基础设施。见 proposal.md - Why 了解动机，见 specs/plugin-jar-management、specs/plugin-bean-override 了解行为契约。

关键约束（决定了本次的取舍）：
- Spring 的 `ApplicationContext.refresh()` 是"事务性"的——如果任意 Bean 在 `finishBeanFactoryInitialization` 阶段实例化失败（构造器/`@PostConstruct` 抛异常），整个上下文刷新失败，应用无法启动。
- 本次明确选择：插件加载时机放在**主程序自身组件扫描完成后、同一次 `refresh()` 内立即进行**，以获得原生、干净的 Bean 定义覆盖语义。作为代价，插件失败隔离能力收窄到"Bean 定义注册阶段"，**不覆盖 Bean 实例化阶段的异常**——这一点已与用户确认为可接受的取舍，写入下方 Goals 与 Risks。

## Goals / Non-Goals

**Goals:**
- 在主程序自身 `@ComponentScan` 产生的 Bean 定义全部注册完成之后、Bean 实例化开始之前，立即扫描并加载 `plugins/` 目录下的插件 jar，将其中的 Controller/Service/Component/Configuration 类解析为 Bean 定义并注册进主 `BeanFactory`。
- 插件在 **Bean 定义注册阶段**的失败（jar 损坏、类解析失败、覆盖目标解析不到等）互相隔离，且不影响主程序自身 Bean 定义的注册。
- 插件可显式声明覆盖主程序已有的 Service/Component/Controller，覆盖通过原生 Bean 定义覆盖机制，对**所有**依赖注入/查找立即生效（不存在"已固化引用不追溯"的问题）。
- 插件中的 Controller 接口能被主程序的 DispatcherServlet 正确路由（无需额外桥接组件）。
- 覆盖能力对安全关键组件（认证过滤器、权限校验、全局异常处理器等）设黑名单禁止覆盖。
- 提供插件（定义注册阶段）状态查询的管理接口。

**Non-Goals（v1 不做，作为后续迭代）：**
- 运行期新增插件热加载（无需重启）：新方案下插件的 Bean 定义注册只发生在主上下文那唯一一次 `refresh()` 过程中，主程序启动完成后无法再用同样方式安全地新增/覆盖 Bean 定义；新增或更新插件必须重启应用。原方案中设想的 `POST /api/v1/plugins/reload` 管理接口不再提供。
- 文件系统监听自动热加载。
- 插件卸载、覆盖关系的运行期回滚（覆盖发生在启动期一次性的定义注册阶段，撤销同样只能通过重启+移除插件 jar 完成）。
- 插件间的安全沙箱/资源隔离（CPU/内存限额、类访问权限控制），仅做类加载隔离。
- 插件状态持久化到数据库（v1 用内存态 `PluginRegistry`，重启后以磁盘 jar 重新计算）。
- 插件 Bean **实例化阶段**异常的隔离/恢复（见 Goals 与 Risks 中的取舍说明）。

## Decisions

### 1. 插件加载时机：主程序组件扫描完成后，立即通过 `BeanDefinitionRegistryPostProcessor` 注册插件 Bean 定义
新增 `PluginBeanDefinitionRegistrar implements BeanDefinitionRegistryPostProcessor, PriorityOrdered`。

**执行顺序的实现方式（与最初设想不同，已按实际验证结果调整）**：最初设想 `getOrder()` 返回一个"数值大于 `ConfigurationClassPostProcessor`"的优先级值来保证晚于它执行，但 `ConfigurationClassPostProcessor` 自身的 `getOrder()` 默认就返回 `Ordered.LOWEST_PRECEDENCE`（已是最低优先级/最大数值），不存在比它更大的候选值，这条路走不通。实际验证 Spring Boot 3.5（Spring 6）`PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors` 源码可知，真正保证顺序的不是数值比较，而是"发现时机"：`ConfigurationClassPostProcessor` 在容器创建时就以固定 bean name 预注册，是第一轮 `PriorityOrdered` 扫描中唯一能找到的候选者，它的 `postProcessBeanDefinitionRegistry` 执行的正是主程序 `@ComponentScan`/`@Configuration` 的完整处理过程——`PluginBeanDefinitionRegistrar` 作为 `cn.nihility.rbac` 包内的 `@Component`，只有在那一轮扫描完成后才会作为 Bean 定义出现，因此必然只能在后续批次中被发现和调用，天然晚于主程序自身组件扫描，且仍在同一次 `refresh()` 的 `invokeBeanFactoryPostProcessors` 阶段（早于任何 Bean 实例化）。据此实现里 `getOrder()` 直接返回 `Ordered.LOWEST_PRECEDENCE`——数值本身不是关键，"发现时机"才是。

该处理器对每个已发现的插件 jar（复用决策见"插件发现与类加载"任务组）执行：
1. 构建插件专属 `URLClassLoader`（parent-first 委派主程序类加载器）。
2. 扫描插件内标注 `@Controller`/`@RestController`/`@Service`/`@Component`/`@Configuration` 的类。**实现方式与最初设想不同**：最初设想用 `ClassPathScanningCandidateComponentProvider`，但该类基于 `classpath*:` 模式解析资源，通过插件专属 `URLClassLoader`（parent-first）取得的 `ClassLoader#getResources(String)` 会沿委派链一路向上聚合，实际会把主程序自身及其全部依赖 jar 中的类也一并扫描进来，不符合"只扫描该插件 jar 自身"的既定行为。改为直接枚举该插件 jar 自身的 `JarEntry` 取得类名清单，用该插件专属类加载器逐个 `Class.forName` 加载后，再用 Spring `AnnotatedElementUtils.hasAnnotation`（meta-annotation 感知，等价于 `useDefaultFilters=true` 时 `@Component` 过滤器的判定逻辑）逐个判定，只依赖该插件自身类加载器加载的类对象，不产生跨 jar 的扫描污染。
3. 为每个类构建 `AnnotatedGenericBeanDefinition` 并注册进主 `BeanDefinitionRegistry`（普通新增 Bean 用插件命名空间前缀的 bean name，如 `plugin.<pluginName>.<simpleClassName>`，避免与主程序或其他插件冲突；覆盖场景见决策 2）。

以上两处调整均为对既定行为契约的等价工程实现，未改变本决策及决策 2-6 的核心架构（详见 `PluginBeanDefinitionRegistrar`、`PluginComponentScanner` 的类级 Javadoc）。

单个插件从"构建类加载器"到"注册全部 Bean 定义"的整个过程用 try/catch 隔离：任一步骤失败，该插件标记为 `FAILED` 并记录原因，**不注册任何该插件的 Bean 定义**（避免半成品状态），处理下一个插件；不影响主程序自身 Bean 定义，也不影响其他插件。

这一阶段之后的 Bean 实例化（`finishBeanFactoryInitialization`）与主程序自身 Bean 一视同仁地进行——插件 Bean 若在构造器/`@PostConstruct` 抛异常，会导致本次 `refresh()`、即整个主程序启动失败（见 Risks 中的取舍说明，已与用户确认接受）。

*已放弃的备选方案*：主程序启动完成后再用独立子 `ApplicationContext` 加载插件——能做到实例化阶段的完全隔离，但覆盖需要"已初始化实例搬迁"（`destroySingleton`+`registerSingleton`），存在"已固化字段注入引用不追溯"的限制，且 Controller 覆盖/暴露需要手动桥接 `RequestMappingHandlerMapping`，实现复杂度更高。本次按用户要求改为当前方案。

### 2. Controller 暴露与覆盖：原生机制，无需手动桥接
因为插件 Controller 的 Bean 定义和主程序 Controller 处于同一个 `BeanFactory`、同一次 `refresh()`，`RequestMappingHandlerMapping` 在正常的 MVC 初始化流程中即可扫描到它们，**不需要**额外的桥接组件。

- 覆盖场景：插件 Controller 声明 `@PluginOverride(target = 主程序Controller.class)` 时，按目标类型解析出主程序已注册的 bean name，用该 bean name 重新注册（同名覆盖），最终该 bean name 下只保留插件的 Bean 定义，`RequestMappingHandlerMapping` 自然只会扫到插件版本，不会出现"同一路径重复映射"的启动期异常。
- 非覆盖场景下的路径冲突：插件 Controller 未声明覆盖，但其 `@RequestMapping` 路径恰好与主程序或另一插件已声明的路径相同——由于 bean name 不同，两者都会被注册为独立 Bean 定义，`RequestMappingHandlerMapping` 初始化时会因路径重复抛出"Ambiguous mapping"，而这已经进入 Bean 实例化/框架初始化阶段，不在本次的定义阶段隔离范围内，会导致整个启动失败。为了尽量把这类问题挡在"定义阶段"、避免退化到实例化阶段才爆炸，`PluginBeanDefinitionRegistrar` 在注册非覆盖 Controller 的 Bean 定义前，会**静态反射读取**其 `@RequestMapping`/`@GetMapping` 等注解值，与已知路径集合（主程序 + 已处理插件）做去重比对，冲突则该插件类的 Bean 定义注册失败（隔离，不影响其他插件），不再等到运行期才失败。

### 3. 覆盖声明与生效方式：显式注解 + 原生 Bean 定义覆盖
- 新增注解 `@PluginOverride(target = 主程序类.class)`，插件类必须显式标注该注解才会被当作覆盖处理；未标注的同名/同接口类一律作为插件独立 Bean 处理（用插件命名空间前缀的 bean name），不做任何隐式覆盖（满足 spec "禁止隐式自动覆盖"）。
- `PluginBeanDefinitionRegistrar` 按 `target` 类型在当前 `BeanDefinitionRegistry` 中定位既有 bean name（要求此时主程序自身该 Bean 的定义已注册，由决策 1 的执行顺序保证），用**同一个 bean name** 重新调用 `registerBeanDefinition`（依赖 `spring.main.allow-bean-definition-overriding: true`，需在 `application.yml` 中显式开启）完成覆盖。
- 因为覆盖发生在任何 Bean 实例化之前，之后所有的依赖注入（构造器注入/字段注入）和运行期 `getBean` 查找拿到的都是插件实现，**不存在**旧方案里"已固化引用不追溯"的限制。

### 4. 多插件覆盖同一目标的冲突规则
插件按扫描到的**文件名字典序**依次处理（可选：插件 jar 内 `META-INF/plugin.properties` 增加 `priority` 数值字段，数值越大越晚处理，同优先级退回文件名排序）。同一目标被多个插件声明覆盖时，`registerBeanDefinition` 对同一 bean name 的重复注册天然是"后注册覆盖先注册"，处理顺序即决定生效顺序；覆盖发生时记录一条冲突日志（哪些插件、目标是谁、最终生效者是谁），供管理接口查询。

### 5. 覆盖黑名单
在 `application.yml` 新增配置项 `rbac.plugin.override.deny-list`，默认包含主程序认证过滤器（`auth/filter` 下的类）、`GlobalExceptionHandler`、`GlobalResponseAdvice` 等安全关键类的全限定名。`PluginBeanDefinitionRegistrar` 在执行覆盖注册前先比对该名单，命中则拒绝该覆盖生效并记录原因（该插件类的 Bean 定义直接不注册，视为该插件对应部分加载失败），插件其余非受限部分仍按正常流程处理。

### 6. 插件元信息与状态查询
插件 jar 内约定 `META-INF/plugin.properties`（`name`、`version`、`priority`，均可选，缺省用文件名兜底）。新增 `plugin` 模块（`cn.nihility.rbac.plugin`，controller/service/dto 分层），内存态 `PluginRegistry` 记录每个插件的名称、来源文件、**定义注册阶段**状态（`REGISTERED`/`FAILED`）、失败原因、覆盖关系列表；提供：
- `GET /api/v1/plugins`：查询插件列表及状态（管理员权限）。状态只反映定义注册阶段的结果——如果某个插件在 Bean 实例化阶段导致主程序启动失败，那次启动直接失败，无法通过该接口查询到"部分失败"的中间态，需要看主程序启动日志定位。

不再提供运行期重新扫描/加载接口（见 Non-Goals）。

## Risks / Trade-offs

- **[Risk][已接受的取舍] 插件 Bean 实例化阶段异常导致主程序整体无法启动** → 这是本次为了获得干净的原生 Bean 覆盖语义而明确接受的行为，不再假装能够隔离。缓解：① 插件开发规范中要求插件在打包发布前，先在独立的最小 Spring Boot 工程里完成启动自测；② 状态查询接口只能覆盖定义注册阶段的失败，实例化阶段失败需要看主程序启动日志定位到具体插件的具体 Bean；③ 生产环境变更插件前建议先在预发环境验证整个应用能正常启动。
- **[Risk] 类加载隔离不彻底导致依赖冲突** → 插件若打包了与主程序不同版本的公共依赖，parent-first 委派下仍以主程序版本为准，插件若强依赖自己那个版本可能行为异常。缓解：插件开发规范中要求插件只携带主程序未提供的私有依赖，公共依赖（Spring、MyBatis-Plus 等）由主程序类加载器提供，插件 jar 中不重复打包。
- **[Trade-off] 覆盖黑名单是静态配置而非语义级安全策略** → 只能防住"整类覆盖"，无法防止插件通过反射等手段绕过限制访问敏感对象。v1 接受该风险，已在 Non-Goals 声明"不做安全沙箱"；如需更强隔离需后续引入独立 JVM 进程/容器级插件运行时，超出本次范围。
- **[Trade-off] 不再支持运行期新增/更新插件** → 相比原方案（子上下文 + 手动重新扫描接口），新方案下任何插件变更都需要重启应用才能生效。业务方如果后续对"零重启热更新"有强诉求，需要作为独立 change 重新评估，可能要在"原生覆盖语义"和"实例化隔离"之间做不同的取舍。
