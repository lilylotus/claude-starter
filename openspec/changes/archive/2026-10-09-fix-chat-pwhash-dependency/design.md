## Context

现状核对（写 design 前已实测确认，不是凭读代码猜测）：

```bash
cd frontend && node -e "
const sodium = require('libsodium-wrappers');
sodium.ready.then(() => {
  console.log('crypto_pwhash_SALTBYTES:', sodium.crypto_pwhash_SALTBYTES); // undefined
  console.log('typeof crypto_pwhash:', typeof sodium.crypto_pwhash);        // undefined
  console.log('typeof crypto_box_keypair:', typeof sodium.crypto_box_keypair); // function
  console.log('typeof randombytes_buf:', typeof sodium.randombytes_buf);       // function
});
"
```

- `package.json` 当前装的 `libsodium-wrappers@0.8.4` 是 libsodium.js 项目的"精简版"
  WASM 构建，`crypto_box_keypair`/`crypto_secretbox_*`/`crypto_scalarmult`/
  `crypto_generichash`/`crypto_aead_xchacha20poly1305_ietf_*`/`randombytes_buf` 等
  `chatCrypto.ts` 用到的其它 API 全部存在且能正常工作，唯独 `crypto_pwhash`
  （Argon2id 口令派生）这一整族 API 在精简版构建里完全不存在（`undefined`）。
- `chatCrypto.ts` 的 `wrapPrivateKey`（`frontend/src/utils/chatCrypto.ts:105`）与
  `unwrapPrivateKey`（同文件 131 行）都依赖 `sodium.crypto_pwhash`/
  `sodium.crypto_pwhash_SALTBYTES` 做口令派生，这是归档 change
  `chat-end-to-end-encryption` design.md Decision 2 明确记录的选型（Argon2id +
  `INTERACTIVE` 强度档位），选型本身没问题，只是依赖的 npm 包变体装错了。
- libsodium.js 项目官方把 WASM 构建拆成两个 npm 包：`libsodium-wrappers`（精简版，剔除
  "大多数应用不需要"的原语以减小体积）与 `libsodium-wrappers-sumo`（全量版，包含
  `crypto_pwhash`/`crypto_pwhash_scryptsalsa208sha256`/`crypto_shorthash` 等精简版
  剔除的原语）。`crypto_pwhash` 属于被精简版剔除的原语之一。
- 影响范围是 100%：无论是"首次使用生成新身份密钥对"还是"再次登录解锁已有身份密钥"，
  两条路径都会调用到 `crypto_pwhash` 系列函数，因此两条路径都必然失败——不是概率性/
  环境相关的问题，是这个依赖包变体下必然触发的 bug，自归档 change 落地后从未真正跑通过。

## What changes and why

把前端依赖从 `libsodium-wrappers` 换成 `libsodium-wrappers-sumo`（`@types` 包同步换成
`@types/libsodium-wrappers-sumo`），`chatCrypto.ts` 的 import 来源改为
`'libsodium-wrappers-sumo'`，其它代码一行不改——`chatCrypto.ts` 用到的所有 API（含
`crypto_pwhash` 这族）在 sumo 版本里都存在，接口签名/行为与精简版完全一致（sumo 只是
"精简版 + 额外原语"的超集，不是另一套不兼容的 API）。

- **备选 A**：继续用精简版 `libsodium-wrappers`，但把口令派生算法从 `crypto_pwhash`
  （Argon2id）换成精简版里确实存在的某个原语（比如拿 `crypto_generichash` 自己拼一个
  简化的 KDF）。
- **为什么不选**：Argon2id 是业界公认的口令哈希/派生标准算法（专门设计用来抵抗 GPU/ASIC
  暴力破解，这正是"用登录密码派生出能加密私钥的包裹密钥"这个场景最需要的属性），自己拿
  通用哈希函数拼一个"等效 KDF"既是重新发明轮子，安全强度也远不如 Argon2id（没有内置的
  内存难度/计算难度参数化防暴力破解设计），属于不应该在加密原语选型上妥协的场景。design.md
  当初选 Argon2id 是经过考虑的决定，不应该因为装错了包就反过来改算法选型。
- **备选 B**：保留精简版 `libsodium-wrappers` 用于 `chatCrypto.ts` 里不需要 `crypto_pwhash`
  的那些函数，额外单独引入一个专门的 Argon2id 库（如 `argon2-browser`）只用于口令派生
  这一步。
- **为什么不选**：多引入一个独立的 WASM/加密依赖包，比"同一个项目下换一个构建变体"复杂度
  更高（两套 WASM 加载、两套类型声明、两个包的版本兼容性都要单独关注），且
  `libsodium-wrappers-sumo` 本来就完整覆盖了 `chatCrypto.ts` 当前用到的全部 API（含
  `crypto_pwhash`），没有理由为了"避免体积增大"这个次要考量去引入第二个密码学依赖包，
  增加攻击面与维护成本。

## Non-goals

- 不重新评估/更换棘轮、AEAD 加解密等其它密码学选型（design.md 已有的 Decision 1/3/4
  不受影响，问题只出在"口令派生这一步用的 API 在装的包变体里不存在"）。
- 不处理 `openspec/specs/chat-e2e-encryption/spec.md` 里其它可能存在的、与归档 change
  design.md 记录的"实现落地"描述之间的历史遗留差异（本次只顺手修正"AES-GCM"这一处与
  当前 bug 修复直接相关、容易让人以为"包裹算法也要跟着这次换包一起变"的表述错误；全面
  核对该 spec 与实现的一致性属于更大范围的文档同步工作，不在本次 bug 修复范围内）。
