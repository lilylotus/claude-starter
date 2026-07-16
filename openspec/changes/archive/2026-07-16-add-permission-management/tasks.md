## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V9__init_tab_permission.sql`：新建 `tab_permission` 表（`id`/`name`/`code`/`show_order`/`remark`/`status`/审计字段），`status` 默认 `2000`，为 `status`/`code` 加索引；列名已核对不与 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突

## 2. 后端：权限点主数据基础

- [x] 2.1 新增 `cn.nihility.rbac.permission.constant.PermissionStatus`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`），风格对齐 `OrgStatus`/`UserStatus`/`DictStatus`/`PositionStatus`/`AppStatus`/`RoleStatus`
- [x] 2.2 新增 `cn.nihility.rbac.permission.entity.PermissionEntity`（对应 `tab_permission`，字段：`id`/`name`/`code`/`showOrder`/`remark`/`status`/审计字段）
- [x] 2.3 新增 `cn.nihility.rbac.permission.mapper.PermissionMapper`（MyBatis-Plus `BaseMapper<PermissionEntity>`）

## 3. 后端：权限管理接口

- [x] 3.1 新增 `PermissionVO`（含 `id`/`name`/`code`/`showOrder`/`remark`/`status`/审计字段）
- [x] 3.2 新增 `PermissionCreateRequest`（`name`/`code` 必填，`showOrder` 默认 `0`，`remark` 可选）
- [x] 3.3 新增 `PermissionUpdateRequest`（不含 `status`；`name`/`code` 必填，`showOrder`/`remark` 可选）
- [x] 3.4 新增 `PermissionConvert`（MapStruct，静态单例风格同 `RoleConvert`：`Xxx INSTANCE = Mappers.getMapper(Xxx.class)`，不用 `componentModel = "spring"`），提供 entity↔VO/Request 的转换方法
- [x] 3.5 新增 `PermissionService`/`PermissionServiceImpl`（结构照抄 `RoleServiceImpl`，无需 join 回填任何名称）：
  - `getPage(page, pageSize)`：分页查询未删除权限点，按 `showOrder` 降序、`id` 升序排序
  - `getById(id)`：查询未删除记录详情
  - `create(request)`：校验编码唯一后新增，`status` 显式置为 `ENABLED`
  - `update(id, request)`：校验编码唯一（排除自身）后更新除 `status` 外的字段
  - `enable(id)`/`disable(id)`：状态切换
  - `delete(id)`：逻辑删除（置 `status = DELETED`），不做下游关联数据阻塞校验
  - `checkCodeUnique(code, excludeId)`：私有方法，直接照抄 `RoleServiceImpl.checkCodeUnique` 写法
- [x] 3.6 新增 `PermissionController`：`GET /api/permissions`（仅 `page`/`pageSize`）、`GET /api/permissions/{id}`、`POST /api/permissions`、`PUT /api/permissions/{id}`、`PUT /api/permissions/{id}/enable`、`PUT /api/permissions/{id}/disable`、`DELETE /api/permissions/{id}`，均加 springdoc `@Tag`/`@Operation` 注解
- [x] 3.7 新增 `PermissionServiceImplTest`（`backend/src/test/java/cn/nihility/rbac/permission/service/impl/`），风格对齐 `RoleServiceImplTest`：覆盖分页查询、新增默认启用、新增编码唯一性校验、更新不改状态、更新编码唯一性校验（含自排除）、启停用、逻辑删除、查询不存在/已删除记录抛业务异常等分支

## 4. 前端：权限管理页面

- [x] 4.1 新增 `frontend/src/types/permission.ts`（`PermissionRow`、`PermissionFormRequest`、状态常量 `PERMISSION_STATUS_ENABLED`/`PERMISSION_STATUS_DISABLED`，对齐后端 DTO 字段命名）
- [x] 4.2 新增 `frontend/src/api/permission.ts`（`getPermissionPage(page, pageSize)`、`getPermissionById`、`createPermission`、`updatePermission`、`enablePermission`、`disablePermission`、`deletePermission`）
- [x] 4.3 新增 `frontend/src/stores/permission.ts`（Pinia，参考 `stores/role.ts`：当前页、每页条数、总数、列表数据、加载状态，`refreshAfterMutation` 在操作后刷新当前页并在页码超出总页数时回退到最后一页）
- [x] 4.4 新增 `frontend/src/views/permission/permission/PermissionManagementView.vue`：
  - 顶部无搜索栏，分页表格展示权限名称、权限编码、备注、状态、显示序号、操作
  - 新增/编辑弹窗：权限名称输入框、权限编码输入框（必填）、显示序号数字输入（默认 `0`）、备注多行文本
  - 详情只读弹窗、启用/停用/删除行内操作（删除二次确认）
- [x] 4.5 `frontend/src/router/index.ts` 的 `implementedComponents` 新增 `/permission/points` 指向 `PermissionManagementView.vue`（替换默认的 `PlaceholderView` fallback）
- [x] 4.6 `frontend/src/router/index.ts` 的 `stubDescriptions` 移除 `/permission/points` 对应条目（该路径已有真实业务组件，不再需要占位文案）

## 5. 验证

- [x] 5.1 `./gradlew test`（`backend/` 目录）：新增的 `PermissionServiceImplTest` 及全部现有测试通过
- [x] 5.2 `npm run build`（`frontend/` 目录）：vue-tsc 类型检查 + vite build 通过
- [x] 5.3 验证前先检查并清理了遗留的旧 `RbacApplication` 进程（本机之前的调试会话残留了 5 个同时占用/竞争 48080 端口的实例），全部终止后用当前代码重新起了单一实例
- [x] 5.4 API 级验证（`curl` 直接调用）：完整走过新增（含重复编码/缺字段的拒绝路径）、详情、编辑（改名称/编码/显示序号/备注，含自身编码不冲突、他人编码冲突两种情形）、停用、启用、删除全流程；确认按 `showOrder` 降序分页展示（`showOrder=10` 排在 `showOrder=8` 之前）；删除后详情正确返回 `{"code":400,"message":"权限点不存在"}` 业务错误而非 HTTP 500；删除后该权限点不再出现在分页列表中
- [x] 5.5（原计划外，补充）前端 dev server（早前会话中已启动，Vite HMR）确认可访问（HTTP 200）；受限于当前环境没有浏览器自动化工具，未做逐项点击的可视化 UI 验证——仅完成类型检查级别（5.2）与后端 API 级别（5.4）的验证
