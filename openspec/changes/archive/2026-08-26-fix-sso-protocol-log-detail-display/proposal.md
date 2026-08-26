## Why

登录日志页面"协议详情"弹窗（`SsoProtocolLogDialog.vue`）当前直接展示 `userId` 数字，管理员排查问题时无法一眼看出这是哪个用户，需要靠额外手动查询用户管理页面核对；同时，当某条记录的失败原因是"当前用户无权访问该应用"（应用访问授权策略拒绝）时，后端 `tab_sso_protocol_log.denied_policy_id` 已经记录了具体是哪条应用访问授权策略造成的拒绝，但弹窗表格完全没有展示这个字段，管理员无法直接定位是哪条策略配置导致用户被拦截，仍需跳到策略管理页面逐条排查。这两点都直接影响"协议详情"作为问题排查工具的可用性。

## What Changes

- SSO 协议调用记录查询结果新增返回 `userName`（关联用户姓名快照）与 `deniedPolicyName`（拒绝来源策略名称）两个只读展示字段：
  - `userName`：按 `userId` 关联 `tab_user.name` 查询得到，`userId` 为空或关联的用户已被删除时为空。
  - `deniedPolicyName`：按 `deniedPolicyId` 关联 `tab_app_access_policy.name` 查询得到，`deniedPolicyId` 为空或关联的策略已被删除时为空。
- 后端分页查询实现从当前的 `LambdaQueryWrapper` 单表查询改为自定义 MyBatis XML 分页查询（`SsoProtocolLogMapper.xml`），用 `LEFT JOIN tab_user`、`LEFT JOIN tab_app_access_policy` 一次性带出上述两个展示字段，不在 Java 服务层做批量查询再合并（沿用 `AppAccessEffectiveMapper.xml` 已有的 join 取名习惯）。
- 前端协议详情弹窗（`SsoProtocolLogDialog.vue`）：
  - "用户ID"列改为展示用户姓名（`userName`），取不到时展示占位符 `-`。
  - 新增"拒绝策略"列，展示 `deniedPolicyName`，为空时展示占位符 `-`。
- 不改变现有查询接口的请求参数（`GET /api/sso-protocol-logs` 筛选参数不变），仅扩展响应字段与前端展示；不改变现有交互结构（仍是登录日志行内按钮 + 弹窗 + 表格分页）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `sso-protocol-access-log`：SSO 协议调用记录分页查询结果新增返回 `userName`、`deniedPolicyName` 两个展示字段，并明确关联用户/策略缺失时的兜底行为（返回空）。

## Impact

- 后端：
  - `SsoProtocolLogVO`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/dto/SsoProtocolLogVO.java`）新增 `userName`、`deniedPolicyName` 字段。
  - `SsoProtocolLogMapper`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/mapper/SsoProtocolLogMapper.java`）新增自定义分页查询方法，对应新增 `backend/src/main/resources/mybatis/mapper/SsoProtocolLogMapper.xml`。
  - `SsoProtocolLogQueryServiceImpl`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/service/impl/SsoProtocolLogQueryServiceImpl.java`）改为调用新的自定义分页查询方法，不再使用 `LambdaQueryWrapper` + `ssoProtocolLogMapper.selectPage`。
  - `SsoProtocolLogConvert`（MapStruct）预期无需改动（新增字段随查询结果直接映射，或由 XML resultMap 直接产出 VO，具体取舍见 design.md）。
  - 不涉及数据库表结构变更（`userName`/`deniedPolicyName` 均为查询时关联得出，不落库）。
  - 不涉及 Controller 接口签名变更（`GET /api/sso-protocol-logs` 请求参数不变）。
- 前端：
  - `frontend/src/types/ssoProtocolLog.ts` 的 `SsoProtocolLogRow` 类型新增 `userName`、`deniedPolicyName` 字段。
  - `frontend/src/components/SsoProtocolLogDialog.vue` "用户ID"列改为展示 `userName`，新增"拒绝策略"列展示 `deniedPolicyName`。
- 权限资源编码文件（`权限资源.txt`）：本次不新增/删除菜单或按钮，无需更新。
- OpenSpec：需要为 `sso-protocol-access-log` 编写 delta spec，更新分页查询结果字段要求。
