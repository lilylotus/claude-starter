## Why

目前项目里没有通用的文件上传/下载能力。审批流程附件、Excel 导入导出等后续需要"存一个文件、按 id 取回"的场景，只能各自实现一套保存逻辑，容易导致文件名冲突、缺少统一的失效/禁用控制。需要先补上这个后端通用能力，后续业务模块直接复用。

## What Changes

- 新增 `fileupload` 后端模块（controller → service(+impl，落库单独拆到 service/support 下的
  `FileUploadRecordWriter` 组件以承载独立事务) → dto → entity → mapper → mapstruct → exception →
  constant → config 分层），提供：
  - 上传接口：接收 `multipart/form-data` 文件，保存到磁盘（默认 `upload` 目录，根目录可通过配置项调整），保存文件名使用随机字符串 + 原始文件后缀名生成；写入一条文件上传记录并返回文件 id 等信息。
  - 下载接口：按文件上传表 id 下载文件；仅"正常"状态的文件允许下载，"失效"状态的文件下载时返回明确的业务异常，不返回文件内容。
- 新增 `tab_file_upload` 表（Flyway 迁移脚本），记录原始文件名、保存在目录中的文件名、文件大小、文件后缀/类型、状态等信息，并带齐 `create_by`/`create_time`/`update_by`/`update_time` 审计字段。
- 新增文件状态常量类（正常/失效两种状态，取值延续项目里 `AdminStatus` 等常量类的编码风格）。
- 本次不新增依赖（用 JDK 自带的 `java.io`/`java.nio`/`java.util.UUID` 实现随机文件名与磁盘读写），不涉及前端页面改动。

## Capabilities

### New Capabilities
- `file-upload`：通用文件上传/下载能力——上传文件落盘 + 落库、按文件表 id 下载、下载前的状态校验（正常可下载、失效禁止下载）。

### Modified Capabilities
（无——不改动任何已有 spec 的行为约定）

## Impact

- 新增代码：`backend/src/main/java/cn/nihility/rbac/fileupload/**`（controller/service(含 service/support)/dto/entity/mapper/mapstruct/exception/constant/config）。
- 新增数据库变更：`backend/src/main/resources/db/migration/V3__add_file_upload.sql`（新建 `tab_file_upload` 表）。
- 新增配置项：`application.yml` 中文件保存根目录配置（默认值 `upload`）。
- 不涉及 `build.gradle` 依赖变更，不涉及前端 `frontend/` 目录改动，不涉及权限资源编码清单 `权限资源.txt`（无新增页面/按钮）。
