## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V3__add_file_upload.sql`，按 design.md Decision 2 建 `tab_file_upload` 表（含 `status` 索引、四个审计字段）

## 2. 常量与配置

- [x] 2.1 新增 `fileupload/constant/FileUploadStatus.java`：`NORMAL=2000`（正常）、`DISABLED=3000`（失效）
- [x] 2.2 新增 `fileupload/config/FileUploadProperties.java`：`@ConfigurationProperties(prefix = "rbac.file-upload")`，`directory` 默认 `upload`，风格对齐 `plugin/config/PluginProperties.java`
- [x] 2.3 在 `application.yml` 追加 `rbac.file-upload.directory: upload` 配置段（含注释说明相对路径解析规则）

## 3. 持久层

- [x] 3.1 新增 `fileupload/entity/FileUploadEntity.java`（对应 `tab_file_upload`，字段命名与迁移脚本驼峰↔下划线对齐，精确 Lombok 注解）
- [x] 3.2 新增 `fileupload/mapper/FileUploadMapper.java`（`BaseMapper<FileUploadEntity>`）

## 4. DTO 与 MapStruct

- [x] 4.1 新增 `fileupload/dto/FileUploadVO.java`（id、originalFileName、fileSize、status、createTime）
- [x] 4.2 新增 `fileupload/mapstruct/FileUploadConvert.java`（entity → VO，接口内静态 `INSTANCE` 单例）

## 5. 异常

- [x] 5.1 新增 `fileupload/exception/FileRecordNotFoundException.java`（继承 `BusinessException`，`code=404`）
- [x] 5.2 新增 `fileupload/exception/FileDisabledException.java`（继承 `BusinessException`，默认 `code`，提示"文件已失效，无法下载"）

## 6. Service

- [x] 6.1 新增 `fileupload/service/FileUploadService.java` 接口：`upload(MultipartFile file)` 返回 `FileUploadVO`；`download(Long id)` 返回文件字节内容 + 原始文件名（如封装成一个内部返回对象）
- [x] 6.2 新增 `fileupload/service/impl/FileUploadServiceImpl.java`：
  - [x] 6.2.1 实现随机保存文件名生成（`UUID` 去横线 + 原始后缀，design.md Decision 3）
  - [x] 6.2.2 实现写盘逻辑（目录不存在时 `Files.createDirectories` 创建；`Files.copy` 流式写入，不整体读入内存）
  - [x] 6.2.3 实现落库逻辑，`@Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)` 只包裹数据库插入部分，写盘在事务方法之外先完成（落库拆到独立的 `FileUploadRecordWriter` 组件，避免同类内部方法调用绕过 Spring AOP 代理）
  - [x] 6.2.4 实现下载读取逻辑：记录不存在抛 `FileRecordNotFoundException`；状态非正常抛 `FileDisabledException`；均通过后从磁盘读取并返回内容 + 原始文件名

## 7. Controller

- [x] 7.1 新增 `fileupload/controller/FileUploadController.java`：
  - [x] 7.1.1 `POST /api/file-upload/upload`（`multipart/form-data`，`@RequestParam("file") MultipartFile file`），加 `@Tag`/`@Operation` 注解
  - [x] 7.1.2 `GET /api/file-upload/download/{id}`，返回 `ResponseEntity<byte[]>`，`Content-Disposition` 用 URL 编码后的原始文件名，`Content-Type` 优先 `Files.probeContentType` 探测、探测不出回退 `application/octet-stream`，加 `@Tag`/`@Operation` 注解

## 8. 验证

- [x] 8.1 `./gradlew build` 编译通过（含 Flyway 迁移脚本语法校验、MapStruct 生成）；另补充 `FileUploadServiceImplTest` 单元测试覆盖上传空文件校验、保存文件名与原始文件名解耦、下载三种分支（正常/记录不存在/状态失效），`./gradlew test --tests "cn.nihility.rbac.fileupload.*"` 通过
- [x] 8.2 原计划"手工验证上传一个文件"因当时没有真实登录态（后端还未接入可联调的登录会话）未执行，改用单元测试 `FileUploadServiceImplTest#upload_shouldWriteFileAndInsertNormalRecord_whenFileProvided` 覆盖等价场景：断言磁盘临时目录下出现与原始文件名不同、以原始后缀结尾的"随机串+后缀"命名文件，且落库记录 `status=FileUploadStatus.NORMAL`（2000）
- [x] 8.3 原计划"手工验证按 id 下载"未执行，改用单元测试 `FileUploadServiceImplTest#download_shouldReturnContent_whenRecordStatusNormal` 覆盖等价场景：预置一条 `status=2000` 的记录和对应磁盘文件，断言下载返回的内容与原文件一致
- [x] 8.4 原计划"手工验证失效文件拒绝下载"未执行，改用单元测试 `FileUploadServiceImplTest#download_shouldThrowFileDisabledException_whenRecordStatusDisabled` 覆盖等价场景：记录 `status=3000` 时下载抛出 `FileDisabledException`，不返回文件内容
- [x] 8.5 原计划"手工验证不存在记录拒绝下载"未执行，改用单元测试 `FileUploadServiceImplTest#download_shouldThrowFileRecordNotFoundException_whenRecordMissing` 覆盖等价场景：不存在的 id 下载抛出 `FileRecordNotFoundException`

（8.2-8.5 均未做真实登录态下的端到端手工验证，用等价的 Mockito 单元测试覆盖了同样的行为断言；如后续接入可联调的登录环境，建议补一轮真实的手工/集成验证。）
