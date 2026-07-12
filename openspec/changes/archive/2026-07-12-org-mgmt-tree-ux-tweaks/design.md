## Context

组织管理是已经实现的能力（见 `openspec/specs/org-management/spec.md`），前后端都已上线：后端 `OrgController#children` 一次性返回全部直属子组织，前端 `OrgManagementView.vue` 依赖 `orgStore.selectedId` 是否为 `null` 来决定右侧表格是否展示数据，且 `treeSelectData` 里手工拼接了一个 `id=0` 的虚拟"顶级组织"节点用于新增/编辑时表示 `parentId=0`。本次改动在这个既有实现之上做四处调整（默认展示、分页、禁用虚拟顶级节点、动态标题），确认过的决策：分页采用后端真分页；新增时上级组织选择器只禁用虚拟顶级节点本身，真实存在的顶级组织节点仍可选。

## Goals / Non-Goals

**Goals:**
- 进入组织管理页面即展示顶级组织的分页列表，不要求用户先点击树节点。
- `GET /api/orgs/children` 支持 `page`/`pageSize` 分页查询并返回 `total`，前端顶级列表和"选中节点的下级组织"列表复用同一套分页机制。
- 新增组织时，上级组织选择器里代表"新建一个新顶级组织"的虚拟节点不可选，真实的顶级组织节点不受影响。
- 选中左侧树节点后右侧标题变为"[组织名称]下级组织"；未选中任何节点时标题为空白（即使此时右侧已经在展示顶级组织的默认列表）。

**Non-Goals:**
- 不改动 `GET /api/orgs/tree`（左侧树查询），树仍然一次性全量返回，不分页。
- 不改动组织的新增/编辑/启停用/删除等写接口的业务规则（唯一性校验、子组织存在时禁止删除等）。
- 不引入通用的、可在其他模块复用的分页组件抽象；本次只解决组织管理这一个列表的分页需求。

## Decisions

### 1. 后端分页：新增通用 `PageResult<T>`，而非直接暴露 MyBatis-Plus 的 `Page<T>`
- `OrgServiceImpl#getChildren` 内部使用 MyBatis-Plus 的 `Page<OrgEntity>` 做 `selectPage` 查询（复用已有的 `IPage` 分页能力，无需手写 `LIMIT/OFFSET`）。
- 但 controller/service 对外的返回类型改用新增的 `cn.nihility.rbac.common.PageResult<T>`（字段：`records`、`total`、`page`、`pageSize`），不直接把 MyBatis-Plus 的 `Page<OrgEntity>`/`IPage` 类型暴露到 API 契约里。
- 理由：`common/` 目录已经是全局响应包装（`Result`）的落地位置，分页包装作为同级的通用响应结构更一致；避免把持久层框架类型（`IPage`）泄漏到 controller 签名和 OpenAPI 文档里。
- 备选方案：直接返回 `IPage<OrgVO>`——更省代码，但字段命名（`records`/`total`/`current`/`size`）和文档语义都绑定了 MyBatis-Plus，且未来如果统一别的分页列表（用户、角色）能少踩一次前后端字段对不上的坑，故不采用。

### 2. `GET /api/orgs/children` 直接改造为分页响应（Breaking），不做双写兼容
- `page` 默认 `1`，`pageSize` 默认 `10`；响应体从裸数组 `OrgVO[]` 变为 `PageResult<OrgVO>`。
- 前端（`api/org.ts` 的 `getOrgChildren`、`types/org.ts`、`stores/org.ts`）同步修改，不保留旧的裸数组返回形态。
- 理由：这是仓库内部前后端都由本项目维护的接口，没有外部调用方；引入版本兼容层（比如再加一个不分页的旧接口）纯属为不存在的调用方增加复杂度，直接破坏性变更即可，已在 proposal 里标注 **BREAKING**。

### 3. 前端默认态：把"顶级组织列表"当作一次隐式的 `fetchChildren(0)`，但不设置 `selectedId`
- `orgStore` 新增 `fetchChildren` 的分页参数（`page`/`pageSize`），并在 `onMounted` 时用 `parentId = 0` 主动拉取第一页数据填充右侧表格，同时**不**把 `selectedId` 置为 `0`（`selectedId` 保持 `null`，语义仍然是"左侧树没有被点击选中的具体节点"）。
- 右侧标题的展示规则直接依赖 `selectedId === null` 来判断是否显示空白，与"表格是否已经有数据"解耦——这样默认态下标题空白、表格却已经有顶级组织数据，符合已确认的需求 3 澄清（"默认首次进入没选左侧组织树时展示空白"指的是标题，不是表格数据）。
- 理由：如果把默认态也算作"选中了虚拟顶级节点"（`selectedId = 0`），左侧 `el-tree` 的 `current-node-key` 找不到匹配节点也不会高亮，但会让"新增"默认上级组织、标题文案等多处逻辑都要额外判断"是不是这个隐式的 0"，复杂度更高；不如让"标题"和"表格数据源（用于分页请求的 parentId）"各自独立管理。

### 4.（已废弃，见决策 6）上级组织选择器禁用虚拟顶级节点
> **此决策已被决策 6 反转，仅保留存档，不代表当前行为。**
- `treeSelectData` 里那个虚拟 `{ id: 0, name: '顶级组织', children: ... }` 节点固定加上 `disabled: true` 字段；`el-tree-select` 的 `:props` 补充 `disabled: 'disabled'` 映射，Element Plus 会原生把该节点渲染为不可选中（灰态），子节点（真实组织）不受影响，编辑模式下已有的 `pruneSubtree`（排除自身及子孙）逻辑不变、两者叠加即可。
- 理由：这是 Element Plus 树组件的原生能力，不需要额外写点击拦截逻辑；且该虚拟节点本身没有对应的真实组织数据，禁用它不影响任何真实组织的可选性。
- 备选方案：改为直接从 `treeSelectData` 里把虚拟节点整个移除——但移除后无法在编辑一个"当前 `parentId` 恰好为 0"的真实顶级组织时正确回显其上级组织为"顶级组织"这一说明性文案（详情页仍需要区分"顶级"和"某个具体组织"），保留虚拟节点、只禁用选中操作更稳妥。

### 6.（反转决策 4）恢复虚拟顶级组织节点的可选中能力，允许新增第一层级组织
- 决策 4 上线后用户实测发现：由于虚拟顶级节点被禁用，新增组织时完全无法创建"第一层级组织"（`parentId = 0`），这不符合实际业务需要——用户明确要求恢复该能力。
- 修复：移除 `treeSelectData` 虚拟节点上的 `disabled: true` 字段，同时去掉 `el-tree-select` 的 `:props` 里 `disabled: 'disabled'` 映射；虚拟顶级组织节点恢复为普通可选节点，选中后 `form.parentId` 为 `0`，新增/编辑后的组织即成为第一层级组织。
- 与此同时发现并修复了一个相关的真实 bug（与本决策同批修复，但成因独立）：`el-tree-select` 在不设置 `check-strictly` 的默认单选模式下，只有叶子节点点击才会真正触发选中，非叶子节点（已有子组织的真实组织）点击只会展开/收起——导致这些节点即使没有被禁用也"选不中"。修复方式是给 `el-tree-select` 加上 `check-strictly` 属性，使任意节点（无论是否有子节点、包括虚拟顶级节点）点击都能被选中。
- 编辑模式下已有的 `pruneSubtree`（排除自身及子孙，防止选择自身/子孙节点作为上级造成环）逻辑不变，继续生效——`check-strictly` 只影响"非叶子节点是否可点击选中"，不影响 `pruneSubtree` 对可选节点范围的裁剪。

### 5.（复核阶段新增）修复分页插件未注册导致的分页完全不生效问题
- **发现过程**：本次改动的复核阶段实际启动了本地可用的 MySQL 做真机验证（`bootRun` + curl 调用 `GET /api/orgs/children`），发现无论传什么 `page`/`pageSize`，响应里的 `total` 恒为 `0`，换页也拿到同一批数据——分页在真实数据库前完全不生效，仅凭代码走读和单元测试没有发现这个问题。
- **根因**：MyBatis-Plus 3.5.x 把 `PaginationInnerInterceptor` 依赖的 JSQLParser 拆到了独立的 `com.baomidou:mybatis-plus-jsqlparser` 模块；项目 `build.gradle` 里原本只有 `mybatis-plus-spring-boot3-starter`，既没有引入这个依赖，也没有注册 `MybatisPlusInterceptor` 这个 Spring bean。缺少分页插件时，MyBatis-Plus 对 `selectPage(Page, wrapper)` 不会报错，而是静默退化为普通查询——不追加 `LIMIT/OFFSET`，`total` 也不会被计算（恒为 0），这是本次改动之外、影响所有未来分页查询的一个存量缺陷，本次顺带在实现分页功能时发现并修复。
- **修复**（征得用户同意后进行）：
  - `backend/build.gradle` 新增一行依赖：`implementation 'com.baomidou:mybatis-plus-jsqlparser:3.5.16'`。
  - 新增 `cn.nihility.rbac.common.config.MybatisPlusConfig`，注册一个 `MybatisPlusInterceptor` bean，内部 `addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL))`。
  - 放在 `common/config/` 而不是 `org/` 模块下：这是一个跨模块的全局基础设施配置（任何未来的分页查询——用户、角色等——都依赖同一个 bean），`common/` 已经是全局响应包装（`Result`/`GlobalResponseAdvice`）的落地位置，新增一个同级的 `config` 子包保持"全局基础设施只有一处注册入口"，避免以后每个业务模块各自重复注册一遍拦截器、或者注册多份导致冲突。
- **验证**：修复后手动通过 API 创建了 3 条顶级组织，`page=1&pageSize=2` 与 `page=2&pageSize=2` 分别正确返回不同的 2 条/1 条记录，`total=3`；验证完成后已删除这些临时测试数据，恢复原状。`./gradlew test` 全部通过。

## Risks / Trade-offs

- [Risk] `GET /api/orgs/children` 是 Breaking Change，如果后续有其他前端/脚本直接调用这个接口会被打断 → Mitigation：目前只有本仓库的 `frontend/` 调用它，属地在改动范围内一并修改；proposal 中已显式标注 **BREAKING**。
- [Risk] 分页发生后，"表格操作触发的状态变更实时可见"这条既有 spec 场景（启用/停用/删除后刷新）如果不保留当前页码，用户操作完当前页最后一条数据后可能因为总数变化而跳页体验不连续 → Mitigation：刷新时保持当前 `page`，若当前页因为数据减少而超出新的总页数，则回退到最后一页（前端分页组件层面处理，不引入新接口）。
- [Risk] 默认态标题空白但表格已有数据，属于故意的不一致展示（表格早于标题"知道"要显示什么），后续如果有人只看代码容易误解为 bug → Mitigation：在 `OrgManagementView.vue` 里对应位置保留简短注释说明这是两个独立状态源（标题看 `selectedId`，表格数据看请求参数），避免被误"修复"。
- [已踩过的坑，非潜在风险] MyBatis-Plus 缺少分页插件时不会报错，而是静默返回未分页的全量结果、`total` 恒为 0——这个问题在本次复核阶段已经被发现并修复（见 Decision 5），记录在这里是为了提醒：如果以后有人误删了 `MybatisPlusConfig` 里的拦截器 bean 或者 `mybatis-plus-jsqlparser` 依赖，不会有编译期或启动期报错提示，只有跑分页接口时数据"看起来正常但页码不生效"，比较隐蔽，需要在代码评审中留意。
- [验证限制] 前端没有做浏览器可交互层面的验证（例如实际点击左侧树节点观察标题变化、打开新增弹窗观察虚拟顶级节点是否置灰不可选），因为当前环境未安装 Playwright/chromium-cli，且引入这类新依赖未跟用户确认过。已完成的验证是：`npm run build`（`vue-tsc` 类型检查 + `vite build`）通过、对最终代码逐行人工审查确认了标题计算、分页参数传递、`disabled` 字段映射等逻辑正确、并启动了真实的前端 dev server + 后端服务，通过 curl 验证了 `/api/orgs/children` 经由 vite 的 `/api` 代理能从后端正确取回分页数据（端到端链路打通）。但"点击树节点后标题实际渲染变化""新增弹窗里虚拟顶级节点实际呈现为灰态不可点"这两点尚未做可视化确认，如果后续要交付验收，建议至少手动在浏览器里操作一遍确认。

## Open Questions

无。
