## 1. 数据库迁移

- [ ] 1.1 新增 Flyway 迁移脚本：`tab_org` 增加 `org_path`/`org_name_path`/`org_parent_path` 三列（`VARCHAR(255)`，均可为空），字段命名检查与 MySQL/PostgreSQL/Oracle/SQL Server 保留字无冲突；各自建普通 `KEY` 索引（`org_path`、`org_name_path`）；验证：`./gradlew bootRun` 启动时迁移无报错，`DESCRIBE tab_org;` 三个新列与索引齐全
- [ ] 1.2 同一迁移脚本内，按 design.md Decision 6 的多轮 `UPDATE` 方式回填存量数据（顶级组织一轮 + 固定轮数的父子 JOIN 回填，覆盖注释里声明的树深度上限）；验证：`SELECT id, parent_id, org_path, org_name_path, org_parent_path FROM tab_org ORDER BY id;` 抽查顶级组织、中间层组织、叶子组织三种情形的路径字段正确，且不存在 `org_path IS NULL` 的未删除组织

## 2. 后端：路径字段维护

- [ ] 2.1 `OrgEntity` 新增 `orgPath`/`orgNamePath`/`orgParentPath` 三个字段；`OrgConvert` 补充映射（`toEntity`/`toVO`/`updateEntity` 按 design.md Decision 7 的建议决定是否纳入 `OrgVO`）；验证：编译通过
- [ ] 2.2 `OrgMapper` 新增 `cascadeUpdateOrgPath`（按 design.md Decision 3 的 SQL，级联更新自身+全部子孙的 `org_path`/`org_parent_path`）与 `cascadeUpdateOrgNamePath`（级联更新自身+全部子孙的 `org_name_path`），SQL 写在 `resources/mybatis/mapper/OrgMapper.xml`，全部使用 `#{}` 占位符、MySQL 5.7 兼容写法（不使用窗口函数/CTE/`REGEXP_REPLACE` 等 8.0+ 专属特性）；验证：新增 Mapper 单元测试覆盖"级联影响自身+全部子孙""不影响无关分支组织"两种场景
- [ ] 2.3 `OrgServiceImpl.create`：写入组织记录时，按 `parentId` 解析上级组织当前的 `orgPath`/`orgNamePath` 拼接写入新组织自身的三个路径字段（顶级组织时 `orgPath`=自身 id、`orgNamePath`=自身名称、`orgParentPath`=null）；验证：单元测试覆盖创建顶级组织、创建非顶级组织两种路径字段结果
- [ ] 2.4 `OrgServiceImpl.update`：`parentId` 变化时，先计算新的 `orgPath`/`orgParentPath` 并更新自身，再调用 `cascadeUpdateOrgPath` 级联更新全部子孙；`name` 变化时调用 `cascadeUpdateOrgNamePath` 级联更新自身+全部子孙的 `orgNamePath`；两处级联更新与主记录写入在同一事务内；验证：单元测试覆盖"变更上级组织后自身与多层子孙的 orgPath/orgParentPath 都正确更新""改名后自身与多层子孙的 orgNamePath 都正确更新""未变更上级组织/名称时路径字段不变"三种场景
- [ ] 2.5 `OrgDescendantExpander.expandWithDescendants` 改造为按 `orgPath` 前缀查询实现（design.md Decision 4），方法签名与返回语义不变；验证：`OrgScopeServiceImpl`/`AppSyncOrgScopeResolver` 相关的既有单元测试全部保持通过，不需要修改这两个调用方的代码；新增/调整 `OrgDescendantExpander` 自身的单元测试，覆盖单根/多根、含已删除子孙节点被排除等场景

## 3. 校验与回归

- [ ] 3.1 新增校验：新增/更新组织请求体中若携带 `orgPath`/`orgNamePath`/`orgParentPath` 字段，系统忽略其取值（`OrgCreateRequest`/`OrgUpdateRequest` 本身不声明这三个字段即可天然满足，验证：确认这两个 DTO 未新增对应字段，反序列化多余字段被忽略不报错）
- [ ] 3.2 运行组织模块现有完整单元测试与集成测试套件，确认本次改造没有破坏创建/更新/启停用/删除、管辖组织范围过滤、审批流程分流等既有行为；验证：`./gradlew test --tests "cn.nihility.rbac.org.*"` 全部通过
- [ ] 3.3 运行依赖 `OrgDescendantExpander` 的既有测试套件（管辖组织范围解析、应用同步组织范围解析），确认新旧实现结果一致；验证：`./gradlew test --tests "cn.nihility.rbac.auth.*" --tests "cn.nihility.rbac.sync.*"` 全部通过

## 4. 端到端验证

- [ ] 4.1 本地启动后端，创建一个三层组织结构（顶级→二级→三级），确认三层的 `org_path`/`org_name_path`/`org_parent_path` 均符合预期
- [ ] 4.2 将中间层组织变更到另一个上级组织下，确认自身与全部子孙组织的 `org_path`/`org_parent_path` 级联更新正确，且组织树、管辖组织范围过滤功能表现与改造前一致
- [ ] 4.3 将中间层组织改名，确认自身与全部子孙组织的 `org_name_path` 级联更新正确
