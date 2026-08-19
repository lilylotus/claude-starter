## MODIFIED Requirements

### Requirement: 应用创建时自动初始化认证配置
新建应用时，系统 SHALL 在创建应用主记录与对外接口凭证配置的同一事务内，为该应用创建一条
默认认证配置记录：协议类型为"无"，回跳地址匹配列表（`servicePatterns`）为空列表。

#### Scenario: 新建应用自动生成默认认证配置
- **WHEN** 管理员成功创建一个新应用
- **THEN** 系统为该应用生成一条认证配置记录，协议类型为"无"，`servicePatterns` 为空

### Requirement: 查询应用认证配置
系统 SHALL 提供接口，按应用 id 查询该应用当前的认证配置：协议类型、回跳地址匹配列表
（`servicePatterns`，CAS/OAuth2.0 等协议共用同一份存储）、登出通知回调地址
（`logoutNotifyUrl`，未配置时返回空），以及基于该应用 `appId` 计算出的只读协议接口地址
（CAS 登录/票据验证/登出三个地址，OAuth2 授权/Access Token/用户信息三个地址），无论当前
协议类型是"无"、"CAS"还是"OAuth2.0"，接口地址均一并返回。

#### Scenario: 查询已配置 CAS 协议的应用
- **WHEN** 调用方查询一个协议类型为 CAS、已配置 2 条匹配规则、已配置登出通知回调地址的
  应用的认证配置
- **THEN** 系统返回协议类型 CAS、该 2 条匹配规则（`servicePatterns`）、已配置的登出通知
  回调地址，以及该应用的 CAS 三个协议接口地址

#### Scenario: 未配置登出通知回调地址时返回空
- **WHEN** 调用方查询一个协议类型为 CAS 但从未配置过登出通知回调地址的应用
- **THEN** 系统返回的 `logoutNotifyUrl` 为空，不报错

### Requirement: 修改应用认证配置
系统 SHALL 提供接口，修改指定应用的协议类型、回跳地址匹配列表（`servicePatterns`），
以及登出通知回调地址（`logoutNotifyUrl`，可选字段，允许留空），且只有当前登录用户对该
应用所属组织具备管辖权限时才允许修改，否则 SHALL 拒绝该次请求。修改成功后 SHALL 记录一条
操作日志。

#### Scenario: 无管辖权限时修改被拒绝
- **WHEN** 当前登录用户对目标应用所属组织不具备管辖权限，仍调用修改认证配置接口
- **THEN** 系统拒绝本次修改请求，不落库

#### Scenario: 修改成功记录操作日志
- **WHEN** 具备管辖权限的管理员把某应用协议类型从"无"改为 CAS，并提交一条匹配规则与一个
  登出通知回调地址
- **THEN** 系统保存新的协议类型、匹配规则（`servicePatterns`）与登出通知回调地址，并生成
  一条对应的操作日志

#### Scenario: 登出通知回调地址留空时保存成功
- **WHEN** 管理员提交的认证配置未填写登出通知回调地址
- **THEN** 系统正常保存本次修改，该应用的登出通知回调地址为空，登出时系统跳过该应用的
  回调通知

### Requirement: 协议类型与匹配列表的关联校验
协议类型为 CAS 或 OAuth2.0 时，回跳地址匹配列表（`servicePatterns`）SHALL 至少包含一条
规则，否则系统 SHALL 拒绝本次修改；协议类型为"无"时，`servicePatterns` SHALL 被清空，
不保留历史值。匹配列表中的每一条规则 SHALL 为非空白字符串，系统 SHALL 对列表去除重复项
后再保存。CAS 与 OAuth2.0（及未来新增的单点登录协议）共用同一份 `servicePatterns`
存储，不再按协议类型分别维护独立列表。

#### Scenario: 选择 CAS 协议但未提供匹配规则被拒绝
- **WHEN** 管理员把协议类型改为 CAS，但 `servicePatterns` 为空
- **THEN** 系统拒绝本次修改请求，提示需至少配置一条匹配规则

#### Scenario: 选择 OAuth2.0 协议但未提供匹配规则被拒绝
- **WHEN** 管理员把协议类型改为 OAuth2.0，但 `servicePatterns` 为空
- **THEN** 系统拒绝本次修改请求，提示需至少配置一条匹配规则

#### Scenario: 协议类型改回"无"时清空历史匹配列表
- **WHEN** 管理员把一个此前配置了匹配规则的应用协议类型改为"无"
- **THEN** 系统保存后该应用的 `servicePatterns` 为空

#### Scenario: 协议类型从 CAS 切换为 OAuth2.0 时沿用同一份匹配列表存储
- **WHEN** 管理员把一个已配置 `servicePatterns` 的 CAS 应用协议类型改为 OAuth2.0，并提交
  一份新的匹配列表
- **THEN** 系统用本次提交的新列表整体替换 `servicePatterns`，不存在"旧 CAS 列表"与"新
  OAuth2.0 列表"并存或混淆的情况
