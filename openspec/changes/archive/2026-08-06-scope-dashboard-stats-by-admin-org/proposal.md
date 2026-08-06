## Why

首页概览统计接口 `GET /api/dashboard/stats` 当前无论当前登录管理员配置了什么管辖组织范围（`tab_admin_org_scope`），四个统计卡片（组织总数、身份总数、接入应用、管理员总数）永远展示系统全局总数。用户反馈这不对：一个只管辖某个子组织的管理员，打开首页看到的应该是自己管辖范围内的规模，而不是整个系统的规模——这与组织管理、任职管理、应用管理、用户管理等业务列表页已经落地的"管辖组织范围收紧数据"行为不一致，造成"列表页看到的数据比首页统计数字少很多"的困惑。

## What Changes

- `GET /api/dashboard/stats` 的统计口径改为按当前登录管理员的管辖组织范围收紧：
  - 当前用户没有配置管辖组织范围（`OrgScopeService.resolveAllowedOrgIds` 返回空 `Optional`，即"不受限制"）时，行为不变，展示系统全局总数。
  - 配置了管辖组织范围时，四个维度分别只统计范围内的数据：组织总数、接入应用按各自的 `orgId` 直接过滤；身份总数（用户）、管理员总数通过各自关联的 `tab_user_position` 任职记录判断"是否存在落在管辖范围内的任职"。
- 接口仍然对所有已登录账号开放（不要求任何具体查看权限点），只是返回的数值口径按管辖组织范围收紧，这一点不变。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `dashboard-overview`: 反转"统计卡片展示真实总数"需求中"口径独立于任何业务列表页数据权限范围条件"的表述，改为按当前登录管理员的管辖组织范围收紧统计口径（未配置管辖范围时仍为全局总数）。

## Impact

- `backend/src/main/java/cn/nihility/rbac/dashboard/service/impl/DashboardStatisticsServiceImpl.java`：注入 `OrgScopeService`，`getStats()` 增加"解析管辖组织范围 → 受限/不受限两条路径"的逻辑。
- `backend/src/main/java/cn/nihility/rbac/user/mapper/UserMapper.java` + `UserMapper.xml`：新增按管辖组织范围计数用户的方法（EXISTS `tab_user_position` 判断）。
- `backend/src/main/java/cn/nihility/rbac/admin/mapper/AdminMapper.java` + `AdminMapper.xml`：新增按管辖组织范围计数管理员的方法（通过 `tab_admin.user_id` 同样 EXISTS `tab_user_position` 判断）。
- 组织总数、应用总数不需要新增 Mapper 方法，复用现有 `OrgMapper`/`AppMapper` 的 `selectCount` + `LambdaQueryWrapper`，追加 `id`/`orgId` 落在管辖范围内的过滤条件即可。
- `backend/src/test/java/cn/nihility/rbac/dashboard/service/impl/DashboardStatisticsServiceImplTest.java`：补充"不受限"（保持全局总数，行为不变）与"受限"（四个维度均按管辖范围收紧）两组用例。
- 不涉及前端改动：`DashboardStatsVO`/`DashboardStats` 响应结构字段不变，只是数值口径变化。
- 不涉及数据库结构变更。
