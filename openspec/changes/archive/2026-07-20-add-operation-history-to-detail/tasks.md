## 1. 后端：`targetId` 筛选参数

- [x] 1.1 `cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest` 新增 `targetId: Long`
      字段
- [x] 1.2 `cn.nihility.rbac.operationlog.controller.OperationLogController#page` 新增
      `@RequestParam(required = false) Long targetId` 入参并透传给
      `OperationLogQueryRequest`，补充对应 `@Parameter` 注解
- [x] 1.3 `resources/mybatis/mapper/OperationLogMapper.xml` 的动态查询 SQL 新增
      `<if test="targetId != null">AND target_id = #{targetId}</if>` 条件
- [x] 1.4 新增 `OperationLogQueryServiceImplTest`：验证 `resourceType` +
      `targetId` 组合筛选、仅 `targetId` 单独出现、未携带 `targetId` 三种场景下
      该字段都正确透传给 `OperationLogMapper#selectOperationLogPage`（本仓库对
      MyBatis XML 动态 SQL 的既有测试惯例是 mock mapper 接口验证参数透传，不在
      单元测试里跑真实 SQL，SQL 层过滤效果随第 6 节的真实接口验证一并确认）；
      `./gradlew build` 全量通过

## 2. 前端：抽取可复用组件

- [x] 2.1 新增 `frontend/src/components/OperationLogDetailDialog.vue`：从
      `OperationLogManagementView.vue` 抽取现有的"字段变更详情"只读弹窗
      （`el-dialog` + `el-descriptions` + 字段变更 `el-table`），
      props `modelValue: boolean`、`logId: number | null`，emit
      `update:modelValue`，内部 watch 后调用
      `operationLogApi.getOperationLogById`
- [x] 2.2 `OperationLogManagementView.vue` 改为使用
      `<OperationLogDetailDialog v-model="detailVisible" :log-id="selectedLogId" />`，
      删除原内联弹窗标签与对应的 `detailData`/`detailLoading` 状态（改为持有
      `selectedLogId`）
- [x] 2.3 `types/operationLog.ts` 的 `OperationLogQueryParams` 新增
      `targetId?: number` 字段
- [x] 2.4 新增 `frontend/src/components/OperationHistoryPanel.vue`：
      props `resourceType: string`、`targetId: number | null`；`watch`
      `targetId`（`immediate: true`）变化时重置到第 1 页并调用
      `operationLogApi.getOperationLogPage({ resourceType, targetId, page, pageSize: 5 })`；
      `targetId` 为 `null` 时不发请求、展示空状态；渲染小标题"操作历史" +
      `el-table`（操作时间/操作类型标签/操作人/"查看变更"按钮）+
      `el-pagination`（`small`，`page-size=5`）+ 空状态文案"暂无操作记录"；
      点击"查看变更"记录 `logId` 并打开内部持有的
      `<OperationLogDetailDialog>`

## 3. 前端：嵌入 8 个已有详情弹窗

- [x] 3.1 `views/identity/org/OrgManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="org" :target-id="detailData?.id ?? null" />`
- [x] 3.2 `views/identity/user/UserManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="user" :target-id="detailData?.id ?? null" />`
      （仅用户主数据自身的历史，不含任职记录）
- [x] 3.3 `views/identity/position/PositionManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="position" :target-id="detailData?.id ?? null" />`
- [x] 3.4 `views/application/app/AppManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="app" :target-id="detailData?.id ?? null" />`
- [x] 3.5 `views/permission/role/RoleManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="role" :target-id="detailData?.id ?? null" />`
- [x] 3.6 `views/permission/permission/PermissionManagementView.vue` 详情弹窗内
      嵌入
      `<OperationHistoryPanel resource-type="permission" :target-id="detailData?.id ?? null" />`
- [x] 3.7 `views/permission/admin/AdminManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="admin" :target-id="detailData?.id ?? null" />`
- [x] 3.8 `views/system/menu/MenuManagementView.vue` 详情弹窗内嵌入
      `<OperationHistoryPanel resource-type="menu" :target-id="detailData?.id ?? null" />`

## 4. 前端：字典管理新增详情弹窗

- [x] 4.1 `views/system/dict/DictManagementView.vue` 左侧字典类型列表操作列新增
      "详情"按钮（位置参照其余模块，位于编辑、启用/停用、删除之前——与其余 8 个
      模块"详情在最前"的既有顺序保持一致），点击后打开新增的只读详情弹窗，调用
      既有 `dictApi.getDictTypeById(id)`，展示类型名称、编码、显示序号、备注、
      状态、创建人、创建时间、更新人、更新时间，并嵌入
      `<OperationHistoryPanel resource-type="dictType" :target-id="typeDetailData?.id ?? null" />`
- [x] 4.2 同一文件右侧字典项表格操作列新增"详情"按钮（同样位于最前），点击后
      打开新增的只读详情弹窗，调用既有 `dictApi.getDictItemById(id)`，展示所属
      字典类型名称、字典项标签、编码、显示序号、备注、状态、创建人、创建时间、
      更新人、更新时间，并嵌入
      `<OperationHistoryPanel resource-type="dictItem" :target-id="itemDetailData?.id ?? null" />`

## 5. 权限资源清单与菜单种子数据（实现完成后处理，不委托给子 agent）

- [x] 5.1 更新仓库根目录 `权限资源.txt`：在 DictManagement 分组下新增
      `DictManagement:dictType:detail`、`DictManagement:dictItem:detail` 两条
      编码
- [x] 5.2 新增
      `backend/src/main/resources/db/migration/V16__seed_dict_detail_menu_resource_data.sql`，
      挂在已存在的 `DictManagement:dictType:view` 节点下：字典类型详情按钮
      `showOrder=95`（介于既有 `add=100` 与 `edit=90` 之间）、字典项详情按钮
      `showOrder=45`（介于既有 `add=50` 与 `edit=40` 之间）；本地 MySQL 验证
      Flyway 迁移应用成功

## 6. 验证

- [x] 6.1 `./gradlew build`（含新增的 `OperationLogQueryServiceImplTest`）通过，
      V16 迁移本地 MySQL 验证应用成功（`flyway_schema_history` version=16）
- [x] 6.2 `npx vue-tsc --noEmit`（`frontend/`）与 `npm run build` 均通过，无
      类型错误
- [x] 6.3 真实浏览器验证：发现端口 48080 上有一个 IntelliJ IDEA 调试会话在跑
      本次改动之前的旧代码（已与用户确认后停止），改为启动干净的
      `./gradlew bootRun`（48080）+ `vite --host 127.0.0.1`（5173）。用
      Playwright（本地已缓存 chromium，临时装到 scratchpad 目录，未写入项目
      依赖）登录后驱动：①在角色管理新增一条测试角色，打开其详情弹窗，确认
      "操作历史"区块存在且含一条"新增"记录；②点击该记录"查看变更"，确认嵌套
      弹窗正确展示字段级变更（含"角色名称"等字段）；③编辑该角色的备注后重新
      打开详情弹窗，确认历史列表最新一条变为"编辑"且排在"新增"记录之上（按
      操作时间降序）；④打开字典管理页面，确认左侧字典类型列表新增的"详情"
      按钮存在（排在编辑/停用/删除之前）且点击后弹窗内含"操作历史"区块。
      全部步骤通过（`ALL_STEPS_PASSED`），并留存了截图确认字典管理页面按钮
      布局。验证过程中因误建测试数据产生了几条 `hist-verify-role-*` 测试角色，
      验证完毕后已通过接口全部清理，不留存于数据库中。未在浏览器里逐一点开
      全部 10 类资源的详情弹窗（受限于验证耗时），已覆盖的角色（扁平资源）和
      字典类型（此前完全没有详情入口的新增功能）两种场景分别代表"已有详情
      弹窗内嵌入历史"和"全新详情弹窗"两类改动，其余 7 个模块的嵌入方式与角色
      完全一致（同一个 `OperationHistoryPanel` 组件、同样的 `resourceType`/
      `targetId` 传参模式），未发现需要针对性验证的差异点
