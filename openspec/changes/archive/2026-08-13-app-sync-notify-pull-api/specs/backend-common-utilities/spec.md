## ADDED Requirements

### Requirement: 统一的 HTTP 客户端工具类
系统 SHALL 提供一个位于 `cn.nihility.rbac.common.util` 包下的 `HttpClientUtils` 工具类，封装对外发起 HTTP 请求的能力，供需要主动调用外部接口的模块（如应用数据同步通知）复用，避免各处重复处理连接池、超时、证书校验等细节。

`HttpClientUtils` SHALL 支持 `GET`/`POST`/`PUT`/`PATCH` 四种请求方法，SHALL 支持以下四种请求体格式：`application/json`（对象与 JSON 互转复用 `JacksonUtils`）、`multipart/form-data`（含文本字段与二进制文件字段）、`application/x-www-form-urlencoded`、任意二进制内容（自定义 `Content-Type`）。

`HttpClientUtils` SHALL 支持按次请求单独指定响应超时时间；未指定时使用全局默认响应超时（5 秒）；连接超时统一使用全局默认（5 秒），不支持按次覆盖。`HttpClientUtils` 内部 SHALL 使用连接池化的 HTTP 客户端（最大连接数、单路由最大连接数可配置），不 SHALL 为每次请求创建新的连接管理器。`HttpClientUtils` SHALL 支持通过全局配置开启"跳过 HTTPS 证书校验"（用于自签名证书场景），开启后对 `https://` 地址的请求 SHALL NOT 因证书不受信任而失败。

#### Scenario: 发送 JSON 请求
- **WHEN** 调用方使用 `HttpClientUtils` 以 `POST` 方式、`application/json` 格式向某地址发送一个对象作为请求体
- **THEN** 请求体是该对象序列化后的 JSON 字符串，`Content-Type` 为 `application/json`

#### Scenario: 发送 multipart/form-data 请求
- **WHEN** 调用方使用 `HttpClientUtils` 以 `POST` 方式发送包含文本字段与一个二进制文件字段的表单
- **THEN** 请求以 `multipart/form-data` 格式发出，文本字段与文件字段均正确携带

#### Scenario: 未指定响应超时时使用全局默认值
- **WHEN** 调用方调用 `HttpClientUtils` 发起请求且未指定响应超时时间
- **THEN** 该次请求使用全局默认响应超时（5 秒）

#### Scenario: 指定响应超时覆盖全局默认值
- **WHEN** 调用方调用 `HttpClientUtils` 发起请求并显式指定响应超时时间为 3 秒
- **THEN** 该次请求的响应超时按 3 秒生效，不使用全局默认的 5 秒

#### Scenario: 跳过 HTTPS 证书校验
- **WHEN** 全局配置开启"跳过 HTTPS 证书校验"，调用方请求一个使用自签名证书的 `https://` 地址
- **THEN** 请求正常发出并能获取响应，不因证书不受信任而抛出异常

#### Scenario: 连接池复用
- **WHEN** 调用方连续多次调用 `HttpClientUtils` 向同一地址发起请求
- **THEN** 各次请求复用同一个连接池管理的 HTTP 客户端，不为每次请求重新建立连接管理器
