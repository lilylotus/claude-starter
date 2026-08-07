## Context

见 proposal.md - Why。当前 `OrgScopeService.resolveAllowedOrgIds(Long userId)` 只用于三个模块的查询接口（`OrgServiceImpl`/`PositionServiceImpl`/`AppServiceImpl` 各自的 list/tree/page 方法），写操作方法（`create`/`update`/`enable`/`disable`/`delete`）完全没有调用它。三个模块的写操作实现风格高度一致（同样的 `getExistingEntity` + 状态机 + 操作日志记录套路），本次改动复用这个既有结构，在关键节点插入一次范围校验，不改变其余逻辑。

## Goals / Non-Goals

**Goals:**
- 组织/任职/应用三个模块的新增、编辑、启用、停用、删除接口，在管辖组织范围受限时，校验涉及的组织 id 落在允许集合内。
- 复用 `OrgScopeService` 现有的解析结果，不引入新的数据源或缓存。
- 不影响管辖组织范围不受限的管理员（`resolveAllowedOrgIds` 返回空 `Optional`）的任何现有行为。
- （后端校验上线后补充）组织管理前端"上级组织"选择器不应让受限管理员选中一个注定会被后端拒绝的选项；编辑一个自身在管辖范围内、但真实上级组织不在管辖范围内的组织时，只要不修改上级组织，编辑应能成功。

**Non-Goals:**
- 不改变查询接口（`getPage`/`getTree`/`getChildren` 等）已有的范围过滤逻辑，那部分已经在 `org-scope-data-permission` change 中实现。
- 不校验 `parentId`/`orgId` 指向的组织是否真实存在（这是创建/更新接口既有的、独立于本次改动的行为，本次不额外补充）。
- 不处理用户管理模块（`UserServiceImpl`）内嵌任职子表单（`syncPositions`）的范围校验；`position-management` spec 明确该内嵌路径与独立任职管理入口是两条不同的写路径，本次改动只覆盖 `/api/positions` 独立入口，内嵌路径留待后续单独评估。

## Decisions

### 1. 在 `OrgScopeService` 接口新增一个校验断言方法，而不是在三个业务模块各自实现
新增 `boolean isOrgIdAllowed(Long userId, Long orgId)`：`resolveAllowedOrgIds` 返回空 `Optional`（不受限）时恒为 `true`；非空时判断 `orgId` 是否在集合内。三个模块的 service 实现里直接调用它做判断、自行决定抛什么异常（"不存在" vs "无权限"），`OrgScopeService` 本身只负责判断，不感知调用方的错误语义——这和该接口现有的单一职责（"给定当前会话身份，判断其数据可见范围"）保持一致，也避免把三个模块各自的错误文案耦合进 `auth` 模块。

备选方案：让 `OrgScopeService` 直接提供一个 `assertOrgIdAllowed(...)` 并抛出统一异常。放弃原因：三个模块对"越权"场景要抛的错误文案不同（"编辑已有记录时越权"要伪装成"记录不存在"，"新建/移动到某个组织时越权"要直接报"无权限"），把异常和文案逻辑塞进 `auth` 模块的通用服务里会让调用方失去这个区分能力。

### 2. "记录不存在"文案复用 vs 独立"无权限"提示，按操作类型区分
- `update`/`enable`/`disable`/`delete`：这四个操作都先按 id 查出已存在的记录，校验失败时复用各自模块已有的"XX 不存在"错误文案（`getExistingEntity` 抛出的 `BusinessException`），不新增区分"不存在"和"无权限"的错误码/文案。理由：与 `PositionServiceImpl.getPage`（design.md Decision 5）已经确立的原则一致——管辖范围之外的记录，对调用者而言应该和"不存在"是同一种观感，不额外暴露"这个 id 存在但你无权限"的越权探测信号。
- `create`：没有"已存在记录"可言，校验的是"要新建/挂到哪个组织下"，直接抛"无权限"类 `BusinessException`（如"无权限在该管辖范围外的组织下新建"），不需要伪装。
- `update` 里还有第二次校验（新 `parentId`/`orgId` 是否在范围内）：这次判断的对象同样是"要移动到哪个组织"而非"某条具体记录是否存在"，因此也直接抛"无权限"类错误，不复用"不存在"文案。

### 3. 校验时机：查出实体之后、写库之前
`update`/`enable`/`disable`/`delete` 复用各自已有的 `getExistingEntity(id)`，拿到实体后立即校验其 `parentId`（org）/`orgId`（position/app）是否在管辖范围内，校验失败直接抛异常，不再执行后续的唯一性校验、字段校验或状态变更，保持"先鉴权、后处理业务逻辑"的顺序，避免不必要的数据库写前校验开销，也避免把"越权"和"业务校验失败"混在一起判断优先级。

### 4. 三个模块各自在自己的 service 实现里内联校验代码，不抽出跨模块的公共 Aspect/Interceptor
三个模块的实体字段名不同（`OrgEntity.parentId` vs `UserPositionEntity.orgId`/`AppEntity.orgId`），触发校验的方法数量少（每个模块 5 个方法），用注解/AOP 统一拦截收益有限，反而会引入一层间接性。延续项目目前"每个 service 直接注入 `OrgScopeService` 按需调用"的既有模式（org/position/app 三处查询方法已经是这么做的）。

### 5.（后端校验上线后补充）组织更新时，`parentId` 未变化则跳过范围校验
`OrgServiceImpl.update` 校验被编辑组织自身 `id` 时，用的是"实体自身 id 在管辖范围内"；但组织的"是否在虚拟根节点边界上"取决于它的 `parentId` 是否在管辖范围内，这两者是两个独立维度——一个受限管理员完全可能合法拥有对某个组织自身的编辑权（其 `id` 在允许集合内），但该组织的真实上级组织不在允许集合内（典型的"虚拟根节点"场景，`org-scope-data-permission` change design.md Decision 4 已确立这是常态而非例外）。如果无条件校验"新 `parentId` 是否在管辖范围内"，会导致这类编辑——哪怕根本没有修改 `parentId`——也被误判为"越权移动"而拒绝。

修复：在 `update` 里读到 `entity`（校验自身 id 通过之后）后，比较 `request.getParentId()` 与 `entity.getParentId()`（更新前的原值）；仅当两者不同（代表这次编辑确实要把组织挪到一个新的上级下）时才调用 `assertParentOrgInScope(request.getParentId())`；两者相同（编辑没有触碰上级组织）时跳过这项校验，因为没有引入任何新的越权行为。

备选方案：允许"新 parentId 等于当前 parentId"也无条件放行，但对任意"新 parentId 不等于当前值"的场景都算作移动——即上述修复方案本身；另一个被放弃的备选是"完全不做变化检测，只要 parentId 落在受限管理员自己可见的组织树范围内就放行"，放弃原因是这会允许管理员把组织挪到一个虽然可见、但严格来说超出自己管辖范围子树的位置（比如两个互不包含的虚拟根节点之间平级移动），偏离了"不能操作管辖范围之外的组织"的原始意图。

### 6.（后端校验上线后补充）暴露 `orgScopeRestricted` 供前端收紧选择器
组织管理前端"新增/编辑组织"弹窗的"上级组织"选择器此前无条件在 `orgStore.tree`（已经过后端管辖范围过滤）前面拼一个代表 `parentId=0` 的虚拟"顶级组织"根节点。这个节点本身不是一个真实组织，不会被 `GET /api/orgs/tree` 的管辖范围过滤逻辑感知或排除，纯粹是前端为了"支持新建顶级组织"手工拼出来的——受限管理员选择器里选中它后，本 change 新增的后端校验必然拒绝（`0` 永远不在允许集合里），造成"选得了但存不了"的体验缺口。

修复方案：在已有的 `GET /api/auth/permissions` 响应（前端登录后已经会调用一次，用于加载权限编码集合）里新增 `orgScopeRestricted: boolean` 字段，值等于 `orgScopeService.resolveAllowedOrgIds(userId).isPresent()`；前端登录后随权限编码集合一并缓存这个布尔值；组织管理页面的"上级组织"选择器数据源按这个标志位决定要不要拼接虚拟"顶级组织"根节点——受限时不拼，不受限时行为不变。

备选方案：
- 新增一个专门的接口（如 `GET /api/orgs/scope-restricted`）返回该布尔值。放弃原因：`GET /api/auth/permissions` 本身就是登录后前端已经会调用一次的"当前用户运行时上下文"接口，复用它不增加一次额外请求；且该值的语义（"当前用户是否受管辖范围限制"）与权限编码集合一样，都属于"运行时鉴权上下文"，放在一起符合已有职责边界。
- 前端从 `GET /api/orgs/tree` 返回的树形结构里反推是否受限（比如"顶层节点的真实 `parentId` 不是 0"）。放弃原因：受限管理员的管辖范围如果恰好直接覆盖某个真实顶级组织本身，其虚拟根节点的真实 `parentId` 也是 `0`，与"不受限"的情况在数据形状上无法区分，不能作为可靠信号。

## Risks / Trade-offs

- [风险] `update` 对"当前所属组织"和"新所属组织"分两次校验，如果实现时漏掉其中一次，会退化为只保护一半场景 → 缓解：tasks.md 按方法逐条列出两次校验点，实现后逐条自查；三个模块结构对称，可以互相对照检查。
- [风险] "不存在"文案复用可能让管辖范围内的管理员在遇到真正不存在的 id 时和遇到范围外 id 时看到一样的报错，调试体验上无法区分 → 可接受，这是安全性优先于调试友好性的既有设计取舍（与 Decision 5 一致），非本次引入的新问题。
- [权衡] 不做 `parentId`/`orgId` 目标组织"是否真实存在"的补充校验，意味着受限管理员仍可能传一个不存在但恰好数值落在自己管辖范围子孙 id 区间之外的随机数——但因为该数字不在允许集合内会被本次改动直接拦截，所以实际不构成新增风险，维持现状即可。
