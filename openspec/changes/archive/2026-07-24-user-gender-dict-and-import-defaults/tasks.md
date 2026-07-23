## 1. 数据库迁移

- [x] 1.1 新增迁移预置字典类型 `gender`（性别）及其字典项 `unknown`（未知，显示序号 1）/
      `male`（男，显示序号 2）/`female`（女，显示序号 3），写法比照 `V4__seed_dict_position_type.sql`
- [x] 1.2 新增迁移把 `tab_user.gender` 列由 `INT NOT NULL DEFAULT 0` 改为
      `VARCHAR(64) NOT NULL DEFAULT 'unknown'`：先 `ALTER TABLE ... MODIFY COLUMN`，
      再用 `UPDATE` 语句把历史遗留的 `'0'`/`'1'`/`'2'` 分别重映射为 `'unknown'`/
      `'male'`/`'female'`
- [x] 1.3 新增迁移为 `tab_metadata_field` 插入一条 `biz_type=USER`、
      `table_name=tab_user`、`column_name=gender`、`field_code=gender`、
      `column_type=VARCHAR(64)`、`field_name=性别` 的记录；为 `tab_form_field_definition`
      插入一条对应的默认字段定义（`control_type=3` 字典下拉，`dict_type_id` 指向 1.1
      新建的 `gender` 字典类型，`is_required=1`，`show_order=7`，其余展示/编辑属性与
      现有 USER 字段定义保持一致的默认值）

## 2. 后端：性别字段类型改造

- [x] 2.1 `UserEntity.gender` 类型由 `Integer` 改为 `String`
- [x] 2.2 `UserCreateRequest.gender`/`UserUpdateRequest.gender` 类型由 `Integer` 改为
      `String`，移除 `@NotNull`，改为 `@Size(max = 64, message = "性别长度不能超过 64 个字符")`，
      Java 默认值改为 `"unknown"`
- [x] 2.3 `UserVO.gender` 类型由 `Integer` 改为 `String`
- [x] 2.4 删除 `cn.nihility.rbac.user.constant.UserGender` 常量类
- [x] 2.5 `UserServiceImpl`：移除 `UserGender` 引用与 `genderLabel(Integer)` 私有方法，
      操作日志快照改为直接存入 `entity.getGender()` 原始编码（比照
      `PositionLogSnapshotSupport` 里 `positionType` 的既有写法，不做编码到中文标签的
      转换）
- [x] 2.6 确认 `UserConvert`（MapStruct）无需额外改动（字段名不变，类型从 Integer 变
      String 后仍是同名直通映射）；若编译报错再针对性修复

## 3. 后端：批量导入数值字段留空修复

- [x] 3.1 `ImportRowExecutor.bindProperties`：单元格文本为空白
      （`!StringUtils.hasText(value)`）且目标属性类型不是 `String`
      （`wrapper.getPropertyType(fieldCode) != String.class`）时跳过
      `setPropertyValue`，保留请求对象的 Java 默认值；其余情况行为不变
- [x] 3.2 新增/更新 `ImportRowExecutorTest` 用例：ORG/USER 任一 `bizType`，`showOrder`
      列在导入字段配置里标记为非必填、Excel 该列留空时，新增成功且落地 `showOrder`
      为请求对象声明的默认值（`0`）；字符串类型字段（如 `remark`）留空时仍然显式清空
      的既有行为不受影响（补一个用例覆盖，避免本次改动引入回归）

## 4. 前端：性别字段动态化

- [x] 4.1 `frontend/src/types/user.ts` 删除 `USER_GENDER_UNKNOWN`/`USER_GENDER_MALE`/
      `USER_GENDER_FEMALE`/`USER_GENDER_OPTIONS`；`gender` 字段类型由 `number` 改为
      `string`（`UserRow` 及其他引用处）
- [x] 4.2 `UserManagementView.vue`：移除 `genderLabel` 函数、性别专属的静态
      `<el-select>`/`<el-option>`、`form.gender` 的静态初始值与静态回填赋值、
      `rules.gender` 静态校验规则；`resetDynamicKeys(form, ['gender', 'positions'])`
      改为 `resetDynamicKeys(form, ['positions'])`；性别改由
      `useDynamicFormFields('USER')` 生成的动态字段（`createFields`/`editFields`/
      `listColumns`/`createRules`/`editRules`）接管，列表列的性别展示改用该组合式函数
      的 `dictOptionLabel` 输出
- [x] 4.3 `UserDetailView.vue`：移除 `genderLabel` 与 `USER_GENDER_OPTIONS` 引用，
      性别详情展示保持与手机号、身份证号等字段一致的手写 `<el-descriptions-item>`
      结构（详情页固有字段本来就不走 `v-for` 循环遍历 schema，只有 `ext1`~`ext10`
      走 `v-for`；`gender`/`mobile`/`idCard` 均是手写结构），改动点仅是取值方式从
      `genderLabel()` 静态查表改为 `userFields.dictOptionLabel(genderField, value)`
      按字典标签查找

## 5. 文档同步

- [x] 5.1 更新 `openspec/specs/user-management/spec.md`：性别字段的数据模型与渲染方式
      调整为字典驱动的动态字段
- [x] 5.2 更新 `openspec/specs/form-field-definition-management/spec.md`：新增人员
      性别的默认元数据字段/表单字段定义条目
- [x] 5.3 更新 `openspec/specs/dict-management/spec.md`：新增 `gender` 字典类型预置
      需求条目
- [x] 5.4 更新 `openspec/specs/excel-import-export/spec.md`：修正数值类型字段留空时的
      处理规则
- [x] 5.5 实现完成后，按 `.claude/agents/openspec-doc-sync.md` 约定，对照真实 diff/
      测试结果核对并更新本 change 的 `tasks.md`/`design.md`/`proposal.md`

## 6. 测试与验证

- [x] 6.1 后端：`./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.user.*"`、
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"` 通过
- [x] 6.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [ ] 6.3 手动验证（需要真实 MySQL 环境，留给用户本地执行）：
      - 字典管理页面能看到并编辑"性别"字典类型下的三个字典项
      - 表单管理 - 字段定义页面能看到人员（USER）分类下的"性别"字段定义，可调整必填/
        显示等属性
      - 表单管理 - 导入模板配置页面，人员（USER）分类下的字段选择器能选中"性别"
      - 人员管理页面新增/编辑弹窗性别选择器选项来自字典管理，与之前的"未知/男/女"一致
      - 人员批量导入：把"显示序号""性别"列在导入字段配置里都设为非必填，Excel 对应
        单元格留空，导入应成功且落地默认值，不再报"不能为空"
      - `./gradlew build` 全量测试通过（含 Flyway 迁移在真实库上正常执行，重点关注
        `tab_user.gender` 列类型转换与历史数据重映射是否符合预期）
