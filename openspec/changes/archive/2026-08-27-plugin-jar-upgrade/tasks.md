## 1. 基础模块与配置

- [x] 1.1 创建 `cn.nihility.rbac.plugin` 包结构（controller/service/dto/entity/support 等分层），添加包级说明，`./gradlew compileJava` 编译通过
- [x] 1.2 在 `application.yml` 新增插件相关配置项：插件目录路径（默认 `plugins/`）、`spring.main.allow-bean-definition-overriding: true`、`rbac.plugin.override.deny-list` 默认黑名单（认证过滤器、`GlobalExceptionHandler`、`GlobalResponseAdvice` 等全限定名），并在 `RbacApplicationTests` 中验证配置能正常绑定加载
- [x] 1.3 定义插件元信息约定：`@PluginOverride(target = Class)` 注解、`META-INF/plugin.properties`（`name`/`version`/`priority`）解析工具类，编写单元测试覆盖"存在/缺失 properties 文件"两种情况

## 2. 插件发现与类加载

- [x] 2.1 实现插件目录扫描器：按文件名字典序（或 `plugin.properties` 里的 `priority`）枚举 `plugins/` 目录下的 `.jar` 文件，目录不存在时记录日志并跳过（不影响主程序启动），编写单元测试验证扫描顺序与目录缺失场景
- [x] 2.2 实现单插件 `URLClassLoader` 构造（parent-first 委派主程序类加载器），编写单元测试验证插件私有类可被加载、主程序公共类（如 Spring 基础类）不重复加载
- [x] 2.3 实现插件内组件扫描（识别 `@Controller`/`@RestController`/`@Service`/`@Component`/`@Configuration`）：直接枚举插件 jar 自身的 `JarEntry` 取得类名清单，用插件专属类加载器逐个加载后按注解判定，不使用最初设想的 `ClassPathScanningCandidateComponentProvider`（`classpath*:` 扫描会沿 parent-first 委派链把主程序及其依赖一并扫进来，不符合"只扫插件自身"的行为，见 design.md 决策 1），编写单元测试用一个示例插件 jar 验证扫描结果

## 3. Bean 定义注册与失败隔离

- [x] 3.1 实现 `PluginBeanDefinitionRegistrar`（`BeanDefinitionRegistryPostProcessor` + `PriorityOrdered`；`getOrder()` 返回 `Ordered.LOWEST_PRECEDENCE`——真正保证晚于主程序自身 `ConfigurationClassPostProcessor` 执行的是"发现时机"而非数值比较，详见 design.md 决策 1 与该类 Javadoc），驱动 2.1-2.3 完成插件发现、类加载、组件扫描，并将结果转换为 `AnnotatedGenericBeanDefinition` 注册进主 `BeanDefinitionRegistry`（非覆盖 Bean 使用 `plugin.<pluginName>.<simpleClassName>` 命名空间前缀 bean name），`./gradlew test` 验证该处理器能被 Spring 正确识别并按预期顺序执行
- [x] 3.2 用 try/catch 包裹单个插件从"构建类加载器"到"注册全部 Bean 定义"的整个流程：任一步骤失败则该插件标记 `FAILED`、不注册该插件任何 Bean 定义，记录到内存态 `PluginRegistry`；编写集成测试验证：一个 jar 本身损坏（非法 jar 格式）时，主程序正常启动、其余正常插件仍成功注册（对应"插件 jar 损坏"场景）
- [x] 3.3 编写集成测试验证：插件的 Bean 定义成功注册后，若该 Bean 在实例化阶段（构造器/`@PostConstruct`）抛异常，主程序本次启动整体失败，且异常堆栈/日志中能定位到具体插件与具体类（对应"插件 Bean 实例化阶段异常"场景，验证的是"预期如此"而非"被隔离恢复"）

## 4. Controller 暴露、覆盖与冲突处理

- [x] 4.1 编写集成测试验证：加载一个包含 `@RestController` 的示例插件 jar 后，`RequestMappingHandlerMapping` 能自动扫描到其路由，对应 HTTP 路径请求能收到插件实现的响应（对应"加载包含 Controller 的插件"场景），确认无需额外桥接组件
- [x] 4.2 实现覆盖目标解析：根据 `@PluginOverride(target=...)` 在当前 `BeanDefinitionRegistry` 中按类型定位主程序已注册的 bean name（此时主程序自身该 Bean 定义已注册完毕），未找到目标时该插件类的 Bean 定义注册失败并记录原因（隔离，不影响其他插件）
- [x] 4.3 实现同名 Bean 定义覆盖注册（复用 `spring.main.allow-bean-definition-overriding: true`），编写集成测试验证覆盖后不论构造器注入、字段注入还是运行期 `getBean` 查找，拿到的都是插件实现（对应"覆盖后的方法调用生效"场景）
- [x] 4.4 验证 Controller 覆盖场景：覆盖目标为 Controller 时按 4.3 同样机制同名覆盖，编写集成测试验证原路径请求由插件实现处理、且启动期不出现"Ambiguous mapping"异常（对应"覆盖 Controller 接口"场景）
- [x] 4.5 实现非覆盖插件 Bean 的路径去重校验：注册非覆盖 Controller 的 Bean 定义前，静态反射读取其 `@RequestMapping`/`@GetMapping` 等注解值，与已知路径集合（主程序 + 已处理插件）比对，冲突则该类 Bean 定义注册失败并记录原因（隔离，不影响其他插件也不等到运行期 `initHandlerMethods` 才失败），编写单元测试覆盖该场景（对应"插件与已知路径/Bean 定义冲突"场景）
- [x] 4.6 实现覆盖黑名单校验：命中 `rbac.plugin.override.deny-list` 的目标类拒绝覆盖注册并记录原因，插件其余非受限部分正常处理，编写单元测试覆盖该场景（对应"尝试覆盖安全关键组件"场景）
- [x] 4.7 实现多插件覆盖同一目标的冲突记录：依赖 `registerBeanDefinition` 对同一 bean name 的"后注册覆盖先注册"天然行为，处理顺序决定生效顺序，覆盖发生时记录冲突日志（哪些插件、目标、最终生效者），编写集成测试验证两个插件覆盖同一目标时的最终生效结果与冲突记录（对应"两个插件覆盖同一目标类"场景）

## 5. 管理接口

- [x] 5.1 新增 `GET /api/v1/plugins` 接口：返回插件名称、来源文件、Bean 定义注册阶段状态、失败原因、覆盖关系列表（管理员权限校验、springdoc `@Tag`/`@Operation` 注解），编写集成测试验证响应结构
- [x] 5.2 更新权限资源编码文件 `权限资源.txt`，补充插件管理相关的菜单/按钮资源编码

## 6. 文档与收尾

- [x] 6.1 在部署相关文档中补充 `plugins/` 目录的用途、插件 jar 编写规范（`@PluginOverride` 用法、`META-INF/plugin.properties` 格式、覆盖黑名单说明），并明确写出 v1 的核心限制：新增/更新插件必须重启应用才能生效，不提供运行期热加载或重新扫描接口；插件 Bean 实例化阶段异常会导致主程序整体启动失败，插件发布前必须自行完成最小化启动自测（本仓库目前没有独立的部署文档文件，落在 `cn.nihility.rbac.plugin` 包级 Javadoc `package-info.java` 中，与其他内容组织约定保持一致）
- [x] 6.2 运行 `./gradlew build` 确认全量测试通过，人工核对本次新增功能与 `design.md` Non-Goals 声明范围一致（未引入超出范围的运行期热加载/安全沙箱/持久化能力）
