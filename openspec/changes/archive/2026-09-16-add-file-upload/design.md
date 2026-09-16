## Context

项目目前没有通用的文件上传/下载能力；审批流程附件、Excel 导入失败明细等场景要么各自实现，要么尚未实现。需要一个独立的后端通用模块，按本仓库既有的分层与配置约定（`plugin`/`excelimport`/`excelexport` 等模块）实现"上传落盘 + 落库、按 id 下载、失效文件禁止下载"。

本次不涉及前端页面、不新增第三方依赖，纯后端 Java 21 + Spring Boot 3.5 + MyBatis-Plus + MySQL（需兼容 5.7）+ Flyway 实现。

## Goals / Non-Goals

**Goals:**
- 提供一个可被其它后端模块复用的文件上传/下载能力：`POST` 上传、`GET` 按 id 下载。
- 保存文件名与原始文件名解耦（随机字符串 + 原始后缀），落盘目录可配置，默认 `upload`。
- 文件状态区分"正常/失效"，下载接口按状态放行/拒绝。

**Non-Goals:**
- 不做文件删除（物理删除磁盘文件）接口，也不做状态"失效"的维护接口（如启用/停用接口）——本次只落地上传写入时默认"正常"状态、下载时校验状态两个行为，状态如何被置为"失效"（人工维护接口、还是未来由业务方复用本表时自行调用 mapper 更新）留待后续 change，避免本次擅自设计一个没有明确使用方的管理接口。
- 不做文件大小/类型白名单限制（沿用 Spring Boot `spring.servlet.multipart.max-file-size` 默认全局限制），不引入病毒扫描、图片压缩等增值能力。
- 不做按业务对象关联文件的能力（如"审批单挂载多个附件"），本次只提供最基础的"存一个文件、按 id 取回"原语，关联关系由后续使用方模块自行建表维护外键。
- 不做日期分区子目录（如 `upload/2026/09/16/`），文件直接落在配置的根目录下——符合本次需求描述的"默认保存到 upload 目录中"，避免引入需求未提及的目录结构。
- 不新增前端页面、不修改 `权限资源.txt`（无新增菜单/按钮）。

## Decisions

### Decision 1：模块目录名与分层
新建 `backend/src/main/java/cn/nihility/rbac/fileupload/` 模块，沿用 `excelimport`/`excelexport` 的分层组织：
- `controller/FileUploadController.java`：`upload`/`download` 两个接口，薄层。
- `service/FileUploadService.java` + `service/impl/FileUploadServiceImpl.java`：落盘 + 落库、查询 + 状态校验 + 读盘。
- `service/support/FileUploadRecordWriter.java`：单独的落库组件，只对数据库插入这一步开启 `@Transactional`（见 Decision 7）。
- `dto/FileUploadVO.java`：上传成功后的返回体（id、原始文件名、文件大小、状态、创建时间）。
- `dto/FileDownloadResult.java`：下载结果内部载体（文件字节内容 `content`、原始文件名 `originalFileName`、探测出的媒体类型 `contentType`），不直接暴露给 controller 之外。
- `entity/FileUploadEntity.java`：对应 `tab_file_upload`。
- `mapper/FileUploadMapper.java`：MyBatis-Plus `BaseMapper<FileUploadEntity>`。
- `mapstruct/FileUploadConvert.java`：entity → VO，接口内静态 `INSTANCE` 单例（不用 `componentModel = "spring"`）。
- `exception/FileRecordNotFoundException.java`、`exception/FileDisabledException.java`：均继承 `common/exception/BusinessException`。
- `constant/FileUploadStatus.java`：状态常量类。
- `config/FileUploadProperties.java`：落盘根目录配置，绑定前缀 `rbac.file-upload`，风格对齐 `plugin/config/PluginProperties.java`。

`FileUploadServiceImpl` 还注入了已有的 `auth/service/CurrentOperatorService`（解析当前登录用户 id），
在落库前用它填充 `create_by`/`update_by` 两个审计字段（都取同一个用户 id 的字符串形式），复用项目里
"写操作统一通过 `CurrentOperatorService` 填充审计字段"的既有约定，design 之初未展开这一点，实现时按
既有约定补上。

不复用 `backend-common-utilities` capability（该 capability 定位是无状态工具类，如 `JacksonUtils`），本模块有自己的表和 REST 接口，属于独立的业务能力，因此在 `openspec/specs/` 下新建 `file-upload` capability，而不是给 `backend-common-utilities` 加需求。

### Decision 2：`tab_file_upload` 表结构
新增 Flyway 迁移脚本 `V3__add_file_upload.sql`（`V2` 是当前最新版本号，见 `db/migration/V2__add_wf_process_definition_route_field_codes.sql`）：

```sql
CREATE TABLE IF NOT EXISTS `tab_file_upload`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `original_file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（含后缀），仅用于展示和下载时的 Content-Disposition 文件名',
    `stored_file_name`   VARCHAR(128) NOT NULL COMMENT '保存在磁盘目录中的文件名（UUID 去横线 + 原始后缀）',
    `file_size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `status`             INT          NOT NULL DEFAULT 2000 COMMENT '文件状态：2000=正常，3000=失效',
    `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_file_upload_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '文件上传记录表';
```

（实际迁移脚本 `V3__add_file_upload.sql` 里索引命名为 `idx_tab_file_upload_status`——对齐仓库里其它迁移脚本
"idx_表名_字段名"的命名习惯，而非本文档最初草拟的 `idx_status`；并加了 `IF NOT EXISTS` 与显式
`COLLATE = utf8mb4_general_ci`。）

字段说明：
- `original_file_name`：原始文件名，仅用于展示和下载时的 `Content-Disposition` 文件名，不参与磁盘路径拼接（避免路径穿越）。
- `stored_file_name`：磁盘保存文件名，形如 `1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d.xlsx`（`UUID` 去掉短横线 + 原始后缀，原始文件无后缀则不带后缀），在配置的根目录下唯一，不建唯一索引（`UUID` 碰撞概率可忽略，且不是业务约束）。
- `status`：复用项目已有 `AdminStatus` 等常量类的整数编码风格（2000/3000），但业务语义是"正常/失效"而非"启用/停用"，因此单独建 `FileUploadStatus` 常量类，不复用 `AdminStatus`（与仓库里状态常量类"值相同但单独成类，避免跨领域概念耦合"的既有约定一致）。
- 未使用软删除（`-1000`）编码：本次不提供删除接口，两态即可满足"正常可下载/失效禁止下载"的需求；后续如需要软删除语义可在新 change 中扩展。
- 无需 `file_suffix` 独立字段：后缀可从 `original_file_name` 或 `stored_file_name` 用 `String` 操作实时截取，不做冗余存储。

### Decision 3：随机保存文件名生成
`FileUploadServiceImpl` 内部：
```java
String suffix = extractSuffix(originalFileName); // 取最后一个 '.' 之后的部分；不存在则返回空串
String storedFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
```
不使用 `File.createTempFile` 之类依赖文件系统状态判断唯一性的方案，`UUID` 本身足够保证工程意义上的唯一性，避免额外的磁盘 I/O 竞争检查。

写盘用 `Files.copy(multipartFile.getInputStream(), targetPath)`，目标目录不存在时先 `Files.createDirectories(rootDir)`（应用启动或首次上传时惰性创建，不在启动阶段强制创建，行为对齐 `plugin.directory` "目录不存在时跳过插件加载流程"一类的宽松处理风格——但此处是写文件而非读，需要主动创建而非跳过）。

### Decision 4：落盘根目录配置
新增 `FileUploadProperties`（`@ConfigurationProperties(prefix = "rbac.file-upload")`）：
```yaml
rbac:
  file-upload:
    # 文件保存根目录（相对路径相对于应用当前工作目录解析），默认 upload。
    directory: upload
```
与 `rbac.plugin.directory` 的解析方式保持一致：相对路径相对于应用当前工作目录解析，不做额外的绝对路径转换或校验。

### Decision 5：REST 接口设计
```
POST /api/file-upload/upload
  Content-Type: multipart/form-data
  form field: file (必填，单文件)
  响应 data: FileUploadVO { id, originalFileName, fileSize, status, createTime }

GET /api/file-upload/download/{id}
  path variable: id (Long, 必填)
  成功响应: HTTP 200，二进制内容，Content-Disposition: attachment; filename*=UTF-8''<original_file_name 的 URL 编码>
    Content-Type 优先用 java.nio.file.Files.probeContentType 探测磁盘文件得到的类型，探测不出时回退 application/octet-stream
  失败响应（记录不存在 / 状态为失效）：走全局异常处理，返回 { code, message, data: null }，不返回二进制内容
```
下载接口的方法签名返回 `ResponseEntity<byte[]>`，与 `excelexport/controller/ExcelExportController.java` 现有下载接口的写法保持一致，确保命中 `GlobalResponseAdvice` 里"二进制内容不做 `Result` 包装"的判断分支。

### Decision 6：下载状态校验与异常设计
`FileUploadServiceImpl.download(Long id)`：
1. `mapper.selectById(id)` 为空 → 抛 `FileRecordNotFoundException`（`code=404`，"文件不存在"）。
2. 记录存在但 `status != FileUploadStatus.NORMAL` → 抛 `FileDisabledException`（`code=400`，"文件已失效，无法下载"）。
3. 均通过后，按 `stored_file_name` 从配置根目录读取磁盘文件；磁盘文件缺失（记录存在但物理文件被外部删除）视为系统异常，不单独定义业务异常类型，走 `GlobalExceptionHandler` 的兜底 `Exception` 处理分支返回"服务器内部错误"，因为这是数据不一致的异常状态而非正常业务分支。

两个异常类都直接继承 `common/exception/BusinessException`，复用 `GlobalExceptionHandler` 里已有的 `handleBusinessException`，不新增专属的 `@ExceptionHandler` 方法。

### Decision 7：事务边界
上传写入（写盘 + 插入记录）用 `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)` 包裹数据库插入部分；写盘操作发生在事务开启前完成（`Files.copy` 不是事务性资源，把它放在事务方法之外，成功写盘后再插入数据库记录，避免"数据库插入回滚了但磁盘文件已经落地"与"事务长时间持有数据库连接等待磁盘 I/O"两个问题）。下载是只读查询，不加 `@Transactional`。

实现时发现：`@Transactional` 注解若直接标在 `FileUploadServiceImpl.upload(...)` 内部私有/本类方法上不会生效——Spring AOP 基于代理实现，同一个类内部方法互相调用（`this.xxx()`）不会经过代理，注解会被静默忽略。因此把数据库插入单独拆到一个新的 Spring bean 组件 `fileupload/service/support/FileUploadRecordWriter.java`（只有一个 `insert(FileUploadEntity)` 方法，标注 `@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)`），`FileUploadServiceImpl` 注入并调用这个组件完成落库，这样事务注解才能通过跨 bean 调用被代理拦截生效。这个模式沿用了仓库里 `excelimport` 模块 `ImportRowExecutor` 的既有做法，不是本次新发明的模式。

## Risks / Trade-offs

- [磁盘文件与数据库记录不一致（记录存在但物理文件被外部删除/挪动）] → 属于本次 Non-Goal 之外的运维场景，下载时走兜底系统异常处理，不做自动修复；后续如需要可在新 change 里补一致性校验任务。
- [并发写同名 `stored_file_name`] → 理论上 `UUID` 冲突概率可忽略不计，不做数据库唯一索引强约束，换取写入时不需要多一次唯一性查询。
- [大文件上传占用内存] → `Files.copy(inputStream, path)` 是流式拷贝，不会把整个文件读入内存；未额外限制文件大小，依赖 Spring Boot 全局 `multipart.max-file-size` 默认配置兜底。

## Open Questions

- 文件"失效"状态目前没有维护入口（无启用/停用接口），后续哪个模块会先复用本表来标记失效（比如审批单撤销后关联附件失效）？需要在该复用方的 change 里决定是直接操作 `FileUploadMapper`，还是由 `fileupload` 模块补一个内部 service 方法（不对外暴露 controller 接口）。
