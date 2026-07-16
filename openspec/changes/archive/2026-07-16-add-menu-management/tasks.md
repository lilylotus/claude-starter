## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V10__init_tab_menu.sql`：新建 `tab_menu` 表（`id`/`name`/`code`/`parent_id`/`resource_type`/`show_order`/`remark`/`status`/审计字段），`parent_id` 默认 `0`，`status` 默认 `2000`，为 `parent_id`/`status`/`code` 加索引；列名已核对不与 MySQL/PostgreSQL/Oracle/SQL Server 保留字冲突（用 `resource_type` 而非 `type`）

## 2. 后端：资源主数据基础

- [x] 2.1 新增 `cn.nihility.rbac.menu.constant.MenuStatus`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`），风格对齐 `OrgStatus`/`RoleStatus`/`PermissionStatus`
- [x] 2.2 新增 `cn.nihility.rbac.menu.constant.MenuResourceType`（`MENU=1`/`BUTTON=2`/`API=3`），并提供一个校验取值合法性的静态方法（`isValid(Integer)`）
- [x] 2.3 新增 `cn.nihility.rbac.menu.entity.MenuEntity`（对应 `tab_menu`，字段：`id`/`name`/`code`/`parentId`/`resourceType`/`showOrder`/`remark`/`status`/审计字段）
- [x] 2.4 新增 `cn.nihility.rbac.menu.mapper.MenuMapper`（MyBatis-Plus `BaseMapper<MenuEntity>`）

## 3. 后端：菜单管理接口

- [x] 3.1 新增 `MenuTreeNodeVO`（`id`/`name`/`code`/`parentId`/`resourceType`/`status`/`showOrder`/`children`），结构对齐 `OrgTreeNodeVO`
- [x] 3.2 新增 `MenuVO`（详情/表格用，含 `id`/`name`/`code`/`parentId`/`parentName`/`resourceType`/`showOrder`/`remark`/`status`/审计字段）
- [x] 3.3 新增 `MenuCreateRequest`（`name`/`code`/`resourceType` 必填，`parentId` 必填、默认场景由前端传 `0`，`showOrder` 默认 `0`，`remark` 可选）
- [x] 3.4 新增 `MenuUpdateRequest`（不含 `status`；`name`/`code`/`resourceType`/`parentId` 必填，`showOrder`/`remark` 可选）
- [x] 3.5 新增 `MenuConvert`（MapStruct，静态单例风格同 `OrgConvert`：`Xxx INSTANCE = Mappers.getMapper(Xxx.class)`，不用 `componentModel = "spring"`），提供 entity↔VO/TreeNodeVO/Request 的转换方法；`parentName` 与 `OrgConvert.toVO` 一致地显式 `@Mapping(target = "parentName", ignore = true)`，由 service 层批量回填
- [x] 3.6 新增 `MenuService`/`MenuServiceImpl`（结构照抄 `OrgServiceImpl`，多一个资源类型校验）：
  - `getTree()`：一次性拉取未删除资源、内存建树，返回顶级节点列表
  - `getChildren(parentId, page, pageSize)`：分页查询指定上级资源的直属子资源，按 `showOrder` 降序、`id` 升序，批量回填 `parentName`
  - `getChildrenTreeNodes(parentId)`：不分页查询直属子资源，供左侧树懒加载
  - `getById(id)`：查询未删除记录详情（含 `parentName`）
  - `create(request)`：校验 `resourceType` 合法性、校验编码唯一后新增，`status` 显式置为 `ENABLED`
  - `update(id, request)`：校验 `resourceType` 合法性、校验编码唯一（排除自身）后更新除 `status` 外的字段
  - `enable(id)`/`disable(id)`：状态切换
  - `delete(id)`：删除前校验是否存在未删除的下级资源（`parent_id = id AND status != DELETED`），存在则拒绝；否则逻辑删除
  - `checkCodeUnique(code, excludeId)`：私有方法，照抄 `OrgServiceImpl.checkCodeUnique`
- [x] 3.7 新增 `MenuController`：`GET /api/menus/tree`、`GET /api/menus/tree/children?parentId=`、`GET /api/menus/children?parentId=&page=&pageSize=`、`GET /api/menus/{id}`、`POST /api/menus`、`PUT /api/menus/{id}`、`PUT /api/menus/{id}/enable`、`PUT /api/menus/{id}/disable`、`DELETE /api/menus/{id}`，均加 springdoc `@Tag`/`@Operation` 注解
- [x] 3.8 新增 `MenuServiceImplTest`（`backend/src/test/java/cn/nihility/rbac/menu/service/impl/`），风格对齐 `OrgServiceImplTest`：覆盖树查询、懒加载子节点查询、分页查询、新增默认启用、新增编码唯一性校验、新增非法资源类型拒绝、删除时存在/不存在子资源两种分支、查询不存在/已删除记录抛业务异常（共 9 个测试用例，全部通过）

## 4. 前端：菜单管理页面

- [x] 4.1 新增 `frontend/src/types/menuResource.ts`（`MenuResourceTreeNode`、`MenuResourceRow`、`MenuResourceFormRequest`、状态常量 `MENU_STATUS_ENABLED`/`MENU_STATUS_DISABLED`、资源类型常量 `MENU_RESOURCE_TYPE_MENU`/`MENU_RESOURCE_TYPE_BUTTON`/`MENU_RESOURCE_TYPE_API` 及对应中文标签映射，对齐后端 DTO 字段命名）——**实施偏差**：原计划命名为 `types/menu.ts`，实现时发现该文件名已被侧边栏 `MenuGroup`/`MenuChild` 类型占用，改名为 `menuResource.ts` 避免覆盖，详见 design.md 决策
- [x] 4.2 新增 `frontend/src/api/menu.ts`（`getMenuTree`、`getMenuTreeChildren`、`getMenuChildren`、`getMenuById`、`createMenu`、`updateMenu`、`enableMenu`、`disableMenu`、`deleteMenu`），结构对齐 `api/org.ts`
- [x] 4.3 新增 `frontend/src/stores/menu.ts`（Pinia，照抄 `stores/org.ts` 的左树右表状态结构：`tree`/`navTreeTopLevel`/`selectedId`/`selectedName`/`currentParentId`/`children`/分页状态，及 `fetchTree`/`loadNavTreeChildren`/`fetchChildren`/`changePage`/`selectNode`/`refreshNavTreeBranch`/`refreshAfterMutation` 方法）
- [x] 4.4 新增 `frontend/src/views/system/menu/MenuManagementView.vue`：
  - 左侧懒加载资源树（默认全部收起），右侧分页表格展示选中节点的直属下级资源（未选中时默认展示顶级资源第一页，标题空白直到选中节点），交互结构照抄 `OrgManagementView.vue`
  - 右侧表格列：资源名称、资源编码、资源类型（标签展示中文）、显示序号、状态、操作
  - 新增/编辑弹窗：资源名称输入框、资源编码输入框（必填）、上级资源 `el-tree-select`（虚拟顶级根节点 + 防环，按需加载）、资源类型 `el-radio-group`（菜单/按钮/API，必填，新增时默认选中"菜单"）、显示序号数字输入（默认 `0`）、备注多行文本
  - 详情只读弹窗（含资源类型、上级资源名称）、启用/停用/删除行内操作（删除二次确认，后端存在下级资源时会返回错误提示）
- [x] 4.5 `frontend/src/router/index.ts` 的 `implementedComponents` 新增 `/system/menus` 指向 `MenuManagementView.vue`（替换默认的 `PlaceholderView` fallback）
- [x] 4.6 `frontend/src/router/index.ts` 的 `stubDescriptions` 移除 `/system/menus` 对应条目

## 5. 验证

- [x] 5.1 `./gradlew test`（`backend/` 目录）：新增的 `MenuServiceImplTest`（9 个用例）及全部现有测试通过
- [x] 5.2 `npm run build`（`frontend/` 目录）：vue-tsc 类型检查 + vite build 通过
- [x] 5.3 验证前先检查并清理了遗留的旧 `RbacApplication` 进程（占用 48080 端口的一个残留调试实例），终止后用当前代码（含 `V10__init_tab_menu.sql`）重新起了单一实例；`flyway_schema_history` 确认迁移 10 成功执行
- [x] 5.4 API 级验证（`curl` 直接调用，Chinese 字段通过写入临时 JSON 文件传参以规避 Git Bash 下 `-d` 参数的 UTF-8 编码问题）：完整走过新增（含重复编码、非法资源类型两种拒绝路径）、全量树查询、懒加载子节点查询、分页子节点查询、详情、停用、启用、存在下级资源时删除被拒绝、无下级资源时删除成功全流程；确认删除后详情正确返回 `{"code":400,"message":"资源不存在"}` 业务错误而非 HTTP 500；测试数据已清理（全部逻辑删除）
- [x] 5.5（原计划外，补充）前端 dev server（Vite，端口 5173）确认可访问（首页与新组件源文件均 HTTP 200）；受限于当前环境没有浏览器自动化工具，未做逐项点击的可视化 UI 验证——仅完成类型检查级别（5.2）与后端 API 级别（5.4）的验证，与 `permission-management` 落地时的验证深度一致
