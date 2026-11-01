## Why

"公共服务"一级菜单目前只有图片管理与图片展示，系统缺少视频文件的集中存放与在线播放能力。需要在"公共服务"下新增"视频管理"页面，支持上传视频、分页管理视频、在浏览器内直接播放视频。

## What Changes

- 新增"公共服务 → 视频管理"菜单页（`/common-service/videos`），一个页面内完成上传、管理、播放：
  - 上传：单个上传，仅支持浏览器可原生播放的 mp4/webm/ogg（ogv）格式，按后缀白名单 + 文件头魔数校验，单个文件上限 2GB（可配置），上传过程展示进度条。
  - 管理：按上传时间降序分页列表，支持文件名关键字、上传时间区间筛选；单条删除与批量删除（逻辑删除，不物理删除磁盘文件，与图片管理一致）。
  - 播放：列表中点击"播放"打开播放弹窗，使用浏览器原生 `<video controls>` 流式播放，支持边下边播与拖动进度条。
- 后端新增 `video` 模块（controller/service/dto/entity/mapper/mapstruct/exception，结构对齐 `image` 模块），新增表 `tab_video`。
- **播放鉴权**：`<video src>` 无法携带 `identity-token`/`menu` 请求头，因此新增"短期播放凭证"机制——前端先调用需要鉴权的 `POST /api/videos/{id}/play-ticket` 换取随机凭证（存 Redis、绑定视频 id、10 分钟滑动过期），再以 `GET /api/videos/{id}/stream?ticket=...` 作为 `<video src>`；流式接口加入 `IdentityAuthFilter` 免登录白名单，只认凭证，返回文件资源并由 Spring 处理 HTTP Range（206 分段响应）。
- `GlobalResponseAdvice` 额外排除 `ResourceRegionHttpMessageConverter`，避免 Range 分段响应被统一响应体包装。
- **上传大小**：全局 `spring.servlet.multipart.max-file-size/max-request-size` 由 10MB 调整为 2GB；新增按路径的 multipart 大小守卫过滤器，非视频上传接口仍保持 10MB 上限，避免图片/Excel 导入/通用文件上传等既有接口（部分会把文件整体读入内存）因全局放开而被超大文件拖垮。
- 新增权限点 `VideoManagement:video:view`（页面访问、列表、换取播放凭证）、`VideoManagement:video:add`（上传）、`VideoManagement:video:delete`（单条/批量删除）；新接口全部登记到 `IdentityAuthFilter` 固定权限映射表，不信任客户端 `menu` 头。
- 新增 Flyway 增量迁移 `V10__add_video.sql`：建表 `tab_video`，登记菜单与权限点并授予 SUPER_ADMIN（后续已由 consolidate-flyway-migrations-v8 并入 `V1__init_schema.sql`，`V10__add_video.sql` 文件已删除）。
- 同步更新 `权限资源.txt`、前端 `MENU_GROUPS`、路由、`PERMISSION_MODULE_LABELS`。

## Capabilities

### New Capabilities

- `video-management`：视频上传校验与落盘落库、分页管理、单条/批量逻辑删除、短期播放凭证、流式播放（HTTP Range）、视频管理前端页面。

### Modified Capabilities

无（multipart 大小守卫与响应包装排除属于实现层调整，记录在 design 中，不改变既有 spec 中的需求语义）。

## Impact

- 后端：新增 `cn.nihility.rbac.video` 包；修改 `IdentityAuthFilter`（白名单 + 固定权限映射）、`GlobalResponseAdvice`；新增 multipart 大小守卫过滤器；`application.yml` 调整 multipart 上限并新增 `rbac.video` 配置块；新增 `V10__add_video.sql`（后续已由 consolidate-flyway-migrations-v8 并入 `V1__init_schema.sql`，`V10__add_video.sql` 文件已删除）。
- 前端：新增 `types/video.ts`、`api/video.ts`、`views/video/VideoManagementView.vue`；修改 `router/menu.ts`、`router/index.ts`、`utils/permissionTree.ts`（模块中文名）。
- 文档：`权限资源.txt` 新增 VideoManagement 模块 3 个编码。
- 依赖：不新增依赖（不引入 ffmpeg 等转码/抽帧库，因此不做服务端转码、缩略图与时长解析）。
- 存储：视频文件保存在 `rbac.video.directory`（默认 `videos`）目录下，需预留足够磁盘空间。

## Implementation Result

- 后端新增 `cn.nihility.rbac.video` 模块（controller/service/support/dto/entity/mapper/mapstruct/exception/config/constant）；新增 `common/filter/MultipartSizeLimitFilter` + `UploadLimitProperties` + 注册配置；修改 `IdentityAuthFilter`（`/api/videos/*/stream` 免登录白名单 + 5 条固定权限映射）、`GlobalResponseAdvice`（排除 `ResourceRegionHttpMessageConverter`）、`GlobalExceptionHandler`（播放凭证无效返回 HTTP 403）、`application.yml`（multipart 2GB、`rbac.video`、`rbac.upload`）。
- 迁移文件实际为 `V10__add_video.sql`（仓库已有 `V9__add_abac_policy.sql`，原计划的 V2 会因版本乱序被 Flyway 拒绝）（后续已由 consolidate-flyway-migrations-v8 并入 `V1__init_schema.sql`，`V10__add_video.sql` 文件已删除）。
- multipart 守卫拦截时沿用项目过滤器错误约定：HTTP 200 + 业务码 `413`（原设计写的是 HTTP 413，已同步修正 design/spec），由前端响应拦截器统一提示。
- 前端新增 `types/video.ts`、`api/video.ts`、`views/video/VideoManagementView.vue`，修改 `router/menu.ts`、`router/index.ts`、`utils/permissionTree.ts`；`权限资源.txt` 新增 VideoManagement 3 个编码。
- 验证：
  - 新增单元测试 20 项全部通过（`VideoServiceImplTest` 14、`VideoPlayTicketServiceTest` 3、`MultipartSizeLimitFilterTest` 3）；指向隔离 MySQL 5.7.40 空 schema 的后端全量测试 1493 项全部通过，Flyway 依次成功执行 V1、V9、V10，SUPER_ADMIN 获得 3 个视频权限点。
  - `npm run build` 通过。
  - 独立启动的隔离后端实例上 HTTP 验证：有效凭证返回 200 + `Accept-Ranges: bytes` + `Cache-Control: no-store`；`Range: bytes=1048576-1048675` 返回 206 + 正确 `Content-Range`，100 字节与磁盘文件逐字节一致，未被统一响应体包装；访问后凭证 TTL 滑动恢复为 600；无凭证、凭证与视频不匹配、视频已删除均返回 403；12MB 图片上传被守卫拦截（code 413），2MB 图片与 12MB 视频上传放行至鉴权。
  - 浏览器验证（临时 Vite 实例代理到隔离后端）：菜单"公共服务 → 视频管理"可见；上传真实 webm 成功、弹窗关闭、列表刷新；播放弹窗流式播放正常（readyState 4，320×180），拖动进度到 0.8s 无错误；不可解码文件显示"播放失败 + 重试"；关键字筛选与重置、单条删除、批量删除（"成功删除 2 个视频"）正常；`.avi` 被前端预校验拦截且"开始上传"保持禁用；删除为逻辑删除，磁盘文件保留。
- 修复（tasks.md 2.9）：浏览器拖动进度条/关闭播放弹窗时主动断开流式连接，原先被兜底异常处理器按"系统异常"打 ERROR 堆栈，并因响应已是 `video/mp4` 二次报 `HttpMessageNotWritableException`。`GlobalExceptionHandler` 新增 `handleClientAbort` 单独处理 `AsyncRequestNotUsableException`/`ClientAbortException`，只记 DEBUG 日志、不写响应体；`GlobalExceptionHandlerTest` 新增 2 个用例（8 项全部通过），并在本地开发环境实测日志变为单行 DEBUG。
- 调整（tasks.md 3.4）：播放弹窗中视频默认音量为 40%，在 `<video>` 的 `loadstart` 事件中设置（早于自动播放出声）；每次打开播放弹窗或重试时新建的播放器均从 40% 开始。`npm run build` 通过；本地开发环境实测首次打开音量为 0.4，调到 0.9 后关闭再打开仍为 0.4。
