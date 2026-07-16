## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V7__init_tab_app.sql`：新建 `tab_app` 表（`id`/`name`/`code`/`owner_id`/`org_id`/`show_order`/`remark`/`status`/审计字段），`status` 默认 `2000`，为 `status`/`owner_id`/`org_id`/`code` 加索引
- [x] 1.2（原计划外，用户反馈后补充）`V7` 直接补充 `code` 字段并加 `idx_tab_app_code` 索引（该文件当时尚未提交到 git、只在本机开发库跑过一次迁移，因此选择直接编辑原文件而非另发 `V8`）；本机开发库执行 `DROP TABLE tab_app` + 删除 `flyway_schema_history` 里 `version=7` 记录，重启后端后 Flyway 用新校验和重新记录该迁移，验证通过

## 2. 后端：应用主数据基础

- [x] 2.1 新增 `cn.nihility.rbac.app.constant.AppStatus`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`），风格对齐 `OrgStatus`/`UserStatus`/`PositionStatus`
- [x] 2.2 新增 `cn.nihility.rbac.app.entity.AppEntity`（对应 `tab_app`，字段：`id`/`name`/`code`/`ownerId`/`orgId`/`showOrder`/`remark`/`status`/审计字段）
- [x] 2.3 新增 `cn.nihility.rbac.app.mapper.AppMapper`（MyBatis-Plus `BaseMapper<AppEntity>`）

## 3. 后端：应用管理接口

- [x] 3.1 新增 `AppVO`（含 `id`/`name`/`code`/`ownerId`/`ownerName`/`orgId`/`orgName`/`showOrder`/`remark`/`status`/审计字段）
- [x] 3.2 新增 `AppCreateRequest`（`name`/`code`/`ownerId`/`orgId` 必填，`showOrder` 默认 `0`，`remark` 可选）
- [x] 3.3 新增 `AppUpdateRequest`（不含 `status`；`name`/`code`/`ownerId`/`orgId` 必填，`showOrder`/`remark` 可选）
- [x] 3.4 新增 `AppConvert`（MapStruct，静态单例风格同 `PositionConvert`：`Xxx INSTANCE = Mappers.getMapper(Xxx.class)`，不用 `componentModel = "spring"`），提供 entity↔VO/Request 的转换方法
- [x] 3.5 新增 `AppService`/`AppServiceImpl`：
  - `getPage(page, pageSize)`：分页查询未删除应用，按 `showOrder` 降序、`id` 升序排序，批量 join `tab_user`/`tab_org` 回填 `ownerName`/`orgName`
  - `getById(id)`：查询未删除记录详情（含 `ownerName`/`orgName`）
  - `create(request)`：新增，`status` 显式置为 `ENABLED`
  - `update(id, request)`：更新除 `status` 外的字段
  - `enable(id)`/`disable(id)`：状态切换
  - `delete(id)`：逻辑删除（置 `status = DELETED`）
- [x] 3.6 新增 `AppController`：`GET /api/apps`（仅 `page`/`pageSize`）、`GET /api/apps/{id}`、`POST /api/apps`、`PUT /api/apps/{id}`、`PUT /api/apps/{id}/enable`、`PUT /api/apps/{id}/disable`、`DELETE /api/apps/{id}`，均加 springdoc `@Tag`/`@Operation` 注解
- [x] 3.7（原计划外，补充）新增 `AppServiceImplTest`（`backend/src/test/java/cn/nihility/rbac/app/service/impl/`），风格对齐既有的 `PositionServiceImplTest`：覆盖分页查询、新增默认启用、更新不改状态、启停用、逻辑删除、查询不存在/已删除记录抛业务异常等分支
- [x] 3.8（原计划外，用户反馈后补充）`AppServiceImpl` 新增私有方法 `checkCodeUnique(code, excludeId)`，直接照抄 `OrgServiceImpl.checkCodeUnique` 的写法：按 `code` + `status != DELETED` 查询，`excludeId` 非空时额外 `ne(id, excludeId)`；`create`/`update` 分别在写库前调用（`create` 传 `excludeId = null`，`update` 传当前 `id`），冲突时抛 `BusinessException("应用编码[code]已存在")`；`AppServiceImplTest` 新增 `create_shouldThrowBusinessException_whenCodeAlreadyExists`、`update_shouldThrowBusinessException_whenCodeUsedByAnotherApp` 两个用例

## 4. 前端：应用管理页面

- [x] 4.1 新增 `frontend/src/types/app.ts`（`AppRow`、`AppFormRequest`、状态常量 `APP_STATUS_ENABLED`/`APP_STATUS_DISABLED`，对齐后端 DTO 字段命名；含 `code` 字段）
- [x] 4.2 新增 `frontend/src/api/app.ts`（`getAppPage(page, pageSize)`、`getAppById`、`createApp`、`updateApp`、`enableApp`、`disableApp`、`deleteApp`）
- [x] 4.3 新增 `frontend/src/stores/app.ts`（Pinia，参考 `stores/position.ts` 里分页列表部分：当前页、每页条数、总数、列表数据、加载状态，`refreshAfterMutation` 在操作后刷新当前页并在页码超出总页数时回退到最后一页）
- [x] 4.4 新增 `frontend/src/views/application/app/AppManagementView.vue`：
  - 顶部无搜索栏，分页表格展示应用名称、应用编码、负责人、所属组织、显示序号、状态、操作
  - 新增/编辑弹窗：应用名称输入框、应用编码输入框（必填）、负责人远程搜索选择器（复用 `GET /api/users?name=`/`?mobile=`，按输入内容是否形如手机号 `^1\d{10}$` 决定传 `mobile` 还是 `name` 参数）、所属组织树选择器（`el-tree-select`，一次性加载 `orgApi.getOrgTree()`）、显示序号数字输入（默认 `0`）、备注多行文本
  - 详情只读弹窗（含应用编码）、启用/停用/删除行内操作（删除二次确认）
  - 编辑弹窗打开时用 `getAppById` 回显的 `ownerName` 现场拼一条负责人下拉选项（原计划未细化此点），避免刚打开编辑弹窗时下拉框因远程搜索结果为空而显示不出已选中的负责人姓名
- [x] 4.5 `frontend/src/router/index.ts` 的 `implementedComponents` 新增 `/application/list` 指向 `AppManagementView.vue`（替换默认的 `PlaceholderView` fallback）
- [x] 4.6 `frontend/src/router/index.ts` 的 `stubDescriptions` 移除 `/application/list` 对应条目（该路径已有真实业务组件，不再需要占位文案）
- [x] 4.7（原计划外，用户反馈后补充）`frontend/src/router/menu.ts` 里 `/application/list` 子菜单文案由"应用列表"改为"应用管理"，同步调整 `AppManagementView.vue` 面板标题（`<h2>`）保持一致；改后侧边栏呈现"应用管理 → 应用管理"（子菜单与其所属一级菜单组同名），是用户明确要求的结果

## 5. 验证

- [x] 5.1 `./gradlew test`（`backend/` 目录）：新增的 `AppServiceImplTest` 及全部现有测试通过
- [x] 5.2 `npm run build`（`frontend/` 目录）：vue-tsc 类型检查 + vite build 通过
- [x] 5.3 API 级验证（本地起 `bootRun`，`curl` 直接调用）：完整走过新增（含选负责人 `ownerId`、选组织 `orgId`）、详情、编辑（改名称/负责人/组织/显示序号/备注）、停用、启用、删除全流程；确认按 `showOrder` 降序分页展示（`showOrder=10` 的记录排在 `showOrder=5` 之前）；删除后详情正确返回 `{"code":400,"message":"应用不存在"}` 业务错误而非 HTTP 500；必填字段缺失时返回参数校验错误
- [x] 5.4（原计划外，补充）前端启动 `npm run dev` 并确认页面可正常加载（HTTP 200）；受限于当前环境没有浏览器自动化工具，未做逐项点击的可视化 UI 验证——仅完成类型检查级别（5.2）与后端 API 级别（5.3）的验证，UI 交互的最终确认建议由使用者在浏览器中手动过一遍新增/编辑/详情/启停用/删除流程
- [x] 5.5（原计划外，用户反馈后补充，`code` 字段专项验证）重新 `./gradlew test`（含新增的两条编码唯一性用例）与 `npm run build` 均通过；重启本地 `bootRun` 后用 `curl` 验证：创建带 `code` 的应用成功；创建重复 `code` 返回 `{"code":400,"message":"应用编码[app001]已存在"}`；创建不带 `code` 返回 `{"code":400,"message":"应用编码不能为空"}`；把另一应用的 `code` 改成已被占用的值返回同样的冲突错误；把某应用更新为它自身当前的 `code` 能正常成功（确认 `excludeId` 自排除逻辑生效）
