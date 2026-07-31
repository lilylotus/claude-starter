## 1. 前端：补齐缺失的权限模块中文名映射

- [ ] 1.1 `frontend/src/utils/permissionTree.ts`：在 `PERMISSION_MODULE_LABELS` 中补上 `LoginLogManagement: '登录日志管理'`（与已有的 `OperationLogManagement: '操作日志管理'` 风格一致），不改动其余条目

## 2. 数据库迁移

- [ ] 2.1 改写 `backend/src/main/resources/db/migration/V8__add_login_log.sql`：在建表语句之后，插入一条"日志管理"顶级 `tab_menu` 记录（`code='log'`，`parent_id=0`，`resource_type=1`，`show_order=5`，紧跟在"系统管理"`show_order=10` 之后）
- [ ] 2.2 登录日志 `tab_menu` 记录的 `parent_id` 改挂到日志管理节点，`code` 保持 `LoginLogManagement:loginLog:view` 不变
- [ ] 2.3 追加 `UPDATE tab_menu` 语句：把 `code='OperationLogManagement:log:view'` 的记录（`V1` 已插入）`parent_id` 改到日志管理节点，`code` 不变
- [ ] 2.4 登录日志 `tab_permission` 记录、SUPER_ADMIN 角色权限关联部分逻辑保持不变（`code` 未变化，无需额外处理）
- [ ] 2.5 补充/调整迁移文件头部注释，说明本次改写内容（新增日志管理顶级菜单、操作日志与登录日志重新挂靠，均不改编码）

## 3. 前端：菜单与路由

- [ ] 3.1 `frontend/src/router/menu.ts`：新增导入一个 `@element-plus/icons-vue` 图标（如 `Document`）用于新分组
- [ ] 3.2 `frontend/src/router/menu.ts`：新增 `log` 分组（`title: '日志管理'`），从 `system` 分组的 `children` 中移除操作日志、登录日志两项，加入新分组，`path` 分别改为 `/log/operation-logs`、`/log/login-logs`；`permissionKey` 保持 `OperationLogManagement:log:view`、`LoginLogManagement:loginLog:view` 不变
- [ ] 3.3 `frontend/src/router/index.ts`：`stubDescriptions` 里 `/system/logs`、`/system/login-logs` 两个 key 改为 `/log/operation-logs`、`/log/login-logs`（描述文案不变）
- [ ] 3.4 `frontend/src/router/index.ts`：`implementedComponents` 里同样两个 key 同步改名（对应的组件引用不变）

## 4. 权限资源.txt

- [ ] 4.1 删除"系统管理"小节下 `OperationLogManagement`、`LoginLogManagement` 两条现有条目
- [ ] 4.2 新增"日志管理"小节，收录 `OperationLogManagement:log:view`（路径改为 `/log/operation-logs`）、`LoginLogManagement:loginLog:view`（路径改为 `/log/login-logs`）两条，编码本身不变

## 5. 验证

- [ ] 5.1 后端：`cd backend && ./gradlew test`
- [ ] 5.2 本地清空开发库（或删除 `flyway_schema_history` 表）后跑一次 `./gradlew bootRun`，确认 `V8` 迁移无报错
- [ ] 5.3 人工核对迁移后的 `tab_menu` 数据：日志管理顶级节点存在、操作日志与登录日志均挂在其下，两者 `code` 均未变化
- [ ] 5.4 前端：`cd frontend && npm run build`（vue-tsc 类型检查 + vite build）
- [ ] 5.5 前端：`npm run dev` 手动验证——用 admin 账号登录：
  - 侧边栏出现"日志管理"一级分组，展开后能看到"操作日志""登录日志"两个二级菜单且可正常访问对应页面；"系统管理"分组下不再出现这两项
  - 角色管理"新增角色"/"编辑角色"弹窗的权限点勾选树中，登录日志分组展示为"登录日志管理"（中文），不再是英文 `LoginLogManagement`
  - 权限点管理页面的列表树中，登录日志分组同样展示为"登录日志管理"

## 6. OpenSpec 收尾

- [ ] 6.1 实现完成后运行 `openspec-doc-sync`，核对本变更的 proposal/design/tasks 与实际实现是否一致
- [ ] 6.2 确认无误后执行 spec 同步（`openspec-sync-specs`）与归档（`openspec-archive-change`）
