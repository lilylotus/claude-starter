# Tasks

## 1. 数据库迁移

- [x] 1.1 确认 `backend/src/main/resources/db/migration` 下当前最新版本号，新增
  `V{n}__add_abac_policy.sql`：建 `tab_abac_policy`（`name`、`remark`、`status`、
  `show_order`、创建人/创建时间/更新人/更新时间）、`tab_abac_policy_resource`
  （`policy_id`、`permission_code`，`policy_id`+`permission_code` 唯一）、
  `tab_abac_policy_user_attr`（`policy_id`、`metadata_field_id`、`operator`、
  `attr_value`）、`tab_abac_policy_time_window`（`policy_id`、可空
  `day_of_week`、`start_time`、`end_time`），均为通用标准 SQL（不使用 MySQL 8.0+
  专属特性）；执行 `./gradlew bootRun`（或等价方式）确认 Flyway 迁移成功应用、无
  报错。
- [x] 1.2 在同一脚本或新增脚本中插入种子数据：权限管理一级菜单下的"ABAC 策略管理"
  二级菜单项，以及 `AbacManagement:policy:view`/`add`/`edit`/`enable`/`disable`/
  `delete` 权限点；更新仓库根目录 `权限资源.txt` 登记这些新增编码。

## 2. 后端：实体与数据访问

- [x] 2.1 新增 `cn.nihility.rbac.abac.entity` 下 4 个实体类（`AbacPolicyEntity`、
  `AbacPolicyResourceEntity`、`AbacPolicyUserAttrEntity`、
  `AbacPolicyTimeWindowEntity`），对应 `tab_abac_*` 表，字段命名驼峰，使用精确
  Lombok 注解；新增 `cn.nihility.rbac.abac.mapper` 下对应 `BaseMapper` 接口，
  执行 `./gradlew compileJava` 确认编译通过。
- [x] 2.2 新增 `cn.nihility.rbac.abac.constant.AbacAttrOperator`（`EQ`/`NE`/`IN`，
  不引用 `appaccess`/`userrole` 的同名常量类，design.md Decision 3）与
  `AbacPolicyStatus`（仅启用/停用两个状态码，取值与项目既有状态码约定一致；策略规则
  没有逻辑删除语义，删除接口是物理删除并级联清理资源编码/用户属性/时间窗口三张子表，
  故不存在"删除状态码"）。

## 3. 后端：用户属性与时间窗口判定组件

- [x] 3.1 新增 `cn.nihility.rbac.abac.support.AbacUserAttrEvaluator`：注入
  `metadata` 模块 `MetadataFieldMapper` 与 `user` 模块 `UserMapper`，对单个
  `userId` + 单条 `AbacPolicyUserAttrEntity` 做三重校验（字段启用、
  `bizType=USER`、`tableName=tab_user`）+ 列名白名单校验 + 动态查询判断是否满足
  （design.md Decision 3）；在 `UserMapper` 新增按 `userId`+动态列名+运算符的
  单用户匹配查询方法（Java 接口方法 + `UserMapper.xml` 对应 SQL，复用既有的列名
  白名单防注入模式）。编写单元测试覆盖 `EQ`/`NE`/`IN` 三种运算符对实际存在/不存在
  的用户数据的判断结果。
- [x] 3.2 新增 `cn.nihility.rbac.abac.support.AbacTimeWindowEvaluator`：对一条
  `AbacPolicyTimeWindowEntity` 判断服务器当前时间是否落在该窗口（`day_of_week`
  为空时每天适用，否则只在对应星期适用；起止时间按 `TIME` 比较）。编写单元测试覆盖
  "指定星期+命中"、"指定星期+不命中"、"不指定星期"三种场景（可通过注入可控的
  时钟/Clock 抽象使测试不依赖系统当前真实时间）。

## 4. 后端：策略管理 Service/Controller

- [x] 4.1 新增 `cn.nihility.rbac.abac.dto` 下请求/响应 DTO（创建/更新请求、详情
  VO、分页查询请求），`jakarta.validation` 注解校验必填项；新增
  `cn.nihility.rbac.abac.mapstruct.AbacPolicyConvert`（静态单例模式，不注册为
  Spring bean）。
- [x] 4.2 新增 `AbacPolicyService`/`AbacPolicyServiceImpl`：实现新增/编辑（目标
  资源编码、用户属性条件、时间窗口条件均整体替换语义）、分页查询、详情、启用/
  停用、删除；新增/编辑时校验"资源编码非空"与"用户属性条件、时间窗口条件至少
  一类非空"（design.md Decision 5），校验用户属性条件引用字段的合法性（USER 域+
  启用状态+同策略内不重复）与时间窗口起止时间合法性（结束晚于起始）。`@Transactional`
  指定回滚异常与传播方式。编写单元/集成测试覆盖 spec.md「ABAC 策略规则的定义与
  维护」「ABAC 策略规则的用户属性条件」「ABAC 策略规则的时间窗口条件」三个
  Requirement 下的全部 Scenario。
- [x] 4.3 新增 `AbacPolicyController`（方法级完整 URL，不使用类级
  `@RequestMapping`，补充 `@Tag`/`@Operation`/`@Parameter`/`@Schema` springdoc
  注解）：分页查询、详情、新增、编辑、启用、停用、删除接口，统一返回
  `Result<T>`。调用 `GET /api/abac/policies` 等接口手动验证返回结构符合预期。

## 5. 后端：运行时判定与 IdentityAuthFilter 集成

- [x] 5.1 新增 `AbacAuthorizationService`/`AbacAuthorizationServiceImpl`：实现
  `isDenied(userId, resourceCode)`（design.md Decision 5 判定流程：候选策略
  查询 → 逐条用户属性+时间窗口匹配 → 任意命中即拒绝）。编写单元测试覆盖 spec.md
  「ABAC 运行时限制判定」Requirement 下的全部 Scenario（资源未绑定策略/命中
  属性条件/命中时间窗口/两维度都配置时需同时满足/多候选任意命中/停用策略不作为
  候选）。
- [x] 5.2 修改 `auth/filter/IdentityAuthFilter.java`：注入
  `AbacAuthorizationService`，在现有 RBAC `hasPermission` 判定通过、且非
  `firstLoginExempt` 时，追加一次 `isDenied(userId, requiredPermission)` 调用，
  为真时按与 RBAC 拒绝一致的方式写错误响应（`AuthErrorCode.FORBIDDEN`/"无权限
  访问该资源"）。编写集成测试覆盖 spec.md「RBAC 判定通过后追加 ABAC 判定」
  Requirement 下的全部 Scenario（RBAC 不通过时不触发 ABAC；RBAC 通过+ABAC
  拒绝时请求被拦截；RBAC 通过+ABAC 不拒绝时正常放行；豁免路径不触发 ABAC）。
- [x] 5.3 跑一次 `./gradlew test` 全量回归，确认新增集成测试通过且未影响既有
  `IdentityAuthFilter`/`AuthorizationService` 相关测试。

## 6. 前端：策略管理页面

- [x] 6.1 新增 `frontend/src/api/abacPolicy.ts`：按后端接口封装分页查询、详情、
  新增、编辑、启用、停用、删除请求方法，类型定义放 `frontend/src/types/`，字段
  命名与后端 DTO 对齐。
- [x] 6.2 新增 `frontend/src/views/permission/AbacPolicyManagementView.vue`：
  列表（展示名称、备注、启用状态、显示序号、目标资源编码、操作列）+ 新增/编辑
  弹窗（资源编码多选、用户属性条件动态表单复用元数据字段选择器、时间窗口动态
  表单）+ 启用/停用/删除交互，参照 `AppAccessManagement` 相关页面的既有交互模式；
  按钮级权限门控对齐新增的 `AbacManagement:policy:*` 权限点。
- [x] 6.3 在 `frontend/src/router/index.ts` 新增路由 `/permission/abac-policy`
  （`permissionKey: 'AbacManagement:policy:view'`），`frontend/src/router/menu.ts`
  权限管理分组下新增对应菜单项；在 `frontend/src/utils/permissionTree.ts` 的
  `PERMISSION_MODULE_LABELS` 补充 `AbacManagement` → 中文名映射（与
  `权限资源.txt` 登记的中文名一致）。
- [x] 6.4 执行 `npm run build` 确认类型检查与构建通过（已通过）；启动 `npm run
  dev` 确认开发服务器可正常启动、页面无编译期报错。未能完成连后端的完整手动
  操作流程验证（新增/编辑/启用/停用/删除 + 权限可见性）——本次实现环境缺少项目
  要求的 JDK 21（仅有 JDK 8/11），`./gradlew bootRun` 因
  `org.gradle.java.home` 指向的 JDK 21 路径不存在而无法启动后端，如实记录，
  未编造验证结果。

## 7. OpenSpec 文档同步

- [x] 7.1 实现完成后，基于真实 diff 与测试结果核对 `proposal.md`/`design.md`/
  `tasks.md` 与实际实现是否一致，如有偏差（如最终迁移脚本版本号、实际新增的
  Mapper 方法签名等实现细节）据实更新文档。已核对：迁移脚本最终版本号为 `V9`；
  `AbacPolicyStatus` 实际只有启用/停用两个状态码（见 2.2 更新）；`design.md`
  Decision 3 提到的用户属性判定组件类名已更正为实际类名
  `AbacUserAttrEvaluator`；`design.md` Decision 6 补充了前端目标资源编码多选
  控件的实际数据来源与过滤条件；`proposal.md` What Changes/Impact 按最终落地的
  效果模型与包结构核对更新；`specs/abac-policy-engine/spec.md` 时间窗口相关
  Scenario 示例文字按 `LocalTime` 实际可表示范围更正为 `23:59:59`（不是行为
  变更，仅示例文字澄清）。
