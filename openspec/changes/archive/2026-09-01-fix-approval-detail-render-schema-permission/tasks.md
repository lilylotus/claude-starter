## 1. 后端：豁免 render-schema 的运行时权限点校验

- [x] 1.1 `backend/src/main/java/cn/nihility/rbac/auth/filter/IdentityAuthFilter.java`
      的 `FIRST_LOGIN_WHITELIST` 增加 `/api/form-fields/render-schema`（design.md
      Decision 1），同步更新类级 Javadoc 里对该白名单的说明
- [x] 1.2 `IdentityAuthFilterTest` 新增测试用例，比照现有
      `doFilter_shouldPassMineApprovalQuery_asSelfService`：请求
      `/api/form-fields/render-schema` 携带合法 `identity-token` 与任意格式合法但
      用户实际不持有的 `menu` 值，断言请求被放行（`filterChain.doFilter` 被调用）且
      `passwordService.isFirstLogin`/`authorizationService.hasPermission` 均未被调用
- [x] 1.3 `cd backend && ./gradlew test --tests
      "cn.nihility.rbac.auth.filter.IdentityAuthFilterTest"` 确认通过（13 项全部通过）

## 2. 验证

- [x] 2.1 本地/联调环境用 `test` 账号（或任意只持有 `ApprovalManagement:request:approve`
      不持有 ORG/USER/POSITION/APP 管理权限点的账号）登录，打开"我的申请"或
      "待我审批"页面一条 `UPDATE` 类型申请的详情弹窗，确认新旧字段对照正常展示，
      不再报无权限
- [x] 2.2 用一个拥有组织/用户/任职/应用管理权限点的账号，确认对应四个管理页面的
      新增/编辑表单渲染（依赖同一个 `render-schema` 接口）不受本次改动影响，无回归
- [x] 2.3 用一个不持有 `FormFieldManagement` 相关权限点的账号，确认
      `/api/form-fields`（分页查询/详情/新增/编辑/启停用/删除）其余接口仍然正确拒绝，
      未被本次改动意外放宽

## 3. OpenSpec 规范同步

- [x] 3.1 `openspec/specs/password-login-auth/spec.md` 新增 Requirement，明确
      `/api/form-fields/render-schema` 豁免操作资源编码校验（proposal.md Modified
      Capabilities）
- [x] 3.2 `openspec/specs/master-data-approval-workflow/spec.md` 补充审批详情弹窗
      读取字段渲染元数据不受被审批对象管理权限点约束的说明
- [x] 3.3 实现完成后，基于真实 diff 与验证结果核对本 change 的
      `proposal.md`/`design.md`/`tasks.md` 与实际实现是否一致：`IdentityAuthFilter.java`
      的实际改动与 design.md Decision 1 描述一致，人工登录验证（2.1/2.2/2.3）已由
      用户完成，proposal.md/design.md 无需修改
