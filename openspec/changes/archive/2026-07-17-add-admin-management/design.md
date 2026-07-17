## Context

参照现有角色管理（`cn.nihility.rbac.role`，纯扁平主数据表）与用户管理
（`cn.nihility.rbac.user`，`tab_user` + 一对多子表 `tab_user_position`，子表通过
"整体 diff 同步"在创建/更新用户时一并维护，多表 JOIN 查询写在
`UserPositionMapper.xml`）两种已有模式。管理员管理比用户管理更复杂一层：需要同时
关联一个多对多的角色集合（`tab_admin_role`）和一个多对多的组织管辖范围集合
（`tab_admin_org_scope`，每行还带一个业务字段"是否包含递归子组织"）。这是本仓库
第一个真正的多对多关联场景，此前角色、权限点之间都还没有互相关联。

## Goals / Non-Goals

**Goals:**
- 管理员主数据 + 角色关联 + 组织管辖范围关联三张表的建表、增删改查后端接口、前端
  管理页面。
- 角色、组织管辖范围在新增/编辑时随管理员主表一并整体提交、整体同步（不提供独立
  的"给某个管理员加一个角色"这类单点接口）。
- 复用已有的用户远程搜索、组织树选择器交互模式，不重新发明。

**Non-Goals:**
- 不实现管理员登录、鉴权、按管辖组织范围过滤业务数据——这些是本次新增的数据模型
  之上后续才会做的事，本次只搭数据结构和维护界面。
- 不给 `tab_role`、`tab_org` 增加"关联管理员"的反向查询入口。
- 不做管理员列表的搜索/筛选栏（与角色管理、权限点管理、菜单管理保持一致的极简
  列表形态：无搜索栏，仅分页 + 增删改查）。
- 管理员逻辑删除时不级联清理 `tab_admin_role`/`tab_admin_org_scope` 中的关联行
  （与 `tab_org` 软删除不级联清理子节点是同样的既有取舍，删除后的管理员本身已经
  不可见/不可用，遗留的关联行不产生实际影响）。

## Decisions

### 1. 表结构

```sql
CREATE TABLE `tab_admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `name`        VARCHAR(64)  NOT NULL COMMENT '管理员名称',
    `code`        VARCHAR(64)  NOT NULL COMMENT '管理员编码',
    `user_id`     BIGINT       NOT NULL COMMENT '关联用户 id，关联 tab_user.id，不建物理外键',
    `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_admin_status` (`status`),
    KEY `idx_tab_admin_code` (`code`),
    KEY `idx_tab_admin_user_id` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员主数据表';

CREATE TABLE `tab_admin_role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`    BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `role_id`     BIGINT       NOT NULL COMMENT '角色 id，关联 tab_role.id',
    `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_role` (`admin_id`, `role_id`),
    KEY `idx_tab_admin_role_admin_id` (`admin_id`),
    KEY `idx_tab_admin_role_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员角色关联表，无独立 status，随管理员整体同步、物理删除';

CREATE TABLE `tab_admin_org_scope` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `admin_id`         BIGINT       NOT NULL COMMENT '管理员 id，关联 tab_admin.id',
    `org_id`           BIGINT       NOT NULL COMMENT '组织 id，关联 tab_org.id',
    `include_children` TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_admin_org_scope` (`admin_id`, `org_id`),
    KEY `idx_tab_admin_org_scope_admin_id` (`admin_id`),
    KEY `idx_tab_admin_org_scope_org_id` (`org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员组织管辖范围表，无独立 status，随管理员整体同步、物理删除';
```

理由：`tab_admin_role`/`tab_admin_org_scope` 都是纯关联/配置行，没有独立的业务
详情或状态需要保留跨版本历史，采用"整体同步"（提交时先删除该管理员名下全部既有
关联行，再按请求列表整批插入）比照搬 `UserServiceImpl.syncPositions` 的按行 diff
更简单、也更符合这两张表的数据形态；`tab_user_position` 之所以用 diff 是因为它的
每一行（任职地址/电话等）本身是有独立价值、值得保留创建时间的业务记录。

### 2. 状态常量

新增 `AdminStatus`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`），沿用项目
统一的三态状态语义，不复用其他模块的状态常量类。

### 3. 接口设计（`/api/admins`）

- `GET /api/admins?page&pageSize` —— 分页查询，无筛选参数，按 `showOrder` 降序、
  `id` 升序排列；每条记录含 `userName`（关联用户姓名，2 表 JOIN 回填），不含
  `roles`/`orgScopes`（与 `UserVO` 分页列表不带 `positions` 是同样的取舍，避免
  列表页 N+1）。
- `GET /api/admins/{id}` —— 详情，`roles`（角色 id + 名称）、`orgScopes`（组织
  id + 名称 + `includeChildren`）随详情一并返回。
- `POST /api/admins` —— 创建，请求体含 `name`/`code`/`userId`/`showOrder`/
  `remark`/`roleIds`（`List<Long>`，可为空）/`orgScopes`（`List<{orgId,
  includeChildren}>`，可为空）。新建默认状态为启用。
- `PUT /api/admins/{id}` —— 更新，请求体同创建（不含状态），角色与组织管辖范围
  按决策 1 的"整体同步"方式覆盖。
- `PUT /api/admins/{id}/enable`、`PUT /api/admins/{id}/disable` —— 启停用。
- `DELETE /api/admins/{id}` —— 逻辑删除（`status = -1000`）。

校验规则：`name`/`code` 必填（`@NotBlank`，长度上限 64）；`userId` 必填
（`@NotNull`）；`code` 在未删除的管理员范围内唯一；`userId` 在未删除的管理员
范围内唯一（一个用户最多关联一个未删除的管理员身份）；`roleIds`/`orgScopes`
中的每个 `orgId`/`roleId`/`includeChildren` 做基础非空校验，不校验其指向的角色/
组织是否存在且启用（与 `orgId`/`ownerId` 在应用管理里的处理力度一致，不做交叉
存在性校验，交给前端下拉选择器保证数据来源合法）。

### 4. 多表 JOIN 查询放在 MyBatis XML

- `AdminMapper.xml`：`selectAdminPage`（`tab_admin LEFT JOIN tab_user` 回填
  `userName`，分页）、`selectAdminDetail`（同样的 JOIN，按 id 查单条）。
- `AdminRoleMapper.xml`：`selectRolesByAdminId`（`tab_admin_role INNER JOIN
  tab_role` 按 `admin_id` 查询，回填角色名称，角色若已被删除则不返回——用
  INNER JOIN 而不是 LEFT JOIN，因为脏关联数据没有展示价值）。
- `AdminOrgScopeMapper.xml`：`selectOrgScopesByAdminId`（`tab_admin_org_scope
  INNER JOIN tab_org` 按 `admin_id` 查询，回填组织名称，同样用 INNER JOIN）。

这是延续本仓库既有约定（见项目记忆"多表查询用 MyBatis XML，不在 Java 侧批量查询
再拼装"），也是 `UserPositionMapper.xml` 已经验证过的写法。

### 5. 角色选项接口

`RoleService` 新增 `List<RoleOptionVO> getEnabledOptions()`，`RoleController`
新增 `GET /api/roles/options`，返回全部未删除且启用（`status = 2000`）的角色
`{id, name, code}` 列表，不分页，`showOrder` 降序、`id` 升序排列。与
`DictItemController#options`（`GET /api/dict-items/options?typeCode=`）是同类
设计，供前端下拉选择器一次性加载全量选项。

### 6. 前端表单交互

- "关联用户"：远程搜索单选（`el-select` + `remote-method`），复用
  `userApi.getUserPage({ name })`/`{ mobile }`，交互与应用管理"负责人"选择器
  完全一致（含编辑弹窗回显已选用户姓名的处理方式）。
- "管理员角色"：多选下拉（`el-select multiple`），数据源 `roleApi.getRoleOptions()`
  （新增接口封装），页面 `onMounted` 时一次性加载（角色数量级与字典项/任职类型
  相当，不需要像组织树那样延迟到打开弹窗才加载）。
- "管辖组织范围"：动态多行子表单，每行 `{ orgId, includeChildren }`，"添加组织"/
  行内删除按钮，交互结构照搬用户管理弹窗内任职信息子表单（`addPositionRow`/
  `removePositionRow` 那一套），每行的组织选择器用 `el-tree-select`（`check-strictly`，
  数据源为全量组织树），`includeChildren` 用 `el-checkbox`。全量组织树延迟到打开
  新增/编辑弹窗时才请求（`await fetchOrgTree()` 放在 `openCreateDialog`/
  `openEditDialog` 内部），遵循 `CLAUDE.md` 里刚确立的既有约定。
- 列表列：管理员名称、管理员编码、关联用户、显示序号、状态、操作；不展示角色、
  管辖组织范围（信息量大，只在编辑/详情弹窗展示）。
- 详情弹窗：只读展示管理员名称、管理员编码、关联用户、显示序号、备注、状态、
  角色列表（标签形式列出角色名称）、管辖组织范围列表（组织名称 + 是否含子组织），
  以及创建人、创建时间、更新人、更新时间。

## Risks / Trade-offs

- [Risk] `orgScopes`/`roleIds` 整体同步（先删后插）在角色/组织较多时会产生较多
  行级 DML → Mitigation：单个管理员的关联行数量级很小（几个角色、几个组织），
  性能影响可忽略，换来的是实现简单、没有"部分行更新失败导致状态不一致"的边界
  情况。
- [Risk] `userId` 唯一性校验只挡未删除范围内的重复绑定，若某用户的管理员记录被
  逻辑删除后又重新创建一条新管理员记录关联同一用户，是被允许的（符合逻辑删除
  语义：旧记录已经"不存在"）→ 不视为需要额外处理的风险，是既有软删除模型的
  自然结果。

## Open Questions

无。
