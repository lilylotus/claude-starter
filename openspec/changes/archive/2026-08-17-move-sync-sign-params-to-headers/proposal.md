## Why

`app-sync-notify-pull` 已实现的签名机制把 `appKey`/`signMethod`/`ts`/`nonce`/`signature` 五个
签名相关参数放在 URL query 参数里，导致拉取接口/通知回调请求的 URL 参数过多过长，且与业务
查询参数（`dataType`/`bizIds`/`fromSequence`/`limit` 等）混在一起，不便于外部应用日志记录
与网关层处理。规范文档发布后收到反馈，要求把签名相关参数改放到请求 Header 中，业务参数仍
留在 query。这是签名参数的传输位置调整，不改变"是否需要签名/验签"以及验签通过与否的判定
规则（`openspec/specs/app-sync-notify-pull/spec.md` 里"通知与拉取请求的签名与验签"需求本身
不涉及参数放在 query 还是 header，属于实现细节调整，不改变该需求的可观察行为）。

## What Changes

- `SignConstants`：删除 `appKey`/`signMethod`/`ts`/`nonce`/`signature` 的 `QUERY_KEY_*`
  常量，改为对应的请求头名称常量：`appKey`/`signMethod`/`ts`/`nonce`/`signature`，均不带
  `X-` 前缀，且与原 query key 字符串完全一致（含已存在的 `X-App-Key` 头本次一并改名为
  `appKey`，5 个签名相关头统一风格）。**BREAKING**：外部已按旧规范（`X-App-Key` 请求头 +
  签名参数放 query）对接的应用需要同步调整调用方式，否则会因缺少 `appKey` 请求头而被拒绝
  （不区分是否开启签名校验，因为 AccessKey 定位应用这一步任何请求都会执行）。
- `OpenApiSignInterceptor`（拉取接口验签方）：从请求头读取 `signMethod`/`ts`/`nonce`/
  `signature`，签名原文的规范化拼接输入由"业务 query 参数 + 请求头读取到的
  `appKey`/`signMethod`/`ts`/`nonce`"组成。
- `NotifySignatureAppender`（出站通知签名方）：不再拼接 URL query，改为返回一个 Header Map
  供调用方直接设置到 HTTP 请求头；签名计算规则（urlSign/bodySign 拼接方式）不变。
- `AppNotifyServiceImpl`：改为使用 `NotifySignatureAppender` 返回的 Header Map 发起请求，
  不再对 `notifyUrl` 做 query 拼接。
- 同步更新 `NotifySignatureAppenderTest`、`OpenApiSignInterceptorTest` 等引用旧
  `QUERY_KEY_*` 常量/旧行为的单元测试。
- 同步更新仓库根目录 `接口调用签名规范.md`（签名参数改为放在请求 Header 中说明，第 8 节
  Java GET/POST 示例代码同步调整）。

不涉及：`SignCanonicalizer` 的规范化拼接算法（按 key ASCII 升序 `k1=v1&k2=v2` 拼接，值不
做二次编码）、`SignAlgorithmCodec`/`SignAlgorithmCodecImpl` 的 HMAC 计算逻辑、`NonceStore`
防重放逻辑、5 分钟时间戳有效窗口，均保持不变。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

（无——本变更是签名参数传输位置的实现调整，`app-sync-notify-pull` spec 中"通知与拉取请求
的签名与验签"需求描述的是签名是否附带、是否验签通过，不约束参数放在 query 还是 header，
该需求的可观察行为未发生变化，故不产出 spec delta，仅在实现层调整，见 `.openspec.yaml` 的
`skip_specs: true`）

## Impact

- 代码：`backend/src/main/java/cn/nihility/rbac/sync/sign/`（`SignConstants`、
  `OpenApiSignInterceptor`、`NotifySignatureAppender`）、
  `backend/src/main/java/cn/nihility/rbac/sync/notify/service/impl/AppNotifyServiceImpl.java`。
  测试：`backend/src/test/java/cn/nihility/rbac/sync/sign/NotifySignatureAppenderTest.java`、
  `backend/src/test/java/cn/nihility/rbac/sync/sign/OpenApiSignInterceptorTest.java`。
- 文档：仓库根目录 `接口调用签名规范.md`。
- 对外接口契约：**BREAKING**——所有已对接的外部应用（拉取方）必须把签名参数从 query 迁移到
  请求 Header，且把原来的 `X-App-Key` 请求头改名为 `appKey`，否则连"定位调用方应用"这一步
  都会失败（与是否开启签名校验无关）；本系统作为通知发起方也会同步改用新的 Header 名称发送，
  外部应用的通知接收端（验签方）同样需要同步调整。
