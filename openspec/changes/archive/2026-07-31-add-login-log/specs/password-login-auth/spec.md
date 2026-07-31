## MODIFIED Requirements

### Requirement: 口令登录
系统 SHALL 提供登录接口，接受经 RSA 公钥加密后的账号与密码密文，使用配置的 RSA 私钥解密后与密码表中的摘要比对；账号不存在、密码不匹配、账号已停用/已删除均 SHALL 返回业务错误而非登录成功；登录成功 SHALL 签发一对 access-key/refresh-key（均为不含横线的 UUID 字符串）并返回给客户端，同时将其状态保存到 Redis。每一次登录尝试（无论成功或失败，含账号密文解密失败）系统 SHALL 同步记录一条登录日志（见 `login-log-management` 能力），登录日志的记录行为不改变本需求描述的对外错误提示文案与信息泄露约束。

#### Scenario: 账号密码正确时登录成功
- **WHEN** 客户端调用登录接口，提交经 RSA 公钥加密的合法账号与匹配该账号当前密码摘要的密码密文
- **THEN** 系统返回 access-key、access-key 过期时间、refresh-key，并在 Redis 中建立对应的会话记录

#### Scenario: 密码错误时登录失败
- **WHEN** 客户端调用登录接口，提交的密码密文解密后与该账号当前密码摘要不匹配
- **THEN** 系统返回业务错误（非零 `code`），不签发 access-key/refresh-key

#### Scenario: 账号不存在时登录失败
- **WHEN** 客户端调用登录接口，提交的账号解密后在系统中不存在对应的未删除用户
- **THEN** 系统返回业务错误（非零 `code`），不泄露"账号不存在"与"密码错误"的具体区别信息

#### Scenario: 账号已停用或已删除时登录失败
- **WHEN** 客户端调用登录接口，提交的账号对应用户状态为停用或已被逻辑删除
- **THEN** 系统返回业务错误（非零 `code`），不签发 access-key/refresh-key
