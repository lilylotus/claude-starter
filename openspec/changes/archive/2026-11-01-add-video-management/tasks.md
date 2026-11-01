## 1. 数据库与配置

- [x] 1.1 新增 `V10__add_video.sql`：建表 `tab_video`（含四个审计字段、`idx_tab_video_status`），登记"视频管理"菜单 + 上传/删除按钮、3 个权限点，并为 SUPER_ADMIN 补授（NOT EXISTS 幂等守卫，MySQL 5.7 兼容）（后续已由 consolidate-flyway-migrations-v8 并入 `V1__init_schema.sql`，`V10__add_video.sql` 文件已删除）
- [x] 1.2 `application.yml`：multipart 上限调整为 2GB；新增 `rbac.video`（directory、max-file-size、play-ticket-ttl-seconds）与 `rbac.upload.default-max-request-size` 配置

## 2. 后端视频模块

- [x] 2.1 `VideoProperties`、`VideoStatus`、`VideoEntity`、`VideoMapper`、DTO（`VideoVO`、`VideoPageQuery`、`VideoBatchDeleteRequest`、`VideoPlayTicketVO`）、`VideoConvert`（`INSTANCE` 单例）、自定义异常
- [x] 2.2 `VideoServiceImpl`：上传（后缀白名单 + 魔数校验 + 大小校验 + `transferTo` 落盘 + 写库失败清理文件）、分页筛选、单条/批量逻辑删除、加载流式资源
- [x] 2.3 `VideoPlayTicketService`：Redis 生成/校验凭证，校验通过滑动刷新 TTL
- [x] 2.4 `VideoController`：上传、分页、删除、批量删除、换取播放凭证、流式播放（`ResponseEntity<Resource>` + Range），补齐 `@Tag`/`@Operation`
- [x] 2.5 `IdentityAuthFilter`：`/api/videos/*/stream` 加入免登录白名单；视频接口加入固定权限映射
- [x] 2.6 `GlobalResponseAdvice` 排除 `ResourceRegionHttpMessageConverter`；`GlobalExceptionHandler` 处理播放凭证无效（403）
- [x] 2.7 新增 `MultipartSizeLimitFilter`：非视频上传的 multipart 请求超过 10MB 返回业务码 413 的统一错误体（HTTP 200，沿用过滤器错误约定）
- [x] 2.8 单元测试：`VideoServiceImplTest`（白名单/魔数/大小/逻辑删除/批量删除）、播放凭证校验与滑动续期、multipart 守卫
- [x] 2.9 修复：浏览器拖动进度条/关闭播放弹窗时主动断开流式连接（`AsyncRequestNotUsableException`/`ClientAbortException`），被兜底异常处理器按"系统异常"打 ERROR 堆栈，并因响应已设为 `video/mp4` 二次报 `HttpMessageNotWritableException`；改为在 `GlobalExceptionHandler` 中单独识别客户端断开，仅记 DEBUG 日志、不写响应体

## 3. 前端

- [x] 3.1 `types/video.ts`、`api/video.ts`（上传进度 + 不超时、列表、删除、批量删除、换取凭证、stream URL）
- [x] 3.2 `views/video/VideoManagementView.vue`：筛选、表格、分页、上传弹窗（预校验 + 进度）、播放弹窗（凭证 + `<video controls>`，关闭停止加载，失败重试），按钮按权限点显隐
- [x] 3.3 `router/menu.ts`、`router/index.ts` 注册菜单与路由；`PERMISSION_MODULE_LABELS` 增加 VideoManagement
- [x] 3.4 播放弹窗中视频默认音量设为 40%（每次打开播放弹窗/重试时的新播放器均从 40% 开始，用户可自行调节）

## 4. 文档与资源清单

- [x] 4.1 `权限资源.txt` 新增 VideoManagement 模块 3 个编码

## 5. 验证

- [x] 5.1 后端 `./gradlew test` 跑视频相关测试与 `RbacApplicationTests`；在隔离 MySQL 5.7 空 schema 上验证 V1+V9+V10 迁移成功
- [x] 5.2 前端 `npm run build` 通过
- [x] 5.3 浏览器验证：上传（进度、格式/大小拦截）、列表筛选分页、播放（边下边播、拖动进度条返回 206）、单条/批量删除、无凭证/错误凭证访问 stream 被拒、图片上传超过 10MB 仍被拦截

## 6. OpenSpec 收尾

- [x] 6.1 依据实际实现与验证结果更新 proposal/design/tasks
