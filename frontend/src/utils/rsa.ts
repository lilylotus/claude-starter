// 浏览器原生 Web Crypto API 实现 RSA-OAEP（SHA-256）加密，不引入第三方加密库（jsencrypt
// 等），与后端 `RsaJdkUtils`（算法 `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`）严格对应。
// 注意：crypto.subtle 仅在安全上下文（HTTPS 或 localhost）可用，与 `npm run dev` 默认地址
// http://localhost:5173 一致，生产环境需部署在 HTTPS 下。

// Base64 字符串转 ArrayBuffer（而非 Uint8Array：TS DOM 类型定义下 crypto.subtle 系列 API
// 要求严格的 ArrayBuffer，直接传 Uint8Array<ArrayBufferLike> 会因 buffer 类型不兼容报错）
function base64ToBytes(base64: string): ArrayBuffer {
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

// ArrayBuffer 转 Base64 字符串
function bufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer)
  let binary = ''
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return btoa(binary)
}

/**
 * 用 Base64 编码的 RSA 公钥（X.509 SPKI DER 格式）对明文做 RSA-OAEP（SHA-256）加密。
 * @param publicKeyBase64 后端 `GET /api/auth/public-key` 返回的 Base64 公钥
 * @param plainText 待加密的明文（账号或密码）
 * @returns Base64 编码的密文
 */
export async function rsaEncrypt(publicKeyBase64: string, plainText: string): Promise<string> {
  const keyDer = base64ToBytes(publicKeyBase64)
  const cryptoKey = await window.crypto.subtle.importKey(
    'spki',
    keyDer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt'],
  )
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    cryptoKey,
    new TextEncoder().encode(plainText),
  )
  return bufferToBase64(encrypted)
}
