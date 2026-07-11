# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库中工作时提供指导。

## 仓库现状

这是一个刚起步的 monorepo，尚未初始化为 git 仓库。目前只包含一个骨架状态的 Spring Boot
后端（默认 `demo` 包，还没有真正的业务接口）、一个还没有任何 change/spec 的 OpenSpec 配置，
以及尚不存在的前端目录。这里大部分"架构"其实是编码在 `.claude/agents/` 下自定义
subagent 里的预期结构，而不是已经存在的代码——在假设某种约定成立之前，先读一遍下面
总结的那几份 agent 文件。

## 常用命令

后端（需在 `backend/` 目录下执行，不是仓库根目录——`gradlew` 在这里）：

```bash
./gradlew build      # 编译 + 跑测试
./gradlew test        # 只跑测试（JUnit 5 / junit-platform-launcher）
./gradlew bootRun     # 本地启动应用（server.port: 48080）
```

跑单个测试类/方法用 Gradle 的 `--tests` 过滤参数，例如：
`./gradlew test --tests "com.example.demo.DemoApplicationTests"`。

前端目录（`frontend/`）目前还不存在。首次需要时会通过 Vite 脚手架搭建
（`npm create vite@latest frontend -- --template vue-ts`）——动手之前先看
`.claude/agents/vue3-frontend-dev.md` 里具体的搭建步骤和约定。

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
  再回退到 Maven Central（见 `backend/build.gradle` 里的 `repositories`）。当前已有依赖：
  `spring-boot-starter-web`、`spring-boot-starter-validation`、
  `springdoc-openapi-starter-webmvc-ui`（Swagger UI，路径 `/swagger-ui.html`）、
  `mapstruct`、`mybatis-plus-boot-starter`、`lombok`。**目前没有**配置 JPA/JDBC 驱动——
  如果任务需要持久化，先跟用户确认方案，不要自作主张加驱动。基础包名是 `com.example.demo`。
- `frontend/` — 尚未创建。搭建后的预期技术栈：Vue 3 + TypeScript + Vite + Element Plus +
  Pinia + vue-router + axios，只用 Composition API（`<script setup lang="ts">`）。
- `openspec/` — spec-driven 的变更管理。`openspec/changes/` 存放进行中的 change 提案
  （proposal.md/design.md/tasks.md），`openspec/changes/archive/` 存放已归档的，
  `openspec/specs/` 存放同步后的权威 spec。两者目前都是空的。
- `.claude/agents/` — 项目专属的 subagent，把下面的约定固化了下来；遇到匹配的任务时
  优先委托给对应 agent，而不是在主对话里另起一套做法。

### 后端约定（`.claude/agents/springboot-backend-dev.md`）

- 新代码按分层组织：`controller/`（薄层：接收参数、触发 `@Valid`、调用 service，不写业务逻辑）→
  `service/`（+ `impl/`）→ `dto/`（不要直接暴露 entity）→ `entity/`（一旦引入持久层）→
  `mapper/`（MapStruct `@Mapper(componentModel = "spring")` 接口负责 entity↔DTO 转换，
  不要手写转换代码）→ `exception/`（自定义异常 + `@RestControllerAdvice` 全局处理器）。
- 请求 DTO 上使用 `jakarta.validation` 注解（`@NotBlank`、`@NotNull`、`@Size` 等），
  配合 controller 方法参数上的 `@Valid`。
- 优先使用精确的 Lombok 注解（`@Getter`/`@Setter`/`@Builder`/`@RequiredArgsConstructor`），
  而不是笼统的 `@Data`，尤其是会参与集合运算或作为 Map key 的实体。
- 除非项目里已经有别的约定，否则通过全局 `@RestControllerAdvice` 把响应统一包成
  `{ code, message, data }` 的形状——前端依赖这个结构保持一致。
- 新增/修改接口时加上 springdoc-openapi 注解（`@Tag`、`@Operation`），让 Swagger UI
  和代码保持同步。
- 修改 `build.gradle` 新增依赖前，先跟用户确认。

### 前端约定（`.claude/agents/vue3-frontend-dev.md`，`frontend/` 创建之后适用）

- `src/api/`（按后端模块划分的 axios 封装）——组件里不要直接调用 `axios`。
- `src/components/`（PascalCase、多单词命名）、`src/views/`（路由页面）、`src/router/`、
  `src/stores/`（Pinia，一个业务领域一个 store，用 `defineStore('x', () => {...})` 的
  setup 语法）、`src/types/`（TS 类型，字段命名和后端 DTO 对齐）。
- axios 响应拦截器统一解包后端 `{ code, message, data }` 的响应结构，组件拿到的是
  解包后的 `data`。
- Element Plus 组件按需引入（配置好 `unplugin-vue-components` /
  `unplugin-auto-import` 后自动导入）；表单校验规则应尽量和后端的 Bean Validation
  规则保持一致。
- 2 空格缩进（和后端 `.editorconfig` 的 4 空格不同）——两套风格不要混用。

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
