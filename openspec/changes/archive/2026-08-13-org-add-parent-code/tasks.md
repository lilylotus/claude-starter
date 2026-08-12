## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V10__org_parent_code.sql`：`ALTER TABLE tab_org ADD COLUMN parent_code VARCHAR(64) NULL COMMENT '上级组织编码' AFTER parent_id;`
- [x] 1.2 同一脚本内追加回填 SQL：`UPDATE tab_org t LEFT JOIN tab_org p ON t.parent_id = p.id SET t.parent_code = p.code WHERE t.parent_id != 0;`（`parent_id = 0` 的顶级组织不匹配 `p`，`parent_code` 保持 `NULL`）

## 2. 后端实体与 DTO

- [x] 2.1 `OrgEntity` 新增 `parentCode` 属性（`String`），紧跟 `parentId` 之后，补充字段注释
- [x] 2.2 `OrgVO` 新增 `parentCode` 属性
- [x] 2.3 `OrgTreeNodeVO` 新增 `parentCode` 属性
- [x] 2.4 确认 `OrgConvert`（`toVO`/`toVOList`/`toTreeNode`）无需新增 `@Mapping` 声明，`parentCode` 按同名属性自动带出；`toEntity`/`updateEntity` 不将请求体中的 `parentCode`（若客户端携带）映射到实体，保持忽略（新增了显式 `@Mapping(target = "parentCode", ignore = true)` 以消除 MapStruct 未映射目标属性警告）

## 3. 后端 Service/Mapper：自动派生与级联更新

- [x] 3.1 `OrgMapper` 新增方法（Lambda `UpdateWrapper`，无需手写 XML）：按 `parentId` 批量更新未删除子组织的 `parentCode`
- [x] 3.2 `OrgServiceImpl.create`：解析 `parentId` 对应父组织的 `code` 并写入 `entity.parentCode`（`parentId=0` 时置空）
- [x] 3.3 `OrgServiceImpl.update`：仅当 `parentId` 发生变化时重新解析新上级组织 `code` 写入 `parentCode`；若本次更新导致该组织自身 `code` 变化，追加一次批量更新其直属未删除子组织的 `parentCode`（复用 3.1 的 Mapper 方法）
- [x] 3.4 确认 `create`/`update` 方法具备事务边界（类或方法级 `@Transactional`），保证主记录写入与级联更新子组织在同一事务内

## 3.5 后端操作历史字段快照补充（实现后补充发现的缺口）

- [x] 3.5 `OrgServiceImpl.toLogSnapshot`：在 `snapshot.put("上级组织", parentName)` 之后追加 `snapshot.put("上级组织编码", entity.getParentCode())`，使变更上级组织时操作历史能看到编码侧的旧值→新值变化；级联更新子组织 `parentCode`（3.3）不经过该快照路径，不需要改动

## 4. 后端验证

- [x] 4.1 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 确认应用可正常启动（含 Flyway 迁移执行成功）
- [ ] 4.2 手工验证：创建组织、变更组织上级、修改组织自身编码三种场景下 `parentCode` 均符合预期（含级联更新直属子组织、不影响已删除子组织、不影响孙级）

## 5. 前端类型与页面

- [x] 5.1 `frontend/src/types/org.ts` 组织详情/树节点相关类型新增 `parentCode` 字段
- [x] 5.2 `frontend/src/views/identity/org/OrgDetailView.vue` 在"上级组织"展示项附近新增"上级组织编码"只读展示项，顶级组织展示为空
- [x] 5.3 确认新增/编辑组织表单不新增 `parentCode` 输入项（无需改动，仅核实）

## 6. 权限资源编码文件核对

- [x] 6.1 核对根目录 `权限资源.txt`：本次改动不新增/删除菜单或按钮，无需更新（组织详情页复用既有 `OrgManagement:org:detail` 权限点）

## 7. 元数据字段目录补充（实现后补充发现的缺口）

- [x] 7.1 新增 Flyway 脚本 `backend/src/main/resources/db/migration/V11__org_parent_code_metadata_field.sql`：往 `tab_metadata_field` 插入一条 `bizType=ORG`、`tableName=tab_org`、`columnName=parent_code`、`fieldCode=parentCode`、`columnType=VARCHAR(64)`、`fieldName=上级组织编码`、`status=2000` 的种子记录，列顺序/风格参照 `V9__metadata_field_role_seed.sql`
- [x] 7.2 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 确认新迁移脚本能正常执行、应用能正常启动
- [ ] 7.3 手工验证：调用 `GET /api/metadata-fields/available?bizType=ORG` 或元数据配置前端页面（`/system/metadata-fields`）能看到"上级组织编码"记录，且可在"应用同步字段映射配置"页面的组织数据域字段映射表格中被选为源字段
