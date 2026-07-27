## 1. 后端：权限点全量列表查询接口

- [x] 1.1 `PermissionService`/`PermissionServiceImpl` 新增 `getAllList()`：查询未逻辑删除（`status != -1000`）的全部权限点（不按状态筛选），按 `showOrder` **升序**、`id` 升序排列（显示序号越小越靠前，与本模块其余接口的降序约定相反，方法注释里需写明原因），复用已有 `PermissionVO`（字段已齐全，不新建 DTO）
- [x] 1.2 `PermissionController` 新增 `GET /api/permissions/list`，加 springdoc `@Operation` 注解，说明用途是"供权限管理树形视图一次性加载全量数据，不供角色勾选等只读选项场景复用"
- [x] 1.3 补充单元测试：覆盖"全量列表包含停用状态权限点""不包含已逻辑删除权限点""按 showOrder 升序排列"三个场景（对应 spec.md 中的 Scenario）

## 2. 后端：`tab_menu` 补录导入相关按钮资源

- [x] 2.1 新增 `backend/src/main/resources/db/migration/V7__add_missing_import_menu_resources.sql`：按 `V1__init_schema.sql` 里同批按钮节点的写法（`SELECT id FROM tab_menu WHERE code = 'xxx:xxx:view'` 取父节点 id，`resource_type=2`，`create_by`/`update_by='admin'`），给 `OrgManagement:org:view`、`UserManagement:user:view`、`PositionManagement:position:view`、`AppManagement:app:view` 四个父节点各补 `xxx:importTemplate`（下载导入模板）、`xxx:import`（批量导入）两条子节点，`show_order` 取比该父节点下现有按钮更小的值，保证排在已有增删改查按钮之后
- [x] 2.2 核对迁移执行后 `GET /api/menus/tree` 返回结果里这 8 个节点均能在对应父节点下查到，且不影响其余节点

## 3. 前端：权限点分组算法抽取为共享工具

- [x] 3.1 把 `RoleManagementView.vue` 里现有的"按编码第一段分组构造两层树"逻辑（`permissionTreeData` 计算属性里的分组算法）抽取为共享工具函数（如 `src/utils/permissionTree.ts`），输入权限点列表（含 `id`/`name`/`code`，及权限点管理树额外需要的 `status`/`showOrder`/`remark`），输出分组后的树形结构；`RoleManagementView.vue` 改为调用该共享函数，不重复实现一份

## 4. 前端：权限点管理页面改为树形展示

- [x] 4.1 `src/api/permission.ts` 新增 `getPermissionList()` 调用 `GET /api/permissions/list`
- [x] 4.2 `stores/permission.ts` 从"分页 store"改造为"加载全量列表"的 store：去掉 `page`/`pageSize`/`total`/分页相关方法，新增加载全量列表的状态与方法；`refreshAfterMutation` 改为重新加载全量列表（不再有"回退到最后一页"的逻辑）
- [x] 4.3 `PermissionManagementView.vue` 把 `el-table` + `el-pagination` 换成 `el-tree`（复用任务 3.1 的分组工具函数），叶子节点展示权限名称、编码、状态标签，行内操作（详情/编辑/启用停用/删除）挂在叶子节点上；模块分组节点展示模块名 + 该模块权限点数量角标；新增"全部展开/全部收起"按钮
- [x] 4.4 新增/编辑/详情跳转/启用停用/删除的既有交互逻辑保持不变，只是触发来源从表格行变为树的叶子节点
- [x] 4.5 视觉上延续仓库已有的"链式连接"视觉语言（参照 `MenuManagementView.vue` 里 `.menu-tree` 的圆点 + 虚线样式），保持与菜单管理树形展示视觉一致

## 5. 联调与验证

- [x] 5.1 后端：`./gradlew build` 全量通过（编译 + 单元测试 + `check`）
- [x] 5.2 前端：`npm run build`（vue-tsc 类型检查 + vite build）通过
- [x] 5.3 本地联调（真实后端 + 真实本地 MySQL，非 mock）：直接查库核实 `tab_menu` 新增的 8 条记录父子关系、`show_order` 均正确，`flyway_schema_history` 确认 `V7` 已成功应用、未影响其余数据。用户提供了 `admin` 当前密码后，用 `openssl pkeyutl`（RSA-OAEP-SHA256）模拟浏览器端加密，对用户本地正在用 IntelliJ 跑着的真实后端实例（端口 `48080`）走通了完整登录：`POST /api/auth/login` 成功签发 `identity-token`；带着这个真实 token 分别调用 `GET /api/permissions/list`（`menu: PermissionManagement:permission:view`）拿到 94 条权限点，字段齐全；调用 `GET /api/menus/tree`（`menu: MenuManagement:menu:view`）确认新增的 8 个 `importTemplate`/`import` 按钮节点全部能在对应父节点下查到、`show_order` 与迁移脚本一致。中途发现自己另外临时起的 `./gradlew bootRun` 因端口冲突启动失败——48080 早已被用户自己的 IntelliJ 调试会话占用，已停止不再重复尝试起新实例，验证全程复用的是用户自己那个真实运行中的后端。用完即清理了本地临时保存的公钥/密文/token 文件，未在磁盘上留下密码或密钥。
- [x] 5.4 用浏览器（或用户本地环境）实际点击验证权限点管理树形展示效果与交互：**部分完成，如实记录**。`claude-in-chrome` 本次用户选择不安装，没有做到真正的浏览器点击验证（叶子节点展开收起、详情跳转、CRUD 弹窗等的实际渲染效果仍未肉眼确认）；但已用真实 token 对权限管理树背后依赖的两个真实接口（`GET /api/permissions/list`、`GET /api/menus/tree`）做了端到端调用验证（见 5.3），确认了树形展示所需的数据链路是通的、字段齐全、排序正确。仍然不等同于走完了页面点击，如需要请用户本地登录后自行核对一遍视觉效果与交互。
- [x] 5.5 本次未新增/删除任何页面菜单或按钮（导入按钮功能本身已存在，只是补齐 `tab_menu` 档案数据），核对 `权限资源.txt` 内容与实际实现仍然一致，无需变更

## 6. 前端：权限点管理树展示微调（用户实测后反馈）

- [x] 6.1 分组虚拟节点展示中文模块名（如 `OrgManagement` 展示为"组织管理"），中文名与 `权限资源.txt` 里登记的模块中文名一致；只调整权限点管理页面自身的展示，不改动 `RoleManagementView.vue` 里权限点勾选树的展示（后者未被要求改动，`buildPermissionTree` 共享工具函数需要保持向后兼容，不能破坏其现有调用方）
- [x] 6.2 树默认状态改为全部收起（不再在首次加载后自动展开全部分组），"全部展开/全部收起"按钮交互保持不变
- [x] 6.3 `RoleManagementView.vue` 里角色新增/编辑弹窗内的权限点勾选树也改为展示中文模块名：把 6.1 里为权限点管理页面写的模块名中文映射表（及兜底逻辑）从 `PermissionManagementView.vue` 抽到共享位置（如 `src/utils/permissionTree.ts` 导出一个默认的 `resolvePermissionModuleLabel` 解析函数 + 对照表），`PermissionManagementView.vue` 和 `RoleManagementView.vue` 都调用同一个共享解析函数，不维护两份重复的映射表
