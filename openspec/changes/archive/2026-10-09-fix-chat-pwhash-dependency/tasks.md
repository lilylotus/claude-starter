## 1. 依赖替换

- [x] 1.1 `frontend/package.json`：移除 `libsodium-wrappers`、`@types/libsodium-wrappers`，
  新增 `libsodium-wrappers-sumo`、`@types/libsodium-wrappers-sumo`
  （实际做法：最终 `package.json` 只保留了 `libsodium-wrappers-sumo`，没有装
  `@types/libsodium-wrappers-sumo`——`npm install` 时该 `@types` 包自己报
  "This is a stub types definition. libsodium-wrappers-sumo provides its own type
  definitions, so you do not need this installed."，于是卸载了它；`npm run build`
  的 `vue-tsc` 类型检查确认 `libsodium-wrappers-sumo` 自带的
  `dist/modules-sumo/libsodium-wrappers.d.ts` 足够覆盖 `chatCrypto.ts` 的全部用法，
  不需要额外的 `@types` 包）
- [x] 1.2 `cd frontend && npm install` 更新 `package-lock.json`
- [x] 1.3 用 Node 直接验证新装的包确实具备 `crypto_pwhash`：
  `crypto_pwhash_SALTBYTES: 16`、`typeof crypto_pwhash: 'function'`，其它既有用到的
  API（`crypto_box_keypair`/`crypto_secretbox_easy`/`crypto_scalarmult`/
  `crypto_generichash`/`crypto_aead_xchacha20poly1305_ietf_encrypt`）同样全部是
  `function`

## 2. 代码改动

- [x] 2.1 `frontend/src/utils/chatCrypto.ts`：`import sodium from 'libsodium-wrappers'`
  改为 `import sodium from 'libsodium-wrappers-sumo'`，文件顶部关于"必须用 default
  import"的注释保留（对 sumo 版本同样适用），并补充一段注释说明"必须用 sumo 版本、
  不能用精简版"的原因，文件其它代码不变
- [x] 2.2 检查仓库内是否还有其它文件直接引用 `libsodium-wrappers`（而不是经由
  `chatCrypto.ts` 这一层封装）——`grep -rl "libsodium-wrappers" frontend/src/` 只命中
  `chatCrypto.ts` 自身，无需改动其它文件

## 3. 文档修正

- [x] 3.1 `openspec/specs/chat-e2e-encryption/spec.md`"身份密钥生成与本地口令包裹存储"
  Requirement 里的"AES-GCM"改为"XSalsa20-Poly1305（`crypto_secretbox`）"，与归档 change
  design.md Decision 2 记录的实际实现一致

## 4. 验证

- [x] 4.1 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）通过；`chat`
  相关 chunk 体积从 449.41 kB 增至 550.95 kB（gzip 159.10 kB → 195.38 kB），与
  proposal.md 预告的"sumo 版体积更大"一致，属于预期代价
- [ ] 4.2 真实联调验证（需要用户在本地连通后端 + MySQL/Redis 的环境下执行，本次未做）：
  账号首次登录触发"生成新身份密钥"路径不再报错，能看到"已为你生成新的密钥"提示；该账号
  再次登出重新登录触发"解锁已有密钥"路径也不再报错；两个账号之间能正常发送/接收单聊消息
  （密文信封加解密成功）
