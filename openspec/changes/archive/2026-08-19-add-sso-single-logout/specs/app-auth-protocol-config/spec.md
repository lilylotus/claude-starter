## MODIFIED Requirements

### Requirement: 查询应用认证配置
系统 SHALL 提供接口，按应用 id 查询该应用当前的认证配置：协议类型、CAS service 匹配列表、
OAuth2 redirect_uri 匹配列表、登出通知回调地址（`logoutNotifyUrl`，未配置时返回空），
以及基于该应用 `appId` 计算出的只读协议接口地址（CAS 登录/票据验证/登出三个地址，OAuth2
授权/Access Token/用户信息三个地址），无论当前协议类型是"无"、"CAS"还是"OAuth2.0"，
接口地址均一并返回。

#### Scenario: 查询已配置 CAS 协议的应用
- **WHEN** 调用方查询一个协议类型为 CAS、已配置 2 条 service 匹配规则、已配置登出通知
  回调地址的应用的认证配置
- **THEN** 系统返回协议类型 CAS、该 2 条匹配规则、已配置的登出通知回调地址，以及该应用的
  CAS 三个协议接口地址

#### Scenario: 未配置登出通知回调地址时返回空
- **WHEN** 调用方查询一个协议类型为 CAS 但从未配置过登出通知回调地址的应用
- **THEN** 系统返回的 `logoutNotifyUrl` 为空，不报错

### Requirement: 修改应用认证配置
系统 SHALL 提供接口，修改指定应用的协议类型、对应的匹配列表，以及登出通知回调地址
（`logoutNotifyUrl`，可选字段，允许留空），且只有当前登录用户对该应用所属组织具备
管辖权限时才允许修改，否则 SHALL 拒绝该次请求。修改成功后 SHALL 记录一条操作日志。

#### Scenario: 无管辖权限时修改被拒绝
- **WHEN** 当前登录用户对目标应用所属组织不具备管辖权限，仍调用修改认证配置接口
- **THEN** 系统拒绝本次修改请求，不落库

#### Scenario: 修改成功记录操作日志
- **WHEN** 具备管辖权限的管理员把某应用协议类型从"无"改为 CAS，并提交一条 service 匹配
  规则与一个登出通知回调地址
- **THEN** 系统保存新的协议类型、匹配规则与登出通知回调地址，并生成一条对应的操作日志

#### Scenario: 登出通知回调地址留空时保存成功
- **WHEN** 管理员提交的认证配置未填写登出通知回调地址
- **THEN** 系统正常保存本次修改，该应用的登出通知回调地址为空，登出时系统跳过该应用的
  回调通知

## ADDED Requirements

### Requirement: 登出通知回调地址格式校验
登出通知回调地址（`logoutNotifyUrl`）非空时，SHALL 为合法的 HTTP/HTTPS URL；格式不合法
时系统 SHALL 拒绝本次修改请求。

#### Scenario: 非法地址格式被拒绝
- **WHEN** 管理员提交的登出通知回调地址不是合法的 HTTP/HTTPS URL
- **THEN** 系统拒绝本次修改请求，不落库
