## Why

两个已上线页面在实际使用中暴露出体验问题，需要修正：一是角色管理新增/编辑弹窗里的权限点勾选树默认全部展开（`default-expand-all`），权限点层级较多时弹窗一打开就是一长串展开节点，不利于快速定位；二是用户管理列表的"备注"列内容长度不受限，一旦用户填了较长备注，该列会把整行撑宽，挤压其他列的展示空间。这两处都是对已归档能力（`role-management`、`user-management`）的维护性修正，需要在 OpenSpec 里补记并推进，而不是绕开流程直接改代码。

## What Changes

- 角色管理新增/编辑弹窗的权限点勾选树（`RoleManagementView.vue`）移除 `default-expand-all`，改为默认全部收起，仅展示第一层分组节点，用户按需手动逐层展开；已勾选状态不受展开/收起影响，仍按角色已有权限正确回显勾选。
- 用户管理列表（`UserManagementView.vue`）"备注"列的展示值超过 6 个字符时，截断显示并追加省略号，不再撑宽表格行；鼠标悬停在被截断的备注文本上时，通过 tooltip 展示完整备注内容。仅对 `columnName === 'remark'` 的动态列生效，不影响其余基于字段定义动态渲染的列。

## Capabilities

### New Capabilities
（无——本次不引入新能力。）

### Modified Capabilities
- `role-management`：「角色权限点勾选树的模块标签展示」相关需求补充场景，约束权限点勾选树弹窗打开时默认全部收起。
- `user-management`：「用户管理前端界面」需求补充场景，约束用户列表"备注"列超长时截断并通过 tooltip 展示完整内容。

## Impact

- **前端代码**：`frontend/src/views/permission/role/RoleManagementView.vue`（权限点勾选树移除默认展开全部）；`frontend/src/views/identity/user/UserManagementView.vue`（备注列截断 + tooltip，新增 `.user-cell--truncate` 样式）。
- **后端代码**：无改动。
- **规格**：`openspec/specs/role-management/spec.md`、`openspec/specs/user-management/spec.md` 补充对应场景描述。
- **风险**：纯前端交互/展示改动，风险低；需要人工核对权限点树默认收起后已勾选权限仍正确回显，以及备注列截断后 tooltip 展示完整文本、不影响未超长备注和其他动态列的正常显示。
