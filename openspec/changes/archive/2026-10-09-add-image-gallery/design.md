## Context

现状核对（写 design 前已读代码确认）：

- 仓库里已有一个通用文件上传模块 `cn.nihility.rbac.fileupload`（`add-file-upload` 归档
  change），`tab_file_upload` 表只有 `originalFileName`/`storedFileName`/`fileSize`/
  `status`（2000 正常/3000 失效）+ 四个审计字段，下载接口固定返回
  `Content-Disposition: attachment`。它的 Purpose 明确写着服务"审批流程附件、Excel 导入
  导出"这类通用附件场景。
- 侧边栏一级导航分组在前端 `frontend/src/router/menu.ts` 的 `MENU_GROUPS` 里硬编码，同时
  后端 `tab_menu` 表里为每个分组登记一条 `parent_id=0`、`resource_type=1`、
  `remark='侧边栏一级导航分组'` 的记录（如 `code='chat'`、`code='identity'`），分组下的
  页面/按钮再挂在该记录 id 下。
- 权限点落库涉及**三张表**，不是只有 `tab_menu`：`tab_menu`（导航树，含菜单/按钮/API 三类
  `resource_type`）、`tab_permission`（权限点目录，供角色勾选分配）、
  `tab_role_permission`（角色-权限关联）。新增 `code` 必须在三张表都补齐，且必须显式给
  `SUPER_ADMIN` 角色补一条 `tab_role_permission` 授权——`V1` 里那条一次性
  `INSERT...SELECT` 全量授权只覆盖了当时已存在的权限点，后续新增的权限点不会被它覆盖
  （`V4` 漏补、靠 `V5` 单独修复过一次，`V6` 的注释完整记录了这个教训）。
- `backend/src/main/resources/db/migration/` 当前最新已应用迁移是
  `V7__add_chat_e2e_encryption.sql`，本次迁移脚本从 `V8` 开始。
- 项目 MySQL 目标版本 5.7，手写 SQL 禁止窗口函数/CTE/`JSON_TABLE` 等 8.0+ 特性。
- 前端 `package.json` 当前没有任何瀑布流/无限滚动/图片懒加载相关的第三方库；Element Plus
  的 `el-image` 组件本身自带 `lazy`（容器内懒加载）与 `preview-src-list` + `preview-teleported`
  （点击放大、内置查看器，支持多图切换/缩放）能力。

## Goals / Non-Goals

**Goals:**
- 新增一个与现有 `fileupload` 模块解耦的 `image` 后端模块，承载图片特有的元数据（宽、高）
  与图片特有的生命周期（逻辑删除），不污染通用文件表的职责边界。
- 图片管理页：上传（服务端校验格式与可解码性）+ 分页列表（支持文件名关键字 + 上传时间
  范围筛选）+ 单条/批量逻辑删除。
- 图片展示页：服务端提供分页 feed 接口（携带宽高），前端用零新增依赖的方式实现"瀑布流 +
  无限滚动 + 懒加载 + 点击查看原图"。
- 新增菜单分组与权限点登记严格遵循现有三表（`tab_menu`/`tab_permission`/
  `tab_role_permission`）+ `SUPER_ADMIN` 显式授权的既有约定，避免重复 `V4` 的教训。

**Non-Goals（本阶段明确不做）：**
- 不做图片压缩/多分辨率缩略图生成（CDN 风格的 `resize` 流水线）——"原始效果"就是直接提供
  唯一存储的那份原始文件，网格视图里的"缩略"效果完全靠前端 CSS 尺寸约束实现，不生成第二份
  文件。
- 不支持 WebP/AVIF 等 JDK `ImageIO` 不原生支持的格式，不引入第三方图片解码依赖。
- 不做图片编辑（裁剪/旋转/滤镜）、不做图集/相册分类、不做图片搜索或标签。
- 不做移动端"下拉刷新"手势（用户需求描述里明确区分了下拉刷新和无限滚动是两件事，本次只做
  无限滚动）。
- 不复用 `fileupload` 模块的落盘/落库逻辑（见 Decision 1），`tab_file_upload` 表结构、
  下载接口行为均不受本次改动影响。

## Decisions

### 1. `image` 模块自包含，不复用 `fileupload` 模块
`image` 模块拥有自己的 `tab_image` 表、自己的磁盘根目录配置、自己的落盘/落库逻辑，而不是
在 `ImageService` 内部调用 `FileUploadService.upload(...)` 再补一张关联表。

- **备选**：`tab_image` 增加 `file_id` 外键引用 `tab_file_upload`，图片上传时先调用
  `FileUploadService.upload(file)` 落盘落库，再插入 `tab_image` 记录宽高等图片特有字段。
- **为什么不选**：
  1. 解析图片宽高需要先用 `ImageIO` 解码文件内容判断"是否为合法可解码的图片"（校验职责
     本身就该在 `image` 模块内完成，不能委托给对图片内容一无所知的 `fileupload` 模块）；
     解码之后再调用 `FileUploadService.upload(file)` 传入同一个 `MultipartFile`，等于让
     Spring 的 multipart 临时文件/字节缓冲被跨模块调用两次读取路径，两个模块对"读了几次
     流"这件事产生隐式耦合，不如 `image` 模块自己一次性读取字节数组、校验、落盘、落库，
     链路更直接。
  2. `fileupload` 模块的 Purpose 定位是"审批附件、Excel 导入导出"等**不关心内容语义**的
     通用文件；强行让它承载"图片"这个有专属校验规则和专属字段（宽高）的业务概念，会让
     通用模块反过来对具体业务分叉（如果以后又来一个"视频"模块是否也要挂在
     `fileupload` 上？），违反它当初"避免各自重复实现一套"这句话里"一套"所指的范围
     （文件保存与命名逻辑），不是"所有带二进制内容的业务实体"。
  3 两表 JOIN 查询分页列表/feed 时需要同时读 `tab_image` 和 `tab_file_upload` 两张表，
     按仓库既有约定（[[feedback_multi_table_query_xml]] 记忆：多表查询要写在 MyBatis XML
     而不是 Java 侧批量查询再合并），会多引入一个 XML Mapper；自包含单表没有这个负担。

### 2. `tab_image` 表结构与状态常量
```
tab_image
  id                BIGINT      主键，自增
  original_file_name VARCHAR    原始文件名（含后缀）
  stored_file_name   VARCHAR    磁盘保存文件名（随机串 + 原始后缀，逻辑与 fileupload 模块
                                一致但代码独立实现，不共享工具类——两边改动各自独立演进，
                                重复的几行 UUID 拼接逻辑不构成需要抽公共包的理由）
  file_size          BIGINT     文件大小（字节）
  image_width        INT        图片宽度（像素），ImageIO 解析失败时拒绝整次上传，不会有
                                 NULL 值
  image_height       INT        图片高度（像素）
  status             INT        2000=正常，-1000=已删除（逻辑删除，与 `menu`/
                                 `MenuStatus.DELETED` 的 `-1000` 取值含义对齐，本表没有
                                 "停用"这个中间状态，不需要 `3000`）
  create_by/create_time/update_by/update_time  四个审计字段，与项目其它表一致
```
`status` 加索引（列表/feed 查询都要按 `status != -1000` 过滤 + 按 `create_time` 排序，
参照 `V3__add_file_upload.sql` 给 `status` 建索引的先例）。

### 3. 上传校验与宽高解析：一次性读入字节数组
`ImageController.upload` 接收 `MultipartFile`，Service 内：
1. 先校验原始文件名后缀 ∈ {`.jpg`, `.jpeg`, `.png`, `.gif`}（大小写不敏感），不合法直接
   拒绝，不读文件内容。
2. `file.getBytes()` 一次性读入 `byte[] content`。
3. `ImageIO.read(new ByteArrayInputStream(content))` 解析；返回 `null` 或抛异常都视为
   "无法解析为合法图片"，拒绝上传（防御"改后缀名伪装图片"的非图片文件）。
4. 解析成功后取 `BufferedImage.getWidth()/getHeight()`，随机生成 `storedFileName`
   （`UUID` 去横线 + 原始后缀，与 `fileupload` 模块相同套路但独立实现，见 Decision 1 第 3
   点），`Files.write(target, content)` 落盘，再插入 `tab_image` 记录。
- 不做文件大小上限校验（当前 `fileupload` 模块也没做类似限制，保持一致，如需要上限应是
  独立的后续需求）。

### 4. 磁盘根目录配置：独立于 `fileupload`
新增 `cn.nihility.rbac.image.config.ImageProperties`，`@ConfigurationProperties(prefix =
"rbac.image")`，`directory` 默认值 `images`（与 `fileupload` 默认的 `upload` 目录同级、
相对应用当前工作目录解析，和 `FileUploadProperties` 的解析规则一致但各自配置、互不影响）。

### 5. 原图内容接口：非 attachment，三处场景复用同一个 URL
`GET /api/images/{id}/raw`：
- 仅当记录存在且 `status = 2000` 时返回图片字节内容，`Content-Type` 用
  `Files.probeContentType` 探测（探测不出回退按原始后缀名猜测 `image/jpeg` 等，不回退到
  `application/octet-stream`——`<img>` 渲染需要一个 `image/*` 起始的 `Content-Type`）；
  **不设置** `Content-Disposition: attachment`（与 `fileupload` 下载接口的行为刻意不同，
  因为这里的用途是被 `<img src>` 直接引用展示，不是"另存为"下载）。
- 记录不存在或已删除：抛业务异常，由全局异常处理器统一返回错误响应（与 `fileupload` 的
  `FileRecordNotFoundException`/`FileDisabledException` 同样的处理方式，但是 `image`
  模块自己的异常类，不复用 `fileupload` 包下的异常类——同 Decision 1，两个模块代码独立）。
- 图片管理页网格缩略图、图片展示页瀑布流缩略、点击查看原图三处场景，前端都直接用这一个
  URL（`<img :src>` 和 `preview-src-list` 填的是同一个地址），因为服务端本来就只存了一份
  原始分辨率文件，"缩略"效果完全是前端 CSS（如 `width: 100%; height: auto`）视觉上的缩放
  展示，不是服务端生成了另一份缩小的文件。

**编码后发现并纠正（本节原文的假设有误，以此为准）**：上线后实测"图片管理缩略图/图片展示
图片均加载失败"。根因是本项目全局的 `IdentityAuthFilter`（`password-login-auth` change
design.md Decision 5）对除显式白名单外的**所有**接口都强制要求请求头携带
`identity-token`（access-key）与 `menu`（权限编码），这两个头目前只由前端 axios 实例
（`frontend/src/api/request.ts`）的请求拦截器注入；浏览器对 `<img src>` 发出的是原生
请求，不会带上这两个头，因此直接把本节描述的 URL 交给 `<img>`/`el-image` 的 `src` 时，
请求会被该过滤器拦截并返回一段 JSON 错误体（HTTP 状态码仍是 200，但响应体不是图片二进
制），浏览器无法把它渲染成图片。本节"前端都直接用这一个 URL 作为 `<img src>`"的描述
**不成立**，接口本身的设计（非 attachment、按状态校验）不变，但前端消费方式改为：用带
鉴权头的 axios 实例以 `responseType: 'blob'` 取回二进制内容，再用
`URL.createObjectURL` 生成本地 blob URL 交给 `<el-image>` 展示/预览（实现为
`frontend/src/components/AuthorizedImage.vue`，图片管理/图片展示两处共用）。随之放弃的
能力：图片展示页瀑布流原先设想的"预览时在多张已加载图片间左右切换"退化为"每张图片只能
单独预览自己"——因为切换到的其它图片此刻可能还没有被懒加载触发过 blob 请求，`el-image`
的 `preview-src-list` 需要的是已经可用的 src 数组而不是"按需触发获取"的数组，而用户
最初提出的需求原文只有"点击图片展示图片原始效果"（单图原图查看），没有提出"预览态下
左右切图"，放弃这个 el-image 附带的便利特性不影响已确认范围内的任何一条验收场景
（见 `specs/image-gallery/spec.md`），如后续需要可另开 change 补上（需要在打开预览前
把尚未加载的图片一并取回）。

### 6. 分页方式：图片管理与图片展示都用 page/pageSize，不用游标
两个列表接口（`GET /api/images`、`GET /api/images/feed`）都用 MyBatis-Plus 的
`page`/`pageSize` 分页（默认 `page=1`、`pageSize` 图片管理页建议 10、图片展示页建议 20，
均在 Controller 侧给默认值），而不是基于 `createTime`/`id` 的游标分页。

- **备选**：图片展示页用游标分页（如 `lastId`），对"无限滚动过程中有新图片插入"更稳健
  （不会因为前面插入了新记录导致后面页码重复/跳过数据）。
- **为什么不选**：游标分页需要约定游标字段的排序稳定性保证（`createTime` 可能重复），
  实现复杂度明显高于当前"管理页面上传频率不高、不是高并发插入场景"的实际需要；项目里其它
  列表接口（`menu-management` 的子资源分页等）全部是 `page`/`pageSize` 风格，保持一致更
  符合"不为假设的未来需求过度设计"的原则。如果后续真的出现"无限滚动翻页时数据错位"的实际
  问题，再切换到游标分页。

### 7. 菜单 + 权限点登记（`V8__add_image.sql`）
按 Context 里确认的三表约定登记：
1. `tab_menu`：新增一级分组 `name='公共服务'`、`code='common-service'`、`parent_id=0`、
   `resource_type=1`；分组下挂两个二级页面 `ImageManagement:image:view`（图片管理）、
   `ImageGallery:image:view`（图片展示），`parent_id` 均为分组 id；图片管理页面下再挂两个
   按钮级资源 `ImageManagement:image:add`（上传）、`ImageManagement:image:delete`
   （删除），`parent_id` 为图片管理页面的 id，`resource_type=2`。图片展示页不挂任何按钮
   （纯浏览，无管理操作）。
2. `tab_permission`：上述 4 个 `code`（2 个页面 view + 2 个按钮）各对应一条权限点记录。
3. `tab_role_permission`：显式为 `SUPER_ADMIN` 角色补齐这 4 条权限点的授权（照抄 `V6`
   的幂等 `NOT EXISTS` 写法），不依赖任何历史全量授权语句。
4. 全程使用 `NOT EXISTS`/`WHERE ... IS NOT NULL` 幂等写法，参照 `V5`/`V6` 的模式，支持
   脚本被重复应用到已执行过的环境而不报错/不重复插入。

### 8. 前端：零新增依赖实现瀑布流 + 无限滚动 + 懒加载
- **瀑布流**：外层容器 `column-count`（按视口宽度响应式调整列数，如 `<768px` 2 列、
  `<1200px` 3 列、否则 4 列，纯 CSS `@media` 查询），每张图片外包一个
  `break-inside: avoid` 的卡片，图片 `width: 100%; height: auto`——这是纯 CSS 多列布局
  实现"瀑布流"效果的常见零依赖做法，不需要 JS 计算每列的累计高度再手动分配。
- **无限滚动**：列表末尾放一个"哨兵"空 `div`，用浏览器原生 `IntersectionObserver` 监听它
  进入视口即触发加载下一页（`page += 1` 调 feed 接口，用 `List.push` 累加到现有数组），
  不使用任何第三方无限滚动库。加载中/没有更多数据时哨兵下方显示对应文案，复用 Element
  Plus 的 loading/文案样式即可，不需要专门组件。
- **懒加载**：~~`<el-image :src="..." lazy :scroll-container="...">`~~ **（编码后纠正，见
  Decision 5 的修正说明）**——`el-image` 的 `lazy` 只是决定"什么时候给原生 `<img>` 设置
  `src`"，设置的 `src` 仍然是会被 `IdentityAuthFilter` 拦掉的裸 URL，解决不了鉴权问题。
  实际做法：封装 `frontend/src/components/AuthorizedImage.vue`，组件自己用浏览器原生
  `IntersectionObserver` 监听自身根元素是否进入 `root`（即瀑布流的滚动容器）指定的区域，
  进入视口才调用 `api/image.ts` 的 `fetchImageRaw(id)`（走 axios，带 `identity-token`/
  `menu` 头）取回二进制内容，`URL.createObjectURL` 生成本地 blob URL 后再渲染
  `<el-image :src="blobUrl">`；组件卸载或 `id` 变化时 `URL.revokeObjectURL` 释放，避免
  内存泄漏。与"分页 feed 按页加载数据"仍是两件独立的事（用户需求描述里特别强调了这一
  点），两者叠加使用，这一点不变。
- **点击查看原图**：~~`preview-src-list` 传入当前已加载的全部图片原图 URL 数组~~
  **（编码后纠正）**——改为每个 `AuthorizedImage` 实例只把自己取到的那一个 blob URL 传给
  `preview-src-list`（单元素数组），`preview-teleported` 不变。放弃的能力见 Decision 5
  修正说明的最后一段（预览态下左右切换到其它图片）。
- 以上三项仍然全部是浏览器原生 Web API（`IntersectionObserver`、`URL.createObjectURL`）
  或 Element Plus 已有组件能力（`el-image` 的 `fit`/`preview-src-list`），`package.json`
  不新增任何依赖，这一点在修正后依然成立。

### 9. 批量删除：复用单条逻辑删除，部分 id 无效时跳过而非整体失败
新增 `DELETE /api/images/batch`（请求体携带 `id` 数组），Service 内对每个 id 执行与单条
删除相同的"存在且未删除 → 更新为已删除"判断，但**不因为其中某个 id 不存在/已被删除就让
整批失败**——跳过这些 id，仅对有效 id 生效，接口返回实际成功删除的数量（或 id 列表）。

- **备选**：严格校验，只要批量请求里有一个 id 不存在/已删除就整体拒绝（抛异常，不删除
  任何记录）。
- **为什么不选**：批量删除的典型触发场景是管理页"多选 + 批量删除"按钮，前端勾选的 id
  集合和后端实际状态之间存在"确认框弹出期间数据又被别人改动"的天然竞态窗口（比如另一个
  管理员刚好删了其中一条）；为这种边界情况让整批操作失败、要求用户重新勾选重试，对使用
  体验没有好处，"尽量把能删的删掉，跳过已经不存在的"是更符合批量操作直觉的语义。单条删除
  接口（`DELETE /api/images/{id}`）保留"不存在则拒绝并报错"的严格语义不变，两者场景不同
  （单条删除失败需要明确提示具体哪一条出了问题，批量删除不需要）。

### 10. 图片管理列表筛选条件：文件名关键字 + 上传时间范围
`GET /api/images` 新增两个可选查询参数：
- `keyword`：按 `original_file_name` 模糊匹配（`LIKE`），命名与 `dict-management`
  （`DictTypeController` 的 `keyword` 参数）保持一致。
- `startTime`/`endTime`：按 `create_time` 区间筛选，命名与 `operation-log-management`/
  `login-log-management` 的 `startTime`/`endTime` 参数保持一致。
两者均可选、可同时使用（AND 组合），不传时退化为 Decision 6 描述的"纯分页、按创建时间
降序"行为。图片展示 feed 接口（`GET /api/images/feed`）不提供筛选参数——展示页是浏览场景，
不需要管理场景的检索能力，保持其接口简单。

- **备选**：筛选条件再加"按上传人（`create_by`）筛选"。
- **为什么不选**：`create_by` 当前落库的是操作人用户 id 的字符串形式（非用户名），前端要
  做"按上传人筛选"还需要一个用户选择器/用户 id→姓名的映射组件，超出用户本次提出的筛选
  需求范围；如果后续明确需要，可在单独的 change 里补上（涉及前端新增一个用户选择交互
  控件，不是简单加一个查询参数）。

### 11. 支持一次上传多张图片：新增后端批量上传接口，前端"先选、再手动点击开始上传"
用户要求"支持一次上传多张图片"，并在设计评审时明确要求用后端批量接口而不是前端循环调用
单文件接口（本节下方记录了两者的取舍讨论；最终选择是后端批量接口）。

**后端**：新增 `POST /api/images/upload/batch`（`multipart/form-data`，字段名
`files`，对应 `@RequestParam("files") List<MultipartFile> files`）。对每个文件独立走
与现有单文件 `upload` 完全相同的校验/落盘/落库逻辑（后缀白名单 → `ImageIO` 解析宽高 →
随机文件名落盘 → 插入 `tab_image` 记录），但**一个文件的失败不影响其它文件**——用
try/catch 包裹每个文件的处理过程，收集每个文件各自的"成功（返回 `ImageVO`）/失败（返回
错误信息）"结果，整个批量请求本身只要 `files` 非空就不会因为其中某个文件校验失败而让
整个 HTTP 请求返回失败状态。新增响应 DTO `ImageBatchUploadResultVO`（`originalFileName`、
`success`、`image`——成功时的 `ImageVO`，失败时为 `null`、`message`——失败原因，成功时
为 `null`），接口返回 `List<ImageBatchUploadResultVO>`，顺序与请求中文件的顺序一致。
`files` 为空时拒绝请求（参数校验错误），不新增记录。
这个"收集每个文件的结果而不是整体成败"的语义，与 Decision 9 批量删除"跳过无效 id、
不整体失败"的语义是一致的设计哲学（批量操作里某一项的失败不应该影响其它项），区别只是
批量删除"跳过"不需要告知细节，批量上传每个文件的失败原因对用户有意义（校验失败具体是
格式不对还是内容不是合法图片），所以用列表携带每项结果，而不是简单返回一个计数。

**前端**（`ImageManagementView.vue`，交互流程保持"先选、再手动点击开始上传"）：
1. `el-upload` 去掉 `:limit="1"`，加上 `multiple`，`:auto-upload="false"` 不变，
   `:show-file-list` 改为 `true`，让用户在点击上传前能看到、也能移除某个已选中的文件。
2. `on-change` 不再立即调用上传接口，只把新选中的文件（按 `uploadFile.uid` 去重）累积
   进本地"待上传列表"；`on-remove` 同步从该列表摘掉被用户移除的文件。
3. 新增"开始上传"按钮（待上传列表非空才可点击），点击后一次性把待上传列表里的全部文件
   作为 `multipart/form-data` 的多个 `files` 字段提交给 `POST /api/images/upload/batch`
   （一次 HTTP 请求，不是循环发多个请求），拿到 `List<ImageBatchUploadResultVO>` 后
   汇总"成功 N 张"或"成功 N 张，失败 M 张：file1.png（原因）、file2.png（原因）"提示，
   清空待上传列表与 `el-upload` 内部文件列表，统一刷新一次图片列表。

- **备选 A（已否决）**：保持"选中即自动上传"，`on-change` 直接对每个新选中的文件调用
  单文件上传接口（多选时 `on-change` 会按文件各自触发一次）。
- **为什么不选**：多个文件几乎同时各自触发独立的上传请求 + 列表刷新 + 成功提示，会导致
  界面上连续弹出多条"上传成功"提示、多次几乎同时的列表刷新请求相互覆盖（最终结果仍然
  正确，但过程观感杂乱），且用户选错文件后无法在上传前移除——"先选中、确认、再统一
  上传"是更常见也更可控的多文件上传交互模式，这一点与最终方案一致，只是触发上传后走
  的是批量接口而不是循环调用单文件接口。
- **备选 B（已否决，最初的默认方案）**：前端不新增后端接口，待上传列表确认后用
  `Promise.allSettled` 并发循环调用现有单文件接口，前端自己汇总结果。
- **为什么不选**：用户明确要求用后端批量接口。确实如最初方案所说，前端循环调用在功能上
  也能达到同样效果、改动面更小；但后端批量接口把"一次批量操作对应一次 HTTP 请求"这个
  语义显式地建到接口契约里（与已有的 `DELETE /api/images/batch` 批量删除接口风格对齐），
  比"前端自己拿单文件接口拼出批量效果"更明确，也更符合用户的要求。
