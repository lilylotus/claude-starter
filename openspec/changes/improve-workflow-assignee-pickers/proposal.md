## Why

流程设计器"审批"节点属性面板里，审批人来源为"指定人员"/"指定角色"/"指定组织负责人"/"发起人部门负责人"/"发起人部门上级负责人"时，目前都要求业务管理员手工输入一串用户 id 或角色编码，而使用者根本不知道用户 id 是多少；"指定岗位"时更是完全没有任何输入控件，无法知道当前配置的是哪个岗位类型。这些都是纯前端可用性问题，导致流程设计器对非技术背景的业务管理员不友好。

## What Changes

- "指定人员"（`assigneeType=USER`）：输入框改为可按姓名或手机号远程搜索的多选下拉，下拉展示用户姓名（含手机号辅助区分同名），选中后底层仍提交用户 id（逗号分隔），沿用现有 DSL 字段格式，不改后端。
- 上述改动顺带去掉面板里"用户 id"这个提示用户 id 的文案，改为"人员"/"用户"一类不暴露内部 id 概念的措辞。
- "指定角色"（`assigneeType=ROLE`）：输入框改为可按角色名称或角色编码筛选的下拉（数据源复用已有的 `GET /api/roles/options`），展示角色名称，选中后底层仍提交角色编码——**不改后端存储语义**（角色编码在本系统里就是角色的稳定业务标识，Flowable 任务候选人权限校验等运行时逻辑也按编码判断，切换为角色 id 属于另一个更大范围的改动，本次不做）。
- "指定组织负责人"/"发起人部门负责人"/"发起人部门上级负责人"（`assigneeType` 为 `ORG_LEADER`/`APPLICANT_DEPT_LEADER`/`APPLICANT_DEPT_PARENT_LEADER`）的"要求持有的管理员角色"字段：复用同一个角色下拉选择器，同样只改前端展示方式，底层仍提交角色编码，不改后端。
- "指定岗位"（`assigneeType=POSITION`）：补上此前完全缺失的输入控件，改为下拉选择，数据源直接复用设计器已经加载好的"条件字段"选项里 `POSITION` 业务类型下 `positionType` 字段的字典选项（`position_type` 字典类型），不需要新增接口调用。

## Capabilities

### Modified Capabilities
- `workflow-process-designer`：审批节点属性面板里"指定人员"/"指定角色"/"指定组织负责人"及其两个同源类型/"指定岗位"五类审批人来源的取值输入方式，从"手工输入 id/编码裸文本"改为"按名称/编码检索的下拉选择控件"，DSL 字段的取值语义（USER 存逗号分隔的用户 id、ROLE 及组织/部门负责人系的管理员角色仍存角色编码、POSITION 存 `position_type` 字典编码）保持不变。

## Impact

- 改动文件：`frontend/src/views/workflow/designer/panels/NodePropertyPanel.vue`（核心改动）、`frontend/src/views/workflow/designer/ProcessDesignerView.vue`（新增一次性加载角色选项并传入面板）。
- 不涉及后端任何改动（不改 `backend/`、不改数据库、不改 `build.gradle`）。
- 不涉及权限资源编码清单 `权限资源.txt`（不新增页面/按钮，只是已有表单控件的展示方式变化）。
