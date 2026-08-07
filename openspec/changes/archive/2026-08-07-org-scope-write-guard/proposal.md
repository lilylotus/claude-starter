## Why

`org-scope-data-permission` change 只给组织树/列表、任职列表、应用列表这些**查询**接口加上了"按当前登录管理员的管辖组织范围过滤"，写操作接口（新增/编辑/启用/停用/删除）完全没有做同样的范围校验。结果是：管理员被限定管辖范围后，仍可以调用组织新增接口传 `parentId=0` 建一个不在管辖范围内的顶级组织——新增成功，但因为不在自己的管辖范围内，创建完之后在组织树/列表里又看不到它。任职管理、应用管理的新增/编辑接口存在同样的缺口（`orgId` 字段不受管辖范围约束），管理员还可以直接传一个管辖范围之外的记录 id 调用编辑/启用/停用/删除接口，绕过查询侧已经收紧的可见性。

补充（后端校验上线后发现的两个衍生问题）：
1. 组织管理前端"新增/编辑组织"弹窗内的"上级组织"选择器，一直无条件在树顶部拼一个代表 `parentId=0` 的虚拟"顶级组织"节点，不感知当前管理员是否受管辖范围限制。受限管理员因此仍能在选择器里选中这个顶级组织选项，点击保存后才被后端拒绝（报"无权限"），造成"选得了但存不了"的体验缺口——本 change 上面新增的后端校验本身没问题，缺的是前端应该提前把这个选项收起来，不给用户一个注定会失败的选择。
2. 排查过程中发现 `OrgServiceImpl.update` 校验新 `parentId` 时没有区分"parentId 是否真的发生变化"：受限管理员编辑一个自身在管辖范围内、但其真实上级组织不在管辖范围内的组织（即"虚拟根节点"场景，`org-scope-data-permission` change 已确立的常见形态）时，即便不修改上级组织，提交时后端仍会因为"新 parentId 不在管辖范围内"而拒绝——这类编辑并没有把该组织挪到管辖范围之外，只是维持原状，不应该被拒绝。这是上面新增校验遗漏的一个精度问题，一并在本次修复。

## What Changes

- 在 `OrgScopeService` 新增一个校验方法：受限时判断某个组织 id 是否在管辖范围内，供写操作接口复用；不受限（`resolveAllowedOrgIds` 返回空 `Optional`）时永远放行，行为与既有查询侧过滤完全一致。
- 组织管理（`OrgServiceImpl`）：
  - `create`：新增前校验请求的 `parentId` 在管辖范围内（受限时顶层 `parentId=0` 永远不在允许集合里，天然阻止受限管理员新建顶级组织）。
  - `update`：编辑前校验被编辑组织自身的 id 在管辖范围内，并校验请求的新 `parentId` 也在管辖范围内。
  - `enable`/`disable`/`delete`：操作前校验目标组织自身的 id 在管辖范围内。
- 任职管理（`PositionServiceImpl`）：对 `UserPositionEntity.orgId` 做同样三类校验（`create` 校验请求 `orgId`；`update` 校验被编辑记录当前 `orgId` 与请求新 `orgId`；`enable`/`disable`/`delete` 校验被编辑记录当前 `orgId`）。
- 应用管理（`AppServiceImpl`）：对 `AppEntity.orgId` 做同样三类校验，逻辑与任职管理对称。
- 校验被编辑/操作记录本身不在管辖范围内时，复用各自模块既有的"记录不存在"错误文案，不额外抛"无权限"信息，避免暴露"这个 id 存在但你无权限"的越权探测信号，与查询侧已确立的设计风格保持一致；`parentId`/`orgId` 指向的目标组织不在管辖范围内时（新建或移动场景），直接抛"无权限"类错误，因为这类校验的对象是"要放進哪个组织"而不是"是否存在某条具体记录"。
- `OrgServiceImpl.update` 校验新 `parentId` 时，仅在请求携带的 `parentId` 与被编辑组织当前的 `parentId` 不同（即真正发生"移动"）时才校验新 `parentId` 是否在管辖范围内；`parentId` 未变化时跳过该项校验，修复"虚拟根节点"/管辖范围直接覆盖某个顶级组织自身场景下，不改变上级组织的编辑被误拒的问题。
- `GET /api/auth/permissions` 响应新增 `orgScopeRestricted` 布尔字段：当前登录用户的管辖组织范围解析结果为受限时为 `true`，否则为 `false`；供前端在权限编码之外，判断是否需要收紧"上级组织"等选择器的可选范围。
- 组织管理前端"新增/编辑组织"弹窗的"上级组织"选择器：`orgScopeRestricted` 为 `true` 时不再拼接代表 `parentId=0` 的虚拟"顶级组织"根节点，选择器数据直接使用已经过后端管辖范围过滤的组织树；为 `false`（不受限）时行为不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `org-scope-data-permission`：新增管辖组织范围解析服务侧的"校验断言"能力（判断给定组织 id 是否在当前用户管辖范围内），供写操作接口复用。
- `org-management`：组织新增/编辑/启用/停用/删除接口新增管辖组织范围校验要求；更新组织时"新上级组织超出范围"校验精确到"仅在实际发生变化时校验"；组织管理前端"上级组织"选择器按 `orgScopeRestricted` 收紧可选项。
- `position-management`：任职新增/编辑/启用/停用/删除接口新增管辖组织范围校验要求。
- `application-management`：应用新增/编辑/启用/停用/删除接口新增管辖组织范围校验要求。
- `permission-driven-visibility`：查询当前用户已授权权限编码的接口响应新增 `orgScopeRestricted` 字段。

## Impact

- 涉及后端文件：`auth/service/OrgScopeService.java`、`auth/service/impl/OrgScopeServiceImpl.java`、`auth/dto/PermissionCodesVO.java`、`auth/controller/AuthController.java`、`org/service/impl/OrgServiceImpl.java`、`user/service/impl/PositionServiceImpl.java`、`app/service/impl/AppServiceImpl.java`。
- 涉及前端文件：`src/api/auth.ts`、`src/types`（权限编码响应类型）、`src/stores/currentUserPermission.ts`、`src/views/identity/org/OrgManagementView.vue`。
- 不改变查询接口（`getPage`/`getTree`/`getChildren` 等）已有的范围过滤逻辑。
- 不受管辖范围限制的管理员（`resolveAllowedOrgIds` 返回空 `Optional`）的行为完全不变，`orgScopeRestricted` 为 `false`，前端选择器行为与改动前一致。
- 任职管理、应用管理的"所属组织"选择器本身就是真实组织节点（不存在 `orgId=0` 的虚拟顶级选项），直接复用已经过后端管辖范围过滤的组织树即天然正确，本次不需要改动。
