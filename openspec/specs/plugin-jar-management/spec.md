## Purpose

定义系统如何发现、加载、注册和管理 `plugins` 目录下的插件 jar 包，使插件中的 Spring 组件（Controller/Service/Component/Configuration 等）能够在不重新打包主程序的情况下对外提供功能。

## Requirements

### Requirement: 插件目录扫描与发现
系统 SHALL 在主程序自身的 Spring 组件扫描完成之后、任何 Bean（含主程序自身的 Bean）开始实例化之前，同一次启动流程内立即扫描指定的插件目录（默认 `plugins/`，可通过配置项调整路径），识别其中的 `.jar` 文件作为待加载插件。非 `.jar` 文件或子目录 SHALL 被忽略并记录日志。系统不提供运行期重新扫描该目录的能力，新增或更新插件需要重启应用。

#### Scenario: 启动时发现插件目录下的 jar 包
- **WHEN** 应用启动、主程序自身组件扫描完成，且 `plugins/` 目录下存在一个或多个 `.jar` 文件
- **THEN** 系统记录发现的插件文件清单，并进入后续的 Bean 定义注册流程

#### Scenario: 插件目录不存在
- **WHEN** 应用启动时配置的插件目录不存在
- **THEN** 系统 SHALL 跳过插件加载流程并记录提示信息，不影响主程序正常启动

### Requirement: 插件 jar 动态加载
系统 SHALL 为每个插件 jar 创建独立的类加载单元，解析其中标注 `@RestController`/`@Controller`、`@Service`、`@Component`、`@Configuration` 的类，并在 Bean 定义注册阶段将其纳入主 Spring 容器的 Bean 定义集合，使其之后与主程序自身的 Bean 一同完成实例化。

#### Scenario: 加载包含 Controller 的插件
- **WHEN** 插件 jar 中包含一个标注 `@RestController` 的类且定义了 HTTP 接口路径
- **THEN** 系统加载成功后，该接口路径 SHALL 可通过 HTTP 请求访问并返回插件实现的响应

#### Scenario: 加载包含 Service/Component 的插件
- **WHEN** 插件 jar 中包含标注 `@Service` 或 `@Component` 的类
- **THEN** 该类的实例 SHALL 被纳入可供其他 Bean 依赖注入的容器管理范围

#### Scenario: 加载包含 Configuration 的插件
- **WHEN** 插件 jar 中包含标注 `@Configuration` 的类，其中定义了 `@Bean` 方法
- **THEN** 系统 SHALL 执行该配置类并注册其声明的 Bean

### Requirement: 插件 Bean 定义注册阶段的失败隔离
单个插件在 **Bean 定义注册阶段**失败（jar 损坏、类无法解析、覆盖目标解析不到、与已知路径/Bean 定义冲突等）时，系统 SHALL 记录失败原因、不为该插件注册任何 Bean 定义，且不得导致主程序启动失败或其他正常插件的 Bean 定义注册失败。该隔离能力**不覆盖** Bean 实例化阶段（构造器/`@PostConstruct` 等生命周期回调）发生的异常——插件的 Bean 定义一旦成功注册，即与主程序自身 Bean 一同参与后续实例化，此阶段任一 Bean（包括插件 Bean）抛出异常都会导致主程序整体启动失败，这是系统的既定行为，非本需求覆盖的隔离范围。

#### Scenario: 插件 jar 损坏
- **WHEN** `plugins/` 目录下某个 jar 文件无法被正确解析（如非合法 jar 格式）
- **THEN** 系统记录该插件加载失败及原因，主程序继续正常启动，其余插件继续按流程加载

#### Scenario: 插件与已知路径/Bean 定义冲突
- **WHEN** 某个插件中未声明覆盖的类，其 Bean 定义（如请求路径）在注册前与主程序或已处理插件的既有定义冲突
- **THEN** 系统 SHALL 拒绝该类的 Bean 定义注册并记录原因，不影响该插件内其他不冲突的类，也不影响其他插件

#### Scenario: 插件 Bean 实例化阶段异常
- **WHEN** 某个插件的 Bean 定义已成功注册，但该 Bean 在后续实例化过程（构造器或 `@PostConstruct` 等）中抛出异常
- **THEN** 系统 SHALL 允许该异常导致主程序本次启动整体失败，并在启动日志中包含足以定位到具体插件、具体类的信息；此场景不要求实现隔离或恢复

### Requirement: 插件状态查询
系统 SHALL 提供查询已发现插件列表及其 **Bean 定义注册阶段**状态（成功/失败/失败原因）的能力，供管理员核实插件运行情况。该状态不反映后续 Bean 实例化阶段的结果——若实例化阶段失败，主程序本次启动直接失败，不存在可查询的中间状态。

#### Scenario: 查询插件列表
- **WHEN** 管理员在主程序成功启动后请求查询当前插件状态
- **THEN** 系统返回每个插件的名称、来源文件、Bean 定义注册阶段状态及（若失败）失败原因
