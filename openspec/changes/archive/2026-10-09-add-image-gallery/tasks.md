## 1. 数据库迁移

- [x] 1.1 新增 `backend/src/main/resources/db/migration/V8__add_image.sql`：
  - [x] 1.1.1 建 `tab_image` 表（design.md Decision 2 字段），`status` 建索引
  - [x] 1.1.2 登记 `tab_menu`：一级分组"公共服务"（`code=common-service`）+ 两个二级页面
    （`ImageManagement:image:view`、`ImageGallery:image:view`）+ 图片管理页下两个按钮
    （`ImageManagement:image:add`、`ImageManagement:image:delete`），design.md Decision 7
  - [x] 1.1.3 登记对应 4 条 `tab_permission` 记录
  - [x] 1.1.4 为 `SUPER_ADMIN` 角色补齐 4 条 `tab_role_permission`
  - [x] 1.1.5 全程用 `NOT EXISTS`/`IS NOT NULL` 幂等写法（照抄 V5/V6 模式）

## 2. 常量与配置

- [x] 2.1 新增 `image/constant/ImageStatus.java`：`NORMAL=2000`、`DELETED=-1000`
- [x] 2.2 新增 `image/config/ImageProperties.java`：`@ConfigurationProperties(prefix =
  "rbac.image")`，`directory` 默认 `images`
- [x] 2.3 `application.yml` 追加 `rbac.image.directory: images` 配置段（含注释）

## 3. 持久层

- [x] 3.1 新增 `image/entity/ImageEntity.java`（对应 `tab_image`，精确 Lombok 注解）
- [x] 3.2 新增 `image/mapper/ImageMapper.java`（`BaseMapper<ImageEntity>`）

## 4. DTO 与 MapStruct

- [x] 4.1 新增 `image/dto/ImageVO.java`（id、originalFileName、fileSize、imageWidth、
  imageHeight、createTime 等展示字段，不包含 `storedFileName` 磁盘文件名）
- [x] 4.2 新增 `image/dto/ImagePageQuery.java`（page、pageSize，默认值在 Controller 侧
  给，校验用 `@Min`；管理页列表额外带 `keyword`、`startTime`、`endTime` 三个可选筛选
  字段，展示页 feed 不使用这三个字段）
- [x] 4.3 新增 `image/dto/ImageBatchDeleteRequest.java`（`ids: List<Long>`，
  `@NotEmpty` 校验非空列表）
- [x] 4.4 新增 `image/mapstruct/ImageConvert.java`（entity → VO，接口内静态 `INSTANCE`
  单例，不用 `componentModel = "spring"`）
  （实际做法：额外新增了 `image/dto/ImageRawContent.java`（原图内容读取结果：字节内容 +
  Content-Type，与 `fileupload` 模块的 `FileDownloadResult` 套路相同但独立实现）用于
  6.1/6.2.6 的返回类型，design.md/tasks.md 未单独列出该文件，属于按 Decision 1/5
  实现原图内容接口的自然产物。）

## 5. 异常

- [x] 5.1 新增 `image/exception/ImageNotFoundException.java`（继承 `BusinessException`）
- [x] 5.2 新增 `image/exception/ImageDeletedException.java`（继承 `BusinessException`，
  提示"图片已删除，无法访问"）
- [x] 5.3 新增 `image/exception/InvalidImageFileException.java`（继承
  `BusinessException`，提示"仅支持 jpg/jpeg/png/gif 格式且内容可解析的图片文件"）

## 6. Service

- [x] 6.1 新增 `image/service/ImageService.java` 接口：`upload(MultipartFile)` →
  `ImageVO`；`page(ImagePageQuery)` → 分页结果（管理页，倒序，支持 keyword/时间范围
  筛选）；`feed(ImagePageQuery)` → 分页结果（展示页 feed，倒序，不传筛选字段，与管理页
  复用同一张表/同一个排序规则，可共用同一个 Mapper 查询方法）；`delete(Long id)`；
  `batchDelete(List<Long> ids)` → 实际删除数量；`loadRawContent(Long id)` → 字节内容 +
  `Content-Type`
  （实际做法：`batchDelete` 入参用 `ImageBatchDeleteRequest` 而非裸 `List<Long>`，与
  Controller 侧 `@Valid` 校验的请求体类型保持一致，返回类型改用 `int`；
  `loadRawContent` 返回新增的 `ImageRawContent` DTO，见 4.4 说明。）
- [x] 6.2 新增 `image/service/impl/ImageServiceImpl.java`：
  - [x] 6.2.1 上传：后缀名白名单校验 → `file.getBytes()` → `ImageIO.read` 解析宽高
    （design.md Decision 3），失败抛 `InvalidImageFileException`
  - [x] 6.2.2 上传：随机 `storedFileName` 生成 + `Files.write` 落盘（目录不存在先
    `Files.createDirectories`）+ 落库，`@Transactional(rollbackFor = Exception.class,
    propagation = Propagation.REQUIRED)` 只包裹落库部分（参照 `FileUploadRecordWriter`
    的拆分方式，避免同类内部方法调用绕过 Spring AOP 代理；新增
    `image/service/support/ImageRecordWriter.java` 承载该事务方法）
  - [x] 6.2.3 分页列表：`status != DELETED`，按 `create_time` 降序；`keyword` 非空时按
    `original_file_name LIKE`，`startTime`/`endTime` 非空时按 `create_time` 区间过滤
    （design.md Decision 10）
  - [x] 6.2.4 单条逻辑删除：记录不存在抛 `ImageNotFoundException`；更新
    `status=DELETED`，不删磁盘文件
  - [x] 6.2.5 批量逻辑删除：（实际做法：未先 `SELECT` 再逐条判断，改为单条
    `UPDATE ... WHERE id IN (...) AND status = NORMAL` 原子语句——id 不存在或已是
    已删除状态的行天然不满足 WHERE 条件而被跳过，不会报错，效果与"先查出仍为 NORMAL
    状态的记录再更新"等价但不存在查询与更新之间的竞态窗口，返回 `mapper.update()`
    的实际受影响行数，design.md Decision 9）
  - [x] 6.2.6 原图内容读取：记录不存在抛 `ImageNotFoundException`；`status=DELETED` 抛
    `ImageDeletedException`；探测 `Content-Type`（`Files.probeContentType`，探测不出按
    原始后缀名回退 `image/jpeg`/`image/png`/`image/gif`）

## 7. Controller

- [x] 7.1 新增 `image/controller/ImageController.java`：
  - [x] 7.1.1 `POST /api/images/upload`（`multipart/form-data`），`@Tag`/`@Operation`
  - [x] 7.1.2 `GET /api/images`（管理页分页列表，`page`/`pageSize`/`keyword`/
    `startTime`/`endTime` 查询参数，均可选）
  - [x] 7.1.3 `GET /api/images/feed`（展示页分页 feed，`page`/`pageSize` 查询参数，默认
    `pageSize` 可与管理页不同，design.md Decision 6，不暴露筛选参数）
  - [x] 7.1.4 `DELETE /api/images/{id}`（单条逻辑删除）
  - [x] 7.1.5 `DELETE /api/images/batch`（批量逻辑删除，`@RequestBody
    ImageBatchDeleteRequest`，`@Valid` 校验 `ids` 非空）
  - [x] 7.1.6 `GET /api/images/{id}/raw`（返回 `ResponseEntity<byte[]>`，不设置
    `Content-Disposition: attachment`，design.md Decision 5）
  - [x] 7.1.7 全部方法补 `@Operation`/`@Parameter` 注解（项目已引入 springdoc）

## 8. 权限资源文档同步

- [x] 8.1 更新仓库根目录 `权限资源.txt`：新增"公共服务"分组，登记 `ImageManagement`（view/
  add/delete）、`ImageGallery`（view）两个模块的条目
- [x] 8.2 更新 `frontend/src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS`：
  `ImageManagement: '图片管理'`、`ImageGallery: '图片展示'`

## 9. 前端

- [x] 9.1 新增 `frontend/src/api/image.ts`：`uploadImage`、
  `listImages(page, pageSize, keyword?, startTime?, endTime?)`、
  `feedImages(page, pageSize)`、`deleteImage(id)`、`batchDeleteImages(ids)`，图片原图
  URL 拼接成 `/api/images/{id}/raw` 的工具函数
  （实际做法：类型定义放在新增的 `frontend/src/types/image.ts`，与其它模块组织方式一致；
  `batchDeleteImages` 通过 axios `delete(url, { data })` 传递请求体；"图片原图 URL 拼接
  工具函数"后来证明是错的，已被第 11 节的 `fetchImageRaw` 取代，见第 11 节）
- [x] 9.2 更新 `frontend/src/router/menu.ts`：新增"公共服务"一级分组（`key:
  'common-service'`，图标用 `@element-plus/icons-vue` 的 `Picture`）+ 两个二级菜单项
- [x] 9.3 更新 `frontend/src/router/index.ts`：注册 `/common-service/images`、
  `/common-service/gallery` 两个路由（懒加载组件）
- [x] 9.4 新增 `frontend/src/views/image/ImageManagementView.vue`：上传按钮（`el-upload`
  手动模式 + 调 `uploadImage`）+ 筛选表单（文件名关键字输入框 + 上传时间范围
  `el-date-picker` daterange）+ 列表（`el-table` 多选列 `type="selection"` 或卡片网格 +
  自定义多选）+ 分页（`el-pagination`）+ 单条删除按钮（`el-popconfirm` 二次确认）+ 批量
  删除按钮（勾选至少一项才可点击，`el-popconfirm` 二次确认后调 `batchDeleteImages`）
  （实际做法：选中文件后立即调用 `uploadImage` 并清空 `el-upload` 内部文件列表，不额外
  展示文件列表/"开始上传"按钮；上传/批量删除按钮按 `ImageManagement:image:add`/
  `ImageManagement:image:delete` 权限点门控展示；缩略图渲染方式后来被第 11 节的修复
  替换，见第 11 节）
- [x] 9.5 新增 `frontend/src/views/image/ImageGalleryView.vue`：CSS `column-count` 瀑布流
  + `IntersectionObserver` 触底加载下一页 + `el-image` 的 `lazy`/`preview-src-list`/
  `preview-teleported`（design.md Decision 8），不引入任何第三方库
  （实际做法：`scroll-container`/`IntersectionObserver` 的 root 均指向
  `layout/AppLayout.vue` 实际滚动的 `.app-layout__main` 容器；每张图片外层卡片用
  `aspect-ratio: imageWidth/imageHeight` 预先占位避免加载完成后的布局跳动；图片渲染方式
  后来被第 11 节的修复替换，见第 11 节）

## 10. 验证

- [x] 10.1 `cd backend && ./gradlew build` 编译通过（含 Flyway 迁移脚本语法校验、
  MapStruct 生成）
- [x] 10.2 后端补单元测试覆盖：上传格式/内容非法拒绝、宽高解析正确、单条/批量逻辑删除
  （含批量删除混入无效 id 不报错的场景）、关键字/时间范围筛选、删除后列表/feed/原图
  接口均不可见或拒绝访问，`./gradlew test --tests "cn.nihility.rbac.image.*"` 通过
  （实际做法：批量删除/关键字/时间范围筛选场景通过捕获 MyBatis-Plus
  `LambdaUpdateWrapper`/`LambdaQueryWrapper` 生成的 SQL 片段断言 WHERE 条件，而非真实
  连接数据库验证数据行为，与 `ApprovalRequestServiceImplTest` 的既有做法一致；删除后
  原图不可访问场景通过 mock 的记录状态验证 `ImageDeletedException`/
  `ImageNotFoundException` 分支，未端到端跑完整的"删除接口 -> 列表/feed/原图接口"链路）
- [x] 10.3 前端 `cd frontend && npm run build`（`vue-tsc` 类型检查 + `vite build`）通过
- [x] 10.4 `npm run dev` 手动验证：（实际做法：仅验证了前端构建与两个新页面的渲染/交互
  骨架无编译或运行时报错，未连接真实后端服务，因此上传/筛选/删除/无限滚动的端到端数据
  联调未执行，详见本次任务汇报说明）

## 11. 缺陷修复：原图接口因全局鉴权过滤器导致加载失败

用户实际联调后反馈"图片管理缩略图/图片展示图片均加载失败"。根因与修复方案见 design.md
Decision 5 的"编码后发现并纠正"说明——`<img src>`/`el-image :src` 直接指向
`/api/images/{id}/raw` 时不会带上 `IdentityAuthFilter` 要求的 `identity-token`/`menu`
请求头，被拦截返回 JSON 错误体，浏览器无法渲染。已按用户确认的修复方案实施：

- [x] 11.1 `frontend/src/api/image.ts`：移除 `getImageRawUrl`（返回裸 URL，已证明不可用），
  改为 `fetchImageRaw(id)`，经 axios 以 `responseType: 'blob'` 取回二进制内容（自动带上
  `identity-token`/`menu` 头）
- [x] 11.2 新增 `frontend/src/components/AuthorizedImage.vue`：管理页/展示页共用的图片
  渲染组件，内部用 `fetchImageRaw` 取回 blob 后 `URL.createObjectURL` 生成本地 URL 再
  渲染 `<el-image>`；`lazy` 为真时用 `IntersectionObserver` 监听自身是否进入 `root`
  指定容器的可视区域再发起请求，为假时（图片管理页，单页最多十几行，不需要真正的懒加载）
  挂载即请求；`id` 变化或组件卸载时 `URL.revokeObjectURL` 释放，避免内存泄漏；
  `preview-src-list` 只包含自己这一张图片的 blob URL（放弃"预览态下左右切换到其它图片"，
  原因见 design.md Decision 5 修正说明末段）
- [x] 11.3 `ImageManagementView.vue`：缩略图列改用 `<AuthorizedImage :id="row.id"
  :lazy="false">`
- [x] 11.4 `ImageGalleryView.vue`：瀑布流卡片改用 `<AuthorizedImage :id="image.id" lazy
  :root="SCROLL_CONTAINER_SELECTOR" :aspect-ratio="...">`，移除不再需要的
  `previewUrlList`/`getImageRawUrl` 引用
- [x] 11.5 `cd frontend && npm run build` 通过，确认改动后类型检查与构建均无报错
- [ ] 11.6 真实联调验证（需要用户在本地连通后端 + MySQL/Redis 的环境下执行，本次未做）：
  图片管理页缩略图正常显示、图片展示页瀑布流图片正常懒加载显示、点击图片能看到原图
  （单图预览，无需验证左右切图）

## 12. 支持一次上传多张图片

用户要求"支持一次上传多张图片"，并要求用后端批量接口而不是前端循环调用单文件接口。
方案见 design.md Decision 11：新增后端批量上传接口，前端"多选 → 待上传列表（可移除）→
显式点击开始上传 → 一次请求调用批量接口 → 汇总成功/失败提示 → 统一刷新一次列表"。

### 后端

- [x] 12.1 新增 `image/dto/ImageBatchUploadResultVO.java`：`originalFileName`、
  `success`、`image`（成功时的 `ImageVO`，失败为 `null`）、`message`（失败原因，成功
  为 `null`）
- [x] 12.2 `ImageService`/`ImageServiceImpl` 新增 `batchUpload(List<MultipartFile>
  files)` → `List<ImageBatchUploadResultVO>`：对每个文件独立调用与单文件 `upload` 相同
  的校验/落盘/落库逻辑，用 try/catch 包裹每个文件的处理，收集每个文件各自的成功/失败
  结果，一个文件失败不影响其它文件继续处理
  （实际做法：单文件校验/落盘/落库逻辑抽成私有方法 `doUpload`，`upload`/`batchUpload`
  都调用它，避免重复代码）
- [x] 12.3 `ImageController` 新增 `POST /api/images/upload/batch`
  （`@RequestParam("files") List<MultipartFile> files`，`files` 为空时拒绝请求），
  `@Tag`/`@Operation` 注解补齐
- [x] 12.4 补单元测试：多文件全部成功、部分文件格式/内容非法时其它文件仍成功且返回体里
  能看到每个文件各自的成功/失败结果、`files` 为空时拒绝请求，
  `./gradlew test --tests "cn.nihility.rbac.image.*"` 通过
- [x] 12.5 `cd backend && ./gradlew build` 通过

### 前端

- [x] 12.6 `frontend/src/api/image.ts` 新增 `uploadImagesBatch(files: File[])`：
  `multipart/form-data` 一次请求携带多个 `files` 字段，调用
  `POST /api/images/upload/batch`，返回类型对应后端 `ImageBatchUploadResultVO[]`
  （类型定义加进 `types/image.ts`）
- [x] 12.7 `ImageManagementView.vue`：`el-upload` 去掉 `:limit="1"`，加 `multiple`，
  （实际做法：改成点击"上传图片"按钮打开一个 `el-dialog`，弹窗内放
  `el-upload`（`drag` + `multiple`），而不是直接在工具栏内联一个 `el-upload`——与
  `components/BatchImportDialog.vue` 的"选择→确认→开始导入→展示结果"交互模式保持
  一致；弹窗内 `el-upload` 默认就带文件列表展示，不需要单独设置 `:show-file-list`）
- [x] 12.8 `on-change` 改为把新选中文件累积进本地"待上传列表" `pendingFiles`，不再
  立即调用上传接口；`on-remove` 同步从 `pendingFiles` 里移除被用户手动移除的文件
  （实际做法：`on-change`/`on-remove` 统一调用 `syncPendingFiles`，直接拿 el-upload
  自己维护的 `uploadFiles` 全量列表重新赋值 `pendingFiles`，不需要手动按 `uid` 去重/
  摘除）
- [x] 12.9 新增"开始上传"按钮（`pendingFiles` 为空时禁用），点击后一次性调用
  `uploadImagesBatch(pendingFiles)`
- [x] 12.10 上传结束后按返回的逐文件结果汇总提示，清空 `pendingFiles` 与 `el-upload`
  内部文件列表，统一刷新一次列表
  （实际做法：`ElMessage` 只汇总成功/失败数量，失败文件名 + 原因列表展示在弹窗内的
  一个小结果区域而不是塞进 toast 文案里，避免失败文件较多时 toast 内容过长）
- [x] 12.11 `cd frontend && npm run build` 通过
- [ ] 12.12 真实联调验证（需要用户在本地环境执行，本次未做）：一次选择多张图片、移除
  其中一张后再上传、全部成功/部分失败三种场景下的提示与列表刷新结果均符合预期
