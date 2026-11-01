## Context

- 图片模块（`cn.nihility.rbac.image`）已实现上传/分页/逻辑删除/原图访问，前端通过 axios 以 blob 方式拉取二进制再 `URL.createObjectURL` 展示——因为 `IdentityAuthFilter` 要求所有业务接口携带 `identity-token` 与 `menu` 请求头，而浏览器原生 `<img>`/`<video>` 请求无法携带自定义请求头。
- 视频体积远大于图片（本次上限 2GB），若照搬 blob 方式需整文件下载完成才能播放，且无法拖动进度条；用户已确认采用"短期播放凭证 + 流式播放"。
- 全局 multipart 上限为 10MB（`spring.servlet.multipart`），图片上传会 `readAllBytes` 整体读入内存，Excel 导入等也依赖该全局上限兜底。
- `GlobalResponseAdvice` 已排除 `ByteArrayHttpMessageConverter`/`ResourceHttpMessageConverter`，但 Range 请求走的是 `ResourceRegionHttpMessageConverter`，当前未排除。
- 新迁移在 V1 基线之后以增量文件追加（`backend-common-utilities` spec），本次为 `V10__add_video.sql`（仓库已存在 `V9__add_abac_policy.sql`，新版本号必须大于已执行的 V9，否则 Flyway 会以版本乱序拒绝迁移）。
- 项目已有 Redis（`RedisUtils`/`StringRedisTemplate`）。

## Goals / Non-Goals

**Goals:**

- "公共服务"下新增"视频管理"单页：上传（带进度）、分页筛选、单条/批量删除、弹窗播放。
- 播放支持边下边播与拖动进度条（HTTP Range）。
- 新接口权限编码由服务端固定映射，不信任客户端 `menu` 头。
- 放开 2GB 上传的同时，不放宽其他既有上传接口的 10MB 上限。

**Non-Goals:**

- 不做分片/断点续传、服务端转码、缩略图、时长解析（不新增依赖）。
- 不做独立的"视频展示"浏览页（播放集成在管理页内）。
- 不物理删除磁盘文件，不做存储清理任务。

## Decisions

### Decision 1: 模块结构与表设计对齐图片模块

后端包 `cn.nihility.rbac.video`：`controller/VideoController`、`service/VideoService` + `impl/VideoServiceImpl`、`service/support/VideoPlayTicketService`（凭证读写）、`dto/`（`VideoVO`、`VideoPageQuery`、`VideoBatchDeleteRequest`、`VideoPlayTicketVO`）、`entity/VideoEntity`、`mapper/VideoMapper`、`mapstruct/VideoConvert`（`INSTANCE` 静态单例）、`exception/`（`VideoNotFoundException`、`InvalidVideoFileException`、`VideoFileTooLargeException`、`InvalidPlayTicketException`）、`config/VideoProperties`、`constant/VideoStatus`。

表 `tab_video`：`id`、`original_file_name`（VARCHAR 255，仅展示）、`stored_file_name`（VARCHAR 128，UUID 去横线 + 小写后缀）、`file_size`（BIGINT 字节）、`content_type`（VARCHAR 64，按后缀确定）、`status`（INT，2000=正常、-1000=已删除）、`create_by`/`create_time`/`update_by`/`update_time`，索引 `idx_tab_video_status`。字段名均避开 MySQL 关键字。

### Decision 2: 上传校验与流式落盘

- 后缀白名单：`.mp4`、`.webm`、`.ogg`、`.ogv`（浏览器原生可播放格式）；`content_type` 分别为 `video/mp4`、`video/webm`、`video/ogg`。
- 魔数校验（只读取文件头前 12 字节，不整体读入内存）：mp4 第 4–7 字节为 `ftyp`；webm 前 4 字节为 EBML 头 `1A 45 DF A3`；ogg/ogv 前 4 字节为 `OggS`。后缀与魔数不匹配 → `InvalidVideoFileException`。
- 大小：`file.getSize() > rbac.video.max-file-size`（默认 2GB）→ `VideoFileTooLargeException`；空文件 → 业务错误。
- 落盘：`MultipartFile.transferTo(目标路径)`，不经 `byte[]`；目录不存在自动创建。落盘成功后写库；写库失败时删除已落盘文件，避免孤儿文件。
- 只提供单文件上传接口（2GB 级文件不适合批量单请求）。

### Decision 3: 短期播放凭证 + Range 流式播放

- `POST /api/videos/{id}/play-ticket`（需登录，固定权限 `VideoManagement:video:view`）：校验视频存在且正常，生成 32 位随机十六进制凭证，Redis 写入 `rbac:video:play-ticket:{ticket}` → `videoId`，TTL = `rbac.video.play-ticket-ttl-seconds`（默认 600）；返回 `{ ticket, expiresInSeconds }`。
- `GET /api/videos/{id}/stream?ticket=xxx`：加入 `IdentityAuthFilter.FULL_WHITELIST`（路径 `/api/videos/*/stream`），不要求登录头；服务端校验凭证存在且绑定的视频 id 与路径 id 一致、视频状态正常，否则抛 `InvalidPlayTicketException`（HTTP 403）。校验通过后**刷新凭证 TTL（滑动过期）**，保证长视频在持续播放/拖动过程中的后续 Range 请求不会因凭证过期失败；停止播放超过 TTL 后凭证自然失效。
- 凭证不做一次性消费：一次播放会产生多个 Range 请求，一次性凭证会导致拖动失败。凭证只绑定单个视频、随机不可猜测、短期有效，泄露影响面限定于该视频的短时间访问。
- 返回 `ResponseEntity<Resource>`（`FileSystemResource`），设置 `Content-Type` 与 `Accept-Ranges: bytes`，由 Spring MVC 自动处理 `Range` 请求头返回 206 + `ResourceRegion`。
- `GlobalResponseAdvice.supports` 追加排除 `ResourceRegionHttpMessageConverter`。

备选方案（blob 整体下载）已被用户否决：大文件需完整下载才能播放、无法拖动、浏览器内存占用大。

### Decision 4: multipart 大小——全局放开 + 按路径守卫

Spring 的 multipart 上限是全局的，无法按接口单独设置。方案：

- `application.yml`：`spring.servlet.multipart.max-file-size` / `max-request-size` 调整为 `2GB`。
- 新增 `common/filter/MultipartSizeLimitFilter`（`OncePerRequestFilter`，优先级高于 `IdentityAuthFilter`）：对 `Content-Type` 为 `multipart/form-data` 的请求，若路径不是 `POST /api/videos/upload` 且 `Content-Length` 超过 `rbac.upload.default-max-request-size`（默认 10MB），直接返回统一错误体（沿用项目过滤器约定：HTTP 200 + 业务码 `code=413`，由前端响应拦截器统一提示）。保持其他既有上传接口原 10MB 上限语义不变。
- 视频上传前端单独设置 axios `timeout: 0` 并通过 `onUploadProgress` 展示进度。

### Decision 5: 权限点与固定权限映射

| 编码 | 说明 | 接口 |
| --- | --- | --- |
| `VideoManagement:video:view` | 视频管理页面访问 | `GET /api/videos`、`POST /api/videos/*/play-ticket` |
| `VideoManagement:video:add` | 上传视频 | `POST /api/videos/upload` |
| `VideoManagement:video:delete` | 删除视频（单条 + 批量） | `DELETE /api/videos/*`、`DELETE /api/videos/batch` |

以上全部加入 `IdentityAuthFilter.FIXED_PERMISSION_MAPPINGS`。注意 `DELETE /api/videos/batch` 与 `DELETE /api/videos/*` 均映射到 delete，不冲突。

菜单：`common-service` 分组下新增 `视频管理`（`VideoManagement:video:view`，resource_type=1，show_order=5，排在图片展示之后），其下按钮 `上传视频`（add，show_order=20）、`删除视频`（delete，show_order=10）。

### Decision 6: Flyway 增量迁移

新增 `V10__add_video.sql`：`CREATE TABLE IF NOT EXISTS tab_video`；`tab_menu`/`tab_permission` 插入（`NOT EXISTS` 幂等守卫）；显式为 SUPER_ADMIN 补授 3 个权限点（V1 的全量授权不覆盖后续脚本）。只用 MySQL 5.7 兼容语法（后续已由 consolidate-flyway-migrations-v8 并入 `V1__init_schema.sql`，`V10__add_video.sql` 文件已删除）。

### Decision 7: 前端

- `types/video.ts`（与后端 DTO 字段对齐）、`api/video.ts`（上传带进度、列表、删除、批量删除、换取凭证、拼接 stream URL）。
- `views/video/VideoManagementView.vue`：工具栏（文件名关键字、上传时间区间、查询/重置、上传按钮 `video:add`、批量删除 `video:delete`）；表格（多选、文件名、格式、大小（人类可读）、上传时间、操作：播放/删除）；分页（默认 10 条）；上传弹窗（`el-upload` 手动上传、前端预校验后缀与 2GB、进度条、上传中禁止关闭）；播放弹窗（打开时换取凭证并设置 `<video controls autoplay>` 的 src，播放器默认音量 40%（在 `loadstart` 时设置，先于自动播放生效），关闭时清空 src 停止加载；`<video>` 触发 `error` 时提示"播放失败"并提供"重试"重新换取凭证）。
- `router/menu.ts` 在 `common-service` 下追加 `{ title: '视频管理', path: '/common-service/videos', permissionKey: 'VideoManagement:video:view' }`；`router/index.ts` 注册懒加载路由；`PERMISSION_MODULE_LABELS` 增加 `VideoManagement: '视频管理'`。

## Risks / Trade-offs

- [播放凭证出现在 URL 查询参数中，可能进入访问日志/浏览器历史] → 凭证随机、绑定单视频、10 分钟滑动过期，不含用户身份信息；不复用登录 access-key。
- [2GB 单请求上传可能因网络中断失败且不可续传] → 前端进度展示与失败提示；分片/续传列为后续增强。
- [全局 multipart 放开后依赖守卫过滤器保持其他接口 10MB] → 守卫基于 `Content-Length`，浏览器 FormData 上传均携带；缺失 `Content-Length` 的分块请求由 Spring 全局 2GB 上限兜底。
- [视频文件不物理删除会持续占用磁盘] → 与图片模块一致，记录为已知限制。
- [不做转码，非 H.264/VP8/VP9 编码的 mp4/webm 可能在部分浏览器无法播放] → 播放弹窗捕获 `error` 并提示；上传仅限白名单容器格式。

## Migration Plan

1. 部署后 Flyway 自动执行 `V10__add_video.sql`；该脚本后续已并入 V1 基线（consolidate-flyway-migrations-v8），新环境只需执行 `V1__init_schema.sql`。
2. 确保 `rbac.video.directory` 所在磁盘空间充足；如有反向代理（Nginx 等），需同步调大 `client_max_body_size` 与超时。
3. 回滚：Git 还原代码；数据库中 `tab_video` 与菜单/权限点数据可保留（无副作用），或手工删除。

## Open Questions

无。
