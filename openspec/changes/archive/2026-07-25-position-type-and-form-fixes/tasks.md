## 1. 表单管理：任职类型加入承重字段锁定保护

- [x] 1.1 后端：在 `LockedFormFields.LOCKED_KEYS` 追加 `key(FormFieldBizType.POSITION, "position_type")`，
      更新类头注释中"目前仅覆盖 ORG/USER/APP"的表述，补充说明 POSITION 的 `position_type` 也在白名单内。
- [x] 1.2 后端：更新 `V1__init_schema.sql` 第 620~623 行"positionType 等字段不出现在元数据字段目录中，
      继续保持硬编码渲染"这条已过时的注释文字（不改动该脚本的 SQL 语句本身，仅修正注释描述，避免继续
      误导后续阅读者；若担心改动已应用历史脚本文件的合规性，可改为在新增的说明性注释/文档中标注替代）。
- [x] 1.3 验证：调用 `GET /api/form-fields/render-schema?bizType=POSITION`（或分页/详情接口）确认
      `positionType` 对应记录的 `locked=true`；调用停用/删除/取消必填接口确认均被拒绝（`LockedFormFieldException`）。
      **已通过本地启动的实例做真实 HTTP 调用验证**：`PUT .../12`（isRequired=false）→ 400 "承重字段的表单定义
      不允许配置为非必填"；`PUT .../12/disable` → 400 "承重字段的表单定义不允许停用"；`DELETE .../12` → 400
      "承重字段的表单定义不允许删除"。验证过程中发现本地开发数据库里该字段的 `isRequired` 此前（早于本次改动，
      2026-07-24 遗留）已被手动置为 `false`，与锁定字段"必填不可关闭"的不变量不一致；已通过合法的 PUT 调用把它
      改回 `true` 修正这条本地数据。
- [ ] 1.4 验证：前端"表单管理"页面切换到"任职"分类，确认 `positionType` 行展示"系统保护"标签，
      不提供删除/停用入口，编辑弹窗内"是否必填"/"是否新增表单展示"/"是否编辑表单展示"开关渲染为禁用态。
      **未做浏览器可视化验证**（本会话没有可用的浏览器自动化工具）；该渲染逻辑是 `FormFieldDefinitionPanel.vue`
      已有的通用 `locked` 展示分支，未在本次改动中修改，仅通过后端接口验证了它读取到的 `locked` 值正确
      （见 1.3），建议使用者用浏览器手动确认一遍视觉效果。

## 2. 任职管理：任职类型并入动态表单渲染，删除硬编码选择框

- [x] 2.1 前端：`PositionManagementView.vue` 删除独立的 `positionTypeOptions`/`fetchPositionTypeOptions()`
      及硬编码的 `<el-form-item label="任职类型">` 模板块与其单独的 `positionTypeRule` 校验规则。
- [x] 2.2 前端：`PositionManagementView.vue` 新增/编辑表单里 `positionAddress`/`positionPhone`/
      `showOrder`/`remark` 在改动前已经由 `positionFields.createFields`/`editFields` 循环驱动（并非
      design.md 决策 2 里估计的手写状态），本次只需要把 `positionType` 一起并入同一循环（排除
      `orgId`、`userId`、`status` 三个继续硬编码渲染的字段），复用该组合式函数已有的
      `buildFormModel`/`buildRules`/`buildSubmitModel` 逻辑；顺带发现并修复列表页"任职类型"列此前已经
      随 `positionFields.listColumns` 动态渲染、又被独立硬编码列重复渲染一次的问题，一并删除了重复列。
- [x] 2.3 前端：`PositionDetailView.vue` 删除独立的 `positionTypeOptions`/`positionTypeLabel()`，改为
      详情页扩展字段展示逻辑统一处理 `positionType`（复用该文件已有的字典编码→标签转换逻辑）。
- [ ] 2.4 验证：任职管理新增/编辑弹窗中"任职类型"下拉选项与此前一致（来自 `position_type` 字典启用项），
      不出现重复渲染两次的情况；详情页正确展示任职类型的字典标签而非编码值。
      **代码走查已确认**渲染路径正确、`npm run build` 通过；**未做浏览器可视化验证**（本会话没有可用的
      浏览器自动化工具），建议使用者用浏览器手动确认一遍新增/编辑弹窗与详情页效果。

## 3. 用户管理：任职子表单动态字段标签换行错位修复

- [x] 3.1 前端：`UserManagementView.vue` 的 `.user-position-row__fields` 内，把所有 `el-form-item` 的
      `label-width="90px"`/`label-width="76px"` 硬编码值统一改为 `label-width="auto"`
      （所属组织、任职类型、任职地址、任职电话、显示序号、备注、动态 `ext1`~`ext10` 字段均适用）。
- [x] 3.2 前端：新增 scoped 样式 `.user-position-row :deep(.el-form-item__label) { white-space: nowrap; }`，
      防止 `label-width="auto"` 情况下展示名称仍然发生换行。
- [ ] 3.3 验证：在"表单管理"页面为 `bizType=POSITION` 的某个 `extN` 字段配置一个超过 4 个汉字的展示名称
      （如"项目负责人角色"），打开用户新增/编辑弹窗的任职子表单，确认该标签单行完整显示、不与下方控件或
      相邻列重叠错位，且"所属组织""任职类型"两列仍保持对齐。
      **未做浏览器可视化验证**（本会话没有可用的浏览器自动化工具），只完成了 CSS/模板代码走查与
      `npm run build` 通过，建议使用者用浏览器手动确认一遍视觉效果。

## 4. 导入模板配置：关联字段下拉过滤已占用字段

- [x] 4.1 前端：`ImportFieldConfigPanel.vue` 新增函数（如 `fetchOccupiedFormFieldIds()`），在
      `openCreateDialog()`/`openEditDialog()` 内调用 `importFieldConfigApi.getImportFieldConfigPage`
      （`bizType` 为当前分类、`pageSize: 200`）取回当前 `bizType` 下全部有效导入字段配置，收集非空
      `formFieldDefinitionId` 为 `occupiedIds` 集合。
- [x] 4.2 前端：`availableFormFields` 计算/赋值逻辑在原有"状态为启用"过滤基础上，追加排除
      `occupiedIds` 命中的表单字段定义；`openEditDialog(row)` 中若 `row.formFieldDefinitionId` 非空，
      需从 `occupiedIds` 中剔除后再计算 `availableFormFields`，确保编辑态选择器仍展示并可选中该配置
      当前关联的字段。
- [ ] 4.3 验证：为某个 `bizType` 下的一个表单字段新增一条导入字段配置后，再次点击"新增"，确认该字段
      不再出现在"关联字段"下拉中；对刚新增的这条配置点击"编辑"，确认下拉中仍能看到并选中其当前关联字段。
      通过 `GET /api/import-field-configs?bizType=ORG` 确认了数据形状（固定标识列 `formFieldDefinitionId`
      为 `null`，符合过滤逻辑的假设）；**未做浏览器交互验证**（本会话没有可用的浏览器自动化工具），建议
      使用者用浏览器手动确认一遍下拉过滤效果。

## 5. 收尾

- [x] 5.1 检查本次改动是否新增/删除页面菜单或按钮；确认无需更新仓库根目录的 `权限资源.txt`
      （已核实：本次改动未新增/删除任何页面菜单或按钮，`权限资源.txt` 无需更新）。
- [x] 5.2 后端 `./gradlew test`（223 个测试全部通过）、前端 `npm run build`（vue-tsc 类型检查 + vite build）
      均通过。
- [x] 5.3 实现完成后，按 `openspec-doc-sync` 约定，基于实际 diff/测试结果核对并更新本 change 的
      `proposal.md`/`design.md`/`tasks.md`，不主动执行 `openspec-sync-specs`/归档（归档需用户手动触发）。
      **已完成**：design.md 已更正决策 2 里对现状的误判、补充顺带修复的重复列 bug、把 Flyway checksum
      风险落地为具体的 repair 步骤说明；proposal.md 核对后确认无需修改。
