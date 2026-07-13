## 1. 文案改名："认证类型" → "任职类型"

- [x] 1.1 `UserManagementView.vue` 任职子表单：`el-form-item label="认证类型"` 改为 `label="任职类型"`
- [x] 1.2 `UserManagementView.vue` 任职子表单：`el-select placeholder="请选择认证类型"` 改为 `placeholder="请选择任职类型"`
- [x] 1.3 `UserManagementView.vue` 任职子表单：必填校验规则 `positionTypeRule` 的 `message: '请选择认证类型'` 改为 `'请选择任职类型'`
- [x] 1.4 `UserManagementView.vue` 详情弹窗任职记录表格：`el-table-column label="认证类型"` 改为 `label="任职类型"`
- [x] 1.5 顺带更新相邻的代码注释（如"认证类型下拉框"注释）以保持与新文案一致

## 2. 修复任职子表单必填字段标签换行

- [x] 2.1 `UserManagementView.vue` 任职子表单中"所属组织"字段的 `label-width` 由 `76px` 改为 `90px`
- [x] 2.2 同一子表单中"任职类型"（原"认证类型"）字段的 `label-width` 由 `76px` 改为 `90px`
- [x] 2.3 确认子表单其余非必填字段（"任职地址""任职电话""显示序号""备注"）的 `label-width` 保持 `76px` 不变

## 3. 组织下拉树默认全部收起

- [x] 3.1 `UserManagementView.vue`：移除此前为"仅展开第一层级"新增的 `computed` `orgTreeDefaultExpandedKeys`（不再需要任何默认展开节点的派生状态）
- [x] 3.2 "所属组织" `el-tree-select` 移除 `default-expand-all`，且不再绑定 `default-expanded-keys`，使树默认全部收起、只展示顶层节点

## 4. 验证

- [x] 4.1 `npm run build`（`vue-tsc -b && vite build`）通过，无类型错误
- [x] 4.2 本地启动前端，打开用户管理页面新增/编辑弹窗，添加至少一条任职记录，确认"所属组织""任职类型"标签在必填星号下不换行、两列对齐，且不再出现"认证类型"字样（headless Chrome 截图核实：两标签 `.el-form-item__label` 渲染高度均为 32px，与单行 line-height 一致，未发生换行）
- [x] 4.3 打开某一用户详情弹窗，确认任职记录表格列标题为"任职类型"（截图 + DOM 断言核实表头文案为"任职类型"，不再是"认证类型"）
- [x] 4.4 打开新增/编辑弹窗，添加一条任职记录并点开"所属组织"下拉树，确认所有层级默认收起、只展示顶层组织节点，点击有子节点的组织可正常手动逐层展开（真实组织树含多层嵌套数据，截图确认下拉面板打开后只展示 3 个顶层节点、均为收起状态，未自动展开任何子层级）
