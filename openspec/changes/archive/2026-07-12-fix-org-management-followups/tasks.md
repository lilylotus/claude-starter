## 1. 补记：归档后已完成的后端维护性修复

- [x] 1.1 `OrgConvert`（MapStruct）取消 `componentModel = "spring"`，改为 `OrgConvert INSTANCE = Mappers.getMapper(OrgConvert.class)` 静态单例；`OrgServiceImpl` 改为直接调用 `OrgConvert.INSTANCE.xxx(...)`，不再构造器注入
- [x] 1.2 MyBatis 原生 `<settings>` 外置到 `backend/src/main/resources/mybatis/mybatis.conf`，`application.yml` 改为 `mybatis-plus.config-location: classpath:mybatis/mybatis.conf`；`mapper-locations` 改为 `classpath*:mybatis/mapper/*.xml`
- [x] 1.3 `RbacApplication` 显式声明 `@MapperScan(basePackages = "cn.nihility.rbac", annotationClass = Mapper.class)`，修复隐式回退扫描在部分运行环境下不生效、导致 `OrgMapper` 未注册为 Spring bean 的启动失败问题
- [x] 1.4 上述后端改动通过 `./gradlew build`（含 `RbacApplicationTests.contextLoads`）以及针对真实本地 MySQL 的 `bootRun` + `/api/orgs` 全量接口冒烟测试验证

## 2. 补记：归档后已完成的前端维护性修复

- [x] 2.1 修复 `OrgManagementView.vue` 中 `el-tree-select` 的 `:props` 传入了当前 Element Plus 版本 `TreeOptionProps` 类型不支持的 `value` 字段（改为仅 `label`/`children`，选中值继续由 `node-key="id"` 驱动）
- [x] 2.2 修复表格"操作"列三处行内按钮回调参数类型未正确收窄为 `OrgRow` 导致的 `vue-tsc` 类型错误（在调用处显式 `row as OrgRow`）
- [x] 2.3 `npm run build`（`vue-tsc -b && vite build`）重新通过，无类型错误

## 3. 修复右侧操作区横向溢出问题

- [x] 3.1 在 `.org-panel`（grid item）上补充 `min-width: 0`，覆盖 CSS Grid 隐式最小宽度导致面板被内部宽表格撑开的问题
- [x] 3.2 验证：用 `playwright-core` 驱动本机已安装的 Edge，在 1366×800 视口下登录并打开 `/identity/orgs`——修复前后对比确认页面级 `scrollWidth === clientWidth`（不再整体溢出），"新增"按钮 `boundingBox` 完全落在视口内；表格自身的 `.el-scrollbar__wrap`（`scrollWidth 1180 / clientWidth 748`）确认列内容仍可在表格内部正常横向滚动，滚动条已存在（悬停时可见），未被裁掉或丢失
- [x] 3.3 `npm run build` 重新确认无回归（`vue-tsc -b && vite build` 通过）
