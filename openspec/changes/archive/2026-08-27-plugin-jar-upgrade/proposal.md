## Why

当前系统的功能修复/升级只能通过重新打包整个后端应用、重启服务来完成，无法针对单个功能模块做增量热更新。业务方需要一种"插件包"机制：将某个功能的补丁/扩展打成独立 jar，放到指定目录后由主程序动态加载生效，且能够覆盖（替换）主程序中已有类的功能实现，从而缩短紧急修复和功能扩展的发布周期，避免每次都重新编译部署整个单体应用。

## What Changes

- 新增 `plugins/` 目录扫描与加载机制：在主程序自身的 Spring 组件扫描完成之后、Bean 实例化开始之前，同一次启动流程内立即扫描该目录下的 jar 包并解析为 Bean 定义注册进主 Spring 容器（不支持运行期新增插件后免重启生效，新增/更新插件需要重启应用）。
- 插件 jar 内部允许包含标准 Spring 注解类（`@RestController`/`@Controller`、`@Service`、`@Component`、`@Configuration` 等），其中定义的 Bean 会被识别并注册进 Spring 容器，插件中的 Controller 接口能够对外提供 HTTP 服务。
- 新增"类覆盖（override）"机制：插件 jar 中的类如果显式标记为覆盖目标（注解标识，非隐式命名匹配），其对应的 Bean 定义会替换主程序中同名/同类型的现有 Bean，使插件内的实现在运行期生效，替代原有实现的方法调用结果。
- **BREAKING**：主程序中原本由 Spring 容器唯一管理的 Bean，在插件覆盖生效后其实际运行时类型/行为会被插件实现替换，依赖 "该 Bean 一定是主程序自带实现" 这一假设的调用方需要重新评估。
- 插件加载失败时的处理策略：仅隔离 **Bean 定义注册阶段**的失败（jar 损坏、覆盖目标解析不到、路径冲突等），单个插件此阶段失败不影响主程序及其他插件；**Bean 实例化阶段**（构造器/`@PostConstruct`）失败仍会导致主程序整体启动失败——这是为换取原生、干净的 Bean 覆盖语义而做出的明确取舍，详见 design.md。

## Capabilities

### New Capabilities
- `plugin-jar-management`: plugins 目录下 jar 包的发现、动态类加载、Spring Bean 注册与生命周期管理。
- `plugin-bean-override`: 插件内类覆盖主程序已有 Bean（Controller/Service/Component/Configuration）功能实现的规则与运行期生效机制。

### Modified Capabilities
（无——本次改动新增独立的插件加载能力，不修改现有已归档 capability 的既有需求）

## Impact

- 受影响代码：新增插件管理模块 `cn.nihility.rbac.plugin`（`annotation`/`config`/`support`/`dto`/`service`/`controller` 分层，`RbacApplication` 本身无需改动——插件加载通过 `BeanDefinitionRegistryPostProcessor` 接入主程序既有的 `refresh()` 流程），用于插件的发现、加载与**只读**状态查询（`GET /api/v1/plugins`）；不提供插件启停/上传/重新扫描等管理操作，插件状态为内存态，不落库，无需 entity/mapper 层。
- 新增运行时目录：应用工作目录下 `plugins/`（存放待加载 jar 包），需要在部署文档/配置中体现。
- 依赖：可能需要引入 jar 动态加载相关能力（优先复用 JDK 自带 `URLClassLoader`/`ServiceLoader`，如需引入第三方类隔离框架需先与用户确认后再改 `build.gradle`）。
- 涉及系统安全边界：动态加载外部 jar 属于高风险能力，最终方案不提供页面上传/启停插件的接口（插件仅通过运维人员手动放入 `plugins/` 目录 + 重启应用生效），权限控制收窄为"谁可以查看插件状态"（`GET /api/v1/plugins` 复用既有权限编码机制），并在 design.md 中明确了覆盖范围限制（`rbac.plugin.override.deny-list` 黑名单禁止覆盖认证过滤器等安全关键类）。
- 与 Spring 容器生命周期、Bean 覆盖策略（`spring.main.allow-bean-definition-overriding` 等）相关，需要评估对现有 `common/`、`auth/` 等基础模块的影响。
