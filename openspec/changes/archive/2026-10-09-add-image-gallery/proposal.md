## Why

当前系统侧边栏没有"公共服务"类的一级导航分组，也没有任何图片相关能力：既不能上传/管理图片素材，也没有面向浏览场景的图片展示页面。需要新增一个"公共服务"一级菜单分组，下挂"图片管理"（上传 + 分页管理已上传图片）与"图片展示"（瀑布流 + 无限滚动 + 懒加载，点击查看原图）两个页面。

## What Changes

- 新增侧边栏一级导航分组"公共服务"（key: `common-service`），下挂两个二级页面："图片管理"
  （`/common-service/images`）、"图片展示"（`/common-service/gallery`）。
- 新增后端 `image` 模块（自包含，不复用现有 `fileupload` 通用文件模块——原因见 design.md
  Decision 1），提供：
  - 上传接口：校验文件为受支持的图片格式（jpg/jpeg/png/gif），解析图片宽高后落盘 + 落库。
  - 图片管理分页列表接口：按创建时间倒序分页查询未删除的图片，支持按文件名关键字、上传
    时间范围筛选。
  - 图片逻辑删除接口：单条删除 + 批量删除两个接口，均仅更新状态，不物理删除磁盘文件；
    批量删除对无效 id 跳过而非整体失败（design.md Decision 9）。
  - 图片展示分页 feed 接口：供前端无限滚动按页加载，返回宽高信息供瀑布流预先计算布局
    避免图片加载后的布局跳动。
  - 原图内容接口：按 id 返回图片原始字节内容（非 attachment，可直接作为 `<img>` 的
    `src`），图片管理网格缩略图与图片展示瀑布流、点击查看原图三处场景复用同一个接口
    （本来就只存了一份原始分辨率，不做额外缩略图生成）。
- 新增 `tab_image` 表（Flyway 迁移脚本，当前最新已应用为 `V7`，本次从 `V8` 开始）。
- 新增批量上传接口 `POST /api/images/upload/batch`（design.md Decision 11）：一次请求
  接收多个文件，每个文件独立校验/落盘/落库，一个文件失败不影响其它文件，返回每个文件各自
  的成功/失败结果列表。
- 前端新增两个页面与对应 `api/image.ts` 封装：
  - 图片管理：上传（支持一次选择多个文件：先选中、展示待上传列表可移除，手动点击"开始
    上传"后一次性调用批量上传接口，汇总成功/失败数量与失败原因提示，design.md
    Decision 11）+ 列表/分页 + 删除（二次确认）。
  - 图片展示：CSS 多列瀑布流布局 + `IntersectionObserver` 实现触底无限加载 + `el-image`
    的 `lazy` 懒加载与 `preview-src-list` 内置查看器实现点击查看原图，均为浏览器原生能力
    或 Element Plus 既有组件能力，**不新增任何前端依赖**。
- 同步更新仓库根目录 `权限资源.txt`（新增"图片管理""图片展示"模块的权限点登记）与
  `frontend/src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS`（补充中文模块名）。

## Capabilities

### New Capabilities
- `image-management`：图片上传（格式/内容校验、落盘落库、宽高解析）、分页管理已上传图片、
  逻辑删除。
- `image-gallery`：图片瀑布流展示——分页 feed 接口驱动的无限滚动、图片懒加载、点击查看
  原始分辨率图片。

### Modified Capabilities
（无——不改动任何已有 spec 的行为约定；现有 `file-upload` 能力本身不受影响，本次不复用
也不修改它。）

## Impact

- 新增代码：`backend/src/main/java/cn/nihility/rbac/image/**`（controller/service(+impl)/
  dto/entity/mapper/mapstruct/exception/constant/config 分层）。
- 新增数据库变更：`backend/src/main/resources/db/migration/V8__add_image.sql`（新建
  `tab_image` 表 + 菜单/权限点/角色授权种子数据）。
- 新增配置项：`application.yml` 中图片保存根目录配置（独立于 `rbac.file-upload.directory`，
  默认值待 design.md 确定）。
- 前端新增：`frontend/src/views/image/`（`ImageManagementView.vue`、`ImageGalleryView.vue`）、
  `frontend/src/api/image.ts`；修改 `frontend/src/router/menu.ts`（新增分组与路由项）、
  `frontend/src/router/index.ts`（注册路由）。
- 文档：`权限资源.txt` 新增"公共服务"分组下 `ImageManagement`、`ImageGallery` 两个模块的
  权限点条目；`frontend/src/utils/permissionTree.ts` 的 `PERMISSION_MODULE_LABELS` 补充
  对应中文名。
- 不涉及 `build.gradle` 新增依赖，不涉及 `package.json` 新增依赖。

## 待确认的假设（请在确认阶段一并确认或纠正）

1. "图片管理"页面的删除能力包含**单条删除 + 批量删除**（多选 + 批量，已按用户要求补充），
   均为逻辑删除（保留磁盘文件与数据库记录，仅打状态标记并从列表/展示中隐藏）；批量删除
   时若其中某些 id 已不存在/已删除，接口会跳过这些 id、仅对有效 id 生效，不会导致整批
   操作失败（design.md Decision 9）。
2. "图片管理"列表筛选条件（已按用户要求补充）为：按原始文件名关键字模糊搜索（`keyword`）
   + 按上传时间范围筛选（`startTime`/`endTime`），两者可选、可组合；不包含"按上传人
   筛选"（design.md Decision 10 说明了原因）。图片展示 feed 接口不提供筛选参数。
3. 图片格式仅支持 JDK 自带 `ImageIO` 可直接解码的 `jpg`/`jpeg`/`png`/`gif`，**不支持
   `webp`**（JDK 默认 `ImageIO` 不内置 WebP 解码器，引入第三方解码库超出本次范围）。如
   果必须支持 webp，需要先确认引入哪个第三方依赖。
4. 图片展示页与图片管理页复用同一张 `tab_image` 表、同一批数据（即图片管理页上传的图片
   会出现在图片展示页里），而不是两套独立的图片集合。
