## Why

`password-login-auth` change 落地了登录、令牌、请求身份校验，但当时明确把"按角色-权限点做真正的访问控制"列为 Non-Goal，`IdentityAuthFilter` 目前对 `menu` 请求头只做三段式格式校验，不判断当前用户是否真的有权访问该资源——这意味着任何登录成功的用户（含没有被授予"管理员"身份的普通用户）事实上能调用系统里的任意业务接口。同时，角色管理（`tab_role`）、权限点管理（`tab_permission`）目前都是完全孤立、互不关联、也不关联管理员的独立 CRUD 模块，"RBAC 权限管理系统"里"角色拥有哪些权限、管理员拥有哪些角色"这条核心链路始终没有被打通。本次补上这条链路，把 `password-login-auth` 遗留的 Open Question 落地为真正的运行时鉴权。

## What Changes

- 新增 `tab_role_permission` 关联表（角色 ↔ 权限点，多对多，无独立 status，随角色整体同步、物理删除，模式对齐既有 `tab_admin_role`）。
- 角色管理新增/编辑表单内嵌权限点勾选（比照管理员管理表单内嵌角色多选的既有交互模式），角色详情页只读展示已分配权限点；权限点数量可能上百条，采用按"模块"（权限点编码冒号分隔的第一段）分组的勾选交互，而非普通下拉多选。
- 权限点管理新增一个不分页的"权限点选项查询"接口（仅返回未删除且启用的权限点），供角色表单加载全量可选项，形态对齐角色模块已有的 `GET /api/roles/options`。
- `IdentityAuthFilter` 新增运行时鉴权判断：在现有的 `identity-token` 校验、`menu` 头格式校验、首登拦截通过之后，进一步判断"当前用户 → 是否有管理员身份 → 其角色 → 角色的权限点集合"是否包含请求携带的 `menu` 编码；不满足则拦截为新增的"无权限"业务错误码（区别于既有的未登录 `401`、首登强制改密 `4010`）。
- 数据引导：把根目录 `权限资源.txt` 里登记的全部资源编码种子化为 `tab_permission` 记录；新增一个"超级管理员"角色并关联全部种子权限点；给已存在的默认登录账号 `admin`（`password-login-auth` change 种子化的 `admin`/`admin`）补一条 `tab_admin` 记录并关联到这个角色，避免默认账号被本次新增的鉴权机制自己锁死。
- 前端 `request.ts` 响应拦截器识别新增的"无权限"错误码，与未登录/首登区分处理（提示信息，不跳转登录页/改密页）。
- **不涉及** `tab_menu`/菜单管理模块本身的改动，`tab_menu` 继续只作为资源编码的文档性清单，不参与本次运行时鉴权判断（角色关联的是权限点，不是菜单资源，两套编码独立维护）。
- **不涉及**管理员管理模块的新 CRUD 能力（该模块已有完整实现，本次改动后其角色分配会首次产生真正的鉴权意义）。
- **不涉及**按管辖组织范围（`tab_admin_org_scope`）过滤数据的"数据权限"能力，那是更大的独立能力，明确排除在本次范围外。

## Capabilities

### New Capabilities
- `rbac-permission-authorization`：角色-权限点关联维护、权限点选项查询、运行时鉴权引擎（`menu` 编码 → 角色 → 权限点的实际访问判断）、默认账号引导授权。

### Modified Capabilities
- `role-management`：角色新增/编辑/详情新增"权限点分配"相关的 `ADDED Requirements`（表单内嵌权限点勾选、详情只读展示）。
- `permission-management`：新增"权限点选项查询"接口的 `ADDED Requirement`。
- `password-login-auth`：`操作资源请求头校验` 这条既有 Requirement 需要 `MODIFIED`——从"仅做三段式格式校验"升级为"真正判断当前用户的角色权限点集合是否包含该编码"；另外新增一条关于默认账号 `admin` 引导授权（自动获得超级管理员角色）的 `ADDED Requirement`。

## Impact

- 后端：`role`、`permission`、`admin`、`auth`（`IdentityAuthFilter`）模块均有改动；新增 Flyway 迁移（`tab_role_permission` 建表 + 权限点种子数据 + 超级管理员角色 + 默认账号的 `tab_admin` 记录）。
- 前端：`RoleManagementView.vue`、`RoleDetailView.vue`、`src/api/role.ts`、`src/api/permission.ts`（新增 options 接口封装）、`src/api/request.ts`（识别新错误码）。
- 依赖：不新增第三方依赖，沿用现有 MyBatis-Plus/Flyway/Element Plus 技术栈。
