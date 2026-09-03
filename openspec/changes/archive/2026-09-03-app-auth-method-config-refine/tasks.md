## 1. 后端：放宽 loginMethods 校验规则

- [x] 1.1 `backend/src/main/java/cn/nihility/rbac/app/authconfig/service/impl/AppAuthConfigServiceImpl.java`
      `normalizeLoginMethods`：去掉强制补齐 `PASSWORD` 的 `result.add(LoginMethod.PASSWORD)`，
      改为仅做取值范围校验 + 去重，结果集合允许为空（代表未配置，不再拒绝、不再报错）；
      同步更新方法上方的 Javadoc（不再是"恒定包含 PASSWORD"，改为说明空列表按未配置处理）。
- [x] 1.2 确认新建应用默认认证配置初始化处（约第 93 行 `.loginMethods(JacksonUtils.toJson(List.of(LoginMethod.PASSWORD)))`）
      保持不变，仅确认无需跟随本次改动调整。
- [x] 1.3 确认 `parseLoginMethods`（`AppAuthConfigServiceImpl` 约第 289-294 行、
      `SsoLoginContextResolver` 约第 105-112 行）两处"解析结果为 `null`/空列表时返回
      `List.of(PASSWORD)`"的回退逻辑无需改动，本身已覆盖"显式保存空列表"场景。
- [x] 1.4 单元测试补充：提交空数组、提交全部为非法值被过滤后为空两种场景验证保存成功且
      查询回退为 `["PASSWORD"]`；提交不含 `PASSWORD` 但含 `SMS`/`QRCODE` 的非空组合场景
      验证保存成功且查询原样返回该组合（不再补齐口令）。

## 2. 前端：应用配置页认证方式勾选交互

- [x] 2.1 `frontend/src/types/app.ts`：`SSO_LOGIN_METHOD_OPTIONS` 中 `PASSWORD` 选项去掉
      `disabled: true`。
- [x] 2.2 `frontend/src/views/application/app/AppConfigView.vue`：
      - 第 1367 行表单项 `label` 由"允许的登录认证方式"改为"认证方式配置"。
      - 第 1378-1380 行 hint 文案由"口令登录固定必选，不可取消"调整为"认证方式可以不配置，
        未配置时默认使用口令登录"。
      - 第 622-643 行 `saveAuthConfig`：移除第 634-636 行"口令恒定必选，兜底补齐 PASSWORD"
        的逻辑，`loginMethods` 按 `loginMethods.value` 原样提交（允许空数组），不新增"至少
        一项"的前端校验。
      - 第 541-543 行相关注释同步更新（不再描述"口令固定选中且禁用取消勾选"，改为说明
        "未配置/全部取消时后端查询会回退为默认口令登录"）。

## 3. 前端：SSO 登录页按实际返回方式渲染

- [x] 3.1 `frontend/src/views/sso/SsoLoginView.vue` 第 400 行：
      `v-if="loginMethods.length <= 1"` 改为
      `v-if="loginMethods.length <= 1 && loginMethods.includes('PASSWORD')"`。
- [x] 3.2 第 506 行 hint 展示条件：
      `loginMethods.length <= 1 || activeTab === 'PASSWORD'` 改为
      `(loginMethods.length <= 1 && loginMethods.includes('PASSWORD')) || activeTab === 'PASSWORD'`。
- [x] 3.3 手动验证：某应用 `loginMethods` 仅为 `["SMS"]` 或 `["QRCODE"]` 时，SSO 登录页
      能正确展示对应的单一登录方式表单，不再错误渲染口令表单。

## 4. OpenSpec spec 同步

- [x] 4.1 `openspec/specs/app-auth-protocol-config/spec.md`：
      - "修改应用认证配置" Requirement 描述由"`PASSWORD` 恒定包含"改为"允许提交空列表，
        代表未配置"。
      - 移除"提交的登录认证方式不含口令时服务端自动补齐" Scenario，新增"显式保存为空
        列表时按未配置处理，后续查询回退为默认口令登录"的 Scenario。
      - "查询应用认证配置" Requirement 中"`PASSWORD` 恒定包含"描述调整为"未配置（含从未
        配置过、以及显式保存为空列表）时默认仅返回 `PASSWORD`"，与现有"未单独配置过时返回
        仅口令"的 Scenario 合并表述。
- [x] 4.2 `openspec/specs/sso-login-methods/spec.md`：
      - "SSO 登录页按应用配置展示可用认证方式" Requirement 补充"唯一方式非口令"的 Scenario
        （只展示对应单一方式的登录表单，不出现标签页切换控件时的正确渲染内容）。

## 5. 权限资源编码文档

- [x] 5.1 检查仓库根目录 `权限资源.txt` 中 `AppManagement:app:config:editAuth` 权限点的
      描述文案是否引用了"口令登录固定必选"这类表述，如有需同步更新为与新行为一致的描述。

## 6. 回归验证

- [x] 6.1 存量应用（从未修改过认证方式配置）：查询/展示行为不变，仍默认仅口令登录。
- [x] 6.2 已启用短信/扫码的存量应用：取消勾选口令、仅保留短信/扫码后保存成功，SSO 登录页
      不再展示口令 Tab，仅展示短信/扫码。
- [x] 6.3 把某应用勾选框全部取消后保存：保存成功，再次打开应用配置页时口令登录重新显示为
      已勾选（回退为默认口令登录），SSO 登录页对该应用也恢复为仅口令登录样式。
- [x] 6.4 管理端直接登录页（`LoginView.vue`）不受影响，固定仍只使用口令登录。
