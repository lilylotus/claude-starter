## 1. 后端字典模块

- [x] 1.1 编写 Flyway 迁移脚本建表 `tab_dict_type`（id/name/code/show_order/remark/status/审计字段）、`tab_dict_item`（id/dict_type_id/label/code/show_order/remark/status/审计字段）
- [x] 1.2 编写 Flyway 迁移脚本预置种子数据：`position_type`（认证类型）字典类型及 `primary`/`part_time`/`temporary`（主职/兼职/挂职）三个字典项
- [x] 1.3 新增 `DictTypeEntity`、`DictItemEntity`（MyBatis-Plus 实体）与 `DictStatus`（状态常量：2000 启用/3000 停用/-1000 逻辑删除）
- [x] 1.4 新增 `DictTypeMapper`、`DictItemMapper`（MyBatis-Plus）
- [x] 1.5 新增 DTO/VO：`DictTypeCreateRequest`、`DictTypeUpdateRequest`、`DictTypeVO`、`DictItemCreateRequest`、`DictItemUpdateRequest`、`DictItemVO`、`DictItemOptionVO`（只读查询用的精简 code/label/showOrder）
- [x] 1.6 新增 `DictConvert`（MapStruct），实体与各 DTO/VO 互转（`dictTypeName`/审计字段按场景显式 ignore）
- [x] 1.7 实现 `DictTypeService`/`DictTypeServiceImpl`：分页查询（按名称/编码模糊搜索）、详情查询、创建（编码全局唯一性校验）、更新（编码唯一性校验，排除自身）、启用/停用、逻辑删除（删除前校验是否存在未删除字典项）
- [x] 1.8 实现 `DictItemService`/`DictItemServiceImpl`：分页查询（按 `dictTypeId`）、详情查询（回填 `dictTypeName`）、创建（同一 `dictTypeId` 下编码唯一性校验）、更新（同上，排除自身）、启用/停用、逻辑删除、按 `typeCode` 查启用项列表（类型不存在/已删除/已停用时返回空列表，不报错）
- [x] 1.9 实现 `DictTypeController`：`GET /api/dict-types`、`GET /api/dict-types/{id}`、`POST /api/dict-types`、`PUT /api/dict-types/{id}`、`PUT /api/dict-types/{id}/enable`、`PUT /api/dict-types/{id}/disable`、`DELETE /api/dict-types/{id}`，附 springdoc `@Tag`/`@Operation` 注解
- [x] 1.10 实现 `DictItemController`：`GET /api/dict-items`、`GET /api/dict-items/{id}`、`POST /api/dict-items`、`PUT /api/dict-items/{id}`、`PUT /api/dict-items/{id}/enable`、`PUT /api/dict-items/{id}/disable`、`DELETE /api/dict-items/{id}`，附 springdoc 注解
- [x] 1.11 实现只读查询接口 `GET /api/dicts/items?typeCode={code}`（归入 `DictItemController`，未单独建 Controller），附 springdoc 注解
- [x] 1.12 编写 `DictTypeServiceImplTest`、`DictItemServiceImplTest` 单元测试：编码重复拒绝创建/更新、删除时存在字典项拒绝、按 typeCode 查询启用项（含类型不存在/已停用返回空列表、字典项本身停用不出现在结果中）等关键场景
- [x] 1.13 `./gradlew build` 全量通过

## 2. 前端字典管理页面

- [x] 2.1 新增 `src/types/dict.ts`：`DictTypeRow`、`DictItemRow`、`DictTypeFormRequest`、`DictItemFormRequest` 类型及状态常量，字段与后端 DTO 对齐
- [x] 2.2 新增 `src/api/dict.ts`，封装字典类型、字典项相关接口（含只读查询接口，供其他模块的 store/组件复用）
- [x] 2.3 新增 `src/stores/dict.ts`（Pinia setup store）：字典类型分页列表、当前选中类型 id、字典项分页列表、左右两侧独立的 loading 状态
- [x] 2.4 新增 `src/views/system/dict/DictManagementView.vue`：左侧 `el-table` 展示字典类型分页列表，右侧 `el-table` 展示选中类型的字典项分页列表，视觉延续项目"链式连接"语言
- [x] 2.5 左右两侧的新增/编辑弹窗、启用/停用切换（行内操作）、删除（`ElMessageBox.confirm` 二次确认）
- [x] 2.6 `router/menu.ts` 的 `system` 分组新增"字典管理"菜单项；`router/index.ts` 中 `/system/dicts` 路由从 `PlaceholderView.vue` 改为指向 `DictManagementView.vue`
- [x] 2.7 `npm run build`（vue-tsc 类型检查 + vite build）全量通过，无类型错误

## 3. 验证

- [x] 3.1 本地 `./gradlew bootRun` 触发 Flyway 迁移，核对 `tab_dict_type`/`tab_dict_item` 表结构及预置的 `position_type` 种子数据；对字典类型、字典项的增/改/启用/停用/删除接口及只读查询接口做端到端冒烟测试（curl 全量覆盖：分页/详情/增/改/启停用/删除/只读查询/删除被拒绝/停用父类型后只读查询降级为空列表，均符合预期）
- [ ] 3.2 浏览器中验证字典管理页面：类型列表分页、选中类型联动字典项表格、增删改启停用操作、删除有字典项的类型被拒绝（当前会话工具集里没有浏览器自动化能力，只验证了 `npm run dev` 正常启动、页面路由返回 200、`/api` 代理到后端正常，未做真实浏览器点击验证，需要用户或后续会话手动在浏览器里走查一遍）
