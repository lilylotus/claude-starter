## 1. 后端：内嵌任职子表单支持扩展字段

- [x] 1.1 `UserPositionRequest` 追加 `ext1`~`ext10`（`String`，风格对齐 `PositionCreateRequest`）
- [x] 1.2 `UserPositionVO` 追加 `ext1`~`ext10`（风格对齐 `PositionVO`）
- [x] 1.3 `UserConvert.java` 删除 `toPositionEntity`/`updatePositionEntity` 上 10 个 `@Mapping(target = "extN", ignore = true)`，改为让 MapStruct 按同名字段自动映射；同步更新/删除相关注释
- [x] 1.4 确认（并按需补齐）内嵌任职子表单在创建/更新用户时，对 `positions[]` 中每一项也执行 `bizType=POSITION` 的动态字段必填/正则/唯一性校验（复用 `PositionServiceImpl` 现有校验逻辑或抽取共享方法，不重复实现一套）

## 2. 前端：内嵌任职子表单渲染扩展字段

- [x] 2.1 `UserManagementView.vue` 引入 `useDynamicFormFields('POSITION')`，在任职信息子表单每一行按渲染元数据动态追加表单项（`showInCreate`/`showInEdit`/`controlType`/`editable`/`placeholder`）
- [x] 2.2 `blankPosition()` 初始化新增行时把 `ext1`~`ext10` 置为空字符串
- [x] 2.3 `openEditDialog` 回填既有任职记录到子表单行数据时，一并拷贝 `ext1`~`ext10`
- [x] 2.4 提交新增/编辑用户请求时，子表单每一行的 `ext1`~`ext10` 随 `positions[]` 一并提交

## 3. 后端：四个详情接口/详情文档确认扩展字段已返回

- [x] 3.1 确认 `OrgVO`/`UserVO`/`UserPositionVO`（详情内嵌任职）/`PositionVO`/`AppVO` 均已包含 `ext1`~`ext10` 并被各自详情接口正确返回（`UserVO`/`PositionVO`/`AppVO`/`OrgVO` 预期已支持，`UserPositionVO` 依赖任务 1.2）

## 4. 前端：四个详情页面展示扩展字段

- [x] 4.1 `OrgDetailView.vue` 接入 `useDynamicFormFields('ORG')`，遍历启用字段定义，以 `fieldName: 值` 追加渲染到 `<el-descriptions>`（字典下拉类型按 `dictOptions` 展示 `label`）
- [x] 4.2 `UserDetailView.vue` 接入 `useDynamicFormFields('USER')` 展示用户自身扩展字段；同时接入 `useDynamicFormFields('POSITION')` 为内嵌任职记录表格追加扩展字段列/展示
- [x] 4.3 `PositionDetailView.vue` 接入 `useDynamicFormFields('POSITION')`，展示该任职记录的扩展字段
- [x] 4.4 `AppDetailView.vue` 接入 `useDynamicFormFields('APP')`，展示该应用的扩展字段
- [x] 4.5 四个详情页统一规则：仅展示当前启用状态的字段定义（不受 `showInList`/`showInCreate`/`showInEdit` 过滤），无启用定义时不展示扩展字段区块

## 5. 后端：操作历史快照纳入扩展字段

- [x] 5.1 新增共享工具方法（如 `formfield` 包下 `FormFieldSnapshotSupport.appendExtFieldSnapshot`），入参为快照 map、`FormFieldDefinitionService.listActiveByBizType(bizType)` 结果、`Map<String,String>`（`ext1`~`ext10` 列名到当前实体对应值），按 `columnName` 匹配后以定义的 `fieldName` 为 key 写入快照
- [x] 5.2 `UserServiceImpl.toLogSnapshot` 追加调用（`bizType=USER`）
- [x] 5.3 `OrgServiceImpl.toLogSnapshot` 追加调用（`bizType=ORG`）
- [x] 5.4 `PositionServiceImpl.toLogSnapshot` 追加调用（`bizType=POSITION`）
- [x] 5.5 `AppServiceImpl.toLogSnapshot` 追加调用（`bizType=APP`）

## 6. 权限资源编码文件同步

- [x] 6.1 检查本次改动是否新增/删除了任何菜单或按钮资源（预期不涉及——只是既有页面内展示更多字段，不新增入口），若确有变化则同步更新仓库根目录 `权限资源.txt`（确认无新增/删除菜单或按钮，未新增入口，`权限资源.txt` 无需变更）

## 7. 验证

- [x] 7.1 后端：`cd backend && ./gradlew test`（含 `./gradlew build`，BUILD SUCCESSFUL）
- [x] 7.2 前端：`cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`，通过）
- [x] 7.3 手工验证：启动 backend（bootRun）+ frontend（dev server），登录后针对已配置的 `ORG`/`USER`/`POSITION`/`APP` 各一个 `ext1` 自定义字段，通过 API 与 Playwright 截图验证：新增用户时内嵌任职子表单能填写该字段；四个详情页均展示该字段值；编辑保存后四个模块的操作历史均能看到该字段的旧值→新值。验证过程中发现并修复了一个未在原 tasks 中列出的问题：内嵌任职子表单（`UserManagementView.vue`）与四个详情页（`OrgDetailView.vue`/`UserDetailView.vue`/`PositionDetailView.vue`/`AppDetailView.vue`）最初把 `positionFields.schema`/`orgFields.schema` 等未经过滤的"全部启用定义"渲染出来，导致已经硬编码展示的原有字段（如任职地址/任职电话/显示序号/备注、组织名称/编码等）与动态渲染重复出现；修复为只渲染 `columnName` 以 `ext` 开头的定义，重新构建前端确认无类型错误后复验通过。
