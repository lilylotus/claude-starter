## Context

`FormFieldSnapshotSupport.appendExtFieldSnapshot()` 已经提供了"按字段定义的 `controlType`/
`dictTypeCode` 把 `ext` 字段存储的编码解析为标签"的能力，但它的解析对象是"字段定义 +
`ext` 列值"这一组合，天然不覆盖不经过 `tab_form_field_definition`/`ext1`~`ext10` 机制、
直接是实体固定列的 `gender`/`positionType`——即便这两列本质上也是"字典驱动的下拉字段"
（`tab_metadata_field`/`tab_form_field_definition` 里确实登记了 `gender` 这一条，但它绑定的
是 `tab_user.gender` 这个真实列而不是某个 `extN`）。`toLogSnapshot()`/
`PositionLogSnapshotSupport.snapshot()` 里这两个字段是手写 `snapshot.put(...)`，编写时
沿用了其余固定文本列（姓名、编码、手机号等）"原样放入"的写法，漏了它们其实需要走一次
字典编码→标签解析。

## Goals / Non-Goals

**Goals:**
- 用户"性别"、任职记录"任职类型"两个核心列在操作日志变更快照中展示字典标签而非编码，
  行为与 `ext` 字典字段保持一致（含"查不到字典项时回退展示原始编码"的兜底规则）。

**Non-Goals:**
- 不重构 `FormFieldSnapshotSupport` 使其同时支持"字段定义驱动"和"固定列驱动"两种输入
  形态——当前只有这两个固定列命中这个缺陷，为两个字段各写一个几行的私有解析方法足够，
  不需要为此抽象出通用组件（YAGNI）。
- 不改这两个字段在新增/编辑表单、详情页面的展示逻辑——前端已经是按标签展示（数据来自字典
  接口而非原始编码），不受本次改动影响。
- 不排查/修复"是否还有其他固定列同样绑定了字典类型却没解析"的问题——本次是响应用户明确
  指出的"性别"缺陷后顺带核实到同构的"任职类型"缺陷一并修复，未做全量扫描；如果后续发现
  其他字段有同样问题，按需另开 change 处理。

## Decisions

- **各自写一个私有解析方法，而不是抽出公共工具方法**：`UserServiceImpl` 和
  `PositionLogSnapshotSupport` 分别只有一个字段需要这个逻辑，且两者的字典类型编码
  （`gender` vs `position_type`）不同、注入的依赖（`DictItemService`）相同但没有其他共享
  状态，各写 4 行左右的私有方法（`genderLabel`/`positionTypeLabel`）比抽象出一个新的共享
  组件更直接，代码量也更少；如果未来出现第三个同类字段，再考虑是否值得抽公共方法。
- **直接注入 `DictItemService.getEnabledOptions(typeCode)`**：与 `FormFieldSnapshotSupport`
  内部解析 `ext` 字典字段标签时使用的是同一个接口方法，保持"按字典类型编码查启用字典项"
  这条查询路径在项目里只有一种实现方式。
- **找不到匹配字典项时回退展示原始编码**：与 `ext` 字典字段、前端 `dictOptionLabel()` 的
  兜底逻辑保持一致，不留空、不报错。

## Risks / Trade-offs

- [权衡] `genderLabel()`/`positionTypeLabel()` 每次调用都会查一次 `getEnabledOptions()`
  （无缓存），与 `FormFieldSnapshotSupport.resolveLabelByCode()` 现状一致——操作日志写入
  是低频操作（用户手动增删改触发），当前数据量下没有性能问题，暂不引入缓存层。
