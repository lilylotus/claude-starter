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

## 10. 补充修复：ORG 的 `parentId` 导入列（用户报告的功能缺口，2026-07-23）

- [x] 10.1 新增迁移 `V29__seed_import_field_config_org.sql`，为 `bizType=ORG` 预置一条
      固定标识列：`fieldCode=__parentCode`，`formFieldDefinitionId=NULL`，默认表头
      "上级组织编码"，`isPrimaryKey=false`、`isRequired=false`（区别于 POSITION/APP
      的固定列，允许留空表示顶级组织）
- [x] 10.2 新增常量类 `cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode`
      （`PARENT_CODE = "__parentCode"`），纳入 `LockedImportFieldConfigs` 白名单；
      同步修正 `ImportFieldConfigServiceImpl.update` 锁定行保护逻辑——原实现硬编码
      "锁定行的 isPrimaryKey/isRequired 必须为 true"，这对 POSITION/APP 恰好成立，
      但会错误地阻止 ORG 的 `__parentCode`（种子值为 false）保持原值不变的正常更新
      （如仅调整表头文字）；已改为按锁定行"当前实体值"比对是否发生变化（不区分改为
      true 还是 false 的方向），POSITION/APP 场景行为不变（现有单测全部保持通过），
      ORG 场景下正确允许 isRequired/isPrimaryKey 保持 false 不变的更新、拒绝把它们
      改为 true。`ImportFieldConfigServiceImplTest` 保持 17 个用例全部通过
- [x] 10.3 `ImportRowExecutor.processOrg` 扩展：解析 `__parentCode` 列——留空按
      `parentId=0`（顶级）处理；非空按 `tab_org.code` 匹配得到 `parentId`，匹配不到
      判定该行失败（"上级组织编码无法匹配到已有组织记录"），匹配到多条按既有"多条已存在
      记录"规则判定失败；方法签名调整为 `processOrg(rowValues, configs)` 以复用
      `headerNameOf` 拼装表头文案。额外加固：`OrgServiceImpl.update` 原本完全没有
      防自环校验，补充"上级组织不能是自身"校验（`parentId == 自身 id` 时拒绝更新），
      `ImportRowExecutor` 复用该 service 层校验自然抛错判定该行失败，未在
      `ImportRowExecutor` 内重复实现（design.md Decision 4），新增
      `OrgServiceImplTest#update_shouldThrowBusinessException_whenParentIdIsSelf`
- [x] 10.4 已读取 `ImportFieldConfigPanel.vue`：编辑弹窗的主键/必填开关状态取自
      `row.isPrimaryKey`/`row.isRequired` 实际值并在 `locked` 时统一禁用编辑
      （`:disabled="editingLocked"`），列表标签也是按实际值条件渲染"主键"/"必填"
      标签，均未硬编码"锁定即必填"的假设；ORG 的 `__parentCode` 行会正确展示为
      "系统保护"标签、不展示"必填"标签、开关禁用且保持关闭。锁定行渲染逻辑本身通用，
      无需改动（仅锁定提示文案"也不可取消主键或必填标记"存在轻微表述漂移，属于纯文案
      问题且不影响功能正确性，未在本次后端改动范围内一并调整）
- [x] 10.5 补充/更新 `ImportRowExecutorTest` 用例：新增 `orgConfigs()` 通用夹具方法
      （组织编码 + `__parentCode`），同步为已有的 4 个 ORG 用例补上 `__parentCode`
      列（原先完全缺失该列，属于回归判断盲区）；新增
      `processRow_shouldCreateOrg_whenParentCodeMatched`（合法上级编码新增成功，
      断言 `parentId` 取自匹配结果）、留空按顶级处理已由改造后的
      `processRow_shouldCreateOrg_whenNoMatch`/`processRow_shouldUpdateOrg_whenOneMatch`
      覆盖（断言 `parentId` 为 0）、`processRow_shouldFailOrg_whenParentCodeNotFound`
      （无法匹配判定失败）。`ImportRowExecutorTest` 共 14 个用例全部通过
- [x] 10.6 `./gradlew compileJava compileTestJava` 通过；
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"` 全部通过；本次未涉及
      前端文件改动，未运行 `npm run build`

## 11. 二次调整：`__parentCode` 改为必填，字面值 `"0"` 表示顶级组织（用户要求，2026-07-23）

第 10 节最初的设计是"留空表示顶级组织"（`isRequired=false`）。用户要求改为：这一列
必填，管理员/数据整理人必须显式在每一行填写上级组织编码，若该组织本身是顶级组织，则
填写字面值 `"0"` 而不是留空。

- [x] 11.1 迁移 `V29__seed_import_field_config_org.sql` 里 `__parentCode` 这一行的
      `is_required` 由 `0` 改为 `1`（该文件是本次改动新增、未合入其他人分支，可直接
      编辑，不新增迁移版本号）。核实发现 `INSERT` 语句的 `is_required` 取值在本次会话
      开始前已是 `1`（历史提交 `5b2fd72 fix(组织导入): 修复组织导入问题`），仅文件顶部
      的说明注释仍停留在"非必填（is_required=0）"的旧描述——已同步更新注释文字，反映
      "必填 + 字面值 0 表示顶级"的最终设计
- [x] 11.2 `ImportRowExecutor.processOrg` 调整 `__parentCode` 解析逻辑：值为字面值
      `"0"` 时 `parentId=0`（顶级），非 `"0"` 的非空值按 `tab_org.code` 匹配得到
      `parentId`，匹配不到判定该行失败；`checkRequiredColumns` 沿用现有必填校验逻辑
      即可（`isRequired=true` 时空字符串直接判定该行失败——"0" 本身不是空字符串，天然
      能通过必填校验，无需特殊放行逻辑）
- [x] 11.3 `LockedImportFieldConfigs`/`ImportFieldConfigServiceImpl` 的锁定行保护逻辑
      不需要改动（第 10 节已把"锁定行 isRequired/isPrimaryKey 是否允许修改"实现为按
      当前实体值比对是否变化，与具体是 `true` 还是 `false` 无关，`__parentCode`
      种子值变为 `true` 后行为自动保持一致：管理员仍然不能把它改回 `false`）。仅做
      确认，未改动相关文件
- [x] 11.4 更新 `ImportRowExecutorTest` 中 ORG 相关用例：`orgConfigs()` 夹具里
      `__parentCode` 改为必填；覆盖三种场景——值为 `"0"` 新增成功且 `parentId=0`
      （`processRow_shouldCreateOrg_whenNoMatch`/`processRow_shouldUpdateOrg_whenOneMatch`
      改为使用字面值 `"0"`，替换原先"留空按顶级处理"的语义）、值为真实上级组织编码
      新增成功（`processRow_shouldCreateOrg_whenParentCodeMatched`，未改动）、值缺失/
      空白判定为必填校验失败（新增 `processRow_shouldFailOrg_whenParentCodeMissing`，
      断言消息为 `checkRequiredColumns` 的既有提示格式"字段[上级组织编码]不能为空"）；
      另把 `processRow_shouldFailOrg_whenMultipleMatch`/
      `processRow_shouldFailOrg_whenBeanValidationViolated` 中作为填充值的
      `__parentCode` 由 `""` 改为 `"0"`，避免因夹具变为必填而被必填校验提前拦截、
      掩盖了这两个用例本身要验证的场景；"上级组织编码无法匹配判定失败"
      （`processRow_shouldFailOrg_whenParentCodeNotFound`）场景未改动，依旧有效
- [x] 11.5 更新 design.md 中 ORG `parentId` 相关的 Risks 记录与决策说明文字，反映
      "必填 + 0 表示顶级"而非"选填留空表示顶级"（设计文档已由用户会话预先更新，本次
      未再改动 design.md）
- [x] 11.6 只运行范围内的测试确认：`./gradlew compileJava compileTestJava`、
      `./gradlew test --tests "cn.nihility.rbac.excelimport.*"`、
      `./gradlew test --tests "cn.nihility.rbac.org.*"`；**不要**运行全量
      `./gradlew build`/`./gradlew test`（会触发 `RbacApplicationTests` 连接本地真实
      MySQL 执行 Flyway 迁移，本次改动范围不涉及验证既有迁移文件，避免重演上一轮"为了
      消除本地 checksum mismatch 而反向修改历史迁移文件"的事故）；**任何情况下都不要
      修改 V1~V28 已存在的迁移文件**，也不要执行 `git add -A`/`git commit`——改动完成后
      交回由用户自行确认

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
