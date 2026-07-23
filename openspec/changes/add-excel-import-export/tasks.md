## 1. 数据库迁移

- [x] 1.1 新增 Flyway migration，建 `tab_import_field_config` 表（列见 design.md 决策 1，
      字段命名检查与 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突），补齐创建人/创建
      时间/更新人/更新时间默认字段
- [x] 1.2 同一 migration 或后续一个 migration 里，为 `bizType=POSITION` 预置两条固定
      标识列种子数据（`fieldCode=__userCode`/`__orgCode`，`formFieldDefinitionId=NULL`，
      `isPrimaryKey=true`、`isRequired=true`，默认表头"人员编号"/"组织编码"）

## 2. 后端：导入字段配置管理

- [x] 2.1 新建模块目录（如 `cn.nihility.rbac.excelimport`）：entity/mapper/service/
      service.impl/dto/mapstruct/exception/controller，遵循项目现有分层约定
- [x] 2.2 实现 `ImportFieldConfigEntity` + MyBatis-Plus `Mapper`
- [x] 2.3 实现分页/列表查询（按 `bizType` 过滤，`showOrder` 升序——排序方向后经
      `sort-showorder-ascending` change 由降序改为升序）、新增、更新、逻辑删除
      service 方法；新增/更新时校验 `formFieldDefinitionId` 指向存在且启用、`bizType`
      一致的表单字段定义；同 `bizType` 下 `fieldCode` 唯一性校验
- [x] 2.4 实现 POSITION 固定标识列的保护逻辑：`__userCode`/`__orgCode` 拒绝删除、拒绝
      更新 `isRequired` 为 `false`（复用/参照 `formfield` 模块锁定字段保护的实现思路）
- [x] 2.5 Controller 暴露分页查询、新增、更新、删除接口，加 springdoc-openapi 注解
      （`@Tag`、`@Operation`）
- [x] 2.6 MapStruct convert：entity↔DTO，静态单例写法（`INSTANCE = Mappers.getMapper(...)`），
      不用 `componentModel = "spring"`

## 3. 后端：模板下载与批量导入引擎

- [x] 3.1 实现按 `bizType` 生成 Excel 模板的工具方法（Apache POI）：查询启用的导入字段
      配置按 `showOrder` 升序写表头，必填列表头加粗+标红；Controller 暴露下载接口
      （返回 `.xlsx` 文件流）
- [x] 3.2 实现 Excel 解析工具：读取首行表头，按表头文字匹配启用的导入字段配置得到列到
      `fieldCode` 的映射；校验所有必填表头是否齐全，缺失时整体拒绝并返回缺失表头名称
- [x] 3.3 实现单次导入行数上限校验（如 1000 行，超出拒绝并提示分批上传）
- [x] 3.4 按 `bizType` 分别实现"Excel 行 → 对应模块 CreateRequest/UpdateRequest"的组装
      逻辑，复用组织/人员/应用既有 service 的 create/update 方法
- [x] 3.5 POSITION 专属：按"人员编号"列匹配 `tab_user.code`、"组织编码"列匹配
      `tab_org.code` 得到 `userId`/`orgId`，匹配不到时该行判定失败并注明具体是哪一列
      无法匹配；复用任职模块既有 create/update 方法
- [x] 3.6 实现已有记录匹配查询：零条→新增流程，一条→更新流程，多条→该行失败
      （原因："匹配到多条已存在记录"）；**实际实现未做成"任意勾选 `isPrimaryKey`
      的列组成通用复合键"，而是按 `bizType` 硬编码**——ORG/USER/APP 固定用单列
      `code` 匹配，POSITION 固定用 `userId`（经 `__userCode` 反查）+
      `orgId`（经 `__orgCode` 反查）+ `positionType` 三列复合匹配；`isPrimaryKey`
      开关目前只用于管理页面的"主键"标签展示与 POSITION 锁定列的保护校验，并未
      驱动实际的匹配查询逻辑（见 design.md 决策 1 补充说明）
- [x] 3.7 实现逐行独立事务提交（每行处理包一层独立事务边界，不使用类级大事务），单行异常
      捕获转为失败明细（行号 + 原因），不影响后续行
- [x] 3.8 Controller 暴露批量导入接口（`multipart/form-data` 上传），返回
      `{ successCount, failList: [{ rowNo, reason }] }`；加 springdoc-openapi 注解

## 4. 前端：类型与 API 封装

- [x] 4.1 `src/types/` 新增导入字段配置类型（`ImportFieldConfig` 等），参照
      `types/formField.ts` 的写法
- [x] 4.2 `src/api/` 新增导入字段配置的 CRUD 封装、模板下载封装（触发浏览器下载）、批量
      导入封装（`FormData` 上传，返回导入结果结构）

## 5. 前端：表单管理页面新增"导入模板配置"tab

- [x] 5.1 `views/system/formfields/` 新增导入配置子视图；**实际实现方式**：
      `FormFieldListView.vue` 改造为外层壳组件，只保留一层外部 tabs（字段定义 /
      导入模板配置），原有字段定义管理逻辑整体搬到新建的
      `FormFieldDefinitionPanel.vue`，导入模板配置逻辑新建在
      `ImportFieldConfigPanel.vue`，两个子组件各自内部再按 `bizType` 二级切换
- [x] 5.2 列表按 `showOrder` 升序展示，必填列的表头名称以红色字体渲染；POSITION 固定
      标识列标记"系统保护"且不展示编辑必填开关/删除入口
- [x] 5.3 新增/编辑弹窗：关联字段选择器（列出当前 `bizType` 下启用的表单字段定义），选中
      后自动带出默认表头名称与字段标识；可调整表头名称、是否主键、是否必填、显示序号

## 6. 前端：组织/人员/任职/应用管理页面接入

- [x] 6.1 `views/identity/org/OrgManagementView.vue` 工具栏新增"下载导入模板"
      "批量导入"按钮
- [x] 6.2 `views/identity/user/UserManagementView.vue` 同上
- [x] 6.3 `views/identity/position/PositionManagementView.vue` 同上
- [x] 6.4 `views/application/app/AppManagementView.vue` 同上
- [x] 6.5 实现共用的批量导入上传弹窗组件（选择 Excel 文件 → 调用对应 `bizType` 的批量
      导入接口 → 展示成功条数与失败明细列表），四个页面复用同一组件，仅传入不同 `bizType`

## 7. 权限资源与文档同步

- [x] 7.1 更新仓库根目录 `权限资源.txt`，为组织/人员/任职/应用四个管理页面新增的
      "下载导入模板""批量导入"按钮补充三段式权限资源编码
- [x] 7.2 实现完成后，按 `.claude/agents/openspec-doc-sync.md` 约定，对照真实 diff/
      测试结果核对并更新本 change 的 `tasks.md`/`design.md`/`proposal.md`

## 9. 补充修复：APP 的 `ownerId`/`orgId` 导入列（doc-sync 阶段发现的功能缺口）

- [x] 9.1 文档同步阶段发现 APP 批量导入实际不可用（`ownerId`/`orgId` 无对应表单字段定义、
      也无预置固定导入列，任意一行都会在 Bean Validation 阶段判定失败）。比照 POSITION，
      新增迁移 `V28__seed_import_field_config_app.sql` 预置 `__ownerCode`（负责人编号，
      匹配 `tab_user.code`）/`__orgCode`（组织编码，匹配 `tab_org.code`）两条固定标识列，
      新增常量类 `AppPseudoFieldCode`，纳入 `LockedImportFieldConfigs` 白名单
- [x] 9.2 `ImportRowExecutor.processApp` 扩展为先解析负责人/组织编码得到
      `ownerId`/`orgId`（逻辑对称于 `processPosition`），再按应用编码匹配主键；更新
      `ImportRowExecutorTest` 相应用例（新增 `processRow_shouldFailApp_whenOwnerCodeNotFound`/
      `processRow_shouldFailApp_whenOrgCodeNotFound`，重写 `processRow_shouldCreateApp_whenNoMatch`）
- [x] 9.3 前端 `ImportFieldConfigPanel.vue` 的锁定提示文案从"任职导入定位人员/组织"调整为
      通用表述（覆盖 POSITION 与 APP 两组固定行），锁定行渲染逻辑本身已是通用的，无需其余改动
- [x] 9.4 `./gradlew compileJava compileTestJava` 通过；
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"` 38 个用例全部通过；
      `npm run build` 通过

## 8. 测试与验证

- [x] 8.1 后端：导入字段配置 CRUD、POSITION/APP 固定列保护、模板生成表头顺序与必填标红、
      批量导入的新增/更新/复合键冲突/必填缺失/行级失败明细等场景的单元或集成测试
      （`cn.nihility.rbac.excelimport.*` 下共 5 个测试类、38 个用例全部通过；均为纯
      Mockito 单元测试，不依赖真实数据库）
- [ ] 8.2 `./gradlew build` 通过（本次会话内未验证：全量 `test` 任务还会跑
      `RbacApplicationTests`（`@SpringBootTest` 需要连接真实 MySQL 执行 Flyway 迁移）
      等既有集成测试，环境不确定，避免长时间挂起未执行；需要用户在本地有 MySQL 环境
      时自行跑一遍 `./gradlew build` 做最终确认，重点关注 V26/V27/V28 三个新迁移脚本能否
      正常应用到真实库）
- [x] 8.3 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [ ] 8.4 手动验证：下载各 `bizType` 模板 → 按模板整理数据 → 批量导入 → 核对新增/更新
      结果与失败明细展示，覆盖 POSITION 的人员/组织编号映射场景（**提前预警**：按
      design.md Risks 部分记录的已知缺口，APP 的批量导入验证目前预期会全部失败在
      "负责人不能为空；所属组织不能为空"，因为页面暂无法为 `ownerId`/`orgId` 配置导入列，
      验证前建议先确认该缺口的处理方案）
