## 1. 父组件：角色选项数据源

- [x] 1.1 `ProcessDesignerView.vue` 新增 `roleOptions` ref，`onMounted` 里并行调用 `roleApi.getRoleOptions()` 加载（与 `loadConditionFieldOptions()` 并列，互不阻塞，失败时退化为空数组不阻断设计器加载）
- [x] 1.2 `NodePropertyPanel` 新增 `role-options` prop，`ProcessDesignerView.vue` 里传入

## 2. 指定人员（USER）：多选远程搜索

- [x] 2.1 `NodePropertyPanel.vue` 新增 `remoteSearchUsers(query)`，按 design.md Decision 1 的 `MOBILE_PATTERN` 规则调用 `GET /api/users`（`name` 或 `mobile` 参数）
- [x] 2.2 新增按 `node.id` 变化的 watcher：`assigneeType=USER` 且已有 `assigneeValue` 时，对本地选项缓存里缺失的 id 并发调用 `GET /api/users/{id}` 补齐姓名/手机号，合并进 `userOptions`
- [x] 2.3 模板改用 `el-select multiple filterable remote`，选项标签 `手机号存在时 "${name}（${mobile}）" 否则 "${name}"`；选中值数组 join 逗号写回 `node.data.assigneeValue`，回显时把 `assigneeValue` 按逗号 split 成数组传给 `el-select` 的 `model-value`
- [x] 2.4 去掉原先"用户 id"相关的 label/placeholder 文案

## 3. 指定角色（ROLE）与组织负责人系"要求的管理员角色"：本地筛选单选

- [x] 3.1 抽出一段共用的角色选择 `<el-select filterable>` 模板/局部函数，选项遍历 `roleOptions`，标签 `"${name}（${code}）"`，`value` 为 `code`
- [x] 3.2 `assigneeType==='ROLE'` 分支使用该控件，`required`，label 改为"审批角色"一类不含"编码"字样的文案
- [x] 3.3 `assigneeType` 为 `ORG_LEADER`/`APPLICANT_DEPT_LEADER`/`APPLICANT_DEPT_PARENT_LEADER` 的分支同样改用该控件，保持选填，label 沿用"要求持有的角色"一类措辞并保留"不填时使用默认角色"的提示

## 4. 指定岗位（POSITION）：补齐缺失的选择控件

- [x] 4.1 新增 computed 从 `conditionFieldOptions` 中查找 `bizType==='POSITION' && fieldCode==='positionType'` 的字段选项
- [x] 4.2 新增 `assigneeType==='POSITION'` 分支，渲染 `el-select`，选项来自该字段选项的 `dictOptions`，`value` 绑定 `node.data.assigneeValue`
- [x] 4.3 该字段选项未找到时（`undefined`）展示空选项下拉 + 提示文案"岗位类型字典未配置或已停用，请联系管理员在表单字段管理中检查"

## 5. 拆分 v-if 分支结构

- [x] 5.1 按 design.md Decision 4 把原先合并的 `ROLE||USER` 与 `ORG_LEADER||APPLICANT_DEPT_LEADER||APPLICANT_DEPT_PARENT_LEADER` 两组分支拆分为 4 个独立分支（USER/ROLE/POSITION/组织负责人系），确保原有的其余表单项（会签模式、空审批人策略、操作权限开关等）不受影响

## 6. 验证

- [x] 6.1 `npm run build`（`frontend/` 目录下）确认 vue-tsc 类型检查通过
- [x] 6.2 启动前端 + 后端，打开流程设计器，手工验证：（本轮环境不具备真实登录态 + MySQL 数据库联调环境，经用户确认视为已满足，予以勾选并归档）
  - [x] 6.2.1 指定人员：按姓名搜索多选 2 个用户，保存后重新打开节点，确认回显姓名而不是 id
  - [x] 6.2.2 指定人员：按 11 位手机号搜索能搜到对应用户
  - [x] 6.2.3 指定角色：输入角色名称关键字和角色编码关键字都能筛出对应角色，选中后保存/重新打开能正确回显角色名称
  - [x] 6.2.4 指定组织负责人：角色选择控件可选可不选，行为与之前手输编码等价
  - [x] 6.2.5 指定岗位：下拉展示岗位类型字典项名称，选中后保存/重新打开能正确回显
  - [x] 6.2.6 发布一条引用了上述配置的流程，确认发布校验和运行时审批人解析行为与改动前一致（因为 DSL 取值语义未变）
