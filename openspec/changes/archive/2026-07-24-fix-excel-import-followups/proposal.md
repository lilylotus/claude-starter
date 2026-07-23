## Why

`add-excel-import-export`（含后续两次组织 `parentId` 相关调整）归档后，用户在实际使用中发现
三个问题：

1. 组织批量导入完成、关闭弹窗后，左侧导航树与右侧表格没有反映刚导入的数据，必须切换到
   其他菜单再切回来（强制整页重新挂载）才能看到，体验很差且容易让人误以为导入失败。
2. 组织批量导入的 Excel 中，如果"组织编码"列与"上级组织编码"列的行顺序是乱序的（比如
   子组织所在行排在其上级组织所在行前面），会导致按文件原始行顺序逐行处理时，子组织行在
   其上级组织尚未被创建的情况下去匹配上级组织编码，找不到而判定失败——但那个上级组织其实
   就在同一份 Excel 的后面几行里，本应该能被正确导入。
3. 操作历史/操作日志里，通过 Excel 批量导入产生的新增、编辑记录，和管理员在页面上手动
   新增、编辑产生的记录，目前完全无法区分，不方便追溯一批数据到底是手动录入还是批量导入
   带来的。

## What Changes

- 组织管理页面的批量导入弹窗完成导入后，改为对左侧导航树做更彻底的刷新（不再局限于当前
  选中节点所在分支），确保任意层级、任意父组织下新导入的组织都能在不切换菜单的情况下正确
  展示；右侧表格的刷新逻辑保持不变（仍刷新当前 `currentParentId` 分支）。
- 组织批量导入引擎调整为"先完整解析 Excel 全部数据行，再决定处理顺序"：解析完成后，按
  "上级组织编码"列在本文件内部构造的父子依赖关系对数据行做拓扑排序，确保一个组织所在行
  在其上级组织所在行（若也在同一文件中）之后处理；文件内构成循环引用（如互为上下级）的
  行直接判定失败，不参与正常导入流程。其余业务对象类型（USER/POSITION/APP）不存在这种
  同批次内的父子依赖，处理顺序不变。
- 新增"操作来源"概念（`tab_operation_log` 表新增 `operate_source` 字段：0=界面操作，
  1=Excel 导入），批量导入引擎在调用组织/人员/任职/应用四个模块既有的创建/更新方法前，
  通过一个线程级标记声明"当前操作来自 Excel 导入"，操作日志记录组件据此在写库时记下操作
  来源，不改变 `OperationLogRecorder` 对外接口签名。操作历史时间线（各模块详情页内嵌的
  "操作历史"面板）与独立的"操作日志管理"页面在展示新增/编辑类型标签的同时，为操作来源是
  "Excel 导入"的记录追加一个额外的标识标签，界面手动操作的记录不展示该标签（保持现状）。

## Capabilities

### Modified Capabilities
- `excel-import-export`：批量导入处理顺序从"按文件行顺序逐行处理"调整为"ORG 类型下按
  文件内部父子依赖拓扑排序后处理"；管理页面导入完成后的刷新范围调整。
- `operation-log-management`：操作日志数据模型新增操作来源字段；操作历史/操作日志展示
  规则新增"标识 Excel 导入来源"的要求。

## Impact

- 后端：`cn.nihility.rbac.excelimport.service.impl.BatchImportServiceImpl` 调整为先解析
  全部数据行再确定处理顺序，ORG 场景新增拓扑排序与循环引用检测逻辑；新增 Flyway 迁移为
  `tab_operation_log` 增加 `operate_source` 列；`cn.nihility.rbac.operationlog` 模块新增
  操作来源常量类与线程级标记工具类，`OperationLogRecorderImpl` 写库时读取该标记；
  `cn.nihility.rbac.excelimport.service.support.ImportRowExecutor` 在 `processRow` 内
  声明/清除该标记。
- 前端：`frontend/src/stores/org.ts` 与 `OrgManagementView.vue` 调整批量导入完成后的
  左侧导航树刷新方式；`OperationHistoryPanel.vue`、`OperationLogManagementView.vue`、
  `src/types/operationLog.ts` 增加操作来源字段的展示。
- 文档：`openspec/specs/excel-import-export/spec.md`、
  `openspec/specs/operation-log-management/spec.md` 更新对应需求条目。
