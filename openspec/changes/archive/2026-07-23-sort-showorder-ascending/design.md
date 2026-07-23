## Context

`showOrder`（显示序号）在项目里被多个模块使用，但排序方向不是全局统一约定——本次仅调整
用户明确点名的两处："表单字段定义"（`cn.nihility.rbac.formfield`）与"导入模板配置"
（`cn.nihility.rbac.excelimport`）。org/user/position/app/dict/menu/role/admin 等模块
自身列表的 `showOrder`（如组织树同级排序、字典项排序）继续维持现状的降序约定，不在本次
范围内，避免过度扩大改动面。

两处涉及的排序方向当前都在后端查询层用 MyBatis-Plus `orderByDesc` 实现，各有两到三处调用点：

- `FormFieldDefinitionServiceImpl`：`getPage`（管理页面分页列表）、`listActiveByBizType`
  （校验/快照场景遍历全部启用定义，顺序对结果本身无影响，只是遍历顺序）、`buildRenderSchema`
  （`GET /api/form-fields/render-schema`，被 `frontend/src/composables/
  useDynamicFormFields.ts` 消费，驱动 org/user/position/app 四个动态表单的列表列顺序与
  新增/编辑表单字段顺序——这是用户可感知排序方向的关键路径）。
- `ImportFieldConfigServiceImpl`：`getPage`（管理页面分页列表）、`listActiveByBizType`
  （被 `ImportTemplateServiceImpl` 用于生成 Excel 模板表头顺序、被
  `BatchImportServiceImpl` 用于遍历必填列校验——模板表头顺序同样是用户可感知的关键路径）。

前端 `useDynamicFormFields.ts` 的 `sortBySchemaOrder` 目前对后端已经降序排好的数据又执行
了一次 `b.showOrder - a.showOrder` 的客户端降序排序（双重降序，结果一致但冗余）；后端改
升序后，这个客户端比较器如果不同步改成 `a.showOrder - b.showOrder`，会在前端把后端刚排好
的升序结果重新倒转回降序，必须两处一起改。

## Goals / Non-Goals

**Goals:**
- 表单字段定义、导入模板配置两处的列表/渲染/模板生成排序方向统一改为升序（值越小越靠前）。
- 不改变任何已存的 `showOrder` 数值本身，只改变读取时的排序方向。

**Non-Goals:**
- 不触碰 org/user/position/app/dict/menu/role/admin 等其他模块自身的 `showOrder` 排序
  约定（这些不在用户本次的请求范围内）。
- 不做"排序方向可配置"的开关，方向变化是一次性、全局生效的行为调整，不引入新的配置项。
- 不迁移/重算已存数据的 `showOrder` 数值（管理员如需恢复之前的视觉顺序，需要自行按新语义
  调整数值，proposal.md 中已标注为 BREAKING）。

## Decisions

### 排序方向反转在查询层做，不在数据层做

只把 MyBatis-Plus 查询链上的 `orderByDesc(...ShowOrder)` 改成 `orderByAsc(...ShowOrder)`，
不批量更新 `tab_form_field_definition`/`tab_import_field_config` 表里已存的 `showOrder`
数值。理由：数值本身没有"错"，只是读取方向变了；管理员原本设置的相对大小关系
（哪个字段该排更前）不会因为这次改动丢失信息，只是需要管理员知晓方向变了、按需重新调整
具体数值以达到期望顺序——这也是 proposal.md 里把这次改动标为 BREAKING 的原因。

被拒绝的替代方案：批量把所有 `showOrder` 取负数或做 `MAX - value` 映射来"保持视觉顺序
不变"。缺点是这类数据迁移在语义上等价于给每条记录重新赋值，一旦以后又有别的字段需要参考
原始 `showOrder` 数值语义（如导出/审计快照），会出现"改之前的数值"和"改之后的数值"混淆，
不如让排序方向的改变对数据是透明的、纯粹是查询语句层面的行为调整。

### 前端 `useDynamicFormFields.ts` 同步反转客户端排序

`buildRenderSchema` 返回结果已经在后端按新方向（升序）排好，前端 `sortBySchemaOrder` 的
客户端二次排序要同步从 `b.showOrder - a.showOrder`（降序）改为 `a.showOrder - b.showOrder`
（升序），否则会抵消后端的改动、前端展示的仍是旧的降序效果。

## Risks / Trade-offs

- **[风险/BREAKING] 已配置过 `showOrder` 的管理员会看到顺序反转** → 缓解：这是用户主动
  要求的行为变化，不是缺陷；两处前端"显示序号"输入框下方的提示文案同步更新为"数值越小，
  排序越靠前"，减少后续管理员配置时的困惑。不做数据迁移兜底（见上面的决策说明）。
- **[风险] `add-excel-import-export` 这个 change 尚未归档，其 `design.md`/`tasks.md`/
  `specs/excel-import-export/spec.md` 里多处"按 showOrder 降序"的表述如果不同步修改，
  会在后续归档时把已经过时的排序方向描述带入 `openspec/specs/`** → 缓解：本次改动同时
  直接编辑该 change 内的这几处表述改成"升序"，不新增额外的 delta 文件（该能力尚未同步进
  `openspec/specs/`，没有可 delta 的基线）。
