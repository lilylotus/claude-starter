## 1. 组织批量导入后左侧导航树刷新

- [x] 1.1 `frontend/src/stores/org.ts` 新增 `navTreeVersion` ref（初始值 0），不新增
      action，直接在 store 里暴露该 ref 供组件读写
- [x] 1.2 `OrgManagementView.vue` 的 `handleImported()` 改为：右侧表格仍调用
      `orgStore.refreshAfterMutation()`；左侧树不再调用 `refreshNavTreeAfterMutation()`，
      改为 `orgStore.navTreeVersion++`
- [x] 1.3 模板里给 `el-tree` 补上 `:key="orgStore.navTreeVersion"`
- [ ] 1.4 手动验证：在组织管理页面选中/展开某个非顶级节点，执行一次批量导入（Excel 中
      含有该节点下的新子组织，以及其他分支下的组织），关闭弹窗后确认左侧树能看到新组织
      （树会收起展开状态，这是预期行为），无需切换菜单

## 2. 组织批量导入：先完整解析再按父子依赖排序处理

- [x] 2.1 `BatchImportServiceImpl` 拆分现有 `processDataRows`：新增一步"解析全部数据行为
      `List<行号+fieldCode->文本 Map>`"，与"按顺序执行 `processRow`"分离
- [x] 2.2 新增私有方法（如 `sortOrgRowsByParentDependency`），仅当 `bizType=ORG` 时对
      解析结果做拓扑排序：以 `code` 为节点、`__parentCode`（非 `"0"` 且能在批次内的
      `code` 集合中找到匹配时）为父→子有向边，Kahn 算法排序；无法参与排序（成环）的行
      直接组装为失败明细（"上级组织编码与文件内其他行形成循环引用，无法确定导入顺序"），
      从后续执行列表中剔除
- [x] 2.3 非 ORG 场景保持原始文件行序，不受影响
- [x] 2.4 按排序结果（或原始顺序）依次调用 `importRowExecutor.processRow`，成功/失败计数
      与失败明细汇总逻辑不变
- [x] 2.5 新增/更新 `BatchImportServiceImplTest` 用例：ORG 场景下子组织行排在其上级组织
      行之前，两行都应导入成功；构造一个循环引用场景（如两行互为上下级），断言两行都判定
      失败且原因符合预期；非 ORG 场景（如 POSITION）文件行序保持不变的既有用例继续通过
- [x] 2.6 `./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"` 通过

## 3. 操作日志新增"操作来源"字段，区分 Excel 导入与界面操作

- [x] 3.1 新增 Flyway 迁移，为 `tab_operation_log` 增加 `operate_source` 列
      （`INT NOT NULL DEFAULT 0 COMMENT '操作来源：0=界面操作，1=Excel导入'`）
- [x] 3.2 新增常量类 `cn.nihility.rbac.operationlog.constant.OperationSource`
      （`MANUAL=0`/`IMPORT=1`，`label()` 方法，写法比照 `OperationType`）
- [x] 3.3 新增 `cn.nihility.rbac.operationlog.context.OperationSourceContext`
      （`ThreadLocal<Integer>` 封装 `mark`/`currentOrDefault`/`clear`）
- [x] 3.4 `OperationLogEntity` 新增 `operateSource` 字段；
      `OperationLogRecorderImpl.record(...)` 写入时补上
      `.operateSource(OperationSourceContext.currentOrDefault())`
- [x] 3.5 `ImportRowExecutor.processRow` 方法体最外层用 try/finally 包裹：进入时
      `OperationSourceContext.mark(OperationSource.IMPORT)`，`finally` 里
      `OperationSourceContext.clear()`
- [x] 3.6 `OperationLogVO`/`OperationLogDetailVO` 新增 `operateSource`/
      `operateSourceLabel` 字段；`OperationLogConvert` 对这两个新字段做
      `@Mapping(target = ..., ignore = true)`（`operateSource` 走默认字段名映射可不用
      ignore，`operateSourceLabel` 需要 ignore，与 `operationTypeLabel` 的既有处理方式
      对齐，实现时以编译结果为准）；`OperationLogQueryServiceImpl` 的 `getPage`/`getById`
      补上 `vo.setOperateSourceLabel(OperationSource.label(vo.getOperateSource()))`
- [x] 3.7 前端 `src/types/operationLog.ts` 新增 `OPERATION_SOURCE_MANUAL`/
      `OPERATION_SOURCE_IMPORT` 常量与 `operateSource`/`operateSourceLabel` 字段（加入
      `OperationLogRow`/`OperationLogDetail` 接口）
- [x] 3.8 `OperationHistoryPanel.vue` 时间线卡片头部，在既有操作类型 `el-tag` 旁，
      `operateSource === OPERATION_SOURCE_IMPORT` 时追加一个次要样式的标签，文案
      "Excel 导入"；界面操作不展示该标签
- [x] 3.9 `OperationLogManagementView.vue` 列表页同样在操作类型列（或紧邻列）补上来源
      标签，展示规则与 3.8 一致
- [x] 3.10 新增/更新单元测试：改为在 `ImportRowExecutorTest` 新增两个用例（`processRow`
      处理期间——在打桩的 `orgService.create` 内部捕获——`OperationSourceContext
      .currentOrDefault()` 等于 `OperationSource.IMPORT`；处理成功返回后、以及处理抛出
      异常后标记均恢复为 `OperationSource.MANUAL`）验证标记生效与 `finally` 清除的不变量；
      `OperationLogRecorderImplTest`（已存在）未新增 `operateSource` 相关用例——该类未打桩
      `OperationSourceContext`，`record(...)` 私有方法对其读取的行为已由
      `ImportRowExecutorTest` 的新用例间接覆盖，重复覆盖价值不大
- [x] 3.11 `./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.operationlog.*"`、
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"` 均已执行通过；
      `npm run build` 属于前端范围，由并行处理前端部分的 agent 负责验证，本次未执行

## 4. 文档同步

- [x] 4.1 更新 `openspec/specs/excel-import-export/spec.md`：新增/修改批量导入处理顺序
      相关需求条目（ORG 拓扑排序 + 循环引用失败场景），修改管理页面导入入口需求条目
      反映左侧树刷新方式调整（该 change 目录下 `specs/excel-import-export/spec.md`
      spec delta 已核对，条目描述与后端实际实现一致，未发现出入）
- [x] 4.2 更新 `openspec/specs/operation-log-management/spec.md`：修改"操作日志手动记录"
      需求补充 `operate_source` 字段，新增"操作历史展示区分操作来源"需求（该 change 目录下
      `specs/operation-log-management/spec.md` spec delta 已核对，条目描述与后端实际实现
      一致，未发现出入）
- [x] 4.3 实现完成后，按 `.claude/agents/openspec-doc-sync.md` 约定，对照真实 diff/
      测试结果核对并更新本 change 的 `tasks.md`/`design.md`/`proposal.md`（独立复核了
      4.1/4.2：两个 spec delta 文件的场景描述、字段名、失败原因文案与
      `BatchImportServiceImpl`/`ImportRowExecutor`/`OperationLogRecorderImpl` 等实际
      代码逐字核对一致，未发现出入；`design.md` 决策 1/2/3 描述的实现位置、字段名、
      算法与实际代码一致；迁移文件确认只新增 `V30`，`V1`~`V29` 未被改动；重新执行了
      `./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*" --tests
      "cn.nihility.rbac.operationlog.*"`、`npm run build`，均通过）

## 5. 测试与验证

- [x] 5.1 后端：批量导入排序/循环引用检测、操作日志来源标记的单元测试（见 2.5、3.10）
- [x] 5.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过（前端 agent 在实现
      任务 1/3.7~3.9 后已验证通过，此时后端 `operateSource`/`operateSourceLabel` 字段
      尚未落地，`OperationLogRow` 上这两个字段在编译期按 TS 接口存在，运行期值为
      `undefined`，`=== OPERATION_SOURCE_IMPORT` 严格比较天然为 `false`，不影响类型检查
      或运行时报错）
- [ ] 5.3 手动验证：组织管理页面用一份"子组织行在前、上级组织行在后"的乱序 Excel 批量
      导入，确认全部成功；查看该批导入产生的组织的"操作历史"面板与独立"操作日志管理"页面，
      确认新增记录标记为"Excel 导入"来源，界面手动新增的其他记录不带该标记
