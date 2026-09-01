## Context

`frontend/src/api/request.ts` 的请求拦截器：

```ts
if (!config.headers.get('menu')) {
  const permissionKey = router.currentRoute.value.meta.permissionKey
  if (permissionKey) {
    config.headers.set('menu', permissionKey)
  }
}
```

`menu` 头默认取"当前路由"的 `permissionKey`，这对组织/用户/任职/应用四个管理页面
自身调用 `render-schema` 是成立的（它们本来就只在拥有对应管理权限点时才能进入该
路由），但对 `ApprovalRequestDetailDialog.vue`（`我的申请`/`待我审批`页面共用的详情
弹窗组件）不成立——弹窗内部对四个 `bizType` 的 `render-schema` 调用完全没有"当前
路由恰好等于目标 `bizType` 管理权限点"这层巧合，而是无条件地把四个 `bizType` 都拉
一遍（组件注释里写明这是为了实现简洁，接受四次轻量 GET 的代价）。

`backend/.../auth/filter/IdentityAuthFilter.java` 的运行时权限判断：

```java
boolean firstLoginExempt = matches(FIRST_LOGIN_WHITELIST, path);
...
if (!firstLoginExempt && !authorizationService.hasPermission(userId, menu)) {
    writeError(response, AuthErrorCode.FORBIDDEN, "无权限访问该资源");
    return;
}
```

`FIRST_LOGIN_WHITELIST` 目前是：

```java
private static final List<String> FIRST_LOGIN_WHITELIST = List.of(
        "/api/auth/password",
        "/api/auth/permissions",
        "/api/dashboard/stats",
        "/api/dashboard/recent-operations",
        "/api/approval-requests/mine",
        "/api/approval-requests/*/cancel");
```

`/api/form-fields/render-schema` 不在其中，因此走 `hasPermission(userId, menu)`
判断；`menu` 是"我的申请"/"待我审批"页面的 `permissionKey`
（`ApprovalManagement:request:view`/`ApprovalManagement:request:approve`），而不是
`FormFieldManagement:*` 或被审批对象的管理权限点——两者语义上根本不对应，且
`ApprovalManagement:request:view` 本来就设计成从不真正授予任何角色（见
`权限资源.txt` "该权限点仅作为该页面请求头 menu 的编码占位"）。所以这条判断在这个
调用场景下必然失败。

用 DB 里 `test` 账号（`tab_admin.id=2`，绑定角色 `testChat`，只授予了
`Chat:*`/`SensitiveWordManagement:*` 八个权限点）复现验证：`hasPermission(2,
'ApprovalManagement:request:view')` 与 `hasPermission(2,
'ApprovalManagement:request:approve')` 均为 `false`，与线上报错现象一致。但这不是
`test` 账号权限配置的问题——即便是被正确授予 `ApprovalManagement:request:approve`
的真实审批人角色，同样通不过这个判断（除非恰好也被授予了
`ApprovalManagement:request:approve` 之外、四个业务模块管理权限点的某个巧合组合，
但判断依据的 `menu` 值本来也不是这几个模块的权限点，无论怎么加审批人的权限点都不会
让这个特定判断通过）。

## Goals / Non-Goals

**Goals:**
- "我的申请""待我审批"两个页面在展示 `UPDATE` 类型申请详情时，能够正常拉取到四个
  业务对象类型的渲染元数据，字段展示名与新旧对照按预期渲染，不因调用者缺少
  `FormFieldManagement`/被审批对象管理权限点而报无权限。
- 不放宽 `/api/form-fields` 除 `render-schema` 外其余接口（增删改查定义本身）的权限
  校验范围。
- 不改变组织/用户/任职/应用四个管理页面调用 `render-schema` 的现有行为（继续正常
  工作，不产生回归）。

**Non-Goals:**
- 不重新设计 `menu` 请求头的派生机制（如给 `useDynamicFormFields`/
  `ApprovalRequestDetailDialog.vue` 显式传入某个精心挑选的 `menu` 值）——不存在一个
  所有审批详情查看者都必然持有的业务权限点，这条路走不通，见 Decision 1。
- 不为 `test` 账号单独调整角色权限点配置——那只是治标，其余账号（包括真实审批人）
  依然会在同样的调用上复现，必须在接口层面修。
- 不改变 `IdentityAuthFilter.FIRST_LOGIN_WHITELIST` 现有其余条目的语义或本次未涉及
  的其它接口的权限校验范围。

## Decisions

### 1. 把 `/api/form-fields/render-schema` 加入 `FIRST_LOGIN_WHITELIST`，而不是让调用方显式指定 `menu` 头

**为什么不显式指定 `menu` 头**：显式指定意味着要选一个"审批详情查看者必然持有"的
权限编码作为 `menu` 值传给 `hasPermission` 判断——不存在这样的编码。"我的申请"的
查看者可以是系统里任意账号（自助查看自己提交的申请，不要求任何审批相关权限点）；
"待我审批"的查看者持有 `ApprovalManagement:request:approve`，但这个编码不是
`FormFieldManagement:*`，语义上对不上 `render-schema` 这个接口本身，硬传它一样要靠
把 `render-schema` 本身在判断逻辑里特殊处理（比如"如果 menu 是
`ApprovalManagement:*` 就放行"），复杂度不比直接加白名单低，还引入了一条新的隐式
特例规则。

**为什么加白名单是合适的**：`render-schema` 本身只读、无副作用、返回的是字段展示
名/控件类型/校验规则/字典选项等渲染元数据，不返回任何组织/用户/任职/应用的实际
业务数据行；四个管理页面已经在通过"当前路由权限点"这个弱约束访问它，这本来就不是
一层刻意设计的强隔离防线。加入白名单后，语义变成"任何已登录用户都能查询字段渲染
元数据"，与白名单里已有的 `/api/auth/permissions`（查自己的权限编码）、
`/api/dashboard/stats`/`/api/dashboard/recent-operations`（首页概览/自己的操作
记录）性质一致——都是"不区分具体业务权限点、任何登录用户都合理需要"的自助/
基础信息查询。

### 2. 不改动 `/api/form-fields` 其余接口

新增字段定义、编辑、启停用、删除、分页查询/详情，都涉及实际的字段配置数据本身
（谁能新增/改动一个业务对象类型的表单字段结构），这些操作应当继续要求
`FormFieldManagement` 相关管理权限点。只有 `render-schema`（渲染元数据的只读快照）
被纳入自助白名单。

## Risks / Trade-offs

- [任何已登录用户都能查询任意 `bizType` 的字段渲染元数据，即使他们看不到对应的管理
  页面菜单] → 该元数据本身不含业务数据，且当前四个管理页面本来就是靠弱约束（路由
  permissionKey 恰好匹配）访问，不存在被削弱的强隔离；风险可接受，与
  proposal.md Impact 一致。
- [`FIRST_LOGIN_WHITELIST` 语义从"审批自助接口专属"扩展为"跨多个功能的自助/基础
  只读接口"] → 该列表本来就已经混合了改密码、查权限编码、首页统计、审批自助查询
  四类不同来源的接口，扩展一个字段渲染元数据接口不改变列表本身的既有性质。

## Migration Plan

1. `IdentityAuthFilter.FIRST_LOGIN_WHITELIST` 增加 `/api/form-fields/render-schema`。
2. `IdentityAuthFilterTest` 新增测试用例（比照现有
   `doFilter_shouldPassMineApprovalQuery_asSelfService`）：验证请求
   `/api/form-fields/render-schema`（携带任意合法但用户实际不持有的 `menu` 值）能够
   放行，且不调用 `passwordService.isFirstLogin`/`authorizationService.hasPermission`。
3. `./gradlew test --tests "cn.nihility.rbac.auth.filter.IdentityAuthFilterTest"` 确认
   通过。
4. 手动验证：使用 `test` 账号登录（或任意只拥有 `ApprovalManagement:request:approve`
   而不持有任何 ORG/USER/POSITION/APP 管理权限点的账号），打开"我的申请"或
   "待我审批"页面里一条 `UPDATE` 类型的申请，确认详情弹窗能正常展示新旧字段对照，
   不再报无权限；同时验证组织/用户/任职/应用四个管理页面自身的新增/编辑表单渲染
   不受影响。
5. 更新 `openspec/specs/password-login-auth/spec.md`、
   `openspec/specs/master-data-approval-workflow/spec.md`（按 proposal.md 的
   Capabilities 描述）。
