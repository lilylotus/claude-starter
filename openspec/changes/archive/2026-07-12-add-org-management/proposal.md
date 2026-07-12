## Why

RBAC 系统的"身份管理"一级菜单下，组织管理是身份体系（组织 → 用户 → 角色 → 权限）里最基础的一环，此前前后端都还没有实现，`/identity/orgs` 路由渲染的是 `PlaceholderView.vue` 占位页。本 change 补齐组织的树形维护能力，作为后续用户、角色等模块挂靠组织节点的前提。

（说明：本 change 是补写的回顾性文档——实现先于 OpenSpec 提案完成，此处记录的是实际已交付的内容，而非事后才执行的计划。）

## What Changes

- 新增组织管理后端模块 `cn.nihility.rbac.org`：组织树查询、直属子组织查询、详情查询、创建、更新、启用/停用、逻辑删除（存在未删除子组织时拒绝删除），编码在未删除组织范围内唯一。
- 新增可复用的全局响应基础设施 `cn.nihility.rbac.common`（`Result<T>` 统一响应包装、`GlobalResponseAdvice`、`BusinessException`、`GlobalExceptionHandler`），后续模块可直接复用。
- **BREAKING**：后端基础包名从 `com.example.demo` 整体重命名为 `cn.nihility.rbac`，Gradle `group` 同步改为 `cn.nihility`。
- 引入 Flyway 做数据库版本管理（`tab_` 表名前缀约定），新增迁移脚本建表 `tab_org`。
- 依赖修正：`mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`（原 starter 固定依赖 Spring Boot 2 版本的 `mybatis-spring`，与 Spring Boot 3.5/Spring Framework 6.2 不兼容，会导致 Mapper Bean 注册报错）；`springdoc-openapi-starter-webmvc-ui` 3.0.3 → 2.8.17（3.x 系列目标 Spring Boot 4，与本项目 Spring Boot 3.5 不兼容）。
- 新增前端组织管理页面 `/identity/orgs`：左侧组织树 + 右侧直属子组织表格的主从结构，支持新增/编辑/启用/停用/删除，`router/menu.ts` 中菜单文案由"组织架构"改为"组织管理"。
- 新增前端 `src/api/org.ts`、`src/stores/org.ts`、`src/types/org.ts`，对接上述 7 个后端接口。

## Capabilities

### New Capabilities
- `org-management`：组织（部门/机构）的树形结构维护——树查询、子节点查询、详情、创建、更新、启停用、逻辑删除，及配套的前端组织树+列表管理界面。

### Modified Capabilities
（无——`openspec/specs/` 目前为空，此前没有已归档的 capability 规格，不涉及既有需求变更。）

## Impact

- **后端代码**：新增 `backend/src/main/java/cn/nihility/rbac/org/**`（entity/constant/mapper/dto/service/controller）与 `backend/src/main/java/cn/nihility/rbac/common/**`；新增 `backend/src/test/java/cn/nihility/rbac/org/service/impl/OrgServiceImplTest.java`；重命名 `com.example.demo` → `cn.nihility.rbac`（含 `RbacApplication.java`、`RbacApplicationTests.java`）。
- **数据库**：新增 Flyway 迁移 `backend/src/main/resources/db/migration/V1__init_tab_org.sql`，建表 `tab_org`。`spring.flyway.enabled` 目前在 `application.yml` 中为 `false`（占位数据源 `localhost:3306/rbac_demo` root/root，尚未连接真实库）。
- **依赖**：`backend/build.gradle` 新增/替换依赖，见上文"依赖修正"。改动前未与用户逐条确认每个依赖变更，但均是为修复已发现的 Spring Boot 3.5 兼容性问题所做的必要调整。
- **前端代码**：新增 `frontend/src/views/identity/org/OrgManagementView.vue`、`frontend/src/api/org.ts`、`frontend/src/stores/org.ts`、`frontend/src/types/org.ts`；修改 `frontend/src/router/menu.ts`（菜单文案）与 `frontend/src/router/index.ts`（路由改指向真实组件）。
- **API**：新增 `/api/orgs/tree`、`/api/orgs/children`、`/api/orgs/{id}`（GET/PUT/DELETE）、`/api/orgs`（POST）、`/api/orgs/{id}/enable`、`/api/orgs/{id}/disable` 共 7 个接口，均通过全局响应包装为 `{ code, message, data }`。
- **验证**：`./gradlew build` 通过（6 个测试，0 失败）；`npm run build`（vue-tsc + vite build）通过，无类型错误。
