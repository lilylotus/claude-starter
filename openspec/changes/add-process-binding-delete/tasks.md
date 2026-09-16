## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V4__add_process_binding_status.sql`，按 design.md Decision 5：
  - [x] 1.1.1 `ALTER TABLE` 去掉 `uk_tab_wf_process_binding_dimension` UNIQUE 索引、新增 `status` 列（默认 2000）、新增同维度普通索引
  - [x] 1.1.2 `UPDATE` 从旧 `enabled` 字段回填 `status`
  - [x] 1.1.3 `ALTER TABLE` 删除 `enabled` 列
  - [x] 1.1.4 追加 `WorkflowDesign:binding:delete` 的 `tab_menu`/`tab_permission` 种子数据（写法参照 V1 里 `WorkflowDesign:binding:edit`）

## 2. 后端常量与实体

- [x] 2.1 新增 `workflow/constant/BindingStatus.java`（`ENABLED=2000`/`DISABLED=3000`/`DELETED=-1000`）
- [x] 2.2 `ProcessBindingEntity`：`enabled: Boolean` 替换为 `status: Integer`

## 3. 后端业务逻辑

- [x] 3.1 `WorkflowProcessBindingService.createBinding`：新建时写入 `status=BindingStatus.ENABLED`；`dimensionQuery` 检查追加 `status != BindingStatus.DELETED` 过滤
- [x] 3.2 `WorkflowProcessBindingService.requireBinding`：查询追加 `status != BindingStatus.DELETED` 过滤
- [x] 3.3 `WorkflowProcessBindingService.setEnabled`：改为写入 `BindingStatus.ENABLED`/`BindingStatus.DISABLED`
- [x] 3.4 `WorkflowProcessBindingService.toVO`：`enabled` 字段由 `Objects.equals(BindingStatus.ENABLED, entity.getStatus())` 派生（`BindingStatus.ENABLED` 是 `int` 常量而非 `Integer`，项目里其它状态常量类同类派生均统一用 `Objects.equals(entity.getStatus(), Status.ENABLED)` 写法，未直接用 `.equals()`，本次保持一致）
- [x] 3.5 新增 `WorkflowProcessBindingService.deleteBinding(bindingId, operatorId)`（按 design.md Decision 4）
- [x] 3.6 `ProcessBindingResolutionService.findEnabled`：过滤条件改为 `status = BindingStatus.ENABLED`

## 4. 后端接口与权限

- [x] 4.1 `WorkflowProcessBindingController` 新增 `DELETE /api/workflow/process-bindings/{bindingId}` 接口，加 `@Operation` 注解
- [x] 4.2 `IdentityAuthFilter.FIXED_PERMISSION_MAPPINGS` 新增 `DELETE /api/workflow/process-bindings/*` → `WorkflowDesign:binding:delete` 映射

## 5. 前端

- [x] 5.1 `frontend/src/api/processBinding.ts` 新增 `deleteBinding(bindingId)`
- [x] 5.2 `ProcessBindingView.vue` 新增 `canDelete` 权限判断、"删除"按钮（二次确认弹窗）与提交逻辑

## 6. 权限资源文档

- [x] 6.1 `权限资源.txt` 补充 `WorkflowDesign:binding:delete` 条目（紧跟在 `:edit` 之后）

## 7. 测试

- [x] 7.1 在 `WorkflowProcessBindingServiceTest`（若 `allow-cross-model-binding-switch` change 已经建了这个测试类则复用，否则新建，`@SpringBootTest` + `@Transactional` 风格参照 `ProcessBindingResolutionServiceTest`）补充：
  - [x] 7.1.1 删除一条绑定后，`listBindings()` 不再返回它
  - [x] 7.1.2 删除一条绑定后，针对同一维度新建绑定成功（不再报"该绑定维度已存在"）
  - [x] 7.1.3 删除一条状态为启用的绑定直接成功，不要求先停用
  - [x] 7.1.4 删除后该维度的 `ProcessBindingResolutionService.resolve` 不再命中该绑定
  - [x] 7.1.5 删除一个不存在（或已被删除）的 `bindingId` 时抛出"业务绑定不存在"

## 8. 验证

- [x] 8.1 `./gradlew build`（`backend/` 目录下）确认编译 + 测试通过（1357+ 项测试全部通过）
- [x] 8.2 `npm run build`（`frontend/` 目录下）确认 vue-tsc 类型检查通过（前后端改动合并后复核，构建通过）
- [ ] 8.3 手工验证（需要真实登录态 + 数据库环境时执行）：本轮未执行浏览器端手工验证，如实保留未勾选
