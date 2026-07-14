## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V6__add_status_to_tab_user_position.sql`：为 `tab_user_position` 增加 `status` 列（`INT NOT NULL DEFAULT 2000`），并为其加索引

## 2. 后端：任职记录状态常量与实体调整

- [x] 2.1 新增 `cn.nihility.rbac.user.constant.PositionStatus`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`），风格对齐 `OrgStatus`/`UserStatus`
- [x] 2.2 在 `UserPositionEntity` 新增 `status` 字段，更新类注释（不再是"无独立 status 列、物理删除"）

## 3. 后端：任职管理独立接口

- [x] 3.1 新增 `PositionVO`（含 `id`/`userId`/`userName`/`orgId`/`orgName`/`positionType`/`positionAddress`/`positionPhone`/`showOrder`/`remark`/`status`/审计字段）
- [x] 3.2 新增 `PositionCreateRequest`（`userId`/`orgId`/`positionType` 必填，其余可选，`showOrder` 默认 `0`）
- [x] 3.3 新增 `PositionUpdateRequest`（不含 `userId`；`orgId`/`positionType` 必填，其余可选）
- [x] 3.4 新增 `PositionConvert`（MapStruct，静态单例风格同 `UserConvert`），提供 entity↔VO/Request 的转换方法
- [x] 3.5 新增 `PositionService`/`PositionServiceImpl`：
  - `getPage(orgId, page, pageSize)`：按 `orgId` 必填分页查询未删除记录，批量回填 `userName`/`orgName`
  - `getById(id)`：查询未删除记录详情
  - `create(request)`：新增，`status` 显式置为 `ENABLED`
  - `update(id, request)`：更新除 `userId`/`status` 外的字段
  - `enable(id)`/`disable(id)`：状态切换
  - `delete(id)`：逻辑删除（置 `status = DELETED`）
- [x] 3.6 新增 `PositionController`：`GET /api/positions`（`orgId` 必填）、`GET /api/positions/{id}`、`POST /api/positions`、`PUT /api/positions/{id}`、`PUT /api/positions/{id}/enable`、`PUT /api/positions/{id}/disable`、`DELETE /api/positions/{id}`，均加 springdoc `@Tag`/`@Operation` 注解

## 4. 后端：用户管理任职查询调整

- [x] 4.1 `UserServiceImpl.listPositionsWithOrgName` 查询条件增加 `ne(status, PositionStatus.DELETED)`
- [x] 4.2 `UserServiceImpl.syncPositions` 中查询"既有任职记录"作为 diff 基准时增加 `ne(status, PositionStatus.DELETED)` 过滤
- [x] 4.3 `UserServiceImpl.syncPositions` 新增记录分支显式 `entity.setStatus(PositionStatus.ENABLED)`

## 5. 前端：任职管理页面

- [x] 5.1 新增 `frontend/src/types/position.ts`（`PositionRow`、`PositionFormRequest`、状态常量 `POSITION_STATUS_ENABLED`/`POSITION_STATUS_DISABLED`，对齐后端 DTO 字段命名）
- [x] 5.2 新增 `frontend/src/api/position.ts`（`getPositionPage(orgId, page, pageSize)`、`getPositionById`、`createPosition`、`updatePosition`、`enablePosition`、`disablePosition`、`deletePosition`）
- [x] 5.3 新增 `frontend/src/stores/position.ts`（Pinia，参考 `stores/org.ts`：左侧懒加载组织树状态 + 当前选中组织 id + 右侧任职分页列表状态）
- [x] 5.4 新增 `frontend/src/views/identity/position/PositionManagementView.vue`：
  - 左侧组织树（复用 `OrgManagementView.vue` 的懒加载写法，默认全部收起）
  - 右侧任职记录分页表格，未选中组织节点时展示空状态提示
  - 新增/编辑弹窗：用户远程搜索选择器（复用 `GET /api/users?name=` 分页接口）、组织选择器（编辑时可改）、任职类型下拉（复用 `dictApi.getDictItemOptions('position_type')`）、任职地址/电话/显示序号/备注
  - 编辑弹窗中所属用户只读展示
  - 详情只读弹窗、启用/停用/删除行内操作
- [x] 5.5 `frontend/src/router/menu.ts` 的 `identity` 分组新增菜单项（`{ title: '任职管理', path: '/identity/positions', permissionKey: 'identity:position:view' }`）
- [x] 5.6 `frontend/src/router/index.ts` 的 `implementedComponents` 登记 `/identity/positions` 指向新页面

## 6. 验证

- [x] 6.1 `./gradlew test`（`backend/` 目录）确认现有用户管理测试仍通过
- [x] 6.2 `npm run build`（`frontend/` 目录）确认类型检查通过
- [x] 6.3 API 级验证（无浏览器自动化工具，未走前端 UI 点击）：对本地运行的后端直接调用 `POST/GET/PUT/DELETE /api/positions*`，完整走过新增、详情、编辑（含改组织）、停用、启用、删除全流程，返回数据符合预期，删除后详情正确返回"任职记录不存在"业务错误
- [x] 6.4 API 级验证：`GET /api/orgs/tree` 之后按其中一个组织 id 查询任职记录，确认此前通过用户管理内嵌任职子表单新增的既有记录（`status = 2000`）在任职管理接口中可见；发现一个既有测试数据关联的用户已被逻辑删除，其任职记录仍正常显示（`userName` 通过 join 解析出来），确认这是符合 user-management 现有"逻辑删除用户不级联删除任职记录"规则的预期行为，不是缺陷

验证过程中发现一个小问题并已修复：`PositionController.page` 的 `orgId` 原先声明为 `@RequestParam Long orgId`（必填），导致完全不带该参数时 Spring 在进入方法体前就抛 `MissingServletRequestParameterException`，被全局异常处理器兜底为通用的"服务器内部错误"，`PositionServiceImpl.getPage` 里"所属组织不能为空"的校验分支实际不可达。已改为 `@RequestParam(required = false)`，让该校验分支生效，重新验证后 `GET /api/positions`（不带 `orgId`）正确返回 `{"code":400,"message":"所属组织不能为空"}`。
