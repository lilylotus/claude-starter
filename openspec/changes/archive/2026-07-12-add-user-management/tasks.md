## 0. 前置依赖

- [x] 0.1 确认 `add-dict-management` change 已实现并通过验证，`position_type` 字典类型（主职/兼职/挂职）种子数据已在数据库中存在

## 1. 后端用户模块

- [x] 1.1 编写 Flyway 迁移脚本建表 `tab_user`（id/name/code/gender/mobile/id_card/show_order/remark/status/审计字段）、`tab_user_position`（id/user_id/org_id/position_type/position_address/position_phone/show_order/remark/审计字段，无独立 status 列）
- [x] 1.2 新增 `UserEntity`、`UserPositionEntity`（MyBatis-Plus 实体）与 `UserStatus`（2000 启用/3000 停用/-1000 逻辑删除）、`UserGender`（0 未知/1 男/2 女）常量类
- [x] 1.3 新增 `UserMapper`、`UserPositionMapper`（MyBatis-Plus）
- [x] 1.4 新增 DTO/VO：`UserCreateRequest`、`UserUpdateRequest`、`UserVO`、`UserPositionRequest`（`id` 可选）、`UserPositionVO`（含回填的 `orgName`）
- [x] 1.5 新增 `UserConvert`（MapStruct），实体与各 DTO/VO 互转
- [x] 1.6 实现 `UserService`/`UserServiceImpl`：
  - [x] 1.6.1 分页查询（按 name/mobile/idCard 模糊搜索组合）
  - [x] 1.6.2 详情查询（回填任职记录列表及各自的 `orgName`）
  - [x] 1.6.3 创建（编号/身份证号唯一性校验，含 `positions` 一并创建）
  - [x] 1.6.4 更新（编号/身份证号唯一性校验排除自身；`positions` 按 id 是否存在做 diff：existing 更新保留创建审计、新增插入、缺失的物理删除；请求携带不属于当前用户的任职记录 id 时拒绝）
  - [x] 1.6.5 启用/停用
  - [x] 1.6.6 逻辑删除（不级联处理任职记录）
- [x] 1.7 实现 `UserController`：`GET /api/users`、`GET /api/users/{id}`、`POST /api/users`、`PUT /api/users/{id}`、`PUT /api/users/{id}/enable`、`PUT /api/users/{id}/disable`、`DELETE /api/users/{id}`，附 springdoc `@Tag`/`@Operation` 注解
- [x] 1.8 编写 `UserServiceImplTest` 单元测试（共 11 个用例）：编号/身份证号重复拒绝创建与更新、任职记录 diff（更新保留创建审计/新增/删除/清空）、更新携带他人任职记录 id 时拒绝、模糊搜索组合条件、查询不存在用户抛异常等关键场景。其中"组合条件搜索"用例（`getPage_shouldReturnPageResult_whenCombiningNameAndMobileConditions`）未按反射方式断言拼出的 SQL 片段，因为脱离 Spring 容器管理的 `LambdaQueryWrapper.getSqlSegment()` 在纯 Mockito 单元测试上下文中会抛出 `MybatisPlusException: can not find lambda cache`；改为直接验证组合搜索场景下 `getPage` 正常执行并返回预期分页结果，具体的"与"关系由 `LambdaQueryWrapper` 依次叠加 `like` 条件的既有默认行为保证，不影响功能正确性，纯粹是单元测试写法上的取舍
- [x] 1.9 `./gradlew build` 全量通过

## 2. 前端用户管理页面

- [x] 2.1 新增 `src/types/user.ts`：`UserRow`、`UserDetail`、`UserPositionRow`、`UserFormRequest` 类型及状态/性别常量，字段与后端 DTO 对齐
- [x] 2.2 新增 `src/api/user.ts`，封装用户分页/详情/创建/更新/启用/停用/删除接口
- [x] 2.3 新增 `src/stores/user.ts`（Pinia setup store）：用户分页列表、搜索条件、loading 状态
- [x] 2.4 新增 `src/views/identity/user/UserManagementView.vue`：搜索栏（姓名/手机号/身份证号）+ 分页表格，行内操作（编辑/启用停用/删除/详情）
- [x] 2.5 新增/编辑弹窗：基本字段表单 + 任职信息行内可增删子表单（组织选择器复用组织管理页面已有的 `el-tree-select` 模式，认证类型下拉框调用字典模块 `src/api/dict.ts` 的只读查询接口按 `typeCode=position_type` 获取选项）
- [x] 2.6 详情弹窗：只读展示用户完整信息（含审计字段）及全部任职记录
- [x] 2.7 `router/menu.ts` 中 `identity` 分组的"用户管理"菜单项调整到"组织管理"之后；`router/index.ts` 中 `/identity/users` 路由从 `PlaceholderView.vue` 改为指向 `UserManagementView.vue`
- [x] 2.8 `npm run build`（vue-tsc 类型检查 + vite build）全量通过，无类型错误

## 3. 验证

- [x] 3.1 本地 `./gradlew bootRun` 触发 Flyway 迁移，核对 `tab_user`/`tab_user_position` 表结构；对用户的增/改/启用/停用/删除/详情/分页搜索接口，以及任职记录的新增/编辑/删除 diff 逻辑做端到端冒烟测试（curl 全量覆盖：创建含 2 条任职记录、更新时保留一条的创建审计+新增一条+物理删除一条、更新携带他人任职记录 id 被拒绝、编号重复拒绝创建、启用/停用、按 name/mobile/idCard 单独与组合搜索、删除后不再出现在列表中，均符合预期；验证后已清理全部测试数据）
- [ ] 3.2 浏览器中验证用户管理页面：列表分页与三种搜索条件、新增用户含多条任职记录、编辑时增删任职记录、详情展示、启停用/删除操作、菜单顺序（用户管理在组织管理之后）（当前会话工具集里没有浏览器自动化能力，只验证了 `npm run dev` 正常启动、`/api` 代理到组织树接口和字典只读接口均正常，未做真实浏览器点击验证，需要用户或后续会话手动在浏览器里走查一遍）
