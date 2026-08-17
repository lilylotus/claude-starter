## 1. 常量调整

- [x] 1.1 `SignConstants`：删除 `QUERY_KEY_APP_KEY`/`QUERY_KEY_SIGN_METHOD`/`QUERY_KEY_TS`/
      `QUERY_KEY_NONCE`/`QUERY_KEY_SIGNATURE`，新增 `HEADER_SIGN_METHOD`（`signMethod`）、
      `HEADER_TIMESTAMP`（`ts`）、`HEADER_NONCE`（`nonce`）、`HEADER_SIGNATURE`
      （`signature`），并把 `HEADER_APP_KEY` 的常量值从 `X-App-Key` 改为 `appKey`
      （**BREAKING**，5 个签名相关头统一不带 `X-` 前缀、与内部字段名同名）。

## 2. 拉取接口验签方改造

- [x] 2.1 `OpenApiSignInterceptor.verifySignature`：改为从请求头读取 `signMethod`/`ts`/
      `nonce`/`signature`（`request.getHeader(...)`），不再从 query 里 `remove(signature)`。
- [x] 2.2 规范化拼接输入调整为：`extractQueryParams(request)` 取到的业务 query 参数
      （此时已不含任何签名参数）+ 手动 put 的 `appKey`/`signMethod`/`ts`/`nonce`（取值来自
      请求头），按 design.md Decision 2 的拉取场景公式执行。
- [x] 2.3 确认 `URL_SIGN_HEX_LENGTH`（64）校验仍作用于从请求头读取的 `signature` 值。

## 3. 出站通知签名方改造

- [x] 3.1 `NotifySignatureAppender`：方法签名从
      `String appendSignatureIfNeeded(String baseUrl, boolean needSign, ...)` 改为
      `Map<String, String> buildSignatureHeaders(boolean needSign, String signAlgorithm,
      String accessKey, String secretKey, String requestBody)`，不再拼接/返回 URL。
- [x] 3.2 `needSign=false` 时返回仅含 `appKey` 一项的 Map；`needSign=true` 时按
      design.md Decision 2 通知场景公式计算 `urlSign`/`bodySign`/`signature`，返回含
      `appKey`/`signMethod`/`ts`/`nonce`/`signature` 五项的 Map。
- [x] 3.3 移除不再需要的 `URLEncoder`/URL 拼接相关代码。

## 4. 通知发起方调用改造

- [x] 4.1 `AppNotifyServiceImpl.notifyOneApp`：改为直接使用 `target.getNotifyUrl()` 发起
      请求（不再调用 URL 拼接逻辑），把 `notifySignatureAppender.buildSignatureHeaders(...)`
      返回的 Header Map 与原有 headers 合并后传给 `HttpClientUtils.postBinary`。
- [x] 4.2 确认不再需要单独 `headers.put(SignConstants.HEADER_APP_KEY, ...)`（已包含在
      `buildSignatureHeaders` 返回值中），避免重复设置。

## 5. 单元测试同步

- [x] 5.1 `OpenApiSignInterceptorTest`：`signedRequestInternal` 等辅助方法里，签名 4 参数
      改用 `request.addHeader(...)` 写入请求头，业务参数继续用 `request.setParameter`；
      所有 `SignConstants.QUERY_KEY_*` 引用改为 `SignConstants.HEADER_*`。
- [x] 5.2 `NotifySignatureAppenderTest`：改为直接断言 `buildSignatureHeaders` 返回的
      `Map<String, String>`（各 header key 的值/长度），删除 `parseQuery` 辅助方法及相关
      `import`（`java.net.URI`、`Pattern`/`Matcher` 等，如无其他用途）。
- [x] 5.3 全仓库检索确认无其他测试/代码引用旧 `QUERY_KEY_*` 常量或
      `appendSignatureIfNeeded` 方法名（`./gradlew build` 编译通过即可验证）。

## 6. 验证

- [x] 6.1 `backend/` 目录下执行 `./gradlew test --tests
      "cn.nihility.rbac.sync.sign.*"` 确认签名相关测试通过。
- [x] 6.2 `backend/` 目录下执行 `./gradlew build` 确认全量编译 + 测试通过。

## 7. 文档同步

- [x] 7.1 更新仓库根目录 `接口调用签名规范.md`：第 2 节签名参数表改为"请求头"，第 3/4/5
      节中涉及 query 参数携带签名参数的表述、示例请求全部改为请求头携带；第 3.1 节规范化
      拼接输入的取值来源需按 design.md Decision 2 分拉取/通知回调两个场景分别说明。
- [x] 7.2 更新第 8 节 Java GET/POST 签名示例代码：`GetSignExample`/`PostSignExample` 改为
      把 `appKey`/`signMethod`/`ts`/`nonce`/`signature` 通过 `HttpRequest.Builder.header(...)`
      设置到请求头，而不是拼接进 URL query；GET 示例的业务参数（`dataType`/`bizIds`）仍留在
      URL query。
