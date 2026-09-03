## MODIFIED Requirements

### Requirement: 查询应用认证配置
系统 SHALL 提供接口，按应用 id 查询该应用当前的认证配置：协议类型、回跳地址匹配列表
（`servicePatterns`，CAS/OAuth2.0 等协议共用同一份存储）、登出通知回调地址
（`logoutNotifyUrl`，未配置时返回空）、认证方式配置（`loginMethods`，取值子集为
`PASSWORD`/`SMS`/`QRCODE`，未配置——含从未修改过、以及显式保存为空列表——时统一返回
仅含 `PASSWORD` 的默认值），以及基于该应用 `appId` 计算出的只读协议
接口地址（CAS 登录/票据验证/登出三个地址，OAuth2 授权/Access Token/用户信息三个地址），无论
当前协议类型是"无"、"CAS"还是"OAuth2.0"，接口地址均一并返回。

#### Scenario: 查询已配置 CAS 协议的应用
- **WHEN** 调用方查询一个协议类型为 CAS、已配置 2 条匹配规则、已配置登出通知回调地址的
  应用的认证配置
- **THEN** 系统返回协议类型 CAS、该 2 条匹配规则（`servicePatterns`）、已配置的登出通知
  回调地址、当前允许的登录认证方式列表，以及该应用的 CAS 三个协议接口地址

#### Scenario: 未配置登出通知回调地址时返回空
- **WHEN** 调用方查询一个协议类型为 CAS 但从未配置过登出通知回调地址的应用
- **THEN** 系统返回的 `logoutNotifyUrl` 为空，不报错

#### Scenario: 未单独配置过登录认证方式时返回仅口令
- **WHEN** 调用方查询一个从未修改过登录认证方式配置的应用（含存量应用）
- **THEN** 系统返回的 `loginMethods` 为 `["PASSWORD"]`

#### Scenario: 显式保存为空列表时查询仍返回仅口令
- **WHEN** 调用方查询一个此前被管理员显式把 `loginMethods` 全部取消勾选并保存过的应用
- **THEN** 系统返回的 `loginMethods` 为 `["PASSWORD"]`，与"从未配置过"的查询结果一致

### Requirement: 修改应用认证配置
系统 SHALL 提供接口，修改指定应用的协议类型、回跳地址匹配列表（`servicePatterns`）、登出
通知回调地址（`logoutNotifyUrl`，可选字段，允许留空），以及认证方式配置（`loginMethods`，
取值子集为 `PASSWORD`/`SMS`/`QRCODE`，允许提交空列表代表不单独配置），且只有当前登录用户
对该应用所属组织具备管辖权限时才允许修改，否则 SHALL 拒绝该次请求。修改成功后 SHALL 记录
一条操作日志。

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

#### Scenario: 提交不含口令的认证方式组合按原样保存
- **WHEN** 管理员提交的 `loginMethods` 只包含 `SMS`/`QRCODE`，不包含 `PASSWORD`
- **THEN** 系统按提交内容原样保存，不自动补齐 `PASSWORD`；该应用此后的 SSO 登录页仅展示
  `SMS`/`QRCODE` 对应的登录方式入口，不展示口令登录

#### Scenario: 提交空列表代表不单独配置认证方式
- **WHEN** 管理员把某应用的 `loginMethods` 全部取消勾选，提交空列表并保存
- **THEN** 系统正常保存本次修改（不拒绝、不报错），该应用此后按"未配置"处理，查询与 SSO
  登录页展示均回退为默认仅口令登录，不会导致该应用彻底无法登录

#### Scenario: 启用短信/扫码登录
- **WHEN** 管理员把某应用的 `loginMethods` 从 `["PASSWORD"]` 改为
  `["PASSWORD","SMS","QRCODE"]` 并提交保存
- **THEN** 系统保存后该应用的 SSO 登录页展示口令、短信、扫码三个登录方式入口
