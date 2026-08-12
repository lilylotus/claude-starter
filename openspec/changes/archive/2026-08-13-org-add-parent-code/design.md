## Context

组织当前只持久化上级组织的 `parentId`（`tab_org.parent_id`，内部自增主键），组织树/详情等查询接口只在响应里回填 `parentName`（服务层按 `parentId` 反查一次父记录名称，见 `OrgServiceImpl.toVOListWithParentName`），从未持久化或返回过父组织的 `code`。`app-api-credentials` 能力已经搭好"应用同步字段映射配置"，未来组织数据要同步给外部应用时，外部系统需要一个不依赖本系统内部 id 的稳定标识来还原上下级关系——`code` 是业务侧维护、天然稳定的标识，天然适合承担这个角色。

组织的 `code` 本身可以通过更新接口修改（`OrgUpdateRequest.code`，`OrgServiceImpl.update` 里有 `checkCodeUnique` 校验但不禁止修改），这意味着一旦选择冗余存储 `parentCode`，就必须处理"父组织自己的 `code` 改了之后，子组织侧的 `parentCode` 怎么保持一致"的问题。

## Goals / Non-Goals

**Goals:**
- 给 `tab_org` 增加持久化列 `parent_code`，任意时刻其值等于 `parentId` 对应父组织当前的 `code`（顶级组织为空）。
- 创建、更新（含改上级组织、改自身编码）三条路径都能保持这个不变量成立，不需要额外的定时任务或手工订正。
- 组织树查询、树懒加载子节点查询、直属子组织分页查询、详情查询四个既有接口的响应新增 `parentCode` 字段。
- Flyway 迁移一次性回填存量数据。

**Non-Goals:**
- 不实现真正对外推送/拉取组织数据给应用的接口（仍是 `app-api-credentials` 的既有 Non-Goal，本变更只是让"组织记录本身"具备可同步的稳定父级标识）。
- 不改变管辖组织范围（`org-scope-data-permission`/`org-scope-write-guard`）相关的过滤或校验逻辑。
- 不允许前端直接编辑 `parentCode`——它完全是派生值，只读展示。

## Decisions

### Decision 1：落库冗余，而非查询时动态关联计算

`parent_code` 作为 `tab_org` 的持久化列存储，写入时从父组织当前 `code` 复制一份，而不是在 `toVOListWithParentName` 之类的读路径里像 `parentName` 一样"每次查询时关联父记录动态计算"。

理由：`parentName` 目前就是查询时动态关联的，本可以照搬同样的模式（零额外写路径复杂度），但这里选择落库冗余，原因是：
1. 组织树查询（`getTree`）与树懒加载子节点查询（`getChildrenTreeNodes`）目前直接把 `OrgEntity` 转 `OrgTreeNodeVO`（`OrgConvert.toTreeNode`），并不像详情/子组织分页查询那样有一次批量反查父记录的步骤；如果改成动态关联，这两个接口也要新增一次批量查询，四个接口里有两个要多引入查询逻辑。落库冗余则是新增列后 `OrgConvert` 自动按同名属性带出，四个接口零额外查询成本。
2. `parentCode` 的语义是"同步给外部系统看的稳定标识"，落库更贴近"这是组织记录自身的一个属性"这一定位，而不是"仅供当前系统内部展示用的派生名称"（`parentName` 场景）。

代价：需要在 `code` 被修改时做级联更新（见 Decision 2），比动态关联多一点写路径复杂度，但换来读路径统一、简单。

### Decision 2：编码变更时级联更新直属子组织的 parentCode，只下沉一层

`OrgServiceImpl.update` 里，若本次更新后 `entity.getCode()` 相对更新前发生变化，在同一次 `update` 事务内追加一步：批量把 `parentId = 该组织id` 的全部直属子组织的 `parent_code` 更新为新 `code`。

不需要递归到孙级及更深层：孙级的 `parentCode` 指向的是"孙级的直接父组织"（即子级）的 `code`，子级的 `code` 没有变化，所以孙级的 `parentCode` 天然不受影响，只有直属子组织这一层需要更新。

实现上通过 `OrgMapper` 追加一条 `UPDATE tab_org SET parent_code = #{newCode} WHERE parent_id = #{orgId} AND status != -1000` 的批量更新（MyBatis-Plus `update(Wrapper)` 用 Lambda 条件构造即可，不需要新增 XML）。

备选方案：不冗余存储，读时动态关联——已在 Decision 1 中说明为何不采用。

### Decision 3：顶级组织（parentId=0）的 parentCode 取值为空字符串/null，不使用哨兵值

顶级组织没有真实的父组织，`parent_code` 列保持为 `null`（数据库层面允许为空），响应 DTO 中该字段为 `null`，与现有 `parentName` 对顶级组织返回 `null` 的既有约定保持一致，不引入类似 `"ROOT"` 的哨兵字符串。

### Decision 4：创建/更新时 parentCode 的解析时机与失败处理

创建组织（`create`）：`assertParentOrgInScope` 校验通过后，若 `parentId != 0`，追加一次 `orgMapper.selectById(parentId)` 查询父组织当前 `code`（此时父组织已确定存在——如果不存在，插入会产生悬空引用，这是既有校验没有覆盖的边界情况，见 Open Questions），写入 `entity.setParentCode(parent.getCode())`；`parentId == 0` 时 `parentCode` 置 `null`。

更新组织（`update`）：仅当 `request.getParentId()` 与更新前的 `entity.getParentId()` 不同（即本次确实变更了上级组织）时，才重新解析新上级组织的 `code` 并写入 `parentCode`；未变更上级组织时保持 `parentCode` 原值不变（原值的正确性由创建时或上一次变更上级组织时已经建立，且如果原父组织自己改过 `code`，Decision 2 的级联更新已经保持同步）。

该解析复用组织详情查询里已有的"按 id 反查父记录"模式（`toVOListWithParentName` 里对 `parentIds` 的批量查询），创建/更新场景单条走 `orgMapper.selectById` 即可，不需要批量。

### Decision 5：暴露字段范围——四个既有查询接口 + 组织详情页面只读展示 + 操作历史字段快照

`OrgVO`、`OrgTreeNodeVO` 均新增 `parentCode` 属性，`OrgConvert` 无需额外 `@Mapping` 声明（新增列后按同名属性自动带出）。前端组织详情页面在现有"上级组织"（`parentName`）展示项附近新增"上级组织编码"只读展示项；新增/编辑表单不新增任何输入项——`parentCode` 完全由后端派生，前端没有可编辑的必要。

`OrgServiceImpl.toLogSnapshot`（操作历史字段快照构造方法）目前已经把 `parentName` 纳入快照，`parentCode` 作为组织记录的同级持久化字段，同样需要纳入快照（`snapshot.put("上级组织编码", entity.getParentCode())`，紧跟在 `"上级组织"` 之后），否则变更上级组织时操作历史看不出编码侧的变化，与 `parentName` 已有的审计粒度不一致。级联更新（Decision 2）产生的子组织 `parentCode` 变化不经过 `create`/`update`/`changeStatus` 中任何一个会调用 `operationLogRecorder` 的路径，因此不会、也不需要为被级联更新的子组织单独产生操作历史记录——这与"操作历史只记录直接对该组织发起的操作"的既有语义一致。

### Decision 6：把 parent_code 同时纳入 tab_metadata_field（ORG）目录

`tab_metadata_field` 是"表单字段定义"（`form-field-definition-management`）绑定来源与"应用同步字段映射配置"（`app-api-credentials`）源字段的统一目录，两者都通过 `GET /api/metadata-fields/available?bizType=ORG` 读取。仅在 `tab_org` 加一列、在 `OrgVO`/`OrgTreeNodeVO` 里暴露，并不会让这两个下游能力感知到这个新字段——它们看的是 `tab_metadata_field` 这张独立的目录表，需要单独插入一条种子记录（`bizType=ORG`、`tableName=tab_org`、`columnName=parent_code`、`fieldCode=parentCode`、`fieldName=上级组织编码`、`status=2000`）。

是否应该像 `parentId` 一样被排除在目录之外？不排除。`parentId` 被排除的原因是它已经有专用交互控件（组织树选择器）覆盖了同样的语义，重复收录会在"表单字段定义"里产生一个用不上的重复绑定入口；而 `parentCode` 没有任何专用控件——它是纯派生只读字段，本身没有用户输入入口，收录进目录不会与任何既有控件重复，且这正是它存在的意义：作为"应用同步字段映射配置"里组织数据域可选的源字段之一。

不需要同时补一条 `tab_form_field_definition` 的 CORE 类型定义：组织详情页面对 `parentCode` 的展示是硬编码的只读展示项（Decision 5），不接入"组织字段的动态列表与表单渲染"这条动态渲染管线（那条管线覆盖的是列表列与新增/编辑表单项，`parentCode` 两者都不参与）；只需要它在 `tab_metadata_field` 里可见、可被"表单字段定义"或"应用同步字段映射配置"选中即可。

## Risks / Trade-offs

- [风险] 级联更新与主更新不在同一次 `UPDATE` 语句内，若批量更新子组织那一步失败，`code` 已经改了但子组织 `parentCode` 未同步 → **缓解**：两步操作都在 `update` 方法现有的同一个 Spring 声明式事务边界内（`OrgServiceImpl` 类/方法上若目前没有显式 `@Transactional`，需要在 tasks 里补上，确保二者要么都成功要么都回滚）。
- [风险] Flyway 回填脚本执行时若存在"父组织 id 指向一个不存在的记录"的脏数据（正常业务流程不会产生，但历史脏数据不能完全排除）→ **缓解**：回填 SQL 用 `LEFT JOIN`，父记录不存在时 `parent_code` 保持为 `NULL`，不报错、不中断迁移。
- [权衡] 冗余存储引入"理论上可能不一致"的窗口（例如绕过应用直接改库），但项目内所有写路径都经过 `OrgServiceImpl`，风险可接受，换来读路径简单、四个查询接口零额外关联开销。

## Migration Plan

1. 新增 Flyway 脚本 `V10__org_parent_code.sql`：
   - `ALTER TABLE tab_org ADD COLUMN parent_code VARCHAR(64) NULL COMMENT '上级组织编码' AFTER parent_id;`
   - 回填存量数据：`UPDATE tab_org t LEFT JOIN tab_org p ON t.parent_id = p.id SET t.parent_code = p.code WHERE t.parent_id != 0;`（顶级组织 `parent_id = 0` 天然不会匹配到 `p`，`parent_code` 保持初始的 `NULL`，无需单独处理）。
2. 再新增一条 Flyway 脚本 `V11__org_parent_code_metadata_field.sql`（Decision 6）：往 `tab_metadata_field` 插入一条 `bizType=ORG`、`columnName=parent_code` 的种子记录，写法与 `V1__init_schema.sql` 里 ORG 其余字段的种子记录、`V9__metadata_field_role_seed.sql` 保持一致的列顺序与风格。
3. 应用层改动（实体/DTO/Service/Mapper/前端）与两个迁移脚本一起随本 change 发布，不需要灰度或分阶段上线——纯新增字段与纯新增种子数据，不影响任何既有读写路径的现有行为。
4. 回滚：如需回滚，`DELETE FROM tab_metadata_field WHERE table_name = 'tab_org' AND column_name = 'parent_code';` 后 `ALTER TABLE tab_org DROP COLUMN parent_code;` 即可，不影响其他列/记录。

## Open Questions

- 创建/更新组织时，若请求携带的 `parentId` 在管辖范围校验通过后、实际查询父记录时发现该 `parentId` 不存在（数据竞态或脏数据），当前既有代码路径没有对此显式校验（`assertParentOrgInScope` 只校验管辖范围，不校验记录是否存在）；本变更新增的 `orgMapper.selectById(parentId)` 会自然获得 `null`，此时按 Decision 3 的口径把 `parentCode` 置为 `null` 即可，不新增额外的"父组织不存在"报错——是否需要借本变更顺手补上这个既有校验空洞，留给 tasks 阶段与用户确认，默认不在本变更范围内（不属于本次需求的动机）。
