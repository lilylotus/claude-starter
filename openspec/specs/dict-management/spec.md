## Purpose

通用字典类型 + 字典项两级维护能力：字典类型的增删改查、启停用、按名称/编码模糊搜索分页查询，字典项在其归属类型下的增删改查、启停用，以及一个按字典类型编码取全部启用项的只读接口，供全系统枚举类字段（如用户管理模块的"认证类型"）复用；配套前端左侧字典类型列表 + 右侧字典项分页表格的主从式管理界面。

## Requirements

### Requirement: 字典类型分页查询
系统 SHALL 提供字典类型的分页查询接口，支持按名称或编码模糊搜索；分页参数 `page`（页码，默认 `1`）、`pageSize`（每页条数，默认 `10`）均为可选；不包含已逻辑删除的字典类型；结果按 `showOrder` 降序、相同时按 `id` 升序排列。

#### Scenario: 查询字典类型分页列表
- **WHEN** 客户端调用 `GET /api/dict-types?page={page}&pageSize={pageSize}`
- **THEN** 系统返回未删除字典类型的分页结果，包含 `records`、`total`、`page`、`pageSize`

#### Scenario: 按名称或编码模糊搜索
- **WHEN** 客户端调用 `GET /api/dict-types?keyword={keyword}`
- **THEN** 系统返回名称或编码包含 `keyword` 的未删除字典类型分页结果

### Requirement: 字典类型详情查询
系统 SHALL 提供按 id 查询字典类型详情的接口，返回结果包含备注及新增人、新增时间、更新人、更新时间等审计字段。

#### Scenario: 查询存在的字典类型
- **WHEN** 客户端调用 `GET /api/dict-types/{id}` 且该字典类型存在且未被删除
- **THEN** 系统返回其完整信息，包括 `remark`、`createBy`、`createTime`、`updateBy`、`updateTime`

#### Scenario: 查询不存在的字典类型
- **WHEN** 客户端调用 `GET /api/dict-types/{id}` 且该 id 不存在或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不返回 HTTP 500

### Requirement: 新增字典类型
系统 SHALL 支持创建字典类型，名称和编码为必填项，且编码在未被逻辑删除的字典类型范围内必须全局唯一；备注为可选字段。新建字典类型默认状态为启用（`2000`）。

#### Scenario: 成功创建字典类型
- **WHEN** 客户端调用 `POST /api/dict-types`，携带合法的 `name`、`code`、`showOrder`，且 `code` 在未删除字典类型中不重复
- **THEN** 系统创建该字典类型，状态为 `2000`（启用），并返回创建后的信息

#### Scenario: 编码重复时拒绝创建
- **WHEN** 客户端调用 `POST /api/dict-types`，其 `code` 与某个未被逻辑删除的字典类型重复
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

### Requirement: 更新字典类型
系统 SHALL 支持更新字典类型的名称、编码、显示序号、备注；编码唯一性校验范围为未被逻辑删除的字典类型，且排除被更新对象自身。更新接口不修改状态。

#### Scenario: 成功更新字典类型
- **WHEN** 客户端调用 `PUT /api/dict-types/{id}`，携带合法的 `name`、`code`、`showOrder`
- **THEN** 系统更新该字典类型信息并返回更新后的结果，`status` 保持不变

#### Scenario: 编码与其他字典类型重复时拒绝更新
- **WHEN** 客户端调用 `PUT /api/dict-types/{id}`，其 `code` 与另一个未删除字典类型重复（非自身）
- **THEN** 系统拒绝更新，返回业务错误

### Requirement: 字典类型启用与停用
系统 SHALL 提供独立的接口将字典类型状态切换为启用（`2000`）或停用（`3000`）。

#### Scenario: 启用字典类型
- **WHEN** 客户端调用 `PUT /api/dict-types/{id}/enable`
- **THEN** 系统将该字典类型 `status` 置为 `2000` 并返回更新后的信息

#### Scenario: 停用字典类型
- **WHEN** 客户端调用 `PUT /api/dict-types/{id}/disable`
- **THEN** 系统将该字典类型 `status` 置为 `3000` 并返回更新后的信息

### Requirement: 字典类型逻辑删除
系统 SHALL 支持对字典类型执行逻辑删除（将 `status` 置为 `-1000`），不做物理删除；当字典类型存在未被逻辑删除的字典项时，系统拒绝删除。

#### Scenario: 成功删除无字典项的字典类型
- **WHEN** 客户端调用 `DELETE /api/dict-types/{id}`，且该字典类型不存在任何未删除的字典项
- **THEN** 系统将该字典类型 `status` 置为 `-1000`，此后不再出现在分页查询、详情查询的结果中

#### Scenario: 存在未删除字典项时拒绝删除
- **WHEN** 客户端调用 `DELETE /api/dict-types/{id}`，且该字典类型存在至少一个未被逻辑删除的字典项
- **THEN** 系统拒绝删除，返回业务错误（非零 `code`），该字典类型状态不变

### Requirement: 字典项分页查询
系统 SHALL 提供按字典类型 id 分页查询其字典项列表的接口；分页参数 `page`（默认 `1`）、`pageSize`（默认 `10`）均为可选；不包含已逻辑删除的字典项；结果按 `showOrder` 降序、相同时按 `id` 升序排列。

#### Scenario: 查询指定字典类型下的字典项分页列表
- **WHEN** 客户端调用 `GET /api/dict-items?dictTypeId={id}&page={page}&pageSize={pageSize}`
- **THEN** 系统返回该字典类型下未删除字典项的分页结果，包含 `records`、`total`、`page`、`pageSize`

### Requirement: 字典项详情查询
系统 SHALL 提供按 id 查询字典项详情的接口，返回结果包含所属字典类型名称、备注及审计字段。

#### Scenario: 查询存在的字典项
- **WHEN** 客户端调用 `GET /api/dict-items/{id}` 且该字典项存在且未被删除
- **THEN** 系统返回其完整信息，包括根据 `dictTypeId` 回填的 `dictTypeName`、`remark`、`createBy`、`createTime`、`updateBy`、`updateTime`

#### Scenario: 查询不存在的字典项
- **WHEN** 客户端调用 `GET /api/dict-items/{id}` 且该 id 不存在或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不返回 HTTP 500

### Requirement: 新增字典项
系统 SHALL 支持在指定字典类型下创建字典项，`dictTypeId`、`label`、`code` 为必填项，且 `code` 在同一 `dictTypeId` 下未被逻辑删除的字典项范围内必须唯一（不同字典类型下可出现相同编码）；备注为可选字段。新建字典项默认状态为启用（`2000`）。

#### Scenario: 成功创建字典项
- **WHEN** 客户端调用 `POST /api/dict-items`，携带合法的 `dictTypeId`、`label`、`code`、`showOrder`，且 `code` 在该字典类型下未删除字典项中不重复
- **THEN** 系统创建该字典项，状态为 `2000`（启用），并返回创建后的信息

#### Scenario: 同一字典类型下编码重复时拒绝创建
- **WHEN** 客户端调用 `POST /api/dict-items`，其 `code` 与同一 `dictTypeId` 下某个未被逻辑删除的字典项重复
- **THEN** 系统拒绝创建，返回业务错误（非零 `code`）

#### Scenario: 不同字典类型下允许相同编码
- **WHEN** 客户端在字典类型 A 下已存在编码为 `X` 的字典项，随后调用 `POST /api/dict-items` 在字典类型 B 下创建编码同样为 `X` 的字典项
- **THEN** 系统允许创建成功

### Requirement: 更新字典项
系统 SHALL 支持更新字典项的名称（`label`）、编码、显示序号、备注；编码唯一性校验范围为同一 `dictTypeId` 下未被逻辑删除的字典项，且排除被更新对象自身；不支持通过更新接口修改字典项所属的 `dictTypeId`。

#### Scenario: 成功更新字典项
- **WHEN** 客户端调用 `PUT /api/dict-items/{id}`，携带合法的 `label`、`code`、`showOrder`
- **THEN** 系统更新该字典项信息并返回更新后的结果，`status`、`dictTypeId` 保持不变

#### Scenario: 同一字典类型下编码与其他字典项重复时拒绝更新
- **WHEN** 客户端调用 `PUT /api/dict-items/{id}`，其 `code` 与同一 `dictTypeId` 下另一个未删除字典项重复（非自身）
- **THEN** 系统拒绝更新，返回业务错误

### Requirement: 字典项启用与停用
系统 SHALL 提供独立的接口将字典项状态切换为启用（`2000`）或停用（`3000`）。

#### Scenario: 启用字典项
- **WHEN** 客户端调用 `PUT /api/dict-items/{id}/enable`
- **THEN** 系统将该字典项 `status` 置为 `2000` 并返回更新后的信息

#### Scenario: 停用字典项
- **WHEN** 客户端调用 `PUT /api/dict-items/{id}/disable`
- **THEN** 系统将该字典项 `status` 置为 `3000` 并返回更新后的信息

### Requirement: 字典项逻辑删除
系统 SHALL 支持对字典项执行逻辑删除（将 `status` 置为 `-1000`），不做物理删除。

#### Scenario: 成功删除字典项
- **WHEN** 客户端调用 `DELETE /api/dict-items/{id}`
- **THEN** 系统将该字典项 `status` 置为 `-1000`，此后不再出现在分页查询、详情查询及按类型编码的只读查询结果中

### Requirement: 按字典类型编码查询启用项
系统 SHALL 提供一个不分页的只读接口，供业务模块按字典类型编码获取其下全部启用状态的字典项精简列表（`code`、`label`、`showOrder`），按 `showOrder` 降序、相同时按 `id` 升序排列。

#### Scenario: 查询存在且启用的字典类型下的启用字典项
- **WHEN** 客户端调用 `GET /api/dicts/items?typeCode=position_type`
- **THEN** 系统返回该字典类型下全部 `status = 2000` 的字典项精简列表（`code`、`label`、`showOrder`）

#### Scenario: 字典类型不存在、已删除或已停用时返回空列表
- **WHEN** 客户端调用 `GET /api/dicts/items?typeCode={code}`，其中 `typeCode` 不存在、对应字典类型已被逻辑删除、或已被停用
- **THEN** 系统返回空列表，不返回业务错误

#### Scenario: 字典项本身被停用时不出现在结果中
- **WHEN** 某字典类型下的字典项 `status` 为 `3000`（停用）
- **THEN** 该字典项不出现在 `GET /api/dicts/items?typeCode={code}` 的返回结果中

### Requirement: 字典状态语义
系统 SHALL 使用统一的整型状态码表达字典类型与字典项的启停用与删除语义：`2000` 表示启用，`3000` 表示停用，`-1000` 表示已逻辑删除；三者互斥。

#### Scenario: 状态码含义一致
- **WHEN** 系统返回任意字典类型或字典项的 `status` 字段
- **THEN** 其值必为 `2000`、`3000`、`-1000` 三者之一，分别代表启用、停用、已删除

### Requirement: 预置认证类型字典数据
系统 SHALL 通过数据库迁移预置一个编码为 `position_type`、名称为"认证类型"的字典类型，及其下编码分别为 `primary`（标签"主职"）、`part_time`（标签"兼职"）、`temporary`（标签"挂职"）的三个字典项，初始状态均为启用（`2000`）。

#### Scenario: 迁移执行后可查询到预置的认证类型字典
- **WHEN** 数据库迁移执行完成后客户端调用 `GET /api/dicts/items?typeCode=position_type`
- **THEN** 系统返回包含 `primary`/`part_time`/`temporary` 三项、标签分别为"主职"/"兼职"/"挂职"的列表

### Requirement: 预置性别字典数据
系统 SHALL 通过数据库迁移预置一个编码为 `gender`、名称为"性别"的字典类型，及其下编码分别为 `unknown`（标签"未知"）、`male`（标签"男"）、`female`（标签"女"）的三个字典项，初始状态均为启用（`2000`）。

#### Scenario: 迁移执行后可查询到预置的性别字典
- **WHEN** 数据库迁移执行完成后客户端调用 `GET /api/dicts/items?typeCode=gender`
- **THEN** 系统返回包含 `unknown`/`male`/`female` 三项、标签分别为"未知"/"男"/"女"的列表

### Requirement: 字典管理前端界面
系统 SHALL 提供字典管理页面（路径 `/system/dicts`），左侧展示字典类型的分页列表，右侧以分页表格展示选中字典类型下的字典项数据；页面进入时右侧面板标题保持空白，用户点击左侧某个字典类型后标题变为"[该字典类型名称]的字典项"。左右两侧均支持增、改、启用/停用、删除、详情操作；字典类型、字典项各自提供独立的"详情"入口，均 SHALL 以独立路由页面承载，不再使用弹窗：字典类型详情页面路径为 `/system/dicts/type/:id`，字典项详情页面路径为 `/system/dicts/item/:id`；两个详情页面均以只读方式展示完整字段（含备注、创建人、创建时间、更新人、更新时间）及该条记录自身的操作历史，页面左上角 SHALL 提供"返回"按钮，点击后返回字典管理列表页（`/system/dicts`）。左侧字典类型列表、右侧字典项表格 SHALL 各自独立提供分页控件，均在页码按钮前提供每页条数下拉选择器，可选值为 10/20/50/100，默认 10；用户在任一侧切换每页条数后，系统 SHALL 仅针对该侧从第一页重新查询并展示，不影响另一侧当前的分页状态。

#### Scenario: 默认状态下右侧面板为空
- **WHEN** 用户打开字典管理页面且尚未点击左侧任何字典类型
- **THEN** 右侧面板标题保持空白，不展示任何字典项数据

#### Scenario: 选中字典类型后展示其字典项并更新标题
- **WHEN** 用户点击左侧列表中的某个字典类型
- **THEN** 右侧表格展示该字典类型下的字典项分页列表（重置为第一页），右侧面板标题变为"[该字典类型名称]的字典项"

#### Scenario: 新增字典项时归属字典类型不可更改
- **WHEN** 用户在已选中某个字典类型的情况下点击右侧"新增"
- **THEN** 新增表单的所属字典类型固定为当前选中的类型，不提供切换其他类型的入口

#### Scenario: 左侧列表操作后右侧联动刷新
- **WHEN** 用户对左侧某个字典类型执行启用、停用或删除操作，且该类型正是当前右侧选中展示的类型
- **THEN** 操作成功后左侧列表与右侧字典项表格均刷新；若该字典类型被删除，右侧面板恢复为未选中的空白状态

#### Scenario: 查看字典类型详情
- **WHEN** 用户在左侧字典类型列表某一行点击"详情"
- **THEN** 系统跳转到字典类型详情页面（`/system/dicts/type/{id}`），以只读方式展示该字典类型的完整信息：类型名称、编码、显示序号、备注、状态、创建人、创建时间、更新人、更新时间

#### Scenario: 查看字典项详情
- **WHEN** 用户在右侧字典项表格某一行点击"详情"
- **THEN** 系统跳转到字典项详情页面（`/system/dicts/item/{id}`），以只读方式展示该字典项的完整信息：所属字典类型名称、字典项标签、编码、显示序号、备注、状态、创建人、创建时间、更新人、更新时间

#### Scenario: 从字典类型详情页面返回列表
- **WHEN** 用户在字典类型详情页面点击左上角"返回"按钮
- **THEN** 系统导航回字典管理列表页（`/system/dicts`）

#### Scenario: 从字典项详情页面返回列表
- **WHEN** 用户在字典项详情页面点击左上角"返回"按钮
- **THEN** 系统导航回字典管理列表页（`/system/dicts`）

#### Scenario: 切换左侧字典类型列表每页条数
- **WHEN** 用户在左侧字典类型列表下方的分页控件中选择 20/50/100 中的某个每页条数
- **THEN** 系统按新的每页条数从第一页重新查询并展示字典类型列表，右侧字典项表格的分页状态不受影响，默认每页条数保持 10 不变

#### Scenario: 切换右侧字典项列表每页条数
- **WHEN** 用户已选中某个字典类型，并在右侧字典项表格下方的分页控件中选择 20/50/100 中的某个每页条数
- **THEN** 系统按当前选中的字典类型与新的每页条数从第一页重新查询并展示字典项列表，左侧字典类型列表的分页状态不受影响，默认每页条数保持 10 不变

### Requirement: 字典详情操作历史展示
系统 SHALL 在字典类型详情页面、字典项详情页面中分别展示该条记录自身的操作历史列表：按操作发起时间降序排列，每页 5 条，支持分页；覆盖新增、编辑、启用、停用四类操作（不包含删除记录——记录被逻辑删除后其详情页面本身不可访问，历史列表天然不会出现删除记录）；每条历史记录展示操作时间、操作类型、操作人，其字段级变更详情（旧值→新值）SHALL 默认直接展示在该条记录下方，不需要额外点击即可看到。进入两个详情页面时 SHALL 分别展示截至当前的最新操作历史，不依赖任何缓存的旧数据。

#### Scenario: 打开字典类型详情页面时展示操作历史
- **WHEN** 用户打开某个字典类型的详情页面
- **THEN** 系统调用 `GET /api/operation-logs?resourceType=dictType&targetId={该字典类型id}&page=1&pageSize=5`，按操作发起时间降序展示该字典类型的操作历史，每条记录均已带有字段级变更详情

#### Scenario: 打开字典项详情页面时展示操作历史
- **WHEN** 用户打开某个字典项的详情页面
- **THEN** 系统调用 `GET /api/operation-logs?resourceType=dictItem&targetId={该字典项id}&page=1&pageSize=5`，按操作发起时间降序展示该字典项的操作历史，每条记录均已带有字段级变更详情

#### Scenario: 操作历史默认展示字段变更明细
- **WHEN** 用户查看字典类型或字典项详情页面的操作历史列表
- **THEN** 每条记录下方直接展示该次操作的字段级变更列表（字段名、旧值、新值），无需点击任何"查看变更"之类的操作即可看到

#### Scenario: 该记录没有可查询到的操作历史时展示空状态
- **WHEN** 该字典类型或字典项是通过数据库迁移预置（Flyway 种子数据，如预置的"认证类型"字典）创建、从未经由本系统的新增/编辑/启用/停用接口产生过操作记录
- **THEN** 操作历史列表展示为空并提示"暂无操作记录"，不视为异常

#### Scenario: 离开详情页面后编辑再重新进入时展示最新操作历史
- **WHEN** 用户打开某个字典类型（或字典项）的详情页面查看历史后返回列表，随后编辑保存该字典类型（或字典项），再次进入同一条记录的详情页面
- **THEN** 操作历史列表 SHALL 重新拉取并展示包含刚才那次编辑在内的最新记录，而不是停留在上一次进入时的旧列表
