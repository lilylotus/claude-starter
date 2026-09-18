// 单聊端到端加密核心算法封装（chat-end-to-end-encryption change design.md Decision 2/3/7），
// 全部基于 `libsodium-wrappers`（NaCl/libsodium 的 WebAssembly 封装）。本文件是纯函数集合，
// 不依赖 Pinia/Element Plus，不持有任何长生命周期状态（私钥/共享密钥由调用方——
// stores/chat.ts——在内存中持有），保持和 utils/rsa.ts 同样的“纯工具”定位。
//
// 涉及的密码学原语（均来自 `libsodium-wrappers` 标准版，非 sumo 版也包含）：
// - 身份密钥对：`crypto_box_keypair`（X25519，与 `crypto_scalarmult` 使用同一条曲线，
//   公私钥可以直接喂给 ECDH）。
// - 口令派生包裹密钥：`crypto_pwhash`（Argon2id，`OPSLIMIT_INTERACTIVE`/`MEMLIMIT_INTERACTIVE`
//   档位——在浏览器主线程同步计算，选偏交互式的强度参数以避免明显卡顿，design.md
//   Open Question 1 记录的取舍）。
// - 包裹/解包身份私钥：`crypto_secretbox_easy`/`crypto_secretbox_open_easy`（XSalsa20-Poly1305
//   AEAD，libsodium "secretbox" 是 `crypto_secretbox` 家族，与 design.md 枚举的备选之一一致）。
// - 会话密钥协商：`crypto_scalarmult`（X25519 ECDH，静态-静态，得到双方独立算出的同一个
//   共享密钥 `SK`）。
// - 消息密钥派生（HKDF-Expand 等效实现）：`libsodium-wrappers` 没有原生 HKDF-Expand API，
//   用 `crypto_generichash`（Blake2b）以 `SK` 作为 keyed-hash 的 key、把
//   `counter（8 字节大端）+ 发送方 userId（8 字节大端）` 拼接后的字节串作为输入做逐条派生：
//   Blake2b 在 keyed 模式下本身就是一个符合 RFC 5869 精神的伪随机函数，用 `SK` 当 key、
//   变长的“上下文信息”当输入，效果等价于 HKDF 的 Expand 步骤（HKDF 的 Extract 步骤在本场景
//   可以省略，因为 `SK` 已经是一次 X25519 运算产出的均匀分布密钥材料，不需要再抽取一次）。
//   额外把发送方 userId 编码进输入，是为了让"我发给对方"和"对方发给我"两个方向在同一个
//   `counter` 取值下也不会派生出相同的 messageKey（等价于从同一个根密钥拆出两条独立的
//   单向链），design.md 原文只提到"结合单调递增计数器"，这里是为了避免双向计数器碰撞对
//   前向保密目标做的一个必要实现细节补充。
// - 消息 AEAD 加解密：`crypto_aead_xchacha20poly1305_ietf_encrypt/decrypt`，24 字节随机 nonce。
// - 公钥指纹：`crypto_generichash`（20 字节输出，128-bit 强度对指纹用途足够，展示时更短），
//   按每 4 字节（8 个十六进制字符）分组，便于带外核对。

// 注意：必须用 default import，不能用 `import * as sodium`。libsodium-wrappers 的
// 各个 crypto_* 函数不是静态 ESM 具名导出，而是在 `sodium.ready` resolve 之后动态挂到
// default 导出的那个对象上（WASM 初始化完成后才知道实际可用的符号列表），命名空间导入
// 拿到的是构建时静态分析出的只读绑定视图，看不到运行时动态挂载的属性，会导致
// `sodium.crypto_box_keypair` 等在实际调用时是 `undefined`（vite build 也会对此发出
// "will always be undefined" 警告）。
import sodium from 'libsodium-wrappers'

// 身份密钥对（X25519）：publicKey 用于注册到服务端密钥目录，privateKey 全程只保存在
// 浏览器本地内存/IndexedDB（口令包裹后），不会以明文形式发送到任何地方。
export interface IdentityKeyPair {
  publicKey: Uint8Array
  privateKey: Uint8Array
}

// 口令包裹后的身份私钥材料，供 utils/chatKeyStore.ts 落 IndexedDB；字段全部是 Base64 字符串
// （IndexedDB 可以直接存 Uint8Array，但用 Base64 字符串更便于跨浏览器 IndexedDB 实现的兼容性
// 与调试可读性）。
export interface WrappedPrivateKey {
  salt: string
  opslimit: number
  memlimit: number
  nonce: string
  ciphertext: string
}

// 密文信封的棘轮头：counter 是本条消息在"发送方 -> 接收方"这个单向链上的序号（从 1 开始
// 单调递增），version 预留给未来升级到完整 Double Ratchet 时区分新旧棘轮逻辑
// （design.md Decision 3 演进路径）。
export interface RatchetHeader {
  counter: number
  version: 1
}

// 密文信封：塞进现有消息 content 字段传输的 JSON 结构（design.md Decision 1）。
// 注意：发送方身份公钥指纹（senderIdentityKeyFingerprint）不放在信封内部，而是作为
// ChatSingleFrameBody/ChatMessageVO 的同级字段传输——这是根据后端实际实现（并行开发，
// 见 backend ChatSingleFrameBody/ChatMessageVO 源码）核对后的结果，与 design.md 行文
// 描述（信封内含该字段）有出入，以后端已落地的真实契约为准。
export interface ChatCiphertextEnvelope {
  ciphertext: string
  nonce: string
  ratchetHeader: RatchetHeader
}

let readyPromise: Promise<typeof sodium> | null = null

// libsodium-wrappers 的 WASM 模块需要异步初始化完成后才能调用任何 crypto_* 函数；
// 本文件所有导出函数在真正使用 sodium API 前都会先 await 这个函数，调用方不需要
// 自己记得初始化时机。
export function ready(): Promise<typeof sodium> {
  if (!readyPromise) {
    readyPromise = sodium.ready.then(() => sodium)
  }
  return readyPromise
}

// Uint8Array <-> Base64 字符串互转，统一使用标准（带填充）变体，与后端
// `identityPublicKey VARCHAR(64)` 按原始字符串存储/比对的约定一致。
export function toBase64(bytes: Uint8Array): string {
  return sodium.to_base64(bytes, sodium.base64_variants.ORIGINAL)
}

export function fromBase64(base64: string): Uint8Array {
  return sodium.from_base64(base64, sodium.base64_variants.ORIGINAL)
}

// 生成一对全新的 X25519 身份密钥对
export async function generateIdentityKeyPair(): Promise<IdentityKeyPair> {
  await ready()
  const pair = sodium.crypto_box_keypair()
  return { publicKey: pair.publicKey, privateKey: pair.privateKey }
}

// 用登录密码派生包裹密钥后，对身份私钥做 AEAD 包裹，返回可直接落 IndexedDB 的结构
export async function wrapPrivateKey(privateKey: Uint8Array, password: string): Promise<WrappedPrivateKey> {
  await ready()
  const salt = sodium.randombytes_buf(sodium.crypto_pwhash_SALTBYTES)
  const opslimit = sodium.crypto_pwhash_OPSLIMIT_INTERACTIVE
  const memlimit = sodium.crypto_pwhash_MEMLIMIT_INTERACTIVE
  const wrapKey = sodium.crypto_pwhash(
    sodium.crypto_secretbox_KEYBYTES,
    password,
    salt,
    opslimit,
    memlimit,
    sodium.crypto_pwhash_ALG_DEFAULT,
  )
  const nonce = sodium.randombytes_buf(sodium.crypto_secretbox_NONCEBYTES)
  const ciphertext = sodium.crypto_secretbox_easy(privateKey, nonce, wrapKey)
  return {
    salt: toBase64(salt),
    opslimit,
    memlimit,
    nonce: toBase64(nonce),
    ciphertext: toBase64(ciphertext),
  }
}

// 用登录密码重新派生包裹密钥并解包出身份私钥；密码错误或数据损坏时 libsodium 内部的
// Poly1305 认证校验会失败，统一转换成一条明确的错误信息，调用方据此提示用户
export async function unwrapPrivateKey(wrapped: WrappedPrivateKey, password: string): Promise<Uint8Array> {
  await ready()
  const salt = fromBase64(wrapped.salt)
  const wrapKey = sodium.crypto_pwhash(
    sodium.crypto_secretbox_KEYBYTES,
    password,
    salt,
    wrapped.opslimit,
    wrapped.memlimit,
    sodium.crypto_pwhash_ALG_DEFAULT,
  )
  const nonce = fromBase64(wrapped.nonce)
  const ciphertext = fromBase64(wrapped.ciphertext)
  try {
    return sodium.crypto_secretbox_open_easy(ciphertext, nonce, wrapKey)
  } catch {
    throw new Error('密码错误或本地聊天密钥数据已损坏，无法解锁')
  }
}

// X25519 ECDH：用自己的身份私钥 + 对方身份公钥算出双方共享的会话密钥 SK
// （静态-静态 ECDH，design.md Decision 3）
export async function computeSharedSecret(myPrivateKey: Uint8Array, theirPublicKey: Uint8Array): Promise<Uint8Array> {
  await ready()
  return sodium.crypto_scalarmult(myPrivateKey, theirPublicKey)
}

// 把 (counter, senderUserId) 编码成 16 字节的大端字节串，作为 HKDF-Expand 等效派生的
// "上下文信息"输入
function encodeRatchetContext(counter: number, senderUserId: number): Uint8Array {
  const buffer = new ArrayBuffer(16)
  const view = new DataView(buffer)
  view.setBigUint64(0, BigInt(counter), false)
  view.setBigUint64(8, BigInt(senderUserId), false)
  return new Uint8Array(buffer)
}

// 基于共享密钥 SK + 单调递增计数器 + 发送方 userId，派生本条消息专用的一次性 messageKey；
// 用后即弃（不做任何缓存），提供"单条消息 key 泄露不影响其他消息"的前向保密
export async function deriveMessageKey(
  sharedSecret: Uint8Array,
  counter: number,
  senderUserId: number,
): Promise<Uint8Array> {
  await ready()
  const context = encodeRatchetContext(counter, senderUserId)
  return sodium.crypto_generichash(sodium.crypto_aead_xchacha20poly1305_ietf_KEYBYTES, context, sharedSecret)
}

// 用一次性 messageKey 加密明文，nonce 随机生成，返回 Base64 编码的 nonce/ciphertext
export async function encryptMessage(
  key: Uint8Array,
  plaintext: string,
): Promise<{ nonce: string; ciphertext: string }> {
  await ready()
  const nonce = sodium.randombytes_buf(sodium.crypto_aead_xchacha20poly1305_ietf_NPUBBYTES)
  const ciphertext = sodium.crypto_aead_xchacha20poly1305_ietf_encrypt(
    sodium.from_string(plaintext),
    null,
    null,
    nonce,
    key,
  )
  return { nonce: toBase64(nonce), ciphertext: toBase64(ciphertext) }
}

// 用对应的一次性 messageKey 解密；AEAD 认证失败（密文损坏/密钥不匹配）时 libsodium 内部
// 抛出异常，统一转换为一条明确的错误信息
export async function decryptMessage(key: Uint8Array, nonceBase64: string, ciphertextBase64: string): Promise<string> {
  await ready()
  const nonce = fromBase64(nonceBase64)
  const ciphertext = fromBase64(ciphertextBase64)
  try {
    const plaintext = sodium.crypto_aead_xchacha20poly1305_ietf_decrypt(null, ciphertext, null, nonce, key)
    return sodium.to_string(plaintext)
  } catch {
    throw new Error('消息解密失败：密文已损坏或密钥不匹配')
  }
}

// 公钥指纹计算：对 Base64 公钥解码后的原始字节做 Blake2b 摘要（20 字节，160-bit），
// 按每 4 字节（8 个十六进制字符）分组、大写展示，便于带外核对（design.md Decision 4）
export async function computeFingerprint(publicKeyBase64: string): Promise<string> {
  await ready()
  const publicKey = fromBase64(publicKeyBase64)
  const digest = sodium.crypto_generichash(20, publicKey, null)
  return formatFingerprint(digest)
}

function formatFingerprint(bytes: Uint8Array): string {
  const hex = Array.from(bytes)
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('')
  const groups: string[] = []
  for (let i = 0; i < hex.length; i += 8) {
    groups.push(hex.slice(i, i + 8))
  }
  return groups.join(' ').toUpperCase()
}

// 密文信封序列化：直接 JSON.stringify，塞进现有消息 content 字段传输
export function serializeEnvelope(envelope: ChatCiphertextEnvelope): string {
  return JSON.stringify(envelope)
}

// 密文信封反序列化 + 最基本的结构校验；格式不对时抛出明确错误，调用方据此展示
// "消息无法解密"占位提示而不是崩溃
export function deserializeEnvelope(json: string): ChatCiphertextEnvelope {
  let parsed: unknown
  try {
    parsed = JSON.parse(json)
  } catch {
    throw new Error('密文信封不是合法的 JSON')
  }
  const envelope = parsed as Partial<ChatCiphertextEnvelope>
  if (
    typeof envelope.ciphertext !== 'string' ||
    typeof envelope.nonce !== 'string' ||
    typeof envelope.ratchetHeader?.counter !== 'number'
  ) {
    throw new Error('密文信封字段缺失或格式不正确')
  }
  return envelope as ChatCiphertextEnvelope
}
