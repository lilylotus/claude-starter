## 1. 数据库

- [x] 1.1 在 `backend/src/main/resources/db/migration/V1__init_schema.sql` 新增
      `tab_app_auth_config` 表（design.md Decision 1 的建表 SQL），紧邻 `tab_app_config`
      定义之后。

## 2. 后端：基础结构

- [x] 2.1 新增 `cn.nihility.rbac.app.authconfig.constant.AuthProtocol`（`NONE`/`CAS`/
      `OAUTH2` 字符串常量）。
- [x] 2.2 新增 `cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity`（对应
      `tab_app_auth_config`，`appRefId` 映射 `app_id` 列，其余字段含 `authProtocol`/
      `casServicePatterns`/`oauth2RedirectUriPatterns`（落库为 JSON 文本，实体字段类型为
      `String`）+ 四个审计字段）。
- [x] 2.3 新增 `cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper extends
      BaseMapper<AppAuthConfigEntity>`。

## 3. 后端：DTO 与转换

- [x] 3.1 新增 `cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO`：`authProtocol`、
      `casServicePatterns: List<String>`、`oauth2RedirectUriPatterns: List<String>`、
      `casLoginUrl`/`casServiceValidateUrl`/`casLogoutUrl`/`oauthAuthorizeUrl`/
      `oauthTokenUrl`/`oauthUserInfoUrl`（均为 `String`），补充 springdoc `@Schema` 注解。
- [x] 3.2 新增 `cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest`：
      `authProtocol`（`@NotBlank`）、`casServicePatterns: List<String>`、
      `oauth2RedirectUriPatterns: List<String>`。
- [x] 3.3 新增 `cn.nihility.rbac.app.authconfig.mapstruct.AppAuthConfigConvert`（静态单例
      写法，`Xxx.INSTANCE`，不用 `componentModel = "spring"`，对齐
      `cn.nihility.rbac.org.mapstruct.OrgConvert` 的既有约定）：只映射 `authProtocol`，
      两个 `List<String>` 字段与 6 个只读 URL 字段在 service 层手动填充（design.md
      Decision 2）。

## 4. 后端：Service

- [x] 4.1 新增 `cn.nihility.rbac.app.authconfig.service.AppAuthConfigService` 接口：
      `createDefaultConfig(Long appRefId, String operator)`、
      `AppAuthConfigVO getByAppId(Long appRefId)`、
      `AppAuthConfigVO updateConfig(Long appRefId, AppAuthConfigUpdateRequest request)`。
- [x] 4.2 实现 `AppAuthConfigServiceImpl.createDefaultConfig`：插入一行
      `authProtocol=NONE`、两个匹配列表落库为 `[]` 的默认记录。
- [x] 4.3 实现 `AppAuthConfigServiceImpl.getByAppId`：查不到记录时懒创建默认行后返回
      （design.md Migration Plan），查到记录时按 Decision 4 的路径模板 + 该应用
      `AppConfigEntity.appId` 计算 6 个只读 URL 一并返回。
- [x] 4.4 实现 `AppAuthConfigServiceImpl.updateConfig`：
      - 复用 `AppScopeGuard.getExistingAppInScope` 做管辖组织范围校验（查不到/无权限时
        拒绝，对应 spec.md "无管辖权限时修改被拒绝" 场景）。
      - 按 design.md Decision 3 的条件校验规则处理 `authProtocol`
        与两个匹配列表的关联关系（CAS/OAUTH2 各自要求对应列表非空且清空另一个列表，NONE
        清空两个列表），规则做 trim + 去空白 + 去重后再保存。
      - 保存后通过 `OperationLogRecorder.recordUpdate` 记录操作日志（资源类型复用
        `OperationLogResourceType.APP`，快照字段：协议类型、CAS service 匹配列表、
        OAuth2 redirect_uri 匹配列表）。
- [x] 4.5 在 `AppConfigServiceImpl` 注入 `AppAuthConfigService`，`createDefaultConfig`
      方法内新增一行 `appAuthConfigService.createDefaultConfig(appRefId, operator)`，与
      现有 `appSyncConfigService.createDefaultDomainConfigs(...)` 调用并列。

## 5. 后端：Controller

- [x] 5.1 新增 `cn.nihility.rbac.app.authconfig.controller.AppAuthConfigController`：
      `GET /api/apps/{id}/config/auth`、`PUT /api/apps/{id}/config/auth`（路径实现时改为
      `config/auth`，对齐 `AppSyncConfigController` 的 `config/sync/**` 前缀风格，见
      design.md Decision 2 更新说明），补充 springdoc `@Tag`/`@Operation` 注解。

## 6. 后端：测试

- [x] 6.1 `AppAuthConfigServiceImplTest`：覆盖 spec.md 全部场景——新建应用自动生成默认
      认证配置、查询已配置 CAS 协议的应用、无管辖权限时修改被拒绝、修改成功记录操作日志、
      选择 CAS/OAuth2.0 但未提供匹配规则被拒绝、协议类型改回"无"时清空历史匹配列表、
      查不到记录时懒创建默认行。
- [x] 6.2 `./gradlew test --tests "cn.nihility.rbac.app.authconfig.*"` 确认新增测试通过；
      `./gradlew build` 确认全量编译 + 测试通过。

## 7. 前端

- [x] 7.1 新增 `frontend/src/types/app.ts` 里的 `AuthProtocol`/`AppAuthConfigVO`/
      `AppAuthConfigUpdateRequest` 类型定义（design.md Decision 6，不新开类型文件）。
- [x] 7.2 `getAppAuthConfig`/`updateAppAuthConfig` 两个请求封装实现时并入既有
      `frontend/src/api/app.ts`（而非独立新文件，见 design.md Decision 6 更新说明）。
- [x] 7.3 `AppConfigView.vue`：`activeTab` 类型扩为 `'basic' | 'sync' | 'auth'`，新增
      "认证管理" `el-tab-pane`：
      - 顶部 `el-alert` 提示"当前仅支持协议配置维护，协议运行时接口尚未开放"
        （design.md Risks）。
      - 协议 `el-select`（无/CAS/OAuth2.0）。
      - CAS 分支：service 匹配规则行编辑（增删），只读展示 CAS 三个协议接口地址。
      - OAuth2.0 分支：redirect_uri 匹配规则行编辑（增删），只读展示 OAuth2 三个协议
        接口地址 + 授权接口参数说明表格（`response_type`/`client_id`/`redirect_uri`/
        `scope`/`state`，静态文案）。
      - 保存按钮受 `hasPermission('AppManagement:app:config:editAuth')` 控制。
      - 实现后端到端验证时发现 `label-width="110px"`（沿用其他 tab 的既有值）下
        "service 匹配列表"/"redirect_uri 匹配列表"标签换行，改为该表单单独用
        `label-width="150px"`。
- [x] 7.4 `npm run build`（`frontend/` 目录下）确认 vue-tsc 类型检查 + vite build 通过。

## 9. 端到端验证

- [x] 9.1 用 Playwright 驱动真实浏览器，起 `./gradlew bootRun` + `npm run dev` 联调，
      登录后新建一个应用，走完整流程验证 spec.md 全部场景：默认协议"无"、切到 CAS 后
      不填规则保存被前端拦截、填一条规则保存成功且刷新页面后仍在、CAS 三个协议接口地址
      正确显示应用 AppId、切到 OAuth2.0 后 redirect_uri 匹配列表 + 参数说明表格正常、
      切回"无"保存后两个匹配列表清空且刷新后仍为空；全程浏览器控制台与网络请求无报错。

## 8. 权限点与文档

- [x] 8.1 `权限资源.txt` 新增 `AppManagement:app:config:editAuth` 一行，说明文字对齐
      `editSync` 现有格式。
- [x] 8.2 若项目存在权限点种子数据脚本（迁移脚本内的 `tab_permission` 初始化数据/管理端
      权限维护页面新增记录方式，视实现时项目现状而定），同步补充该权限点，确保新增的
      `editAuth` 权限点可以在角色管理里被实际勾选授权。
