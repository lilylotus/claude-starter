## Why

这是一份追溯性文档：以下两个缺陷已经在日常缺陷排查中被直接修复并验证通过构建，现在按仓库的 OpenSpec
规范补写提案/设计/任务/规范变更文档，确保变更留痕、可追溯。

1. 操作日志管理页面（`/log/operation-logs`）表格列宽设置不合理：部分列用 `min-width`（弹性列）、
   部分列用固定 `width` 混搭，导致"操作人"列内容换行、"被操作对象"与"操作人"两列因中间弹性列
   被过度拉伸而看起来相距很远、"操作模块"与"资源类型"两列同理，且调整过程中一度把全部列改成
   固定宽度又导致表格整体只占可用展示区域的一半、右侧大片空白。
2. 前端请求拦截器（`frontend/src/api/request.ts`）里"access-key 过期后用 refresh-key 静默刷新并
   重试原始请求"的逻辑没有重试次数上限：如果重试后的请求依然收到未登录错误（不是简单换新
   access-key 就能解决的情况），会被当作全新的一次 401 处理，再次触发刷新并重试，如此无限递归，
   表现为浏览器不断请求 `/api/auth/refresh`、界面卡死。登录日志管理页面是这个缺陷第一次被复现
   触发的地方，但缺陷本身在 `password-login-auth` 能力的"前端请求头与静默刷新"这一横切逻辑里，
   与登录日志管理本身无关，任何页面在同样条件下都会触发。

## What Changes

- `operation-log-management` 能力：明确"操作日志管理前端界面"列表的列宽/换行展示要求——过长
  文本单行展示（省略号 + 悬浮提示，不换行）、列宽按内容合理分配（不出现单列因内容短却承担全部
  弹性空间导致的异常宽列/异常间距）、表格整体宽度适配可用展示区域（不出现明显小于可用区域、
  右侧大片留白的情况）。
- `password-login-auth` 能力：明确"前端请求头与静默刷新"对同一个原始请求最多只触发一次"刷新后
  重试"，重试后仍未通过身份校验时直接判定登录态失效并跳转登录页，不再无限递归刷新。

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `operation-log-management`：为"操作日志管理前端界面"需求补充列宽/换行展示的具体约束。
- `password-login-auth`：为"前端请求头与静默刷新"需求补充"最多重试一次"的边界约束。

## Impact

- 前端：`frontend/src/views/system/log/OperationLogManagementView.vue`（表格列宽/`show-overflow-tooltip`
  调整）、`frontend/src/api/request.ts`（响应拦截器新增单次重试标记）。
- 不涉及后端代码改动，不涉及数据库迁移。
