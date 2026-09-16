## Context

流程设计器（`frontend/src/views/workflow/designer/`）审批节点属性面板
`NodePropertyPanel.vue` 目前对"审批人来源"取值的处理方式：
- `USER`/`ROLE` 共用同一个 `el-input`，标签按类型显示"用户 id"或"角色
  编码"，要求使用者手工输入裸值（USER 支持逗号分隔多个 id，ROLE 只
  支持单个编码，对应后端 `UserAssigneeResolver`/`RoleAssigneeResolver`
  的解析逻辑）。
- `ORG_LEADER`/`APPLICANT_DEPT_LEADER`/`APPLICANT_DEPT_PARENT_LEADER`
  共用另一个 `el-input`，标签"要求的管理员角色"，同样要求手工输入角色
  编码（对应 `OrgLeaderAssigneeResolver` 等三个解析器共用的
  `AdminRoleLookupService`，取值语义与 `ROLE` 类型完全一致，都是"角色
  编码"）。
- `POSITION` 没有匹配任何 `v-if` 分支，属性面板不渲染任何输入控件——
  但 `PositionAssigneeResolver` 实际会读取 `assigneeValue` 作为
  `position_type` 字典编码去查任职记录，现状是这个值根本没有入口可填。

已经和用户确认过一个关键决策：`ROLE` 及组织/部门负责人系"要求的管理员
角色"这两处，**后端继续按角色编码存储和解析不变**（不切换为角色 id）。
原因：角色编码不仅被 `RoleAssigneeResolver` 使用，还被 Flowable 任务
候选人权限校验（`TaskAuthorizationService`/`WorkflowTaskServiceImpl` 的
`userHasRoleCode`）在运行时按编码动态判断，且 `V1__init_schema.sql` 里
已经有一条内置示例审批流程（主数据变更审批）把角色编码写死在种子数据
里；切换为角色 id 需要同时改运行时权限校验逻辑并补一条数据迁移脚本，
风险和范围明显超出"把输入框换成好用的选择器"这个本次要解决的问题。
因此本次是**纯前端改动**，不改任何后端文件、不改数据库。

## Goals / Non-Goals

**Goals:**
- `USER`：改为按姓名/手机号远程搜索的多选下拉，展示姓名，提交给 DSL 的
  值格式（逗号分隔用户 id）不变。
- `ROLE` 与组织/部门负责人系的"要求的管理员角色"：改为按名称/编码筛选
  的下拉，展示角色名称，提交给 DSL 的值仍是角色编码，不变。
- `POSITION`：补上目前完全缺失的选择控件，下拉展示岗位类型字典项名称，
  提交给 DSL 的值是字典编码。
- 面板文案里去掉"用户 id"这类直接暴露内部 id 概念的提示语。

**Non-Goals:**
- 不改 `ROLE`/组织负责人系角色字段的后端存储语义（仍是编码，不是 id），
  见上文决策说明。
- 不改 `USER`/`ROLE`/`POSITION` 三种类型各自的"单选还是多选"能力边界：
  `USER` 本来就支持多选（后端按逗号分隔解析），`ROLE`
  与组织负责人系角色字段、`POSITION` 后端解析器都只接受单个值，本次不
  扩展为多选（多选没有对应的后端解析支持，属于超出本次范围的功能）。
- 不新增后端接口；`USER` 搜索复用既有 `GET /api/users`
  （`name`/`mobile` 参数），`ROLE` 下拉复用既有 `GET /api/roles/options`，
  `POSITION` 下拉复用设计器已经在加载的条件字段选项（`GET
  /api/form-fields/render-schema?bizType=POSITION`），不增加新的网络
  请求路径。

## Decisions

### Decision 1：USER 类型改为远程搜索多选，复用 AdminManagementView 的搜索模式
参考 `frontend/src/views/permission/admin/AdminManagementView.vue` 里
"关联用户远程搜索选择器"的既有实现（`MOBILE_PATTERN = /^1\d{10}$/` 判断
输入是否形如手机号，命中则按 `mobile` 参数调用
`GET /api/users`，否则按 `name` 参数调用；两个参数后端是"与"关系，
一次只传一个），在 `NodePropertyPanel.vue` 内新增等价的
`remoteSearchUsers(query)` + `el-select multiple filterable remote`。

与 `AdminManagementView` 的单选场景不同，这里需要多选，且节点切换后
需要正确回显"已经选中、但当前搜索结果列表里没有"的用户（属性面板不会
在切节点时销毁重建，只是 `node` prop 变化）。因此新增一个按
`node.id` 变化触发的 watcher：当切换到 `assigneeType=USER` 且节点已有
`assigneeValue`（逗号分隔的 id 列表）时，对不在当前 `userOptions`
缓存里的 id 并发调用 `GET /api/users/{id}` 补齐姓名，合并进本地选项
缓存，保证 `el-select` 能正确渲染已选中用户的姓名标签而不是空白/id。

`el-select` 选项标签格式沿用 `AdminManagementView` 的既有约定：
`手机号存在时 "${name}（${mobile}）"，否则 "${name}"`。

### Decision 2：ROLE 与组织负责人系角色字段改为本地筛选下拉，数据源提升到父组件加载一次
角色总数是有界的主数据（不像用户那样量级不可控），复用现有
`GET /api/roles/options`（`roleApi.getRoleOptions()`，仅返回未删除且
启用的角色，含 `id`/`name`/`code`）一次性拉全量，不需要远程搜索防抖。

与条件字段下拉数据源 `conditionFieldOptions` 的既有架构一致（父组件
`ProcessDesignerView.vue` 统一拉取、以 prop 形式下发给纯展示的
`NodePropertyPanel.vue`），本次在 `ProcessDesignerView.vue` 的
`onMounted` 里新增一次 `roleApi.getRoleOptions()` 调用（与
`loadConditionFieldOptions()` 并列，互不阻塞），结果存入新的
`roleOptions` ref，作为新 prop `role-options` 传给
`NodePropertyPanel.vue`。

面板内新增一个 `el-select filterable`，选项标签为
`"${name}（${code}）"`，`value` 直接绑定角色 `code`（DSL 的
`assigneeValue` 存编码，不经过 id 转换，见 Non-Goals）。Element Plus
`filterable` 默认按选项渲染出的
`label` 文本做子串过滤，所以标签里拼上编码后，无论业务管理员输入角色
名称关键字还是编码关键字都能筛出目标角色，不需要自定义
`filter-method`。

`ROLE` 与"要求的管理员角色"两处共用同一段选择控件模板/逻辑（同一个
`role-options` 数据源、同样的标签格式），只是后者保持非必填（`v-else`
分支里不加 `required`，维持现状：不填时后端按
`WorkflowConstants.DEFAULT_ORG_LEADER_ROLE_CODE` 回退）。

### Decision 3：POSITION 类型复用已加载的条件字段选项，不新增请求
`ProcessDesignerView.vue` 的 `conditionFieldOptions`（已经作为 prop 传入
`NodePropertyPanel.vue`）里包含 `bizType='POSITION'` 的全部表单字段定义，
其中 `fieldCode='positionType'` 的一项就是 `position_type` 字典字段
（`controlType=3` 字典下拉，`dictOptions` 是该字典类型下的全部字典项）。

新增一个 computed
`positionTypeOption = conditionFieldOptions.find(opt => opt.bizType === 'POSITION' && opt.fieldCode === 'positionType')`，
在 `assigneeType === 'POSITION'` 分支渲染一个 `el-select`，选项遍历
`positionTypeOption?.dictOptions ?? []`（`label`/`value` 对），`value`
绑定 `node.data.assigneeValue`。

若 `positionTypeOption` 为 `undefined`（理论上不应发生，除非
`positionType` 字段定义被业务管理员在表单字段管理里停用/删除），下拉
选项为空列表，控件下方追加一行提示文案"岗位类型字典未配置或已停用，
请联系管理员在表单字段管理中检查"，不阻断其余表单编辑，与
`conditionFieldOptions` 加载失败时"退化为空列表，不阻断设计器主流程
加载"的既有容错风格一致。

### Decision 4：v-if 分支结构调整
现有模板按 `assigneeType` 取值用两组 `v-if`/`v-else-if` 分支
（`ROLE||USER` 一组，`ORG_LEADER||APPLICANT_DEPT_LEADER||
APPLICANT_DEPT_PARENT_LEADER` 一组）合并渲染同一个 `el-form-item`，
本次改动后 `USER` 和 `ROLE` 的控件形态不同（多选远程搜索 vs 本地筛选
单选），不能再共用同一个分支，拆成：
```
v-if="assigneeType === 'USER'"            → 多选远程搜索
v-else-if="assigneeType === 'ROLE'"       → 角色本地筛选单选
v-else-if="assigneeType === 'POSITION'"   → 岗位类型下拉
v-else-if="… ORG_LEADER/APPLICANT_DEPT_LEADER/
            APPLICANT_DEPT_PARENT_LEADER"  → 角色本地筛选单选（复用同一段模板）
```
`ROLE` 与组织负责人系两个分支控件形态相同，用同一段 `<el-select>`
模板（通过 Vue 模板复用/提取小函数减少重复），但保留各自独立的
`el-form-item label`/`required` 属性（`ROLE` 必填、组织负责人系选填），
不合并成一个分支，避免 `required` 语义混在一起难以维护。

## Risks / Trade-offs

- [USER 多选回显需要按 id 逐个调用 `GET /api/users/{id}`] → 单个审批
  节点配置的指定人员数量通常是个位数（会签场景一般不会有几十上百个
  固定审批人），并发调用可接受；不引入批量查询接口，避免为这一个前端
  展示需求新增后端 API（`GET /api/users` 目前没有 `ids` 批量参数）。
- [ROLE/组织负责人系角色字段继续按编码存储] → 编码被业务管理员在角色
  管理页面改名/改编码后，已发布的流程定义会静默解析不到人（现状已经
  如此，本次不引入新风险，也不修复这个既有风险——用户已确认这是本次
  改动范围之外的问题）。
- [POSITION 依赖 `positionType` 表单字段定义不被停用/删除] → 属于运维
  层面的既有约束（该字段本身在"任职管理"页面也依赖同一份定义渲染必填
  任职类型下拉），非本次改动引入的新依赖；渲染兜底为空列表 + 提示文案，
  不会导致整个属性面板崩溃。
