## Context

组织（`tab_org`）、用户/人员（`tab_user`）、应用（`tab_app`）三类主数据各自有一个业务编码
字段 `code`（服务层校验未删除范围内唯一，非数据库唯一约束）。任职（`tab_user_position`，
模块目录在 `cn.nihility.rbac.user` 下，字段见 `UserPositionEntity`）没有自己的业务编码，
它是"人员在某个组织下的一条任职记录"，由 `userId` + `orgId` + `positionType`
（任职类型，取自字典 `position_type`）等构成语义身份，且当前既有的独立任职管理入口
（`PositionCreateRequest`）本身就是"选择已存在用户 + 选择组织 + 填任职类型"的三元组。

"表单字段定义"（`tab_form_field_definition`，模块 `cn.nihility.rbac.formfield`）已经按
`bizType`（ORG/USER/POSITION/APP）维护了一份"该对象可开放配置的字段"清单：展示名称
`fieldName`、字段标识 `fieldCode`、控件类型、必填/唯一/展示等开关、显示序号
`showOrder`。这份清单目前只覆盖对象自身的列（如 ORG 的 `name`/`code`/`showOrder`/
`remark`/`ext1`~`ext10`），不包含 POSITION 用于定位所属用户/组织的
`userId`/`orgId`（这两个在现有表单里是选择器控件，不是"可开放配置的展示字段"）。

## Goals / Non-Goals

**Goals:**
- 让管理员在不改代码的前提下，为 ORG/USER/POSITION/APP 四类对象各自定义一套 Excel
  导入列（表头名、是否主键、是否必填、显示顺序），并驱动模板生成与批量导入。
- 批量导入复用各模块既有的 create/update service（含既有的必填/唯一/正则校验、锁定字段
  保护等逻辑），不重新实现一套校验规则。
- 导入失败要有明确的行级反馈（第几行、什么原因），不是笼统的"导入失败"。

**Non-Goals:**
- 不支持导入配置跨版本/跨环境迁移导出（如导出导入配置本身），只做数据库内维护。
- 不支持导入历史记录的可视化归档（复用现有 `operationlog` 模块记录批量导入这一操作即可，
  不为每条导入的业务记录单独建审计表）。
- 不做 Excel 导入的异步任务化（不引入消息队列/任务表）；单次上传在一次 HTTP 请求内
  同步处理完成，通过合理的行数上限（见 Risks）控制请求时长。

## Decisions

### 1. 导入字段配置关联表单字段定义，但主键/必填语义独立建模

新表 `tab_import_field_config`：

| 列 | 说明 |
|---|---|
| `id` | 主键 |
| `biz_type` | ORG/USER/POSITION/APP，创建后不可改 |
| `form_field_definition_id` | 可空，关联 `tab_form_field_definition.id`；非空时 `excel_header_name`/`field_code` 默认取自该定义，仍允许改写 `excel_header_name` |
| `field_code` | 冗余存字段标识。落库而非仅运行时 join，避免表单字段定义被删除后导入配置失联。**实现落地时把这条规则从"仅 POSITION 特例"放开成通用规则**：`form_field_definition_id` 非空时 `field_code` 取自绑定的表单字段定义；`form_field_definition_id` 为空时允许任意 `biz_type` 直接提交 `field_code`（`ImportFieldConfigServiceImpl.create` 校验：非空即用，未提供则报错）。POSITION 的 `__userCode`/`__orgCode`、APP 的 `__ownerCode`/`__orgCode` 均走这条路径，均由数据库迁移预置（见决策 2），前端新增/编辑弹窗本身仍要求管理员必须关联表单字段定义（不开放自由直填任意字段标识的入口），只有这两组数据库预置的固定行例外——细节见决策 2 |
| `excel_header_name` | Excel 表头文字，可与表单展示名称不同 |
| `is_primary_key` | 是否作为匹配已有记录的主键列之一。**注意：这是数据模型层面的字段，但批量导入引擎（`ImportRowExecutor`）并未实现"读取所有勾选 `is_primary_key` 的列组成通用复合键查询"，而是按 `biz_type` 硬编码匹配逻辑**——ORG/USER/APP 固定用单列 `code`（不经过 `is_primary_key` 配置）匹配已有记录；POSITION 固定用 `__userCode`/`__orgCode` 反查出的 `userId`/`orgId`，再加上 `positionType`（若该行有值）三列复合匹配。`is_primary_key` 开关目前的实际作用仅限于：①管理页面列表用"主键"标签展示给管理员参考；②POSITION 锁定列的保护校验强制其为 `true`、拒绝管理员取消。这是数据模型与执行引擎之间尚未完全打通的一处简化实现，不是文档笔误 |
| `is_required` | 导入语义下的必填，独立于表单字段定义的 `isRequired`（例如某字段表单里选填，但要求批量导入时必须提供） |
| `show_order` | 决定模板表头列顺序，值越大越靠前，与表单字段定义的 `showOrder` 相互独立 |
| `status` | 2000 启用 / -1000 逻辑删除，与项目内其他主数据一致 |
| 创建人/创建时间/更新人/更新时间 | 按项目约定补齐 |

选择"关联但不完全复用"的理由：
- 复用 `fieldCode`/字段清单，避免管理员在导入配置里重新敲一遍字段标识、维护两份容易失配的清单。
- `isPrimaryKey` 是导入场景特有的概念，表单字段定义里没有对应开关，不能塞进那张表（会污染
  表单渲染语义）。
- 导入 `isRequired` 与表单 `isRequired` 允许不同值（如 ext 字段表单里选填，但导入必须给），
  必须独立存储，不能直接读表单字段定义的 `isRequired`。
- 若表单字段定义被删除/停用，已落库的导入配置行不因此报错或消失（`field_code` 冗余存储、
  `form_field_definition_id` 允许悬空），只是管理员编辑时该行的"关联定义"选择器会提示已失效，
  需要手动处理（重新关联或删除该配置行）——不在本次范围内做级联删除，避免误删导入配置。

被拒绝的替代方案：完全独立建模（导入配置自己重复一份 `fieldName`/`fieldCode`/
`controlType`）。缺点是两份清单容易随时间漂移（表单加了新字段，管理员忘了同步到导入配置），
且新增/编辑弹窗要重新做一遍字段选择器，与"表单字段定义已经是权威字段清单"的项目现状矛盾。

### 2. POSITION 的特殊标识列：userCode / orgCode 作为固定伪字段，不进 `tab_form_field_definition`

POSITION 的表单字段定义清单里没有、也不应该有 `userId`/`orgId`（它们是选择器，不是展示字段）。
但 Excel 导入必须能通过人可读的编码定位到具体的人员和组织，不能要求管理员在 Excel 里填数据库
自增 id。设计为：POSITION 的导入配置在数据库迁移（`V27__seed_import_field_config_position.sql`）
里预置两条**不可删除**的固定配置行（`locked` 概念沿用表单字段定义已有模式，
`form_field_definition_id` 为 `NULL`，`field_code` 分别是 `__userCode`/`__orgCode`
这类不与真实业务字段冲突的保留标识，常量定义在
`cn.nihility.rbac.excelimport.constant.PositionPseudoFieldCode`），分别对应"人员编号"
（匹配 `tab_user.code`）与"组织编码"（匹配 `tab_org.code`），`is_primary_key=true`、
`is_required=true`、不可在前端被停用/取消必填/删除/改绑（保护逻辑不落库，由
`LockedImportFieldConfigs` 白名单在服务层更新/删除时反查判定，白名单目前只收录这两条
POSITION 记录）。任职类型 `positionType` 本身已经是 POSITION 的一条普通表单字段定义（有
`fieldCode`），管理员可自行为其新建一条普通（非锁定）导入配置。

**实际匹配逻辑与最初设想有出入**：`ImportRowExecutor.processPosition` 并未真正实现"复合
匹配键由勾选 `is_primary_key` 的列动态组成"，而是硬编码为 `userId`（经 `__userCode` 反查）
+ `orgId`（经 `__orgCode` 反查）+ `positionType`（若本行提供了值才加入查询条件）三列，
`positionType` 是否参与匹配取决于该行 Excel 是否有值，而不是取决于对应导入配置是否勾选了
`is_primary_key`。不做数据库唯一约束强制，只在导入匹配时用这三列查询是否已存在记录，
匹配到多于一条时该行判定失败。

**决策 1 中提到的"`field_code` 直填路径已放开为任意 `bizType` 通用规则"，本质上是本决策的
POSITION 特例被实现时顺手放开的副产品**：数据库/服务层已支持任意 `biz_type` 下
`form_field_definition_id=NULL` + 直填 `field_code` 创建导入配置，但目前只有 POSITION
的这两条通过数据库迁移预置数据的方式用到了它——前端页面没有开放对应的直填入口（详见决策 1
的说明与 Risks 部分）。

被拒绝的替代方案：让管理员在导入配置里自由勾选任意表单字段定义组合作为"主键"，不预置固定列。
问题是 POSITION 离开 `userId`/`orgId` 就无法定位记录，普通表单字段定义列表里没有这两个概念，
管理员无从选择，必然导致 POSITION 的导入功能因缺失身份列而不可用；预置固定行是把这个约束
在数据模型层面显式表达出来，而不是指望前端表单校验兜底。

### 3. 批量导入事务边界：逐行独立提交，汇总失败明细

批量导入接口对上传的 Excel 逐行调用既有 service 的 create-or-update 逻辑，每行在独立的
数据库事务里提交。**实际实现**：单行处理封装在独立的 `@Component`（`ImportRowExecutor`）
的 `processRow` 方法上，标注 `@Transactional(propagation = Propagation.REQUIRES_NEW)`，
方法内部遇到异常直接向上抛出、不在方法内部 `catch`——依赖 Spring 事务代理在异常抛出时
回滚本次 `REQUIRES_NEW` 事务，避免"方法内部 catch 异常但事务已提交部分写入"的坑。异常的
捕获与"行号 + 原因"的失败明细组装挪到调用方 `BatchImportServiceImpl.processDataRows` 的
循环里（`try { importRowExecutor.processRow(...); successCount++; } catch (Exception ex) {
...加入 failList }`），因为若在 `processRow` 方法内部 catch 异常，Spring 的
`@Transactional` 注解将不会触发回滚（Spring AOP 代理只在异常从被代理方法抛出时才回滚）。
单行失败（校验不通过、主键匹配到的记录状态异常等）不影响后续行处理；整批处理完成后返回
`{ successCount, failList: [{ rowNo, reason }] }`。

选择逐行提交而非整批全成功/全失败事务的理由：
- 批量导入的典型场景是"一次性导入几十上百条历史数据"，其中个别行数据有问题（如缺失必填列、
  编码重复）很常见；整批回滚意味着管理员改完那几行问题数据后要重新上传整份文件，体验差，
  且不易定位到底哪几行有问题（除非额外做一次"预校验不落库"的扫描）。
- 逐行独立提交能立刻反馈"哪些成功了、哪些失败了、为什么"，管理员只需修正失败的行、重新整理
  一份只含失败行的小文件再次上传，更符合"边导边修"的实际操作习惯。
- 代价是导入过程非原子操作，可能出现"部分生效"的中间状态；通过返回详尽的失败明细 +
  管理员对失败明细列表可复制/参考再次整理数据来缓解，不做自动重试或补偿事务。

### 4. 批量写入复用现有 service，不新增专用批量接口

Excel 每一行解析出的字段值，按 `bizType` 分别调用 `OrgService`/`UserService`（或按当前
真实类名）/`UserPositionService`（任职）/`AppService` 现有的单条 create/update 方法
（沿用其中的必填、正则、唯一性、锁定字段保护等既有校验），导入模块自身只负责：解析 Excel →
按导入字段配置组装成对应模块的 CreateRequest/UpdateRequest → 按主键列查询是否已存在记录 →
调用 create 或 update → 捕获异常转成行级失败明细。不新增/修改这四个模块 service 接口的方法
签名，避免这次改动外溢到已归档的 org/user/position/app 管理能力的现有实现里。

**实现细节补充**：因为导入引擎是直接 `new` 出 `OrgCreateRequest`/`UserUpdateRequest` 等
既有 DTO 并调用 `OrgService.create()`/`update()` 等既有 service 方法，绕开了 controller
层 `@Valid` 触发的自动校验，DTO 上原有的 `@NotBlank`/`@Pattern`/`@Size` 等 Bean Validation
注解不会自动生效。为此 `ImportRowExecutor` 注入了 `jakarta.validation.Validator`，在组装完
CreateRequest/UpdateRequest、调用 service 方法之前手动调用
`validator.validate(request)`，把违反的约束信息拼接成失败原因抛出 `BusinessException`，
确保既有 DTO 上的校验规则在导入路径下依然生效，不需要为导入场景重新写一遍校验逻辑。

### 5. 模板生成与解析基于已有 Apache POI 依赖

`backend/build.gradle` 已有 `org.apache.poi:poi` + `poi-ooxml` 5.4.1，本次不新增依赖。
模板生成：按 `bizType` 查询启用的导入字段配置（按 `showOrder` 升序，与表单字段定义
"数值越小越靠前"的既有约定保持一致，见 `sort-showorder-ascending` change），写一行表头到
`.xlsx`，必填列表头单元格加粗 +
字体标红（`XSSFFont#setBold(true)` + `setColor(IndexedColors.RED)`）。解析：读取上传文件
第一个 sheet，第一行按表头文字反查该 `bizType` 下导入配置的 `excelHeaderName` 得到列到
`fieldCode` 的映射（不强依赖列的物理顺序，允许管理员手工调整过列序的模板仍可导入），
之后逐行按映射取值。

### 6. 全局响应包装器排除二进制下载响应；上传大小限制调大（本次改动之外的全局基础设施调整）

模板下载接口返回 `.xlsx` 文件流，原有的 `GlobalResponseAdvice`（全局 `@RestControllerAdvice`,
把所有控制器返回值统一包成 `{ code, message, data }`）`supports()` 方法此前对所有返回值都
返回 `true`，会把二进制内容当作要包装的业务数据处理，导致下载的文件被破坏。实现时给
`supports()` 加了排除条件：命中 `ByteArrayHttpMessageConverter`/`ResourceHttpMessageConverter`
的返回值不再包装，直接原样输出二进制内容。这一改动影响的是全局所有控制器的响应处理逻辑，
不局限于本次新增的模板下载接口。

批量导入接口是 `multipart/form-data` 上传，`application.yml` 原有的 `spring.servlet.multipart`
未显式配置，走 Spring Boot 默认的单文件 1MB/单请求 10MB 上限，1MB 不足以覆盖上限 1000 行
Excel 的典型文件体积，因此把 `max-file-size`/`max-request-size` 都显式调大到 10MB。这也是
项目级的全局上传大小限制调整，不只影响批量导入接口——目前项目里没有其他上传类接口，暂无冲突。

## Risks / Trade-offs

- **[风险] 逐行独立事务导致大文件导入耗时长，单次 HTTP 请求可能超时**
  → 缓解：接口层面限制单次上传行数上限（如 1000 行，超出直接拒绝并提示分批上传），
  超时阈值/具体上限数字在 tasks.md 实现时按前后端联调情况确定。
- **[风险] `tab_import_field_config` 与 `tab_form_field_definition` 通过冗余
  `field_code` 关联而非强外键约束（外键置空后不级联），两者可能漂移不一致**
  → 缓解：前端导入配置的新增/编辑弹窗只允许从当前 `bizType` 下"启用状态的表单字段定义"里
  选择（与表单管理页面新增字段定义弹窗的选择器体验一致），减少人工敲错标识的机会；
  已失联的历史配置行在列表里提示"关联字段已失效"但不自动清理，由管理员决定去留。
- **[风险] POSITION 复合主键（userCode+orgCode+positionType）匹配到多条历史脏数据**
  （如既有数据里已存在重复组合）→ 缓解：导入时若按复合键查到多于一条记录，判定为该行失败
  （原因："匹配到多条已存在记录，无法确定更新目标"），不做隐式选择第一条。
- **[取舍] 不做导入预校验（先扫描整份文件报告问题、再决定是否真正写入）**，直接边解析边写库
  → 用户体验上不如"预检"直观，但避免了两套校验逻辑（预检 vs 实际导入）不一致的维护成本；
  管理员可通过小批量试导入的方式自行"预检"。
- **[已解决，2026-07-23 二次调整] ORG 的 `parentId` 导入列缺失，无法通过导入定位上级
  组织**（2026-07-23 用户报告）——`OrgCreateRequest`/`OrgUpdateRequest` 的 `parentId`
  为 `@NotNull`（默认值 0 表示顶级），但它在 `tab_form_field_definition` 体系里没有
  对应条目（组织管理页面里是树形选择器，不是可开放配置的展示字段），
  `ImportRowExecutor.processOrg` 原本完全不解析上级组织，导致任意一行导入的组织都被
  当作顶级节点新增/更新，无法用于批量建立组织层级——与下面已解决的 APP `ownerId`/
  `orgId` 是同一类缺口。修复方式比照 POSITION/APP：新增迁移预置 ORG 的 `__parentCode`
  （上级组织编码，匹配 `tab_org.code`）固定标识列，常量定义在新增的
  `cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode`，纳入
  `LockedImportFieldConfigs` 白名单。

  **首次实现（`is_required=false`，留空表示顶级）**：`__parentCode` 留空时按批量导入
  固有的"整行覆盖"语义把 `parentId` 显式置为 0，非空时按 `tab_org.code` 匹配得到
  `parentId`，匹配不到该行判定失败。顺带发现并修复 `ImportFieldConfigServiceImpl`
  锁定行保护逻辑的一处通用性缺陷（原先硬编码"锁定行的 isPrimaryKey/isRequired 必须为
  true"，只对 POSITION/APP 恰好成立），改为按锁定行"当前实体值是否被改动"判定，与具体
  是 `true` 还是 `false` 无关；顺带给 `OrgServiceImpl.update` 补上此前完全缺失的
  "上级组织不能是自身"防自环校验。

  **二次调整（用户要求，同日）**：把"留空表示顶级"改为"必填 + 字面值 `"0"` 表示顶级"
  ——管理员/数据整理人必须在每一行显式填写上级组织编码，顶级组织填 `"0"`，不再允许
  留空。`is_required` 由 `false` 改为 `true`（与 POSITION/APP 的固定列保持一致的必填
  语义，不再是 ORG 特例）；`processOrg` 判定逻辑相应调整为：值等于字面值 `"0"` →
  `parentId=0`，其余非空值按 `tab_org.code` 匹配，匹配不到该行判定失败；空字符串/缺失
  由既有的必填校验（`checkRequiredColumns`）在到达 `processOrg` 之前就判失败，无需
  额外处理。前一轮为兼容"可选"语义而对锁定行保护逻辑做的通用化改动（按当前实体值比对
  而非硬编码 `true`）继续保留且仍然正确——`__parentCode` 种子值变为 `true` 后自动获得
  与 POSITION/APP 一致的"不可被改回 false"保护，不需要为此再单独改代码。
- **[已解决] APP 的 `ownerId`/`orgId` 导入列配置**——`AppCreateRequest`/
  `AppUpdateRequest` 的 `ownerId`/`orgId` 均为 `@NotNull` 必填字段，但两者在
  `tab_form_field_definition` 体系里没有对应条目（APP 管理页面里是选择器，不是可开放配置的
  展示字段）。最初实现时只有 POSITION 走了决策 2 描述的"数据库预置固定标识列"路径，APP 没有
  对应预置数据，导致批量导入 APP 时任意一行都会在 `ImportRowExecutor` 的手动 Bean
  Validation 阶段因缺少这两个必填字段而判定失败。修复方式：比照 POSITION，新增迁移
  `V28__seed_import_field_config_app.sql` 为 `bizType=APP` 预置两条固定标识列
  （`__ownerCode` 表头"负责人编号"匹配 `tab_user.code`、`__orgCode` 表头"组织编码"匹配
  `tab_org.code`，常量定义在新增的
  `cn.nihility.rbac.excelimport.constant.AppPseudoFieldCode`），纳入
  `LockedImportFieldConfigs` 白名单获得与 POSITION 两条固定行一致的锁定保护；
  `ImportRowExecutor.processApp` 相应扩展为先解析负责人/组织编码得到 `ownerId`/`orgId`
  （逻辑与 `processPosition` 对称），再按应用编码（`code`）做主键匹配。前端
  `ImportFieldConfigPanel.vue` 的锁定行渲染逻辑本就是根据后端返回的通用 `locked` 字段
  驱动、不局限于 POSITION，因此新增的两条 APP 固定行无需额外前端改动即可正确展示为"系统
  保护"，只把弹窗内的固定提示文案从"任职导入定位人员/组织"改为更通用的"定位关联人员/组织"。

## Open Questions

- 单次导入行数上限的具体数值——建议 1000 行，若管理员反馈不够用后续再调整，不在本次范围内
  做成可配置项。
- `UserManagementView.vue` 对应的模块目录名/组件名，实现时确认路径与 design 阶段一致：
  `frontend/src/views/identity/user/UserManagementView.vue`（已解决）。
