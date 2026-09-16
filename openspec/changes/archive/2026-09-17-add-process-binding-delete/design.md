## Context

`tab_wf_process_binding` 表目前只有一个 `enabled BOOLEAN` 字段表达"启用/停用"，没有"已删除"这个状态；`WorkflowProcessBindingController` 也只有 view/新建/切换版本/启用/停用五个接口，没有删除接口（`approval-process-binding-console` change design.md 当初明确决策"不做绑定删除功能"）。用户现在要求补上删除能力。

已经和用户确认过关键决策：采用软删除（新增 `status` 字段，与 `AdminStatus`/`RoleStatus` 等项目里其它状态常量类同一套编码风格：启用=2000/停用=3000/已删除=-1000），不做物理删除。

这个决策带来一个必须处理的技术约束：`tab_wf_process_binding` 在 `(biz_type, operation_type, scope_type, scope_id)` 四列上有一条数据库级 `UNIQUE KEY uk_tab_wf_process_binding_dimension`。如果保持这条 UNIQUE 索引不变，软删除一条绑定后，这一行仍然占着这四列的取值组合，`UNIQUE` 约束会让"针对同一维度新建一条新绑定"在数据库层直接失败——这就完全违背了"删除后维度可重新使用"这个用户想要的效果。MySQL 5.7 不支持带过滤条件的"部分唯一索引"，没办法让 UNIQUE 约束自动排除已删除的行。

## Goals / Non-Goals

**Goals:**
- 业务绑定记录状态从"启用/停用"两态扩展为"启用/停用/已删除"三态。
- 删除一条绑定后：不出现在列表/解析中，但保留行本身（含审计字段）；该绑定占用的维度可重新新建。
- 新增独立的 `WorkflowDesign:binding:delete` 权限点，前端新增删除按钮。

**Non-Goals:**
- 不提供"查看已删除绑定"或"恢复已删除绑定"的界面/接口——本次只要求"删除后维度可重新使用"，不要求可追溯查看已删除记录（如后续需要，可在新 change 里基于现有 `status` 字段直接加一个查询开关，不需要改动数据结构）。
- 不引入操作日志（`operationlog` 模块）记录——`WorkflowProcessBindingService` 现有的新建/切换版本/启停操作本身都没有接入操作日志，本次只新增删除这一个操作，不单独为它打破这个既有的（不完整的）覆盖现状，避免变成"只有删除有日志、其它操作没有"的不一致状态。审计仍然依赖行上的 `update_by`/`update_time`。
- 不要求删除前必须先停用——参照 `RoleServiceImpl.delete` 的既有实现（角色可以直接从任意状态被删除，不要求先停用），本次保持一致。

## Decisions

### Decision 1：`status` 替换 `enabled`，编码复用项目既有风格
新增常量类 `cn.nihility.rbac.workflow.constant.BindingStatus`：
```java
public final class BindingStatus {
    public static final int ENABLED = 2000;
    public static final int DISABLED = 3000;
    public static final int DELETED = -1000;
}
```
`ProcessBindingEntity.enabled: Boolean` 替换为 `status: Integer`。`ProcessBindingVO` **不**新增 `status` 字段、保留原有的 `enabled: Boolean` 字段不变（由 `toVO` 里 `BindingStatus.ENABLED.equals(entity.getStatus())` 派生），前端已删除的绑定压根不会出现在任何查询结果里，不需要额外字段来区分"已删除"，因此前端类型/组件除了新增"删除"按钮外不需要其它改动。

### Decision 2：UNIQUE 索引降级为普通索引，唯一性校验移到应用层
迁移脚本里把
```sql
UNIQUE KEY `uk_tab_wf_process_binding_dimension` (`biz_type`, `operation_type`, `scope_type`, `scope_id`)
```
降级为普通索引（保留索引本身用于查询加速，只是去掉唯一性约束）。"同一维度只能有一条未删除绑定"的规则改为完全由 `WorkflowProcessBindingService.createBinding` 的应用层查询保证（`dimensionQuery` 增加 `status != BindingStatus.DELETED` 过滤条件）。

这与本项目里"编码类"字段的既有惯例是一致的：`AdminEntity.code`/`RoleEntity.code` 等字段的唯一性也是"需在未删除的记录范围内保证"，同样没有数据库级 UNIQUE 索引兜底（MySQL 不支持部分唯一索引），完全依赖应用层校验。本次只是把 `tab_wf_process_binding` 从"曾经错误地假设可以用硬 UNIQUE 表达"的状态，改成和其它表一致的应用层校验模式。

**接受的风险**：应用层校验存在竞态窗口（两个并发请求同时对同一未删除维度调用新建接口，理论上可能都通过 `dimensionQuery` 检查后各自插入成功，形成同维度多条未删除绑定）。这个风险和项目里角色编码/管理员编码等字段的既有校验方式是同一类风险，本次不引入比现状更强的并发防护（如显式加锁），维持现有代码库对这类"低频管理操作"的一贯处理水位。

### Decision 3：查询过滤规则
- `WorkflowProcessBindingService.listBindings()`：查询条件追加 `status != BindingStatus.DELETED`。
- `WorkflowProcessBindingService.requireBinding(bindingId)`：查询条件追加 `status != BindingStatus.DELETED`（已删除的绑定按"不存在"处理，`get`/`switchDefinition`/`setEnabled`/`deleteBinding` 均复用这个方法，因此对已删除的绑定重复调用这些接口都会得到"业务绑定不存在"，是幂等且符合直觉的行为）。
- `ProcessBindingResolutionService.findEnabled(...)`：过滤条件从 `.eq(ProcessBindingEntity::getEnabled, true)` 改为 `.eq(ProcessBindingEntity::getStatus, BindingStatus.ENABLED)`。

### Decision 4：新增删除接口
`WorkflowProcessBindingService` 新增：
```java
@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
public void deleteBinding(Long bindingId, Long operatorId) {
    requireBinding(bindingId);
    processBindingMapper.update(null, new LambdaUpdateWrapper<ProcessBindingEntity>()
            .eq(ProcessBindingEntity::getId, bindingId)
            .set(ProcessBindingEntity::getStatus, BindingStatus.DELETED)
            .set(ProcessBindingEntity::getUpdateBy, operatorId == null ? null : operatorId.toString())
            .set(ProcessBindingEntity::getUpdateTime, LocalDateTime.now()));
}
```
`WorkflowProcessBindingController` 新增：
```java
@Operation(summary = "删除业务绑定")
@DeleteMapping("/api/workflow/process-bindings/{bindingId}")
public Result<Void> delete(@PathVariable Long bindingId) {
    workflowProcessBindingService.deleteBinding(bindingId, requireCurrentUserId());
    return Result.success();
}
```
`IdentityAuthFilter.FIXED_PERMISSION_MAPPINGS` 新增一条：
```java
new PermissionMapping("DELETE", "/api/workflow/process-bindings/*", "WorkflowDesign:binding:delete"),
```
（这个模块的接口权限走的是方法+路径的固定映射白名单，不信任客户端 `menu` 请求头，新增接口必须同步登记，否则请求会被当作未匹配任何固定映射、退回旧的"信任 `menu` 头"行为——workflow 模块本身已经为了修复越权漏洞改成不信任 `menu` 头，遗漏登记会让这条新接口的权限校验重新出现该漏洞的风险。）

### Decision 5：数据库迁移脚本
`V4__add_process_binding_status.sql`（`V3` 是当前最新版本，见 `V3__add_file_upload.sql`），分三步：
1. `ALTER TABLE` 一次性完成：`DROP INDEX uk_tab_wf_process_binding_dimension`、`ADD COLUMN status INT NOT NULL DEFAULT 2000`、`ADD INDEX idx_tab_wf_process_binding_dimension (biz_type, operation_type, scope_type, scope_id)`（普通索引，保留查询加速，去掉唯一约束）。
2. `UPDATE tab_wf_process_binding SET status = IF(enabled = 1, 2000, 3000)`（从旧字段回填）。
3. `ALTER TABLE tab_wf_process_binding DROP COLUMN enabled`。

同一脚本内追加 `WorkflowDesign:binding:delete` 的 `tab_menu`/`tab_permission` 种子数据插入，写法与 V1 里 `WorkflowDesign:binding:edit` 的插入语句一致（`parent_id` 挂在 `WorkflowDesign:binding:view` 菜单节点下，`resource_type=2`，`show_order` 取 `:edit` 的下一个序号，`NOT EXISTS` 幂等守卫）。

### Decision 7（实现中发现的遗留缺口）：单独补授 `V5__grant_binding_delete_permission.sql`
自测时发现：V4 只登记了 `WorkflowDesign:binding:delete` 的 `tab_menu`/`tab_permission` 种子数据，没有给任何角色授权。V1 里 `WorkflowDesign:binding:edit`/`:view` 等权限点是在同一脚本内、"授权 `SUPER_ADMIN` 全部权限点"的 `INSERT...SELECT` **之前**插入的，因此天然被那条一次性授权语句覆盖；但 V4 是独立的后续迁移脚本，不会被 V1 那条语句影响，导致 `WorkflowDesign:binding:delete` 这个权限点虽然在 `tab_permission`/`tab_menu` 里存在，却没有任何角色（含 `SUPER_ADMIN`）拥有它——现象是业务绑定管理页面对所有用户都只显示切换版本/启用/停用，看不到删除按钮。

修复方式：新增 `V5__grant_binding_delete_permission.sql`，单独给 `SUPER_ADMIN` 角色补一条 `tab_role_permission` 记录（`NOT EXISTS` 幂等守卫，`role_id`/`permission_id` 均用子查询按 `code` 定位，避免硬编码 id）。这是本次实现过程中发现的遗留缺口修复，不改变 Decision 4/5 里已确定的接口行为与迁移步骤。

### Decision 6：前端改动
`api/processBinding.ts` 新增：
```typescript
export function deleteBinding(bindingId: number): Promise<void> {
  return request.delete(`/workflow/process-bindings/${bindingId}`)
}
```
`ProcessBindingView.vue`：
- 新增 `canDelete = computed(() => hasPermission('WorkflowDesign:binding:delete'))`。
- 操作列在"启用/停用"按钮旁新增"删除"链接按钮（`v-if="canDelete"`），点击先 `ElMessageBox.confirm`（"确定要删除该业务绑定吗？删除后该维度可以重新配置绑定"），确认后调用 `bindingApi.deleteBinding` 并 `loadAll()` 刷新列表。
- 不需要改动 `ProcessBindingVO`/`definitionMap`/`groupFor` 等既有逻辑（后端已删除的绑定不会出现在 `listBindings()` 结果里，前端按现有方式渲染即可）。

## Risks / Trade-offs

- [UNIQUE 约束降级为普通索引后，唯一性完全依赖应用层] → 见 Decision 2，与项目里其它"编码唯一性"字段的既有风险水位一致，不是本次新引入的风险类别。
- [删除操作缺少 `expectedRevision` 乐观锁保护] → 与既有的 `setEnabled`（启停）保持一致，均为"按 id 直接更新"，不做乐观锁；只有 `switchDefinition`（切换指向的流程定义，后果更重）才要求 `expectedRevision`，本次不改变这个既有的分级保护策略。
- [删除已启用的绑定会立即让该维度回退到祖先/全局绑定或直接"未配置"] → 这是预期行为（等同于用户主动停用后没有其它兜底绑定的情况），管理页面的删除二次确认弹窗会提示"删除后该维度可以重新配置绑定"，不做额外的"是否有正在运行的关联实例"检查（运行中实例本来就已经冻结了各自的 `definitionId`，不受绑定表当前状态影响，见现有 `resolve`/`resolveForStart` 的设计）。
