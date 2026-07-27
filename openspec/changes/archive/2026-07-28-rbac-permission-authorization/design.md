## Context

当前 `tab_role`（角色）、`tab_permission`（权限点）都是完全独立的 CRUD 模块，`tab_admin`（管理员）已经关联 `tab_user` + `tab_role`（`tab_admin_role`）+ `tab_org` 管辖范围（`tab_admin_org_scope`），但角色本身不关联任何权限，`IdentityAuthFilter`（`password-login-auth` change 落地）只对 `menu` 请求头做格式校验，不做真正的访问控制判断——这是该 change 明确记录的 Non-Goal/Open Question，本次要把这条链路补齐：管理员 → 角色 → 权限点 → 是否允许访问某个 `menu` 编码。

已与用户确认锁定、不再讨论的两个架构前提（详见 proposal.md）：
1. 角色关联的是"权限点"（`tab_permission`），不是"菜单资源"（`tab_menu`）；两套编码独立维护，`tab_menu` 本次不参与鉴权。
2. 无管理员身份的普通用户默认零权限，鉴权逻辑没有"超级管理员"特判分支——超级管理员就是被授予全部权限点的一个普通角色。

## Goals / Non-Goals

**Goals:**
- 角色可以勾选权限点（新增/编辑表单内嵌，对齐管理员表单内嵌角色多选的既有交互）。
- 权限点新增一个不分页的启用态选项查询接口，供角色表单加载全量可选项。
- `IdentityAuthFilter` 在现有校验通过后，追加"当前用户的角色权限点集合是否包含请求 `menu` 编码"的真实判断，不满足则拦截为新的"无权限"错误码。
- 把 `权限资源.txt` 全量编码种子化为 `tab_permission`，新增一个拥有全部权限点的"超级管理员"角色，把默认账号 `admin` 接入这个角色，避免鉴权上线后把自己锁死。

**Non-Goals:**
- 不改动 `tab_menu`/菜单管理模块，不建立它与权限点/角色的关联。
- 不新增管理员管理模块的 CRUD 能力。
- 不做按管辖组织范围（`tab_admin_org_scope`）过滤数据的"数据权限"，这是独立的更大能力。
- 不做权限点/角色之外更细粒度（如字段级）的权限控制。
- 不做角色权限变更后"踢掉已登录会话强制重新登录"之类的会话失效联动——权限判断在每次请求时都会重新查库（见 Decision 3），角色权限一旦调整，受影响用户的下一次请求就会立即按新权限生效，不需要额外的会话失效机制。

## Decisions

### 1. 新增 `tab_role_permission` 关联表，模式对齐既有 `tab_admin_role`
```sql
CREATE TABLE tab_role_permission (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    role_id        BIGINT      NOT NULL COMMENT '角色 id，关联 tab_role.id',
    permission_id  BIGINT      NOT NULL COMMENT '权限点 id，关联 tab_permission.id',
    create_by      VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by      VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tab_role_permission (role_id, permission_id),
    KEY idx_tab_role_permission_role_id (role_id),
    KEY idx_tab_role_permission_permission_id (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4
  COMMENT = '角色权限点关联表，无独立 status，随角色整体同步、物理删除';
```
无独立 `status`，角色编辑时整体同步（先按 `role_id` 物理删除全部既有关联，再按提交的 `permissionIds` 批量插入），与 `AdminServiceImpl#syncRoles`/`syncOrgScopes` 现有的"整体同步"实现方式完全一致，不做增量 diff——角色的权限点数量级不会大到需要为性能做增量优化，delete-all + reinsert 更简单、不容易出现"漏删/漏加"的边界 bug。

### 2. 角色 DTO 新增 `permissionIds`，与管理员表单的 `roleIds` 同构
- `RoleCreateRequest`/`RoleUpdateRequest` 新增 `permissionIds: List<Long>`（可选，不传或空数组视为"不授予任何权限点"）。
- `RoleVO`（详情接口 `GET /api/roles/{id}` 返回）新增 `permissions: List<PermissionOptionVO>`（含 `id`/`name`/`code`），前端编辑表单回填时用 `detail.permissions.map(p => p.id)` 得到 `permissionIds`，与 `AdminManagementView.vue` 现有 `form.roleIds = detail.roles.map((role) => role.roleId)` 的写法完全同构。
- 新增 `RolePermissionEntity`/`RolePermissionMapper`（`extends BaseMapper`，另加一个自定义方法 `selectPermissionsByRoleId(roleId)`，JOIN `tab_permission` 回填名称/编码，SQL 写在 `resources/mybatis/mapper/RolePermissionMapper.xml`，风格对齐现有 `AdminRoleMapper`/`AdminRoleMapper.xml`——已被反馈记录为约定：多表查询走 XML，不在 Java 侧批量查询再拼装）。

### 3. 权限点选项查询：`GET /api/permissions/options`
不分页，仅返回未删除且启用（`status = 2000`）的权限点，返回 `id`/`name`/`code`，形态、命名、排序规则（`showOrder` 降序、`id` 升序）完全对齐角色模块已有的 `GET /api/roles/options`。供前端角色表单一次性加载全量可选项（权限点数量级在百这个量级，一次性加载没有分页的必要）。

### 4. 运行时鉴权：单条 JOIN 查询取用户的权限编码集合，不做缓存
`IdentityAuthFilter` 在现有的 `identity-token` 校验 → `menu` 头格式校验 → 首登拦截全部通过后，追加一步：

```sql
SELECT DISTINCT p.`code`
FROM tab_admin a
JOIN tab_admin_role ar ON ar.admin_id = a.id
JOIN tab_role r ON r.id = ar.role_id AND r.status = 2000
JOIN tab_role_permission rp ON rp.role_id = r.id
JOIN tab_permission p ON p.id = rp.permission_id AND p.status = 2000
WHERE a.user_id = #{userId} AND a.status = 2000
```
一条 SQL 拿到"这个登录用户当前拥有的全部启用权限编码"集合，Java 侧只做一次 `Set.contains(menuCode)` 判断；不在集合里（含该用户压根没有启用状态的 `tab_admin` 记录的情况，此时这条 JOIN 自然返回空集）统一拦截为"无权限"错误。

新方法放在 `permission` 模块的 `PermissionMapper`（自定义方法 `selectGrantedPermissionCodesByUserId(userId)`，XML 里写这条多表 JOIN，对齐"多表查询走 XML"的既有约定）——虽然查询跨了 `admin`/`role` 模块的表，但返回的数据形状（一组 `tab_permission.code`）本质上属于"权限点"这个概念，放在 `PermissionMapper` 上比新开一个专门的鉴权查询接口更自然；实现落地为 `cn.nihility.rbac.auth.service.AuthorizationService`/`AuthorizationServiceImpl`（方法 `hasPermission(Long userId, String menuCode)`），内部注入 `PermissionMapper` 调用上述查询做一次 `Set.contains` 判断——`auth` 模块已经有直接注入 `user` 模块 `UserMapper`（登录查用户）的先例，这里是同一种"认证/鉴权天然需要跨模块读数据"的场景，不算新增架构模式。

`IdentityAuthFilter` 实际执行的四步顺序是：`identity-token` 校验 → `menu` 头格式校验 → 首登拦截 → 本步权限判断。修改密码接口（`/api/auth/password`）对应的资源编码属于"任何已登录用户都必须能执行的自助操作"，未被登记进 `权限资源.txt`/权限点种子数据里，因此 `IdentityAuthFilter` 的 `FIRST_LOGIN_WHITELIST` 白名单**同时豁免第三步（首登拦截）和第四步（本节的权限判断）**，不只是豁免首登拦截——否则包括默认账号 `admin` 在内的所有用户会在"首次登录 → 强制改密"这一步就被本次新增的鉴权机制自己锁死，永远无法完成首登流程。

**是否缓存角色-权限点集合**：本次选择**不缓存**，每次业务请求都实时查一次库。理由：
- 这是一条命中索引的多表 JOIN（`tab_admin.user_id`、`tab_admin_role.admin_id`/`role_id`、`tab_role_permission.role_id`/`permission_id` 均有索引），不是全表扫描，单次查询成本很低；作为内部管理系统，并发量级不需要为这一条查询专门引入缓存层的复杂度。
- 引入缓存（无论是 Redis 还是内存）都会带来"角色权限点调整后何时失效"的一致性问题，需要额外设计失效策略（写时失效/TTL），而 Non-Goals 里已经明确"权限调整应该在下一次请求即时生效，不做会话失效联动"——不缓存刚好是实现这一目标最简单、最不容易出 bug 的方式，属于"用最简单方案先满足需求，性能不够再优化"的合理取舍，避免过早引入不必要的复杂度。
- 如果未来实测这条查询成为瓶颈，是一个局部可替换的优化点（比如换成 Redis 按 `userId` 缓存 + 角色/权限相关写操作时主动失效），不影响本次其余设计。

### 5. 新增错误码：`FORBIDDEN = 403`
`cn.nihility.rbac.auth.constant.AuthErrorCode` 新增：
```java
/** 无权限：已登录且未处于首登待改密状态，但当前用户的角色权限点集合不包含请求的 menu 编码
 *（含用户没有启用状态的管理员身份这种情况）。 */
public static final int FORBIDDEN = 403;
```
与既有 `UNAUTHORIZED = 401`（未登录/令牌失效）、`FIRST_LOGIN_REQUIRED = 4010`（首登强制改密）互不冲突，取值贴合 HTTP 403 Forbidden 的通用语义，便于前端按数值直觉理解。`IdentityAuthFilter` 沿用现有的"直接手写 JSON 响应，不 throw"的既有实现方式（详见 `password-login-auth` design.md Decision 5 的说明，本次不重复）。

### 6. 前端权限点勾选交互：按模块分组的 `el-tree` 复选框，而非下拉多选
权限点数量在百这个量级，`el-select multiple` 下拉多选在这个量级下选项列表会很长、缺少分组，体验差；改用 `el-tree`（`show-checkbox`、`node-key="id"`）构造一棵**前端合成**的两层树：第一层是按权限点编码冒号分隔的第一段（如 `UserManagement`）分组出的虚拟节点（无对应后端 id，仅用作分组容器，勾选状态由子节点联动，不作为可勾选的独立叶子），第二层是具体权限点（勾选状态对应 `permissionIds`）。分组节点标签直接使用编码里的英文模块段（如 `UserManagement`），不额外维护一张"模块编码 → 中文名"的映射表——够用、足够低成本；如果后续想要更友好的中文分组标题，是一个局部可replace的展示层优化，不影响本次数据结构和交互骨架。

角色详情页（只读）复用同样的"按模块分组"数据结构，但展示形式不是 `el-tree`，而是"分组标题 + `el-tag` 标签云"（`RoleDetailView.vue`：每个模块一个分组标题 + 该模块下权限点各渲染一个 `el-tag`）——详情页是纯只读场景，不需要 `el-tree` 的勾选/联动能力，标签云更轻量、也更符合"一屏看清一个角色有哪些权限点"的浏览诉求；新增/编辑弹窗里的可勾选交互仍然是 `el-tree`，两者共享同一套"按编码模块段分组"的分组逻辑，只是叶子节点的呈现组件不同。

### 7. 前端错误码分流：`request.ts` 新增 403 分支
响应拦截器现有对 `body.code` 的处理已经区分 `401`（触发静默刷新/跳登录页）、`4010`（跳改密页）；新增 `403` 分支：不跳转、不清空登录态，直接 `ElMessage.error(body.message || '无权限访问')` 并 reject，与其余未识别的业务错误码走相同的兜底提示路径（可以合并处理，不需要为 403 单独写一套提示文案，除非后端返回的 `message` 本身已经够用）。

### 8. 数据引导：种子迁移的生成方式
新增 Flyway 迁移，内容包括：
- 把 `权限资源.txt` 里全部 94 条 `模块:资源:操作` 编码种子化为 `tab_permission` 记录（`name` 取该行编码后面的中文描述，`code` 就是编码本身，按模块分段插入并加注释，`show_order` 可以统一给一个默认值或按行号递减，不强求语义排序）。**迁移脚本里的编码/名称必须用脚本从 `权限资源.txt` 原文生成，不要手工转抄**——上次的经验教训是手工转写密钥导致了一个真实的运行期 bug，94 条数据手工转抄同样容易出现遗漏/错字，实现阶段应该写一个一次性小脚本解析 `权限资源.txt` 生成 SQL 片段，而不是逐行手打。
- 新增一条"超级管理员"角色记录（`tab_role`，具体 `name`/`code` 由实现阶段定，建议 `超级管理员`/`SUPER_ADMIN`），并在 `tab_role_permission` 里把这条角色与全部种子权限点关联。
- 给默认账号 `admin`（`tab_user.code = 'admin'`，`password-login-auth` change 已种子化）新增一条 `tab_admin` 记录（`user_id` 取自查询 `tab_user.id`，仿照 `V3__seed_default_admin_user.sql` 用 `SELECT ... INTO @变量` 的写法），并在 `tab_admin_role` 里关联到"超级管理员"角色。

## Risks / Trade-offs

- [不缓存权限集合，每次业务请求多一条 JOIN 查询] → 见 Decision 4，内部系统量级下可接受，是有意的简单优先取舍。
- [权限点分组标签用英文模块段，不够友好] → 见 Decision 6，低成本的展示层局限，不影响功能正确性，后续可替换。
- [`tab_permission`/`tab_menu` 两套编码独立维护，可能出现权限点编码和菜单资源编码"看起来一样但其实是两份数据"导致运营时手滑打错、两边不一致] → 这是已经和用户确认过的架构取舍（Decision 见 proposal.md 背景），非本次引入的新缺陷；`权限资源.txt` 仍然是唯一的人读权威清单，两套种子数据都应该从它派生，保持源头一致。
- [种子迁移一次性插入 94 条权限点 + 关联数据，属于较大的一次性 Flyway 迁移] → 通过脚本从 `权限资源.txt` 生成、分模块加注释组织，降低可维护性风险；迁移只执行一次，后续新增按钮资源时走"新增 change 的增量迁移"模式（参照 `password-login-auth` change 里 `V4__add_user_reset_password_menu.sql` 的先例），不需要重新生成整份种子。

## Open Questions

无——两个此前需要用户确认的关键架构问题（角色授权对象、无管理员身份用户的默认权限）已经在提出本次 change 前通过 `AskUserQuestion` 得到明确答复，均已写入本设计。
