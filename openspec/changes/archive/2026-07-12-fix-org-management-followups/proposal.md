## Why

`add-org-management` 归档之后，组织管理功能在实际联调和使用过程中暴露出一批问题：一是运行期/构建期的技术缺陷（Spring 容器里 `OrgMapper` bean 找不到、`npm run build` 类型检查失败），二是一个真实的可用性缺陷——组织管理页面右侧操作区没有随视口宽度自适应，用户需要手动拖动横向滚动条才能看到"新增"按钮。这些都是对已归档能力的维护性修正，需要在 OpenSpec 里补记并推进，而不是绕开流程直接改代码。

## What Changes

- 修复组织管理页面（`OrgManagementView.vue`）右侧操作区因布局未随视口宽度自适应而横向溢出，导致必须拖动滚动条才能看到"新增"按钮的问题。
- 补记 `add-org-management` 归档后已经落地的几处维护性修复，纳入 OpenSpec 记录：
  - 后端：`OrgConvert`（MapStruct）不再注册为 Spring bean，改为通过 `Mappers.getMapper(...)` 静态创建单例调用，`OrgServiceImpl` 相应改为直接引用静态实例而非构造器注入。
  - 后端：MyBatis 原生 `<settings>` 配置从 `application.yml` 内联的 `mybatis-plus.configuration` 外置到独立文件 `src/main/resources/mybatis/mybatis.conf`，通过 `mybatis-plus.config-location` 加载；自定义 Mapper XML 的存放路径统一改为 `src/main/resources/mybatis/mapper/`。
  - 后端：`RbacApplication` 显式声明 `@MapperScan(basePackages = "cn.nihility.rbac", annotationClass = Mapper.class)`，修复因隐式回退扫描机制在部分运行环境下不生效、导致 `OrgMapper` 未注册为 Spring bean（`UnsatisfiedDependencyException`）的启动失败问题。
  - 前端：修复 `OrgManagementView.vue` 中 `el-tree-select` 的 `:props` 传入了当前 Element Plus 版本 `TreeOptionProps` 类型不支持的 `value` 字段、以及表格"操作"列的行数据类型未正确收窄导致的 4 处 `vue-tsc` 类型错误，使 `npm run build` 重新通过。

## Capabilities

### New Capabilities
（无——本次不引入新能力。）

### Modified Capabilities
- `org-management`：为「组织管理前端界面」需求补充一条场景，约束页面在常见视口宽度下操作按钮（含"新增"）无需横向滚动即可可见/可达。

## Impact

- **前端代码**：`frontend/src/views/identity/org/OrgManagementView.vue`（布局样式调整 + 已完成的类型修复）。
- **后端代码**：`backend/src/main/java/cn/nihility/rbac/org/mapstruct/OrgConvert.java`、`backend/src/main/java/cn/nihility/rbac/org/service/impl/OrgServiceImpl.java`、`backend/src/main/java/cn/nihility/rbac/RbacApplication.java`（均已完成）。
- **后端配置**：`backend/src/main/resources/application.yml`、新增 `backend/src/main/resources/mybatis/mybatis.conf`（均已完成）。
- **规格**：`openspec/specs/org-management/spec.md` 中「组织管理前端界面」需求新增一条场景。
- **风险**：布局修复涉及 CSS，需要在常见视口宽度下人工/自动核实不再出现横向滚动条；其余均为已验证过的维护性修复（`./gradlew build`、`npm run build` 均已通过）。
