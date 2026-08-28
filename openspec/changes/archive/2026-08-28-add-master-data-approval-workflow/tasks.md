## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移脚本，创建 `tab_approval_request` 表（字段见 design.md Decision 1：`biz_type`/`operation_type`/`target_id`/`result_target_id`/`request_payload`/`status`/`approver_id`/`approve_time`/`opinion`/`flowable_process_instance_id`/`flowable_task_id`/`create_by`/`create_time`/`update_by`/`update_time`），字段命名检查与数据库关键字无冲突；验证：`./gradlew bootRun` 启动时迁移无报错，`DESCRIBE tab_approval_request;` 字段齐全
- [x] 1.2 同一或另一条迁移脚本，创建 `tab_approval_switch` 表（`biz_type` 唯一、`enabled`、审计字段），并 `INSERT` 四条记录（ORG/USER/POSITION/APP）（design.md Decision 9）；验证：`SELECT * FROM tab_approval_switch;` 有且仅有四条记录
- [x] 1.3 新增 `ApprovalRequestStatus` 常量类（`PENDING=1000`/`APPROVED=2000`/`REJECTED=3000`/`CANCELLED=4000`），`ApprovalOperationType` 常量类（`CREATE`/`UPDATE`/`ENABLE`/`DISABLE`/`DELETE`）；验证：编译通过
- [x] 1.4 新增 `V10__set_approval_switch_default_disabled.sql`：将 `tab_approval_switch` 表 `enabled` 列的默认值与四条既有记录统一更新为 `0`（关闭），使系统初始化后默认不对任何 `bizType` 走审批，需管理员在"审批设置"页面手动开启（design.md Decision 9 / Migration Plan）；`V8__add_master_data_approval_workflow.sql` 已在部分环境执行过，不可直接修改其 INSERT 语句，本任务改用新迁移覆盖默认值；验证：`./gradlew bootRun` 启动时迁移无报错，`SELECT biz_type, enabled FROM tab_approval_switch;` 四条记录均为 `enabled=0`

## 2. Flowable 引擎集成

- [x] 2.1 确认 `backend/build.gradle` 已声明的 Flowable 7.2.0 依赖版本与 Spring Boot 3.5 兼容（先跑一次 `./gradlew build` 观察是否有版本冲突/类加载报错，如有冲突需要先与用户确认调整版本，不擅自改动 `build.gradle` 声明的其他依赖）
- [x] 2.2 `application.yml` 新增 Flowable 配置：`flowable.database-schema-update: true`（自动建表，design.md Decision 4）；确认 `flowable-spring-boot-starter-rest` 自动注册的路径不与 `IdentityAuthFilter` 现有白名单/鉴权机制冲突，如有冲突通过配置关闭其自动路由（`flowable.rest.app.enabled` 或等效配置项，以实际可用配置为准）；关闭 Modeler UI 的匿名访问或限制其可访问范围；验证：`./gradlew bootRun` 启动日志确认 Flowable 自动建表成功（`ACT_*` 系列表），且现有接口鉴权行为不受影响（跑一次现有集成测试确认无回归）
- [x] 2.3 新增 `src/main/resources/processes/approval-process.bpmn20.xml`：开始事件 → 用户任务"审批"（不设置候选组，见 design.md Decision 3）→ 排他网关按 `approved` 流程变量分流 → "已通过"/"已拒绝"两个结束事件；验证：应用启动日志确认该流程定义被自动部署（`RepositoryService` 能查到该流程定义）
- [x] 2.4 新增 `ApprovalProcessService`（或类似命名）封装 Flowable `RuntimeService`/`TaskService` 调用：启动流程实例（携带 `requestId` 流程变量）、按 `taskId` 完成用户任务（设置 `approved` 变量）、终止流程实例（供撤回使用）；验证：单元测试覆盖启动流程实例后能查询到对应的运行时任务

## 3. 后端：approval 审批模块

- [x] 3.1 创建 `cn.nihility.rbac.approval` 包骨架（`entity`/`dto`/`mapper`/`service`/`service.impl`/`controller`/`mapstruct`/`constant`），新增 `ApprovalRequestEntity`（MyBatis-Plus `BaseMapper` 数据访问对象，对应 `tab_approval_request`）与 `ApprovalSwitchEntity`（对应 `tab_approval_switch`）
- [x] 3.2 新增 `ApprovalRequestMapper`、`ApprovalSwitchMapper`（均为 `BaseMapper<T>`）
- [x] 3.3 新增 `ApprovalSwitchService`：查询四个 `bizType` 的开关状态（需要 `ApprovalManagement:switch:view` 权限点）、修改指定 `bizType` 开关状态（需要 `ApprovalManagement:switch:edit` 权限点，复用操作日志记录本次变更）；验证：单元测试覆盖查询、修改成功、无权限修改被拒绝三种场景
- [x] 3.4 新增通用的 `WriteOperationResultVO<T>`（`approvalEnabled`/`approvalRequest`/`data` 三个字段，design.md Decision 9）
- [x] 3.5 新增提交审批申请的 DTO 与 Service 方法：`ApprovalRequestService.submit` 仅处理已启用审批的场景，按 `bizType`/`operationType` 分发并执行结构校验（`@Valid`，复用各模块既有的 `XxxCreateRequest`/`XxxUpdateRequest`）与管辖组织范围校验（复用 `OrgScopeService.isOrgIdAllowed`，USER 不做管辖范围校验），序列化 `requestPayload`（JSON），启动 Flowable 流程实例，写入 `tab_approval_request`，返回 `WriteOperationResultVO{approvalEnabled=true, approvalRequest=申请信息}`；不得在该 Service 内判断开关并通过通用 `executeWrite` 执行关闭审批时的原业务逻辑；验证：四个 `bizType` × 五种 `operationType` 组合的提交单元测试通过（含管辖范围拒绝、结构校验拒绝、提交时不做业务规则校验的正向用例）
- [x] 3.6 新增审批通过 Service 方法：仅 `ApprovalManagement:request:approve` 权限点持有者可调用（Controller 层由 `menu` 请求头机制保证，Service 层不重复判断）；以提交人身份（`CurrentUserContext` 临时切换为 `create_by` 对应用户 id，方法返回后恢复）重新执行管辖组织范围校验；反序列化 `requestPayload`，调用对应模块既有的 `create`/`update`/`enable`/`disable`/`delete` 方法；成功后更新申请状态为"已通过"、回填 `resultTargetId`（仅 CREATE）、完成 Flowable 用户任务；业务方法抛出异常时不修改申请状态（保持"待审批"），异常原样抛出给调用方；验证：单元测试覆盖每个 `bizType` 的成功路径、业务规则校验失败路径（如编码重复）、提交人管辖范围事后收紧导致失败的路径
- [x] 3.7 新增审批拒绝 Service 方法：校验拒绝意见非空，更新申请状态为"已拒绝"并记录审批人/意见，完成 Flowable 用户任务，不执行任何业务方法；验证：单元测试覆盖拒绝成功、意见为空被拒绝两种场景
- [x] 3.8 新增撤回 Service 方法：仅提交人本人可撤回状态为"待审批"的申请，终止对应 Flowable 流程实例；验证：单元测试覆盖提交人撤回成功、非提交人撤回被拒绝、已处理申请撤回被拒绝三种场景
- [x] 3.9 新增查询 Service 方法：分页查询"我的申请"（按当前用户 `create_by` 过滤）与"待我审批"（`status=PENDING`，仅权限点持有者可调用），支持按 `bizType`/`operationType`/`status` 过滤；UPDATE 类型申请的详情查询需要同时返回目标记录当前值（调用对应模块既有的详情查询方法）与 `requestPayload` 新值；验证：单元测试覆盖两个查询接口的过滤条件与权限校验
- [x] 3.10 新增 `ApprovalRequestController`：`POST /api/approval-requests`（提交，`bizType`/`operationType` 作为参数或路径的一部分，具体路由风格与项目现有多资源接口保持一致）、`POST /api/approval-requests/{id}/approve`、`POST /api/approval-requests/{id}/reject`、`POST /api/approval-requests/{id}/cancel`、`GET /api/approval-requests/mine`、`GET /api/approval-requests/pending`；补充 springdoc 注解（`@Tag`/`@Operation`/`@Parameter`）；验证：集成测试覆盖全部接口的正常路径与鉴权拒绝路径
- [x] 3.11 新增 `ApprovalSwitchController`：`GET /api/approval-switches`（查询四个 `bizType` 状态）、`PUT /api/approval-switches/{bizType}`（修改指定 `bizType` 状态）；补充 springdoc 注解；验证：集成测试覆盖查询、修改成功、无权限修改被拒绝

## 4. 后端：改造组织/用户/任职/应用四个模块的写接口

- [x] 4.1 `OrgController` 的 `create`/`update`/`enable`/`disable`/`delete` 方法在最外层查询审批开关（`bizType=ORG`）：关闭时直接调用 `OrgService` 原方法，开启时才调用审批提交服务，统一返回 `WriteOperationResultVO<OrgVO>`；验证：开关开启时调用这些接口后 `tab_org` 表不产生变化、`tab_approval_request` 新增一条待审批记录；开关关闭时调用后 `tab_org` 立即变化、不产生审批记录
- [x] 4.2 `UserController` 同上（`bizType=USER`），确认关闭时直接调用 `UserService`，且"更新用户"接口提交审批或直接执行时的请求参数均完整携带 `positions` 数组；验证同上
- [x] 4.3 `PositionController` 同上（`bizType=POSITION`），关闭时直接调用 `PositionService`；验证同上
- [x] 4.4 `AppController` 同上（`bizType=APP`），关闭时直接调用 `AppService`；验证同上
- [x] 4.5 检查四个模块现有的 `create`/`update`/`enable`/`disable`/`delete` Service 方法签名是否可以被审批服务直接复用调用（大概率不需要改动方法签名，只需要确认这些方法可以在非 Controller 上下文中被调用、`CurrentUserContext` 的设置时机正确）；如需要新增方法重载或调整可见性，一并完成；验证：`./gradlew build` 全量编译通过
- [x] 4.6 运行四个模块现有的完整单元测试与集成测试套件，确认本次改造没有破坏既有的业务规则校验、管辖组织范围校验、操作日志记录等既有行为（开关开启场景下，这些逻辑应该原封不动地被审批通过流程复用；开关关闭场景下的测试应断言与本 change 之前完全一致的"调用即生效"行为，且与默认关闭的初始状态一致）；验证：`./gradlew test --tests "cn.nihility.rbac.org.*" --tests "cn.nihility.rbac.user.*" --tests "cn.nihility.rbac.app.*"` 全部通过

## 5. 权限点

- [x] 5.1 新增四个权限点 `ApprovalManagement:request:view`（查看我的申请/待我审批）、`ApprovalManagement:request:approve`（审批通过/拒绝）、`ApprovalManagement:switch:view`（查看审批开关）、`ApprovalManagement:switch:edit`（修改审批开关），按项目现有权限点登记方式（`tab_menu` + `tab_permission` 迁移脚本，参考 excel 导出功能新增权限点时的写法）登记，并为 `SUPER_ADMIN` 角色补授；验证：登录后 `GET /api/auth/permissions` 返回结果包含新增编码
- [x] 5.2 更新仓库根目录 `权限资源.txt`，新增 `ApprovalManagement`（审批管理）模块分组及四条权限记录；验证：`git diff 权限资源.txt` 格式与既有条目一致

## 6. 前端：审批相关页面

- [x] 6.1 新增 `frontend/src/api/approval.ts`：封装提交/审批通过/拒绝/撤回/我的申请/待我审批六个接口调用；新增 `frontend/src/api/approvalSwitch.ts`：封装查询/修改开关两个接口调用
- [x] 6.2 新增"我的申请"页面（路径如 `/approval/mine`）：列表展示当前用户提交的全部申请，按状态筛选，可撤回"待审批"状态的申请，点击可查看申请详情（新增类展示新值，编辑类展示新旧对照，复用 `form-field-definition-management` 的渲染元数据接口做字段展示名映射，见 design.md Decision 8）
- [x] 6.3 新增"待我审批"页面（路径如 `/approval/pending`），`v-if`/路由级权限点门控 `ApprovalManagement:request:approve`：列表展示全部待审批申请，可查看详情、批准、拒绝（拒绝需填写意见）
- [x] 6.4 新增"审批设置"页面（路径如 `/approval/settings`），`v-if`/路由级权限点门控 `ApprovalManagement:switch:view`：展示组织/用户/任职/应用四个开关，切换操作 `v-if` 门控 `ApprovalManagement:switch:edit`
- [x] 6.5 更新 `router/menu.ts` 新增"审批管理"分组及三个菜单项（我的申请/待我审批/审批设置），`permissionKey` 分别对应上面的权限点
- [x] 6.6 组织/用户/任职/应用四个管理页面的新增/编辑/启用/停用/删除操作，成功回调按响应的 `approvalEnabled` 分流：为 `true` 时展示"已提交审批，等待审批通过后生效"提示，不乐观更新本地列表；为 `false` 时按响应的 `data` 展示与本 change 之前一致的"创建/更新成功"提示并更新本地列表
- [x] 6.7 更新仓库根目录 `权限资源.txt` 对应的前端按钮权限点覆盖范围说明（如涉及新增按钮级权限点使用位置）
- [x] 6.8 `npm run build` 通过

## 7. 端到端验证

- [x] 7.1 `./gradlew build` 后端整体编译 + 单元测试全部通过
- [x] 7.2 `npm run build` 前端类型检查 + 构建通过
- [x] 7.3 本地启动前后端（应用完 V10 迁移），确认组织的审批开关初始为关闭状态；提交新增组织，确认接口调用后立即在列表中看到新组织，不生成审批申请、不出现在"待我审批"列表
- [x] 7.4 在"审批设置"页面手动开启组织的审批开关后完整走一遍：提交新增组织 → 组织列表不出现新组织 → 用拥有 `ApprovalManagement:request:approve` 权限的账号在"待我审批"看到该申请 → 批准 → 组织列表出现新组织，操作日志记录的操作人为提交人；再走一遍拒绝流程，确认组织不产生；随后重新关闭开关，确认恢复为提交即生效
- [x] 7.5 验证用户更新（含任职记录整体同步）审批通过后，任职记录的新增/更新/物理删除行为与改造前直接调用 `PUT /api/users/{id}` 一致
- [x] 7.6 验证批量导入（Excel 导入组织/用户/任职/应用）仍然直接生效，不产生审批申请，不受本次改动影响（含开关开启/关闭两种状态下均如此）
