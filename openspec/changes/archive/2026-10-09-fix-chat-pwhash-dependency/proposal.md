## Why

单聊端到端加密功能上线后完全不可用：任何账号首次登录生成身份密钥、或已有密钥的账号再次
登录解锁本地私钥，都 100% 报错"聊天加密密钥初始化失败，单聊消息暂时无法收发"
（`frontend/src/stores/chat.ts:337`）。实测拿到的浏览器控制台堆栈是：

```
TypeError: length cannot be null or undefined
  at libsodium-wrappers.mjs
  at wrapPrivateKey (chatCrypto.ts:107)
```

根因已确认：`package.json` 里装的是 `libsodium-wrappers`（标准/精简版 WASM 构建），这个
构建**不包含** `crypto_pwhash`（Argon2id 口令派生）这一族 API——用 Node 直接 `require`
装好的这个包并 `await sodium.ready` 之后验证，`sodium.crypto_pwhash`、
`sodium.crypto_pwhash_SALTBYTES` 均为 `undefined`，而 `crypto_box_keypair`/
`randombytes_buf`/`crypto_secretbox_KEYBYTES` 等其它 API 都正常存在。`chatCrypto.ts` 的
`wrapPrivateKey`/`unwrapPrivateKey`（design.md Decision 2 记录的"口令包裹私钥"实现）
依赖 `sodium.crypto_pwhash`/`sodium.crypto_pwhash_SALTBYTES` 做 Argon2id 口令派生，
调用时拿到 `undefined` 长度传给 `randombytes_buf`，直接抛出上面这个 `TypeError`，与报错
堆栈完全吻合。`crypto_pwhash` 这族 API 只存在于 **sumo**（全量）版本的 WASM 构建里，即
`libsodium-wrappers-sumo` 包，而不是当前装的精简版 `libsodium-wrappers`——这是当初引入
依赖时选错了包变体，从归档 change（`chat-end-to-end-encryption`）落地起就从未真正工作过。

## What Changes

- 前端依赖从 `libsodium-wrappers`（+ `@types/libsodium-wrappers`）换成
  `libsodium-wrappers-sumo`——同一个 libsodium.js 项目的全量 WASM 构建，包含
  `crypto_pwhash`（Argon2id）等精简版缺失的 API；其它已在用的 API（`crypto_box_keypair`、
  `crypto_secretbox_*`、`crypto_scalarmult`、`crypto_generichash`、
  `crypto_aead_xchacha20poly1305_ietf_*` 等）sumo 版本同样具备，不需要额外改调用方式。
  **不**额外装 `@types/libsodium-wrappers-sumo`——`libsodium-wrappers-sumo` 自带类型
  声明文件，那个 `@types` 包只是一个已废弃的 stub（安装时会提示"不需要装它"）。
- `frontend/src/utils/chatCrypto.ts` 的 `import sodium from 'libsodium-wrappers'` 改为
  从 `'libsodium-wrappers-sumo'` 导入，文件内其它代码不变。
- 顺带修正 `openspec/specs/chat-e2e-encryption/spec.md`"身份密钥生成与本地口令包裹存储"
  Requirement 里的算法名称：原文写的是"AES-GCM"，但归档 change 的 design.md Decision 2
  早已记录实际落地用的是 `crypto_secretbox_easy`（XSalsa20-Poly1305 AEAD），当时只改了
  design.md 没同步改这份已同步的 spec，本次顺手修正这处文档与实现不一致（不改变任何
  行为约定，纯粹是文字表述纠正）。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `chat-e2e-encryption`："身份密钥生成与本地口令包裹存储" Requirement 的算法名称描述由
  "AES-GCM" 纠正为实际使用的 XSalsa20-Poly1305（`crypto_secretbox`），不改变任何行为
  约定/Scenario。

## Impact

- 前端依赖变更：`frontend/package.json`（`libsodium-wrappers` → `libsodium-wrappers-sumo`，
  `@types/libsodium-wrappers` → `@types/libsodium-wrappers-sumo`）。
- 代码改动：`frontend/src/utils/chatCrypto.ts` 的 import 来源。
- 文档改动：`openspec/specs/chat-e2e-encryption/spec.md` 一处算法名称文字修正。
- 不涉及后端改动，不涉及数据库改动，不改变任何已发送/已存储消息的密文格式（棘轮/AEAD
  算法选型本身不变，只是让口令派生这一步真正能跑起来）。
- 风险提示：sumo 版 WASM 体积比精简版更大（libsodium.js 官方说明是预期的权衡），会小幅
  增加前端 `chat` 相关 chunk 的包体积，属于本次修复必须付出的代价。
