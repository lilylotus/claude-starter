## Context

现状（见 proposal.md - Why）：`SignConstants` 定义了 `appKey`/`signMethod`/`ts`/`nonce`/
`signature` 五个 query key 常量；`OpenApiSignInterceptor.verifySignature` 从
`request.getParameterMap()` 提取全部 query 参数（含签名参数），移除 `signature` 后对剩余
参数（业务参数 + 4 个签名参数）做 `SignCanonicalizer.canonicalize` 再算 HMAC 比对；
`NotifySignatureAppender.appendSignatureIfNeeded` 生成 4 个签名参数 + `signature`，用
`URLEncoder` 拼接到 `baseUrl` 后返回新 URL 字符串；`AppNotifyServiceImpl.notifyOneApp` 用
该 URL 发起 `HttpClientUtils.postBinary`。`X-App-Key` 请求头此前就已存在（用于两种场景下
都要携带、定位调用方应用）。本次改动范围扩大为：另外 4 个签名参数从 query 移到请求头，
**同时**已有的 `X-App-Key` 头本次也一并改名为 `appKey`，5 个头统一为不带 `X-` 前缀、与
内部字段名/原 query key 完全同名的风格（用户明确要求"保持统一"）。

## Goals / Non-Goals

**Goals:**
- 明确新的请求头名称清单，及生成方/校验方分别如何读写。
- 明确签名原文的规范化拼接输入在拉取场景与通知回调场景下分别包含哪些字段，字段值来自
  query 还是来自 header。
- 保证 `SignCanonicalizer`/`SignAlgorithmCodec`/`NonceStore` 三个组件不改动签名，只调整
  上游数据来源。

**Non-Goals:**
- 不改变签名算法本身（HMAC-SHA256/HMAC-SM3）、时间戳时效窗口（5 分钟）、nonce 去重策略。
- 不引入新的签名版本协商机制（不做新旧两种传输方式并存的兼容层）——`needSign=true` 的应用
  一律按新规则验签，旧客户端需要跟随文档同步调整。

## Decisions

### 1. 新请求头名称清单

| 内部字段 | 旧位置（废弃） | 新请求头名称 |
| --- | --- | --- |
| AccessKey | 请求头 `X-App-Key`（已存在，本次改名） | `appKey` |
| 签名方法 | query key `signMethod` | `signMethod` |
| 时间戳 | query key `ts` | `ts` |
| 随机数 | query key `nonce` | `nonce` |
| 签名结果 | query key `signature` | `signature` |

5 个请求头统一不带 `X-` 前缀，且直接复用原 query key / 内部字段名字符串本身作为请求头名称
（`appKey`/`signMethod`/`ts`/`nonce`/`signature`），生成方与校验方不再需要维护一套"内部字段
名 → 请求头名"的映射关系，请求头名本身就是规范化拼接时用的 map key。

在 `SignConstants` 中：
- `HEADER_APP_KEY` 常量值从 `X-App-Key` 改为 `appKey`（**BREAKING**，见 Risks）。
- 删除 `QUERY_KEY_APP_KEY`/`QUERY_KEY_SIGN_METHOD`/`QUERY_KEY_TS`/`QUERY_KEY_NONCE`/
  `QUERY_KEY_SIGNATURE`，新增 `HEADER_SIGN_METHOD`（值 `signMethod`）、`HEADER_TIMESTAMP`
  （值 `ts`）、`HEADER_NONCE`（值 `nonce`）、`HEADER_SIGNATURE`（值 `signature`）。

理由：用户明确要求 5 个头风格统一、不带 `X-` 前缀（`X-` 前缀是历史上非标准头的常见约定，
RFC 6648 已不建议新增头再使用该前缀，本次干脆把已存在的 `X-App-Key` 也一起改名，避免风格
不统一）；复用原 query key 字符串作为请求头名，是为了让"内部签名字段"与"请求头名"一一对应、
不需要额外映射表，降低生成方/校验方两端出错的概率。不复用 HTTP 标准头（如 `Authorization`）
因为这是自定义多字段签名机制，标准头语义不完全匹配。

### 2. 签名原文的规范化拼接输入组成

`SignCanonicalizer.canonicalize` 的入参 `Map<String, String>` 本身不关心 key 的名称/来源，
只按 key 升序拼接。生成方与校验方需要用同一组"内部字段名"（`appKey`/`signMethod`/`ts`/
`nonce`，可继续复用旧 query key 字符串作为纯粹的内部 map key，与实际请求头名称无关）构造
这个 Map，取值来源如下：

- **拉取场景（GET，无请求体，`OpenApiSignInterceptor`）**：
  ```
  Map = 业务 query 参数（如 dataType、bizIds、fromSequence、limit，来自 URL query，
        不再包含任何签名参数，因为它们已不在 query 里）
      + { "appKey": <appKey 请求头值>,
          "signMethod": <signMethod 请求头值>,
          "ts": <ts 请求头值>,
          "nonce": <nonce 请求头值> }
  urlSign = HMAC(secretKey, canonicalize(Map))
  校验：urlSign 与 signature 请求头值（64 位十六进制）比对
  ```
  即：`extractQueryParams` 不再需要 `remove(signature)`（signature 已经不在 query 里），
  改为直接把 4 个签名字段从请求头读取后 `put` 进业务参数 Map（因为请求头名与内部字段名同
  名，读取后可以直接原样 put，不需要改名）。

- **通知回调场景（POST，有请求体，`NotifySignatureAppender`）**：
  ```
  Map = { "appKey": accessKey, "signMethod": signMethod, "ts": ts, "nonce": nonce }
        （不含业务参数——通知回调请求本身不携带业务 query 参数）
  urlSign  = HMAC(secretKey, canonicalize(Map))
  bodySign = HMAC(secretKey, requestBodyRawJson)
  signature = urlSign + bodySign   # 128 位十六进制，无请求体时退化为仅 urlSign（64 位）
  ```
  与现状计算逻辑完全一致，只是最终 `appKey`/`signMethod`/`ts`/`nonce`/`signature` 五个值
  不再拼接进 URL query，而是放进返回的 Header Map（`appKey` 沿用原有"始终设置"的规则，
  与 `needSign` 无关；其余 4 项只在 `needSign=true` 时设置），由调用方
  （`AppNotifyServiceImpl`）设置到 HTTP 请求头。

### 3. `NotifySignatureAppender` 的返回值类型调整

方法签名从 `String appendSignatureIfNeeded(...)` 改为
`Map<String, String> buildSignatureHeaders(...)`：
- `needSign=false` 时返回仅含 `appKey` 一项的 Map。
- `needSign=true` 时返回含 `appKey`/`signMethod`/`ts`/`nonce`/`signature` 五项的 Map。

`AppNotifyServiceImpl.notifyOneApp` 相应调整：直接用 `target.getNotifyUrl()`（不再做 URL
拼接）+ `buildSignatureHeaders` 返回的 Header Map 发起 `HttpClientUtils.postBinary`。

理由：让"构造签名相关 header"这个职责完整封装在 `NotifySignatureAppender` 内，调用方不需要
了解 header 名称细节，也避免调用方既要处理 URL 又要处理 Map 两种输出。

### 4. 测试调整策略

`OpenApiSignInterceptorTest`：`signedRequestInternal` 辅助方法里，业务参数继续用
`request.setParameter` 写入 query，签名 4 参数改用 `request.addHeader` 写入请求头；断言中
引用的 `SignConstants.QUERY_KEY_*` 改为 `SignConstants.HEADER_*`。

`NotifySignatureAppenderTest`：不再从返回 URL 里解析 query 参数断言，改为直接断言返回的
`Map<String, String>` 中各 header key 的值/长度；删除 `parseQuery` 辅助方法（不再需要）。

## Risks / Trade-offs

- **[Risk] BREAKING 变更范围比最初方案更大**：`X-App-Key` 头本次一并改名为 `appKey`，而
  “携带 AccessKey 定位调用方应用”这一步对所有请求都执行（与 `needSign` 无关）→ 上线后
  **所有**已对接的外部应用（不只是开了签名校验的应用）都会因为服务端读不到 `X-App-Key`
  头而被拒绝，影响面比"只影响开了签名校验的应用"更大 → **Mitigation**：`接口调用签名规范.md`
  同步更新并显著标注这是不兼容调整；上线前需要与全部已知对接方（无论是否开启签名校验）
  逐一确认切换时间点（不在本次编码范围内，由业务侧协调）。
- **[Risk] 请求头大小写/多值问题**：部分 HTTP 客户端库或代理可能对自定义 header 做大小写
  归一化或截断 → **Mitigation**：Servlet 的 `HttpServletRequest.getHeader` 本身大小写不敏感，
  JDK `HttpClient`/主流 HTTP 客户端发送自定义 header 均保留原样，风险可控，无需额外处理。

## Migration Plan

- 无数据库迁移，纯代码调整，随正常发版上线。
- 上线前后端需要保证生成方（`NotifySignatureAppender`）与校验方
  （`OpenApiSignInterceptor`）在同一次部署中一起更新，不支持灰度期间新旧签名方式并存
  （proposal.md Non-Goals 已声明不做兼容层）。
- 回滚策略：整体回滚到变更前版本（回滚代码 + 回滚文档），无需额外数据回滚步骤。
