## 1. 数据库迁移

- [x] 1.1 新增 Flyway 迁移脚本 `V4__unify_app_auth_service_patterns.sql`：
  `ALTER TABLE tab_app_auth_config ADD COLUMN service_patterns TEXT DEFAULT NULL` +
  按 `auth_protocol`（`CASE` 表达式）把 `cas_service_patterns`/
  `oauth2_redirect_uri_patterns` 中当前协议对应的一列数据迁移进 `service_patterns`
  （`ELSE '[]'`）+ `ALTER TABLE ... DROP COLUMN cas_service_patterns` +
  `DROP COLUMN oauth2_redirect_uri_patterns`（MySQL 5.7 兼容写法，见 design.md
  Decision 2）。

## 2. 后端：entity/VO/UpdateRequest/MapStruct

- [x] 2.1 `AppAuthConfigEntity`：`casServicePatterns`/`oauth2RedirectUriPatterns` 两个
  字段替换为单一 `servicePatterns`。
- [x] 2.2 `AppAuthConfigVO`：同上字段替换；`AppAuthConfigUpdateRequest`：同上字段替换
  （沿用原有的可选/校验注解风格）。
- [x] 2.3 `AppAuthConfigConvert`（MapStruct）：更新 `@Mapping(target = ...,
  ignore = true)` 列表，`servicePatterns` 走自动同名映射（entity/VO 都仍是 JSON 文本
  字符串，不需要手写转换）。

## 3. 后端：service 层校验与保存逻辑

- [x] 3.1 `AppAuthConfigServiceImpl.createDefaultConfig`：默认值从两个空列表
  （`casServicePatterns`/`oauth2RedirectUriPatterns` 均 `JacksonUtils.toJson(List.of())`）
  改为单一 `servicePatterns` 空列表。
- [x] 3.2 `AppAuthConfigServiceImpl.updateConfig`：保存逻辑从"按协议类型决定写哪一列"
  简化为直接把规范化后的 `servicePatterns` 整体落库（协议类型为 NONE 时清空）。
- [x] 3.3 `AppAuthConfigServiceImpl.assertProtocolPatternsValid`：两条按协议分别校验的
  规则合并为一条——协议类型为 CAS 或 OAUTH2 时 `servicePatterns` 至少一条规则。
- [x] 3.4 `toVO`/`parsePatterns` 等辅助方法同步只处理一份 `servicePatterns`。
- [x] 3.5 `toLogSnapshot`（操作日志字段快照）：两个匹配列表字段合并为一个
  "回跳地址匹配列表"字段。

## 4. 后端：AppProtocolGuard

- [x] 4.1 `assertCasServiceAllowed`：读取匹配列表的代码改为
  `parsePatterns(authConfig.getServicePatterns())`，协议类型校验逻辑不变。
- [x] 4.2 `assertOAuthRedirectUriAllowed`：同上。
- [x] 4.3 `assertLogoutServiceAllowed`：删除原有"按协议类型 if/else 选列"分支，统一读
  `servicePatterns`，只保留"协议类型是否为 NONE"的拒绝分支。

## 5. 前端

- [x] 5.1 `frontend/src/types/app.ts`：`AppAuthConfigVO`/`AppAuthConfigUpdateRequest`
  的 `casServicePatterns`/`oauth2RedirectUriPatterns` 合并为 `servicePatterns:
  string[]`。
- [x] 5.2 `AppConfigView.vue`：`casPatternRows`/`oauth2PatternRows` 合并为单一
  `servicePatternRows`；`applyAuthConfig`/`saveAuthConfig` 相应只读写一份；模板里
  CAS/OAuth2 两个协议分支内原本各自的匹配列表编辑区合并为协议类型下拉之后的一个协议无关
  的"回跳地址匹配列表"编辑区（同 design.md Decision 4 的布局位置）。
- [x] 5.3 前端本地校验函数（`validateAuthConfig` 里"协议为 CAS/OAuth2.0 时匹配列表至少
  一条"的判断）同步改为只判断 `servicePatternRows`。

## 6. 文档同步

- [x] 6.1 更新根目录 `SSO单点登录接入规范.md` 中涉及"CAS service 匹配列表"/"OAuth2
  redirect_uri 匹配列表"两个独立字段名的描述，统一改为 `servicePatterns`（回跳地址匹配
  列表）。

## 7. 测试

- [x] 7.1 `AppAuthConfigServiceImplTest`：默认配置生成、查询、保存、协议类型与匹配列表
  关联校验相关用例改为围绕单一 `servicePatterns` 断言；补充"协议类型从 CAS 切换为
  OAuth2.0 时沿用同一份 `servicePatterns` 存储、整体替换"的用例。
- [x] 7.2 `AppProtocolGuardTest`：`assertCasServiceAllowed`/`assertOAuthRedirectUriAllowed`/
  `assertLogoutServiceAllowed` 相关用例的测试数据 seed 方法改为写入 `servicePatterns`
  单列。
- [x] 7.3 `CasControllerTest`/`OAuthControllerTest`/`SsoLogoutControllerTest`：涉及
  seed 认证配置数据的地方同步改为单列写法。
- [x] 7.4 迁移脚本验证：起真实 MySQL 连接，验证 `V4__*.sql` 对存量 CAS/OAuth2.0/NONE
  三种协议类型的行都能正确迁移到 `service_patterns`（可复用现有"真实 MySQL 连接"测试
  风格，或在 `./gradlew build` 的 Flyway 启动校验里间接验证 schema 变更本身不报错，
  另外手工核对一次迁移后数据）。
