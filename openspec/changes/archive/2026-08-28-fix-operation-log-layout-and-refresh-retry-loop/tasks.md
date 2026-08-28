## 1. 操作日志列表列宽调整

- [x] 1.1 `OperationLogManagementView.vue` 的"操作人"列改为 `show-overflow-tooltip`，避免内容换行；验证：内容较长的操作人展示文案单行展示、悬浮可查看完整内容
- [x] 1.2 "被操作对象"列改为固定宽度并配合 `show-overflow-tooltip`，不再单独承担全部弹性空间；验证：该列与相邻列间距恢复正常
- [x] 1.3 "操作模块""资源类型"两列统一改为 `min-width` + `show-overflow-tooltip`，与"被操作对象""操作人"共同分摊剩余空间；验证：多列共同分摊后单列不再被异常拉伸，且表格整体撑满可用展示区域，不出现右侧大片空白
- [x] 1.4 `npm run build` 通过

## 2. 静默刷新重试次数上限

- [x] 2.1 `frontend/src/api/request.ts` 响应拦截器 401 分支新增 `_retriedAfterRefresh` 标记，限制同一原始请求最多触发一次"刷新后重试"；验证：单元/手动验证——模拟重试后依然 401 的场景，确认不会再次调用刷新接口，而是直接跳转登录页
- [x] 2.2 `npm run build` 通过

## 3. 文档同步

- [x] 3.1 `openspec/specs/operation-log-management/spec.md`、`openspec/specs/password-login-auth/spec.md` 待本 change 归档时同步补充本文档的 spec delta 内容
