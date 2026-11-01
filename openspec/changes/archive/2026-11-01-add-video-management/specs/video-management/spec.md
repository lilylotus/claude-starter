## ADDED Requirements

### Requirement: 视频上传校验与落盘落库
系统 SHALL 提供 `POST /api/videos/upload`（multipart，字段名 `file`，需要 `VideoManagement:video:add` 权限）上传单个视频。系统 SHALL 仅接受后缀为 `.mp4`、`.webm`、`.ogg`、`.ogv`（不区分大小写）且文件头魔数与后缀对应格式一致的文件；文件大小 SHALL 不超过配置项 `rbac.video.max-file-size`（默认 2GB）。校验通过后系统 SHALL 以"随机字符串 + 小写原始后缀"作为保存文件名，将文件流式写入配置目录 `rbac.video.directory`（不整体读入内存），并新增一条状态为正常（2000）的视频记录，返回该记录；写库失败时 SHALL 删除已写入的磁盘文件。

#### Scenario: 上传合法 mp4 视频
- **WHEN** 用户上传一个后缀为 `.mp4`、文件头第 4–7 字节为 `ftyp`、大小 50MB 的文件
- **THEN** 系统保存文件并返回新增记录，包含原始文件名、文件大小、内容类型 `video/mp4`、状态正常

#### Scenario: 后缀不在白名单时拒绝
- **WHEN** 用户上传 `.avi` 或 `.mkv` 文件
- **THEN** 系统返回业务错误"仅支持 mp4/webm/ogg 格式的视频"，不落盘、不落库

#### Scenario: 后缀与文件内容不符时拒绝
- **WHEN** 用户把一个文本文件改名为 `.mp4` 上传
- **THEN** 魔数校验失败，系统返回同样的格式错误，不落盘、不落库

#### Scenario: 超过大小上限时拒绝
- **WHEN** 用户上传的视频大小超过 `rbac.video.max-file-size`
- **THEN** 系统返回业务错误提示文件超过上限，不落盘、不落库

### Requirement: 视频分页管理列表
系统 SHALL 提供 `GET /api/videos`（需要 `VideoManagement:video:view` 权限）按创建时间降序分页返回未删除的视频记录，支持可选的文件名关键字（模糊匹配原始文件名）与创建时间区间筛选，`page` 默认 1、`pageSize` 默认 10。

#### Scenario: 按关键字与时间区间筛选
- **WHEN** 用户以关键字 `demo` 和某个时间区间查询
- **THEN** 只返回原始文件名包含 `demo`、创建时间在区间内且未删除的记录，按创建时间降序

#### Scenario: 已删除视频不出现在列表中
- **WHEN** 某视频已被逻辑删除
- **THEN** 该视频不出现在任何分页查询结果中

### Requirement: 视频单条与批量逻辑删除
系统 SHALL 提供 `DELETE /api/videos/{id}` 与 `DELETE /api/videos/batch`（请求体 `{ ids }`），均需要 `VideoManagement:video:delete` 权限。删除 SHALL 仅把状态更新为已删除（-1000），不物理删除磁盘文件。单条删除时记录不存在或已删除 SHALL 返回业务错误；批量删除 SHALL 跳过不存在或已删除的 id，返回实际删除数量。

#### Scenario: 单条删除已删除的视频
- **WHEN** 用户删除一个已经被删除的视频
- **THEN** 系统返回"视频不存在"类业务错误

#### Scenario: 批量删除中包含无效 id
- **WHEN** 用户批量删除 3 个 id，其中 1 个不存在
- **THEN** 系统删除其余 2 个并返回 2

### Requirement: 视频短期播放凭证
系统 SHALL 提供 `POST /api/videos/{id}/play-ticket`（需要登录与 `VideoManagement:video:view` 权限），在视频存在且状态正常时生成随机播放凭证，绑定该视频 id 存入 Redis，有效期为 `rbac.video.play-ticket-ttl-seconds`（默认 600 秒），返回凭证与有效期秒数。视频不存在或已删除时 SHALL 返回业务错误。

#### Scenario: 为正常视频换取播放凭证
- **WHEN** 有 `VideoManagement:video:view` 权限的用户为正常视频请求播放凭证
- **THEN** 系统返回随机凭证与有效期秒数

#### Scenario: 无权限用户无法换取凭证
- **WHEN** 不具备 `VideoManagement:video:view` 权限的用户请求播放凭证（即使伪造 `menu` 请求头）
- **THEN** 系统按固定权限映射拦截，返回无权限错误

### Requirement: 视频流式播放
系统 SHALL 提供 `GET /api/videos/{id}/stream?ticket=...`，该接口不要求 `identity-token`/`menu` 请求头，只校验播放凭证：凭证存在、绑定的视频 id 与路径 id 一致且视频状态正常时，系统 SHALL 返回视频文件内容，设置对应 `Content-Type` 与 `Accept-Ranges: bytes`，并支持 HTTP `Range` 请求返回 206 分段内容；每次校验通过 SHALL 刷新凭证有效期（滑动过期）。凭证缺失、无效、过期、与视频 id 不匹配或视频已删除时 SHALL 返回 HTTP 403。分段响应 SHALL NOT 被统一响应体 `{ code, message, data }` 包装。

#### Scenario: 带有效凭证播放并拖动进度条
- **WHEN** 浏览器 `<video>` 以有效凭证请求 stream，随后拖动进度条发出 `Range: bytes=10485760-` 请求
- **THEN** 系统返回 206 及对应字节区间内容，凭证有效期被刷新

#### Scenario: 凭证与视频不匹配
- **WHEN** 使用为视频 A 换取的凭证请求视频 B 的 stream
- **THEN** 系统返回 403，不返回视频内容

#### Scenario: 无凭证访问
- **WHEN** 不带 `ticket` 参数请求 stream
- **THEN** 系统返回 403

### Requirement: 非视频上传接口保持原上传大小上限
系统为支持大视频上传而放开全局 multipart 上限后，除 `POST /api/videos/upload` 外的 multipart 请求 SHALL 仍受 `rbac.upload.default-max-request-size`（默认 10MB）限制，超过时 SHALL 返回业务码 `code=413` 的统一错误体（HTTP 状态码沿用项目过滤器约定为 200），不进入业务处理。

#### Scenario: 图片上传超过 10MB
- **WHEN** 用户向 `POST /api/images/upload` 上传 20MB 文件
- **THEN** 系统返回业务码 413 与"上传文件过大"错误，图片接口不被调用

### Requirement: 视频管理前端页面
系统 SHALL 在侧边栏"公共服务"下提供"视频管理"页面（`/common-service/videos`，权限 `VideoManagement:video:view`），在同一页面内提供：文件名关键字与上传时间区间筛选、按上传时间降序的分页表格（文件名、格式、大小、上传时间、操作）、上传弹窗（前端预校验格式与大小、展示上传进度、上传期间不允许关闭弹窗）、单条与批量删除（二次确认）、播放弹窗（打开时换取播放凭证并使用原生 `<video controls>` 流式播放，关闭弹窗时停止加载，播放失败时提示并支持重试）。上传、删除按钮 SHALL 分别按 `VideoManagement:video:add`、`VideoManagement:video:delete` 权限显隐。

#### Scenario: 上传视频展示进度
- **WHEN** 有上传权限的用户选择一个合法视频并开始上传
- **THEN** 弹窗展示上传进度百分比，完成后提示成功并刷新列表

#### Scenario: 播放视频
- **WHEN** 用户在列表中点击某视频的"播放"
- **THEN** 打开播放弹窗，视频边下载边播放，可拖动进度条

#### Scenario: 无上传权限时隐藏上传按钮
- **WHEN** 用户只有 `VideoManagement:video:view` 权限
- **THEN** 页面不显示"上传"和"删除"相关按钮，仍可查看列表与播放
