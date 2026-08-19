## Context

见 proposal.md - Why。现状（`cn.nihility.rbac.app.authconfig.*` / `cn.nihility.rbac.sso.support.AppProtocolGuard`）：

- `tab_app_auth_config` 有 `cas_service_patterns`、`oauth2_redirect_uri_patterns` 两个
  JSON 字符串数组文本列，`AppAuthConfigEntity`/`AppAuthConfigVO`/`AppAuthConfigUpdateRequest`
  各自维护两个同构字段（`List<String>`，落库前 `JacksonUtils.toJson`，取出后
  `JacksonUtils.toObj`）。
- `AppAuthConfigServiceImpl.updateConfig`：保存时按 `authProtocol` 决定"两列中哪一列写
  真实值、哪一列写空列表"（`entity.setCasServicePatterns(... ? casServicePatterns :
  List.of())`，OAuth2 那列同理），`assertProtocolPatternsValid` 按协议类型分别校验对应
  列非空。
- `AppProtocolGuard.assertCasServiceAllowed`/`assertOAuthRedirectUriAllowed`/
  `assertLogoutServiceAllowed` 三个方法都先确认协议类型，再各自 `parsePatterns` 对应的列
  做 ANT 匹配；`assertLogoutServiceAllowed` 已经出现"按协议类型 if/else 选列"的分支
  （add-sso-single-logout change 引入），是本次要消除的重复模式的直接例证。
- 前端 `AppConfigView.vue` 认证管理表单：`casPatternRows`/`oauth2PatternRows` 两个独立
  的 `ref<string[]>`，`applyAuthConfig`/`saveAuthConfig` 分别读写。

## Goals / Non-Goals

**Goals:**
- 用单一 `servicePatterns` 字段替换两个协议专属字段，贯穿数据库列、entity/VO/
  UpdateRequest、`AppProtocolGuard` 校验、前端表单状态。
- 保持现有 ANT 匹配行为、"协议启用时至少一条规则""协议为无时清空"等既有校验语义不变，
  只变字段形状，不变行为。
- 迁移现有数据：按当前 `auth_protocol` 把两列中"当前协议对应的那一列"的值迁移进新列。

**Non-Goals:**
- 不在本次引入新的单点登录协议（OIDC/SAML 等）本身，只是为"将来加协议不用再加列"打基础。
- 不改变 ANT 匹配算法/库（仍用 `AntPathMatcher`）。
- 不改变"协议类型互斥"的既有约束（一个应用同一时刻只能是 NONE/CAS/OAUTH2 之一，
  `servicePatterns` 语义上永远是"当前生效协议"的匹配列表，不是"CAS 列表 + OAuth2 列表
  的并集"）。

## Decisions

### 1. 新列名 `service_patterns`，类型/JSON 编码方式与旧两列保持一致

新列沿用旧两列在 `db/migration/V1__init_schema.sql` 里的既有约定：`TEXT DEFAULT NULL`、
存 JSON 字符串数组文本，entity 侧仍是 `private String servicePatterns`（不是
`List<String>`，与现有"落库存文本、读写时经 `JacksonUtils` 互转"的模式一致，避免引入新的
存储范式）。

### 2. 迁移策略：新增列 + 按协议类型回填 + 删除旧两列，三步在同一个迁移脚本完成

```sql
ALTER TABLE tab_app_auth_config ADD COLUMN service_patterns TEXT NULL
    COMMENT '回跳地址 ANT 匹配规则列表（JSON 字符串数组），CAS/OAuth2.0 等协议共用'
    AFTER oauth2_redirect_uri_patterns;

UPDATE tab_app_auth_config
SET service_patterns = CASE auth_protocol
    WHEN 'CAS' THEN cas_service_patterns
    WHEN 'OAUTH2' THEN oauth2_redirect_uri_patterns
    ELSE '[]'
END;

ALTER TABLE tab_app_auth_config DROP COLUMN cas_service_patterns;
ALTER TABLE tab_app_auth_config DROP COLUMN oauth2_redirect_uri_patterns;
```

三条语句都是标准可移植 SQL（`CASE` 表达式、`ALTER TABLE ADD/DROP COLUMN`），MySQL 5.7
原生支持，不涉及窗口函数/CTE，符合项目对手写 SQL 的可移植性约束。

*备选方案：只新增列，保留旧两列不删（避免一次迁移做两件事）。* 放弃原因：旧两列保留会
让"读旧列还是新列"的歧义长期存在，且 entity 层已经要整体切换到单字段，保留旧列没有实际
读者，纯粹增加维护负担，不如一次迁移到位。

### 3. `AppProtocolGuard` 三个校验方法收敛为"先定协议类型、再统一读 `servicePatterns`"

`assertCasServiceAllowed(appId, service)`/`assertOAuthRedirectUriAllowed(clientId,
redirectUri)`/`assertLogoutServiceAllowed(appId, service)` 各自仍保留"校验协议类型是否
匹配调用方期望的协议"这一层（CAS 方法要求 `authProtocol=CAS`，OAuth2 方法要求
`authProtocol=OAUTH2`，登出方法按当前协议类型分派要求哪种），但读取匹配列表的代码从
"按协议选列"简化为直接 `parsePatterns(authConfig.getServicePatterns())` 一行，
`assertLogoutServiceAllowed` 里原有的 if/else 选列分支整体删除（协议类型校验分支还在，
只是不再需要额外选择"读哪个字段"）。

### 4. 前端表单：两个 `ref<string[]>` 合并为一个，编辑区文案改为协议无关表述

`casPatternRows`/`oauth2PatternRows` 合并为单一 `servicePatternRows`，
`applyAuthConfig`/`saveAuthConfig` 相应只读写一份；模板里 CAS/OAuth2 两个 `<template
v-if>` 分支内原本各自的"service 匹配列表"/"redirect_uri 匹配列表" `el-form-item` 合并成
协议类型分支外层共用的一个"回跳地址匹配列表"编辑区（放在协议类型下拉之后、协议专属的只读
接口地址展示之前，与当前"登出通知回调地址"字段类似的位置模式：协议无关字段统一放在协议
分支外层）。

## Risks / Trade-offs

- [BREAKING：接口请求/响应字段名变化] → `AppAuthConfigVO`/`AppAuthConfigUpdateRequest`
  的 `casServicePatterns`/`oauth2RedirectUriPatterns` 字段消失，改为 `servicePatterns`；
  由于该配置接口目前只有本项目前端一个调用方（无外部第三方直接调用管理端 API 的已知场景），
  前后端在同一个 change 里同步改造，不需要兼容期。
- [迁移脚本对 `auth_protocol` 为历史脏数据（非 NONE/CAS/OAUTH2）的行为] → `CASE` 表达式
  的 `ELSE '[]'` 分支兜底，任何非 CAS/OAUTH2 的取值（含理论上不应存在的脏数据）都迁移为
  空列表，不会迁移出混合了两个协议规则的数据，符合"servicePatterns 只代表当前生效协议"
  的语义。
- [回滚] → 若迁移后发现问题，回滚需要新增一个反向迁移脚本（把 `service_patterns` 按
  `auth_protocol` 拆回两列），Flyway 不支持自动回滚已应用的迁移；本次改动范围小、有
  完整测试覆盖，按项目现有迁移历史的一贯做法（历次 breaking 迁移均未设计自动回滚脚本），
  不额外增加回滚脚本，出问题时按需人工新开一个后续迁移修正。
