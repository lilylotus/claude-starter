## ADDED Requirements

### Requirement: 组织路径字段自动维护
系统 SHALL 为每个组织持久化三个路径字段：组织路径（`orgPath`，用 `/` 分隔、从根组织到自身的 id 序列，如 `1/2/3`）、组织名称路径（`orgNamePath`，同样用 `/` 分隔、从根组织到自身的名称序列，如 `一级组织/二级组织/三级组织`）、上级组织路径（`orgParentPath`，即 `orgPath` 去掉最后一段自身 id；顶级组织 `orgParentPath` SHALL 为空）。这三个字段完全由系统根据组织树结构与名称派生维护，不接受客户端在新增/更新组织接口中直接指定其取值（请求体中即使携带这些字段也 SHALL 被忽略）。

创建组织时，系统 SHALL 在写入组织记录的同一事务内，按请求的 `parentId` 解析上级组织当前的 `orgPath`/`orgNamePath`，拼接自身 id/名称后写入新组织的 `orgPath`/`orgNamePath`/`orgParentPath`（顶级组织的 `orgPath` 即自身 id、`orgNamePath` 即自身名称、`orgParentPath` 为空）。

更新组织导致 `parentId` 发生变化（变更了上级组织）时，系统 SHALL 重新解析该组织自身的 `orgPath`/`orgParentPath`，并级联更新其**全部**未被逻辑删除的子孙组织（不限于直属子组织）的 `orgPath`/`orgParentPath`——子孙组织路径中包含该组织的祖先链，上级组织变化会影响路径中这一段，因此需要级联到全部子孙层级；未变更上级组织时 `orgPath`/`orgParentPath` 保持不变。

组织自身的 `name` 发生变化时，系统 SHALL 级联更新该组织**全部**未被逻辑删除的子孙组织（不限于直属子组织）的 `orgNamePath`——因为改名的组织可能是某些子孙节点路径中间的一段，不止影响直属子组织。

#### Scenario: 新建非顶级组织时自动派生路径字段
- **WHEN** 客户端调用 `POST /api/orgs` 创建一个组织，`parentId` 指向一个 `orgPath` 为 `1/2`、`orgNamePath` 为 `一级组织/二级组织` 的既有组织，新组织自身 id 为 `3`、名称为"三级组织"
- **THEN** 系统创建成功，新组织的 `orgPath` 为 `1/2/3`、`orgNamePath` 为 `一级组织/二级组织/三级组织`、`orgParentPath` 为 `1/2`

#### Scenario: 新建顶级组织时路径字段只含自身
- **WHEN** 客户端调用 `POST /api/orgs` 创建一个 `parentId=0` 的顶级组织，自身 id 为 `10`、名称为"顶级组织"
- **THEN** 系统创建成功，新组织的 `orgPath` 为 `10`、`orgNamePath` 为"顶级组织"、`orgParentPath` 为空

#### Scenario: 变更上级组织时级联更新全部子孙的路径字段
- **WHEN** 组织 C（`orgPath=1/2/3`）拥有子孙组织 D（`orgPath=1/2/3/4`）与孙组织 E（`orgPath=1/2/3/4/5`），审批通过后组织 C 的上级组织从组织 2 改为组织 6（`orgPath=1/6`）
- **THEN** 系统更新后，C 的 `orgPath` 变为 `1/6/3`，D 的 `orgPath` 变为 `1/6/3/4`，E 的 `orgPath` 变为 `1/6/3/4/5`，三者的 `orgParentPath` 同步更新为各自 `orgPath` 去掉最后一段的值

#### Scenario: 组织改名时级联更新全部子孙的名称路径
- **WHEN** 组织 B（`orgNamePath=一级组织/二级组织`）拥有子孙组织 C（`orgNamePath=一级组织/二级组织/三级组织`）与孙组织 D，审批通过后组织 B 改名为"二级组织新"
- **THEN** 系统更新后，B 的 `orgNamePath` 变为 `一级组织/二级组织新`，C 的 `orgNamePath` 变为 `一级组织/二级组织新/三级组织`，D 的 `orgNamePath` 同步替换中间这一段，其余未变化的祖先/自身名称段保持不变

#### Scenario: 未变更上级组织与名称时路径字段保持不变
- **WHEN** 客户端调用 `PUT /api/orgs/{id}`，请求携带的 `parentId` 与该组织当前 `parentId` 相同，`name` 与当前值相同，仅修改其他字段
- **THEN** 系统更新成功，该组织及其子孙组织的 `orgPath`/`orgNamePath`/`orgParentPath` 均保持原值不变

#### Scenario: 请求体携带路径字段时被忽略
- **WHEN** 客户端调用 `POST /api/orgs` 或 `PUT /api/orgs/{id}`，请求体中携带了 `orgPath`/`orgNamePath`/`orgParentPath` 字段
- **THEN** 系统忽略这些字段的客户端取值，按上述规则由系统自行派生
