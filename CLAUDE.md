# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库中工作时提供指导。

## 项目情况

此项目是基于 RBAC （Role-Based Access Controll）的权限管理系统

- 前端目录为 `frontend/`，后端目录为 `backend/`

## 常用命令

后端（需在 `backend/` 目录下执行，不是仓库根目录——`gradlew` 在这里）：

```bash
./gradlew build      # 编译 + 跑测试
./gradlew test        # 只跑测试（JUnit 5 / junit-platform-launcher）
./gradlew bootRun     # 本地启动应用（server.port: 48080）
```

跑单个测试类/方法用 Gradle 的 `--tests` 过滤参数，例如：
`./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"`。

前端（运行于 `frontend/`）：

```bash
npm install           # 安装依赖
npm run dev            # 本地开发，默认 http://localhost:5173，/api 反向代理到后端 48080
npm run build           # vue-tsc 类型检查 + vite build，产物在 frontend/dist
```

演示登录账号：`admin` / `admin123`（后端还没有鉴权接口，登录逻辑先用
`src/api/auth.ts` 里的本地模拟实现占位，接口就绪后按同一函数签名替换即可）。

OpenSpec 变更工作流（schema：`spec-driven`，见 `openspec/config.yaml`）：

```bash
openspec list --json                                  # 查看活跃的 change
openspec status --change "<name>" --json               # 查看某个 change 的状态
openspec instructions apply --change "<name>" --json    # 需要读取/同步的产物文件
```

使用 `/opsx:*` 系列 slash 命令（propose、explore、apply、sync、archive）或对应的
`openspec-*` skill 来推进一个 change 的生命周期。

## 架构

- `backend/` — Java 21 + Spring Boot 3.5.16，Gradle Wrapper，依赖解析会先走阿里云镜像
  再回退到 Maven Central（见 `backend/build.gradle` 里的 `repositories`）。基础包名是
  `cn.nihility.rbac`（Gradle `group` 为 `cn.nihility`），启动类 `RbacApplication`。当前
  已有依赖：`spring-boot-starter-web`、`spring-boot-starter-validation`、
  `springdoc-openapi-starter-webmvc-ui:2.8.17`（锁定 2.x——3.x 目标 Spring Boot 4，
  与本项目的 Boot 3.5 不兼容；Swagger UI 路径 `/swagger-ui.html`）、`mapstruct`、
  `com.baomidou:mybatis-plus-spring-boot3-starter`（不要用 `mybatis-plus-boot-starter`，
  它固定依赖了不兼容 Spring Framework 6.2 的 `mybatis-spring:2.1.2`，Mapper Bean
  注册会报错）、`com.mysql:mysql-connector-j`、`flyway-mysql`、`lombok`。持久层已用
  MyBatis-Plus + MySQL，表结构用 Flyway 迁移脚本管理（`src/main/resources/db/migration/
  V*__*.sql`），表名统一加 `tab_` 前缀。MyBatis 原生 `<settings>`（驼峰↔下划线映射等）
  写在 `src/main/resources/mybatis/mybatis.conf`，由 `application.yml` 里
  `mybatis-plus.config-location` 指向它，不要再用 `mybatis-plus.configuration` 内联写法；
  以后需要手写 SQL 的自定义 Mapper XML 统一放在 `src/main/resources/mybatis/mapper/`
  下（对应 `mybatis-plus.mapper-locations: classpath*:mybatis/mapper/*.xml`），目前还
  没有任何 XML。MyBatis-Plus 专属配置（如 `global-config.db-config.id-type`）仍留在
  `application.yml`，因为原生 `mybatis-config.xml` 表达不了这些。全局响应包装
  `{ code, message, data }` 和业务异常处理在 `common/` 下（`Result`、
  `GlobalResponseAdvice`、`BusinessException`、`GlobalExceptionHandler`），新模块直接
  复用，不要各自重复实现一套。
- `frontend/` — Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-router + axios，
  只用 Composition API（`<script setup lang="ts">`），Element Plus 组件通过
  `unplugin-auto-import`/`unplugin-vue-components` 自动引入，无需手写 import。
  `src/` 下结构：`api/`（axios 实例 + 按模块的请求封装）、`stores/`（Pinia，目前只有
  `auth`）、`router/`（`index.ts` 路由表 + `menu.ts` 侧边栏四个一级菜单的数据源）、
  `layout/`（`AppLayout.vue` 整体外壳 + `components/SideNav.vue`、`HeaderBar.vue`）、
  `views/`（`login/`、`dashboard/`，以及权限点/角色/用户等尚未实现业务逻辑的页面
  统一复用 `views/PlaceholderView.vue`，靠路由 `meta.title/description/permissionKey`
  驱动文案）、`styles/`（`variables.scss` 设计令牌 + `element-theme.scss` 覆盖 Element
  Plus 的 CSS 变量，把默认蓝换成品牌蓝 `#2D6CDF`）、`types/`。视觉上有一条贯穿登录页
  动画、侧边栏子菜单连接线、面包屑分隔符、概览页时间线的"链式连接"视觉语言（圆点 +
  虚线），呼应 RBAC 里身份→角色→权限→资源的层层关联，改动这几处时保持这条视觉语言一致。
- `openspec/` — spec-driven 的变更管理。`openspec/changes/` 存放进行中的 change 提案
  （proposal.md/design.md/tasks.md），`openspec/changes/archive/` 存放已归档的，
  `openspec/specs/` 存放同步后的权威 spec。两者目前都是空的。
- `.claude/agents/` — 项目专属的 subagent，把下面的约定固化了下来；遇到匹配的任务时
  优先委托给对应 agent，而不是在主对话里另起一套做法。

### 后端约定（`.claude/agents/springboot-backend-dev.md`）

- 新代码按分层组织：`controller/`（薄层：接收参数、触发 `@Valid`、调用 service，不写业务逻辑）→
  `service/`（+ `impl/`）→ `dto/`（不要直接暴露 entity）→ `entity/`（持久层实体）→
  `mapper/`（MyBatis-Plus `BaseMapper` 数据访问接口）→ `mapstruct/`（MapStruct `@Mapper`
  接口负责 entity↔DTO 转换，不要手写转换代码；**不使用** `componentModel = "spring"`，
  即不注册为 Spring bean，改为接口内声明
  `Xxx INSTANCE = Mappers.getMapper(Xxx.class);` 静态单例，调用方直接
  `XxxConvert.INSTANCE.xxx(...)`，不做构造器注入，例如
  `cn.nihility.rbac.org.mapstruct.OrgConvert`）→ `exception/`（自定义异常 +
  `@RestControllerAdvice` 全局处理器）。
- 请求 DTO 上使用 `jakarta.validation` 注解（`@NotBlank`、`@NotNull`、`@Size` 等），
  配合 controller 方法参数上的 `@Valid`。
- 优先使用精确的 Lombok 注解（`@Getter`/`@Setter`/`@Builder`/`@RequiredArgsConstructor`），
  而不是笼统的 `@Data`，尤其是会参与集合运算或作为 Map key 的实体。
- 除非项目里已经有别的约定，否则通过全局 `@RestControllerAdvice` 把响应统一包成
  `{ code, message, data }` 的形状——前端依赖这个结构保持一致。
- 新增/修改接口时加上 springdoc-openapi 注解（`@Tag`、`@Operation`），让 Swagger UI
  和代码保持同步。
- 修改 `build.gradle` 新增依赖前，先跟用户确认。
- 所有表字段、数据层DTO类字段必须检查是否和各个类型数据库关键字冲突，防止SQL语法错误。字段命名规则：驼峰命名，数据库字段统一下划线分隔，避免使用数据库关键字。
- 所有表必须有默认字段创建人、创建时间、更新人、更新时间

### 前端约定（`.claude/agents/vue3-frontend-dev.md`）

- `src/api/`（按后端模块划分的 axios 封装）——组件里不要直接调用 `axios`。新增业务组件
  时按需建 `src/components/`（PascalCase、多单词命名），一个业务领域一个 Pinia store
  （setup 语法 `defineStore('x', () => {...})`），类型放 `src/types/`，字段命名和后端
  DTO 对齐。
- `src/api/request.ts` 里的 axios 响应拦截器统一解包后端 `{ code, message, data }` 的
  响应结构，组件/store 拿到的是解包后的 `data`；非 0 的 `code` 会被拦截器统一
  `ElMessage.error` 提示并 reject，调用方不用重复写错误提示。
- Element Plus 组件按需引入（配置好 `unplugin-vue-components` /
  `unplugin-auto-import` 后自动导入）；表单校验规则应尽量和后端的 Bean Validation
  规则保持一致。
- 2 空格缩进（和后端 `.editorconfig` 的 4 空格不同）——两套风格不要混用。

### OpenSpec 规范

注意：
- 所有编码之前必须先创建 OpenSpec 规范的标准 `tasks.md` / `design.md` / `proposal.md` 过程文档
- 编码完成后若有调整在更新 OpenSpec 规范的标准 `tasks.md` / `design.md` / `proposal.md` 过程文档

### OpenSpec 文档同步（`.claude/agents/openspec-doc-sync.md`）

某个 change 的实现工作完成之后（不是实现过程中），应基于真实的 diff/测试结果，
把 `tasks.md` / `design.md` / `proposal.md` 和实际构建结果对齐，而不是凭对原计划的
记忆去写。这是独立于把 spec delta 应用到 `openspec/specs/`（`openspec-sync-specs`
负责）和归档该 change（`openspec-archive-change` 负责）的另一个步骤。

## 代码风格

Java 代码遵循 `java-code-style` skill 的规范（4 空格缩进、K&R 大括号风格、UTF-8、
120 列换行、方法参数超过 2 个需换行、类/方法/字段必须有注释、方法与字段用小驼峰命名、
静态变量用大写下划线命名）——如果和 `backend/.editorconfig` 的设置有重叠，以后者为准
（目前是：UTF-8、LF、4 空格缩进、120 字符行宽）。
