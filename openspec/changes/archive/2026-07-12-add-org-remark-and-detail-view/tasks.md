## 1. 后端：新增 remark 字段

- [x] 1.1 新增 Flyway 迁移 `V2__add_org_remark.sql`：`ALTER TABLE tab_org ADD COLUMN remark VARCHAR(255) NULL COMMENT '备注' AFTER show_order;`
- [x] 1.2 `OrgEntity` 补充 `remark` 字段
- [x] 1.3 `OrgCreateRequest`、`OrgUpdateRequest`、`OrgVO` 补充 `remark` 字段
- [x] 1.4 `OrgConvert`（MapStruct）确认 `remark` 在 `toEntity`/`updateEntity`/`toVO` 中按同名字段自动映射，无需额外 `@Mapping`
- [x] 1.5 `./gradlew build` 通过；针对真实本地 MySQL 执行 `bootRun` 触发迁移，`DESCRIBE tab_org` 确认新增了 `remark` 列（位于 `show_order` 之后），`flyway_schema_history` 记录 v2 成功
- [x] 1.6 冒烟测试：`curl` 创建带 `remark` 的组织，`GET /api/orgs/{id}` 读回一致，清理测试数据

## 2. 前端：类型与表单

- [x] 2.1 `src/types/org.ts` 的 `OrgRow`、`OrgFormRequest` 补充 `remark: string`
- [x] 2.2 新增/编辑表单加入"备注"输入框（`el-input type="textarea"`，可选，不加校验规则）

## 3. 前端：列表列调整 + 详情弹窗

- [x] 3.1 移除表格中的"新增人""新增时间""更新人""更新时间"四列
- [x] 3.2 操作列新增"详情"链接按钮（位于"编辑"之前），操作列宽度 200px → 240px
- [x] 3.3 新增只读详情 `el-dialog`（`el-descriptions`），复用 `orgApi.getOrgById` 加载数据，展示组织名称、编码、上级组织（名称）、状态、显示序号、备注、新增人、新增时间、更新人、更新时间
- [x] 3.4 `npm run build` 通过；用 `playwright-core` 驱动本机 Edge 实测：登录 → 新增父组织 → 选中父组织后新增带备注的子组织 → 断言表头仅剩"组织名称/编码/状态/显示序号/操作"（无审计字段列）→ 打开子组织"详情"，断言弹窗内容包含全部字段和备注文本 → 截图确认视觉正确 → 清理测试数据

## 4. OpenSpec 收尾

- [x] 4.1 `openspec-sync-specs` 把本 change 的 delta 同步进 `openspec/specs/org-management/spec.md`
- [x] 4.2 不执行归档（archive 需用户手动确认后再执行）
