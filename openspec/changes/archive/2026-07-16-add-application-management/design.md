## Context

"应用管理"菜单组在 `frontend/src/router/menu.ts` 里已经存在两个子菜单项（路径 `/application/list`、`/application/secret`），但都还没有真实业务组件，路由表通过 `implementedComponents` 缺省 fallback 到 `PlaceholderView.vue`。本次只落地 `/application/list`（应用主数据管理），不涉及 `/application/secret`。`/application/list` 子菜单项文案原为"应用列表"，实现完成后按用户要求改为"应用管理"（与其所属的一级菜单组同名，侧边栏呈现为"应用管理 → 应用管理"，是用户明确要求的结果，不是疏漏）。

后端目前没有任何"应用"相关的包/表。参照身份管理体系里结构最相近的 `position-management`（一个既要选"人"又要选"组织"的实体）：`user-management` 已经提供了按姓名/手机号模糊搜索用户的分页接口（`GET /api/users?name=&mobile=`），`org-management` 已经提供全量组织树接口（`GET /api/orgs/tree`），两者都可以直接复用作为"负责人"和"所属组织"两个选择器的数据源，不需要新增查询接口。

## Goals / Non-Goals

**Goals:**
- 新增应用主数据表 `tab_app`，具备独立的 `2000`/`3000`/`-1000` 状态语义（与 org/user/position 一致），支持增删改查、启停用、逻辑删除。
- 应用列表按 `showOrder` 降序（相同时 `id` 升序）分页展示，不提供搜索栏（用户已确认范围：负责人姓名/手机号搜索仅用于新增/编辑弹窗内的负责人选择器，不用于列表筛选）。
- 新增/编辑弹窗：负责人通过远程搜索选择已存在用户（支持按姓名或手机号搜索），所属组织通过组织树单选。

**Non-Goals:**
- 不实现"应用密钥"菜单（`/application/secret`）及配套的 appKey/appSecret 生成、轮换能力。
- 不在应用列表页面提供按负责人/组织的搜索过滤（已与用户确认范围）。
- 不支持在应用管理页面新建用户（负责人只能选择已存在用户，与任职管理选用户的模式一致）。
- 不给应用记录设计除"应用编码"外、本次需求未提及的字段。

## Decisions

- **新增独立顶层包 `cn.nihility.rbac.app`，不复用 `org`/`user` 包**：应用是与组织、用户平级的新领域实体（只是引用了 `orgId`/`ownerId` 两个外键），不像任职记录那样和已有实体共享同一张表，因此按项目惯例新建独立包（`controller`/`service`/`service.impl`/`dto`/`entity`/`mapper`/`mapstruct`/`constant`）。
- **`AppEntity` 只存 `ownerId`/`orgId` 两个外键，不冗余存储 `ownerName`/`orgName`**：与 `UserPositionEntity` 不存 `orgName`/`UserEntity` 一致的做法一样，展示用的名称在 `AppServiceImpl` 查询时批量 join `tab_user`/`tab_org` 回填到 `AppVO`，避免修改用户/组织名称后应用列表里的冗余名称过期。
- **列表接口 `GET /api/apps` 不接受任何筛选参数，只有 `page`/`pageSize`**：已与用户确认应用列表页面不提供搜索栏；如果未来需要按负责人/组织筛选，届时再新增 change 扩展该接口，现在不做预留参数。
- **负责人选择器复用 `GET /api/users?name=`/`?mobile=`，不新增专用搜索接口**：该接口已支持 `name`/`mobile` 独立可选的模糊搜索，前端 `el-select` 的 `remote-method` 里把输入框内容同时传给 `name` 和 `mobile` 两个参数即可（后端两个条件是"与"关系，同时传参会导致搜索不到人）——因此前端远程搜索改为提供一个"识别输入内容形如手机号则传 `mobile`，否则传 `name`"的简单启发式（手机号格式：`^1\d{10}$`），而不是同时传两个参数。
- **`AppStatus` 独立建常量类，不复用 `OrgStatus`/`UserStatus`/`PositionStatus`**：与项目"每个实体独立一份状态常量类"的既有惯例一致（`2000`/`3000`/`-1000` 语义相同，但不同领域概念不复用同一个类）。
- **创建/更新请求不校验 `ownerId`/`orgId` 指向的用户/组织是否存在或未被禁用/删除**：与 `PositionCreateRequest` 对 `userId`/`orgId` 的处理方式一致（只做非空校验，不做跨表存在性校验），前端选择器本身只能选出已存在的启用/停用用户和组织，不做额外后端防御；这是延续既有模式的一致性选择，不是本次新引入的宽松点。
- **`AppVO` 中 `ownerName`/`orgName` 若对应的用户/组织已被逻辑删除，沿用 `PositionVO`/`UserPositionVO` 现有行为**：join 不到时返回 `null`（不做特殊兜底文案），与既有任职记录展示行为保持一致。
- **补充（用户反馈后追加）：`tab_app` 新增 `code`（应用编码）字段，必填，在未删除应用范围内唯一**：与 `OrgEntity.code` 的既有做法完全一致——唯一性只在应用层（`AppServiceImpl.checkCodeUnique`，直接照抄 `OrgServiceImpl.checkCodeUnique` 的实现：按 `code` + `status != DELETED` 查询，更新场景额外 `ne(id, excludeId)` 排除自身）校验，不在数据库层加 `UNIQUE` 约束/索引（`idx_tab_app_code` 是普通 `KEY`），保持和 `tab_org` 一致的"应用层校验、DB 层仅加速查询"的既有取舍。`code` 未设计成不可变（不像任职记录的 `userId` 那样一旦创建不可改）——创建后仍允许通过更新接口修改，因为"编码"更接近组织编码的语义（标识符，但业务上允许改名式调整），而不是任职记录里"换人=删除重建"那种强身份绑定语义。

## Risks / Trade-offs

- [负责人远程搜索的"手机号 vs 姓名"启发式判断（`^1\d{10}$` 匹配则按手机号搜，否则按姓名搜）无法覆盖"姓名恰好是 11 位纯数字"这种极端情况] → 概率极低且用户主体是人名，不做更复杂的双路查询（避免前端一次输入触发两次请求增加复杂度），可接受。
- [`tab_app` 新表暂无任何数据依赖方，后续"应用密钥"等能力会以 `appId` 外键关联它] → 本次只建最小字段集（`name`/`ownerId`/`orgId`/`showOrder`/`remark`/`status`/审计字段），不为尚未设计的能力预留字段，符合"不做假设性设计"的项目要求；后续能力落地时再通过新的 Flyway 迁移扩展。

## Migration Plan

- 新增 `backend/src/main/resources/db/migration/V7__init_tab_app.sql`：
  ```sql
  CREATE TABLE `tab_app` (
      `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
      `name`        VARCHAR(64)  NOT NULL COMMENT '应用名称',
      `code`        VARCHAR(64)  NOT NULL COMMENT '应用编码',
      `owner_id`    BIGINT       NOT NULL COMMENT '负责人用户 id',
      `org_id`      BIGINT       NOT NULL COMMENT '所属组织 id',
      `show_order`  INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
      `remark`      VARCHAR(255)          DEFAULT NULL COMMENT '备注',
      `status`      INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
      `create_by`   VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
      `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
      `update_by`   VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
      `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
      PRIMARY KEY (`id`),
      KEY `idx_tab_app_status` (`status`),
      KEY `idx_tab_app_owner_id` (`owner_id`),
      KEY `idx_tab_app_org_id` (`org_id`),
      KEY `idx_tab_app_code` (`code`)
  ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '应用主数据表';
  ```
- Flyway 迁移只前进不回退，如需撤销需另发新迁移脚本；本条例外——`V7` 在被任何环境正式提交/共享前，`code` 字段是直接编辑进 `V7` 原文件生效的（而不是另发 `V8`），因为该迁移此前只在本机开发库跑过一次且尚未提交到 git，不存在其他环境依赖其旧校验和；处理方式是本机手动 `DROP TABLE tab_app` 并删除 `flyway_schema_history` 里 `version=7` 的记录后重新启动应用，让 Flyway 用新校验和干净地重新记录这条迁移。
