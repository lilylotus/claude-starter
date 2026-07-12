## Why

组织管理列表页当前把审计字段（新增人/新增时间/更新人/更新时间）直接摆在表格里，占用了大量横向空间（也是导致此前操作按钮被挤出视口的原因之一），但日常浏览列表时并不需要逐行看这些信息；这些信息更适合放进"查看详情"这种按需展开的场景。同时组织目前没有自由文本备注字段，无法记录创建/维护该组织时的补充说明。

## What Changes

- 组织管理列表表格中移除"新增人""新增时间""更新人""更新时间"四列，不在列表里展示。
- 新增"组织详情"只读查看能力：在操作列新增"详情"入口，展示组织的完整信息，包括组织名称、编码、上级组织、状态、显示序号、备注，以及新增人、新增时间、更新人、更新时间。
- 组织新增字段"备注"（`remark`），在新增/编辑表单中可填写，创建组织表也随之新增该列。
- 后端：`tab_org` 表新增 `remark` 列（Flyway 新迁移脚本），`OrgEntity`/`OrgCreateRequest`/`OrgUpdateRequest`/`OrgVO` 均补充该字段。

## Capabilities

### New Capabilities
（无——本次是对已有 `org-management` 能力的扩展，不引入新能力。）

### Modified Capabilities
- `org-management`：
  - "组织管理前端界面"需求的列表列集合变更（移除审计字段列），新增"查看组织详情"场景。
  - "新增组织""更新组织""组织详情查询"需求补充 `remark` 字段的读写行为。

## Impact

- **后端代码**：`backend/src/main/resources/db/migration/`（新增迁移脚本）、`OrgEntity`、`OrgCreateRequest`、`OrgUpdateRequest`、`OrgVO`、`OrgConvert`（MapStruct 映射补充 `remark`）。`OrgService`/`OrgController` 无需新增接口——现有 `GET /api/orgs/{id}` 已经返回完整 `OrgVO`（含审计字段），补上 `remark` 即可同时支撑编辑回填和详情展示。
- **前端代码**：`frontend/src/types/org.ts`（`OrgRow`/`OrgFormRequest` 补充 `remark`）、`frontend/src/api/org.ts`（如需要）、`frontend/src/views/identity/org/OrgManagementView.vue`（移除 4 个审计字段列、新增只读详情弹窗、表单新增备注输入框）。
- **规格**：`openspec/specs/org-management/spec.md` 中"组织管理前端界面""新增组织""更新组织""组织详情查询"四个需求的更新。
- **风险**：`remark` 是新增的可选字段，向后兼容；移除列表列不影响后端接口契约（`GET /api/orgs/children` 仍然返回完整 `OrgVO`，只是前端不渲染这几列），因此不是 breaking change。
