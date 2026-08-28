## MODIFIED Requirements

### Requirement: 前端请求头与静默刷新
前端 SHALL 在每个需要登录态的请求上携带 `identity-token`（access-key）与 `menu`（对应路由的资源编码）请求头；当请求因 access-key 过期被拦截时，前端 SHALL 使用本地缓存的 refresh-key 静默换取新 access-key 并重试原始请求，若 refresh-key 同样无效则清空本地会话并重定向登录页。针对同一个原始请求，"静默刷新并重试"SHALL 最多触发一次：重试后的请求如果仍然收到未登录错误（即换新 access-key 也没有解决该请求的身份校验问题），前端 SHALL NOT 再次触发刷新，而是直接判定当前登录态失效，清空本地会话并重定向登录页，避免因反复触发刷新导致的无限递归请求。

#### Scenario: 业务请求携带身份与资源请求头
- **WHEN** 前端发起一个需要登录态的业务请求
- **THEN** 该请求携带 `identity-token` 请求头（当前 access-key）与 `menu` 请求头（当前路由对应的资源编码）

#### Scenario: access-key 过期时静默刷新后重试
- **WHEN** 前端某个业务请求收到 access-key 已过期的未登录错误，且本地缓存的 refresh-key 仍然有效
- **THEN** 前端自动调用刷新接口换取新 access-key，并使用新 access-key 重试原始请求，不需要用户重新手动登录

#### Scenario: refresh-key 也失效时跳转登录页
- **WHEN** 前端尝试用本地缓存的 refresh-key 静默刷新，但刷新接口同样返回业务错误
- **THEN** 前端清空本地登录态并重定向到登录页

#### Scenario: 重试后仍未通过身份校验时不再次触发刷新
- **WHEN** 前端某个业务请求经历一次"静默刷新并重试"后，重试的请求依然收到未登录错误
- **THEN** 前端不再次调用刷新接口，直接清空本地登录态并重定向到登录页，不出现反复调用刷新接口的情况
