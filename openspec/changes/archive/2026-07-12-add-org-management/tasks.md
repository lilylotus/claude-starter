## 1. 后端基础设施

- [x] 1.1 后端基础包名从 `com.example.demo` 重命名为 `cn.nihility.rbac`（含 `RbacApplication.java`、`RbacApplicationTests.java`），`build.gradle` 的 `group` 同步改为 `cn.nihility`
- [x] 1.2 替换 `mybatis-plus-boot-starter` → `mybatis-plus-spring-boot3-starter`，修复与 Spring Boot 3.5 / Spring Framework 6.2 不兼容导致的 Mapper Bean 注册报错
- [x] 1.3 降级 `springdoc-openapi-starter-webmvc-ui` 3.0.3 → 2.8.17，修复与 Spring Boot 3.5 不兼容问题
- [x] 1.4 引入 Flyway（`flyway-mysql` + `mysql-connector-j`），建立 `db/migration` 目录及 `V<版本>__<描述>.sql` 命名约定、`tab_` 表名前缀约定
- [x] 1.5 新增统一响应基础设施 `cn.nihility.rbac.common`：`Result<T>`、`GlobalResponseAdvice`、`BusinessException`、`GlobalExceptionHandler`

## 2. 后端组织模块

- [x] 2.1 编写 Flyway 迁移脚本 `V1__init_tab_org.sql`，建表 `tab_org`（id/name/code/parent_id/status/show_order/ext1-10/审计字段）
- [x] 2.2 新增 `OrgEntity`（MyBatis-Plus 实体）与 `OrgStatus`（状态常量：2000 启用/3000 停用/-1000 逻辑删除）
- [x] 2.3 新增 `OrgMapper`（MyBatis-Plus）
- [x] 2.4 新增 DTO/VO：`OrgCreateRequest`、`OrgUpdateRequest`、`OrgVO`、`OrgTreeNodeVO`
- [x] 2.5 新增 `OrgConvert`（MapStruct），实体与各 DTO/VO 互转，`parentName`/`children`/审计字段/扩展字段按场景显式 ignore
- [x] 2.6 实现 `OrgService`/`OrgServiceImpl`：树查询（内存建树）、直属子节点查询、详情查询（批量回填 `parentName`）、创建（编码唯一性校验）、更新（编码唯一性校验，排除自身）、启用/停用、逻辑删除（删除前校验是否存在未删除子组织）
- [x] 2.7 实现 `OrgController`：`GET /api/orgs/tree`、`GET /api/orgs/children`、`GET /api/orgs/{id}`、`POST /api/orgs`、`PUT /api/orgs/{id}`、`PUT /api/orgs/{id}/enable`、`PUT /api/orgs/{id}/disable`、`DELETE /api/orgs/{id}`，附 springdoc `@Tag`/`@Operation` 注解
- [x] 2.8 编写 `OrgServiceImplTest` 单元测试：树嵌套组装、创建时编码重复拒绝、删除时存在子组织拒绝、删除时无子组织成功、查询不存在组织抛异常（5 个用例）
- [x] 2.9 `./gradlew build` 全量通过（6 个测试：5 个 `OrgServiceImplTest` + 1 个 `RbacApplicationTests` 上下文加载，0 失败）

## 3. 前端组织管理页面

- [x] 3.1 新增 `src/types/org.ts`：`OrgTreeNode`、`OrgRow`、`OrgFormRequest` 类型及状态常量，字段与后端 DTO 对齐
- [x] 3.2 新增 `src/api/org.ts`，封装上述 7 个后端接口
- [x] 3.3 新增 `src/stores/org.ts`（Pinia setup store）：组织树、当前选中节点 id、直属子节点列表、树/表格独立的 loading 状态
- [x] 3.4 新增 `src/views/identity/org/OrgManagementView.vue`：左侧 `el-tree` 展示完整组织树（默认全部展开，虚线+圆点链式连接样式），右侧 `el-table` 展示选中节点的直属子组织
- [x] 3.5 新增/编辑弹窗：`el-tree-select` 选择上级组织，数据源前拼接虚拟"顶级组织"根节点（对应 `parentId = 0`）；编辑模式下通过 `pruneSubtree()` 剔除自身及子孙节点防止成环
- [x] 3.6 行内操作：编辑、启用/停用切换、删除（`ElMessageBox.confirm` 二次确认），操作后统一调用 `orgStore.refreshAfterMutation()` 刷新树和表格
- [x] 3.7 `router/menu.ts` 菜单文案由"组织架构"改为"组织管理"；`router/index.ts` 中 `/identity/orgs` 路由从 `PlaceholderView.vue` 改为指向 `OrgManagementView.vue`
- [x] 3.8 `npm run build`（vue-tsc 类型检查 + vite build）全量通过，无类型错误

## 4. 后续验证

- [x] 4.1 接入真实本地 MySQL 数据源（`application.yml` 更新为实际连接信息），`spring.flyway.enabled` 打开为 `true`，执行 `./gradlew bootRun` 触发 Flyway 迁移：`V1__init_tab_org.sql` 成功应用，`tab_org` 实际建表结构核对无误；随后对 `/api/orgs` 的 tree/children/create/enable/disable/delete 全量接口做了端到端冒烟测试，均返回预期结果，验证后停止进程、清理临时测试数据
- [x] 4.1.1 MyBatis-Plus 配置补充：新增 `mybatis-plus.config-location` 指向独立的 `src/main/resources/mybatis/mybatis.conf`（驼峰/下划线映射等原生 `<settings>`），`mapper-locations` 改为 `classpath*:mybatis/mapper/*.xml`；重新走 bootRun 冒烟验证，`parentId`/`showOrder` 等字段映射确认正常
- [ ] 4.2 组织与用户/角色的关联能力（不在本 change 范围内，留给后续身份管理相关 change）
