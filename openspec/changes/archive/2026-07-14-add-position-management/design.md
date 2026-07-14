## Context

任职记录持久化在 `tab_user_position`（`cn.nihility.rbac.user.entity.UserPositionEntity`），目前只被用户管理模块使用：新增/编辑用户时，`UserServiceImpl.syncPositions` 按 `id` 对请求里的任职记录列表和该用户既有的任职记录做整体 diff——带 `id` 的按行更新，不带 `id` 的新增，既有记录中未出现在请求列表里的物理删除（`userPositionMapper.deleteByIds`）。该表当前没有 `status` 列，注释里明确写着"不像 `UserEntity` 那样存在独立的 status 列"。

组织管理（`org-management`）左侧已经有一套"默认全部收起、点击节点懒加载展开、选中节点后右侧分页表格展示直属数据"的成熟交互（`OrgManagementView.vue` + `stores/org.ts`），任职管理左侧树要复用同一套模式，但右侧的数据源是"任职记录"而不是"子组织"，且任职与组织之间没有父子递归关系（一条任职记录直接挂在一个具体 `orgId` 上，不存在"顶级组织聚合查询"的语义）。

## Goals / Non-Goals

**Goals:**
- 任职记录获得独立的 `2000`/`3000`/`-1000` 状态语义，可独立启用/停用/逻辑删除，不再要求打开某个用户的编辑弹窗才能操作。
- 新增"任职管理"页面：左侧组织树（懒加载、默认全收起）+ 右侧按 `orgId` 分页查询的任职记录表格。
- 用户管理内嵌任职子表单的现有行为（整体 diff、物理删除未出现在请求里的记录）保持不变，只是要与新的 `status` 列共存而不冲突。

**Non-Goals:**
- 不改造 `org-management` 本身（不新增组织相关接口/字段）。
- 不给用户管理内嵌子表单增加状态编辑能力（那里仍然只有"删掉这一行"，物理删除）。
- 不支持任职管理页面里新建用户（只能选择已存在用户）。
- 不支持任职管理页面编辑"所属用户"（换人 = 删除重建）。

## Decisions

- **复用 `UserPositionEntity`/`UserPositionMapper`，不新建实体/表**：任职管理和用户管理内嵌子表单本质上操作同一份数据（同一个用户在同一个组织下的任职记录），拆成两张表会导致数据不一致；新增 `status` 列即可满足两边的需求。
- **`status` 列复用 `2000`/`3000`/`-1000` 语义，但单独建 `PositionStatus` 常量类**：与 `OrgStatus`/`UserStatus` 保持同样"每个实体独立一份状态常量类"的项目惯例（三者值相同但不复用同一个类，避免跨领域概念耦合）。
- **新增独立的 `PositionController`/`PositionService`(`Impl`)/`PositionConvert`，而不是把接口塞进 `UserController`/`UserService`**：任职管理是"以组织为导航维度"的独立查询/CRUD 入口，语义和参数（`orgId` 必填分页查询、编辑不含 `userId`）都与用户模块现有的"整体 diff"入口不同，混在一起会让 `UserService` 职责过重。三者仍放在 `cn.nihility.rbac.user` 包下（而不是新建顶层包），因为复用同一个实体/Mapper，物理上离得越近，维护越简单。
- **`GET /api/positions` 的 `orgId` 设为必填、无默认值**：任职和组织不是父子递归关系，不存在"顶级组织"这种可以聚合查询任职记录的虚拟节点；未选中组织时前端根本不发起该请求（右侧直接展示空态提示），保持接口语义单一（不用像 `org-management` 的 `children` 接口那样处理 `parentId` 缺省即顶级的分支）。
- **新增任职记录只能选择已存在用户，且编辑时 `userId` 不可改**：换人本质上是"这条任职记录对应的人变了"，等价于原任职失效 + 新任职生效，用删除重建表达比"原地把 userId 改掉"更符合"任职记录"的语义（历史审计更清晰），也避免了新增一个"是否允许修改所属用户"的编辑态特殊分支。
- **用户搜索复用已有的 `GET /api/users?name=` 分页接口，不新增用户搜索专用接口**：该接口已经支持按姓名模糊搜索、分页，前端用 `el-select` 的 `remote` 模式（`remote-method` 里调小 `pageSize`，如 20 条）即可满足"输入姓名联想"的需求，没有必要为一个下拉选择器新增后端接口。
- **`UserServiceImpl` 里两处任职记录查询增加 `status != DELETED` 过滤**：
  1. `listPositionsWithOrgName`（用户详情回显、内嵌子表单编辑时的初始数据）——排除已被任职管理页面逻辑删除的记录，避免它们又出现在用户编辑表单里。
  2. `syncPositions` 里查询"当前用户既有任职记录"作为 diff 基准的那次查询——同样排除已逻辑删除的记录，让它们从 diff 的角度"已经不存在"，用户编辑保存时不会因为请求里没带上这些 id 而触发到 `deleteByIds`（虽然对已经是 `-1000` 的行再次物理删除也无害，但从语义上讲，diff 基准应该只覆盖"当前有效"的记录）。
  3. `syncPositions` 里新增记录分支显式 `entity.setStatus(PositionStatus.ENABLED)`（复用 `PositionStatus`，不复用 `UserStatus`，理由同上）。
- **任职管理列表/详情返回的 `positionType` 仍是字典编码，不在后端解析中文名**：与用户管理现有 `UserPositionVO` 一致，前端复用已有的 `getDictItemOptions('position_type')` 在本地做 code→label 映射，不新增后端解析逻辑。

## Risks / Trade-offs

- [新增 `status` 列后，历史遗留数据的默认值需要通过 Flyway 迁移回填] → 迁移脚本里 `ADD COLUMN ... DEFAULT 2000`，MySQL 会给已有行自动回填默认值，无需额外 `UPDATE` 语句。
- [`UserServiceImpl.syncPositions` 现有物理删除行为不变，意味着任职管理页面刚"停用"或"逻辑删除"的一条记录，如果用户又去编辑该用户并保存，可能因为 diff 基准已经排除了它而被物理删除（虽然它本来就已经是逻辑删除状态）] → 属于可接受的既有行为延伸（该记录本来就已经被视为"不存在"），不做额外保护；已在 Decisions 里说明。
- [两个入口（任职管理页面的独立 CRUD、用户管理内嵌子表单的整体 diff）同时可以改动同一份数据，理论上可能出现"用户管理页面还开着旧的任职列表，任职管理页面已经把某条记录改了组织"的并发编辑覆盖] → 现有用户管理内嵌子表单本身就是"编辑时整体加载、保存时整体 diff"的简单模型，没有乐观锁；本次改动不引入新的并发控制机制，风险与现状一致，不在本次范围内解决。

## Migration Plan

- 新增 `backend/src/main/resources/db/migration/V6__add_status_to_tab_user_position.sql`：
  ```sql
  ALTER TABLE `tab_user_position`
      ADD COLUMN `status` INT NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）' AFTER `remark`,
      ADD KEY `idx_tab_user_position_status` (`status`);
  ```
- Flyway 迁移一旦发布不可修改/回滚，如需撤销需另发一个新的迁移脚本移除该列（本次不做该分支的准备，按项目现有约定"迁移只前进不回退"处理）。
