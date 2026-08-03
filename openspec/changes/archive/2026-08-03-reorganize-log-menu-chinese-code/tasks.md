## 1. 前端：补齐缺失的权限模块中文名映射

- [x] 1.1 `frontend/src/utils/permissionTree.ts`：在 `PERMISSION_MODULE_LABELS` 中补上 `LoginLogManagement: '登录日志管理'`（与已有的 `OperationLogManagement: '操作日志管理'` 风格一致），不改动其余条目

## 2. 数据库迁移

- [x] 2.1 新增 `backend/src/main/resources/db/migration/V9__reorganize_log_menu.sql`：插入一条"日志管理"顶级 `tab_menu` 记录（`code='log'`，`parent_id=0`，`resource_type=1`，`show_order=5`，紧跟在"系统管理"`show_order=10` 之后）（`V8` 已提交 git 并推送到 `origin/develop`，不再改写，改为新增迁移）
- [x] 2.2 追加 `UPDATE tab_menu` 语句：把 `code='LoginLogManagement:loginLog:view'` 的记录（`V8` 已插入）`parent_id` 改到日志管理节点，`code` 不变
- [x] 2.3 追加 `UPDATE tab_menu` 语句：把 `code='OperationLogManagement:log:view'` 的记录（`V1` 已插入）`parent_id` 改到日志管理节点，`code` 不变
- [x] 2.4 登录日志 `tab_permission` 记录、SUPER_ADMIN 角色权限关联部分逻辑保持不变（`V8` 已处理，`code` 未变化，无需额外处理）
- [x] 2.5 迁移文件头部注释说明本次新增内容（新增日志管理顶级菜单、操作日志与登录日志重新挂靠，均不改编码，也不改动 `V8`）

## 3. 前端：菜单与路由

- [x] 3.1 `frontend/src/router/menu.ts`：新增导入一个 `@element-plus/icons-vue` 图标（如 `Document`）用于新分组
- [x] 3.2 `frontend/src/router/menu.ts`：新增 `log` 分组（`title: '日志管理'`），从 `system` 分组的 `children` 中移除操作日志、登录日志两项，加入新分组，`path` 分别改为 `/log/operation-logs`、`/log/login-logs`；`permissionKey` 保持 `OperationLogManagement:log:view`、`LoginLogManagement:loginLog:view` 不变
- [x] 3.3 `frontend/src/router/index.ts`：`stubDescriptions` 里 `/system/logs`、`/system/login-logs` 两个 key 改为 `/log/operation-logs`、`/log/login-logs`（描述文案不变）
- [x] 3.4 `frontend/src/router/index.ts`：`implementedComponents` 里同样两个 key 同步改名（对应的组件引用不变）

## 4. 权限资源.txt

- [x] 4.1 删除"系统管理"小节下 `OperationLogManagement`、`LoginLogManagement` 两条现有条目
- [x] 4.2 新增"日志管理"小节，收录 `OperationLogManagement:log:view`（路径改为 `/log/operation-logs`）、`LoginLogManagement:loginLog:view`（路径改为 `/log/login-logs`）两条，编码本身不变

## 5. 验证

- [x] 5.1 后端：`cd backend && ./gradlew test`（BUILD SUCCESSFUL，`@SpringBootTest` 连接本地开发库，Flyway 已在启动时把 `V9` 应用上去）
- [x] 5.2 未额外清空开发库重跑：5.1 的 `./gradlew test` 已经在本地开发库（`127.0.0.1:3306/rbac`）上触发 Flyway 把 `V9` 迁移应用成功，等价于验证过迁移无报错
- [x] 5.3 人工核对迁移后的 `tab_menu` 数据（直接 `mysql` 查询）：`log`（id=97，parent_id=0，show_order=5）存在；`OperationLogManagement:log:view`（id=74）、`LoginLogManagement:loginLog:view`（id=96）均已挂到 `parent_id=97`，两者 `code` 均未变化
- [x] 5.4 前端：`cd frontend && npm run build`（vue-tsc 类型检查 + vite build，构建成功）
- [x] 5.5 前端：`npm run dev` 手动验证——用 admin 账号登录（用户已在浏览器中人工核对确认通过）：
  - 侧边栏出现"日志管理"一级分组，展开后能看到"操作日志""登录日志"两个二级菜单且可正常访问对应页面；"系统管理"分组下不再出现这两项
  - 角色管理"新增角色"/"编辑角色"弹窗的权限点勾选树中，登录日志分组展示为"登录日志管理"（中文），不再是英文 `LoginLogManagement`
  - 权限点管理页面的列表树中，登录日志分组同样展示为"登录日志管理"

## 6. OpenSpec 收尾

- [x] 6.1 实现完成后运行 `openspec-doc-sync`，核对本变更的 proposal/design/tasks 与实际实现是否一致（agent 核对后确认三份文档已准确反映实际实现，无需改动）
- [x] 6.2 确认无误后执行 spec 同步（`openspec-sync-specs`）与归档（`openspec-archive-change`）：已把两份 delta spec（menu-management、operation-log-management）合并进 `openspec/specs/` 对应主 spec
