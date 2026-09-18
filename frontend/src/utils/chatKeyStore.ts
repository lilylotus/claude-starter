// 单聊端到端加密本地密钥仓库：用浏览器原生 IndexedDB（无需额外依赖）存放三类数据，
// 全部按"当前登录用户账号编码"（ownerKey）分区，避免同一浏览器多账号互相覆盖
// （design.md Decision 2/4，tasks.md 4.3）。
//
// 这里用账号编码（`tab_user.code`，即 stores/auth.ts 的 `accountCode`）而不是数值型
// `userId` 作为分区键：REST 登录响应（POST /api/auth/login）不携带数值 userId，数值 userId
// 只有在 WebSocket 网关认证成功（LOGIN_ACK）后才能拿到，而"登录成功后立即解锁/生成聊天
// 身份密钥"这一步必须在此之前完成（不依赖用户先进入聊天页面）；账号编码在登录成功那一刻
// 就已确定、跨会话稳定、同一浏览器内唯一，作为本地分区键同样满足"不同账号互不覆盖"的
// 需求。
//
// 三类数据：
// 1. identityKeys：口令包裹后的本地身份私钥材料（design.md Decision 2）。
// 2. trustedFingerprints：TOFU 已确认信任的对方公钥指纹（design.md Decision 4）。
// 3. sendCounters：向每个对方发送消息时使用的单调递增棘轮计数器（design.md Decision 3）。
//    持久化的原因：如果只存在内存里，浏览器刷新/重新登录后计数器会归零，导致用同一个
//    counter 重新派生出和历史某条消息完全相同的 messageKey，破坏"单条消息 key 泄露不
//    影响其他消息"的前向保密目标。

import type { WrappedPrivateKey } from './chatCrypto'

const DB_NAME = 'rbac-chat-e2e'
const DB_VERSION = 1
const STORE_IDENTITY_KEYS = 'identityKeys'
const STORE_TRUSTED_FINGERPRINTS = 'trustedFingerprints'
const STORE_SEND_COUNTERS = 'sendCounters'

// 落库的身份私钥材料：口令包裹结果（WrappedPrivateKey）+ 对应的身份公钥（Base64，
// 一并存放方便"本地已存在密钥材料"分支直接拿到公钥，不需要额外从私钥重新推导）
export type StoredIdentityKeyMaterial = WrappedPrivateKey & { publicKey: string }

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION)
    request.onupgradeneeded = () => {
      const db = request.result
      if (!db.objectStoreNames.contains(STORE_IDENTITY_KEYS)) {
        db.createObjectStore(STORE_IDENTITY_KEYS, { keyPath: 'ownerKey' })
      }
      if (!db.objectStoreNames.contains(STORE_TRUSTED_FINGERPRINTS)) {
        db.createObjectStore(STORE_TRUSTED_FINGERPRINTS, { keyPath: 'id' })
      }
      if (!db.objectStoreNames.contains(STORE_SEND_COUNTERS)) {
        db.createObjectStore(STORE_SEND_COUNTERS, { keyPath: 'id' })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error ?? new Error('打开本地聊天密钥数据库失败'))
  })
}

// 通用的单次事务封装：打开一个 store，执行一次读/写请求并 resolve 其结果
function runRequest<T>(
  storeName: string,
  mode: IDBTransactionMode,
  executor: (store: IDBObjectStore) => IDBRequest,
): Promise<T> {
  return openDb().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const tx = db.transaction(storeName, mode)
        const store = tx.objectStore(storeName)
        const req = executor(store)
        req.onsuccess = () => resolve(req.result as T)
        req.onerror = () => reject(req.error ?? new Error('本地聊天密钥数据库操作失败'))
      }),
  )
}

function compositeId(ownerKey: string, counterpartUserId: number): string {
  return `${ownerKey}:${counterpartUserId}`
}

// ---- 身份密钥材料 ----

export async function loadIdentityKeyMaterial(ownerKey: string): Promise<StoredIdentityKeyMaterial | null> {
  const record = await runRequest<({ ownerKey: string } & StoredIdentityKeyMaterial) | undefined>(
    STORE_IDENTITY_KEYS,
    'readonly',
    (store) => store.get(ownerKey),
  )
  return record ?? null
}

export async function saveIdentityKeyMaterial(
  ownerKey: string,
  material: StoredIdentityKeyMaterial,
): Promise<void> {
  await runRequest(STORE_IDENTITY_KEYS, 'readwrite', (store) => store.put({ ownerKey, ...material }))
}

// ---- TOFU 已信任的对方公钥指纹 ----

export async function getTrustedFingerprint(ownerKey: string, counterpartUserId: number): Promise<string | null> {
  const record = await runRequest<{ fingerprint: string } | undefined>(
    STORE_TRUSTED_FINGERPRINTS,
    'readonly',
    (store) => store.get(compositeId(ownerKey, counterpartUserId)),
  )
  return record?.fingerprint ?? null
}

export async function setTrustedFingerprint(
  ownerKey: string,
  counterpartUserId: number,
  fingerprint: string,
): Promise<void> {
  await runRequest(STORE_TRUSTED_FINGERPRINTS, 'readwrite', (store) =>
    store.put({
      id: compositeId(ownerKey, counterpartUserId),
      ownerKey,
      counterpartUserId,
      fingerprint,
      updatedAt: Date.now(),
    }),
  )
}

// ---- 发送方向的棘轮计数器 ----

// 取下一个可用的发送计数器（从 1 开始单调递增）并持久化，同一 (ownerKey, counterpartUserId)
// 组合永不重复取到同一个值
export async function nextSendCounter(ownerKey: string, counterpartUserId: number): Promise<number> {
  const id = compositeId(ownerKey, counterpartUserId)
  const current = await runRequest<{ counter: number } | undefined>(STORE_SEND_COUNTERS, 'readonly', (store) =>
    store.get(id),
  )
  const next = (current?.counter ?? 0) + 1
  await runRequest(STORE_SEND_COUNTERS, 'readwrite', (store) =>
    store.put({ id, ownerKey, counterpartUserId, counter: next }),
  )
  return next
}
