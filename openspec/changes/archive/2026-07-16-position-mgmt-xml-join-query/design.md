## Context

`CLAUDE.md` 早已约定"以后需要手写 SQL 的自定义 Mapper XML 统一放在 `src/main/resources/mybatis/mapper/` 下"，`application.yml` 也已配置好 `mybatis-plus.mapper-locations: classpath*:mybatis/mapper/*.xml`，但截至本次改动，项目里从未真正写过一个 XML 文件——所有模块（`org`/`user`/`menu` 等）遇到"详情/列表需要回填关联表名称"的场景，统一用的是"先查主表，再对关联 id 集合发起一次 `IN` 批量查询，最后在 Java 里用 `Map` 拼装"这种模式（`OrgServiceImpl.toVOListWithParentName`、`MenuServiceImpl.toVOListWithParentName`、`PositionServiceImpl.toVOListWithNames` 等）。这种模式在"只有一个关联表"时能接受，但任职管理需要同时关联用户表和组织表两张表，已经是 3 条 SQL 顺序执行；用户明确指出这类多表查询应该用 MyBatis XML 一次 JOIN 完成，而不是应用层拼装。

本次改动只针对用户点名的"任职管理"入口（`cn.nihility.rbac.user.controller.PositionController` → `PositionServiceImpl.getPage`/`getById`）。`UserServiceImpl` 里为用户详情回填任职列表（含组织名称）的另一处类似拼装逻辑不在本次范围内，是否同步改造留待用户后续单独确认，避免在没有明确授权的情况下扩大改动面。

## Goals / Non-Goals

**Goals:**
- 任职管理的两个多表查询（按组织分页查询、按 id 查询详情）改为 MyBatis XML 里的单条 SQL JOIN，替代原来的"3 次查询 + Java 拼装"。
- 保持对外行为完全不变：接口路径、请求/响应字段形状、排序规则（`showOrder` 降序、`id` 升序）、"用户/组织若已被逻辑删除仍返回记录、只是关联名称拿不到时为 null"的既有语义（原 Java 实现里 `Map.get()` 查不到时天然返回 `null`，等价于 SQL 层的 `LEFT JOIN`）。
- 建立项目里第一个 MyBatis XML Mapper 文件，作为后续模块遇到多表查询时的参照范例。

**Non-Goals:**
- 不改造 `user-management` 内嵌任职子表单里类似的多表拼装逻辑（`UserServiceImpl` 里为用户详情回填任职记录 + 组织名称的部分）——范围收敛到用户明确点名的"任职管理"独立入口。
- 不改变任何 API 契约、前端代码、数据库表结构。
- 不引入分页缓存、二级缓存等性能优化机制，本次只是把"多次查询"合并为"一次 JOIN"，不做更进一步的性能工程。

## Decisions

- **XML 文件命名与位置**：`backend/src/main/resources/mybatis/mapper/UserPositionMapper.xml`，`namespace` 对应 `cn.nihility.rbac.user.mapper.UserPositionMapper`，与 Mapper 接口同名，是 MyBatis XML Mapper 的标准约定，便于按接口名直接定位对应的 XML。
- **接口方法与 XML 语句一一对应，新增而非替换 `BaseMapper` 能力**：`UserPositionMapper` 仍然 `extends BaseMapper<UserPositionEntity>`（单表 CRUD：`insert`/`updateById`/`selectById` 等继续复用，不用 XML 重写），只新增两个方法声明 `selectPositionPage`/`selectPositionDetail` 对应 XML 里的两条自定义 SQL；这是 MyBatis-Plus 项目里"MyBatis-Plus 负责单表 CRUD，MyBatis 原生 XML 负责多表查询"的标准分工方式，两者共存于同一个 Mapper 接口，不需要拆成两个接口。
- **分页方法直接用 `IPage<PositionVO>` 作为返回类型和首参数类型**：项目已经在 `MybatisPlusConfig` 里全局注册了 `PaginationInnerInterceptor`，该插件按"首参数是否为 `IPage`"识别分页请求并自动改写 SQL 追加 `LIMIT`/`OFFSET`、统计 `total`，对 XML 自定义 SQL 同样生效（不要求一定是 `BaseMapper` 内置方法），因此分页方法签名为 `IPage<PositionVO> selectPositionPage(IPage<?> page, @Param("orgId") Long orgId, @Param("deletedStatus") int deletedStatus)`，XML 里只写不带 `LIMIT` 的基础查询语句，分页由插件透明处理，与 `BaseMapper.selectPage` 用法保持一致的心智模型。
- **查询结果直接映射为 `PositionVO`，不新增中间 DTO/`resultMap`**：`mybatis.conf` 已全局开启 `mapUnderscoreToCamelCase`，只要 SQL 里把 JOIN 出来的列用下划线命名别名（如 `user_name`、`org_name`），MyBatis 就能直接 `resultType="cn.nihility.rbac.user.dto.PositionVO"` 自动映射到位，不需要额外定义 `<resultMap>` 或新增一个"扁平化查询行"类型，减少样板代码。
- **用 `LEFT JOIN` 而非 `INNER JOIN`**：保持与原 Java 实现完全一致的语义——原来的批量 `IN` 查询在关联的用户/组织已被删除（id 找不到）时，`Map.get()` 返回 `null`，对应字段就是 `null`，任职记录本身依然正常返回；`LEFT JOIN` 是这个语义在 SQL 层的直接对应，`INNER JOIN` 会在用户/组织缺失时把整条任职记录从结果里过滤掉，属于行为变化，不采用。
- **`status != DELETED` 的过滤条件只作用于 `tab_user_position` 主表，不对 `tab_user`/`tab_org` 的状态做任何过滤**：与原实现一致——原 Java 拼装逻辑查询用户/组织名称时没有过滤对方的 `status`（不管用户/组织本身是启用/停用/删除，只要 id 能查到就回填名称），SQL JOIN 版本同样不对 `u.status`/`o.status` 加条件，避免引入原来没有的过滤行为。
- **删除 `PositionServiceImpl` 里的 `UserMapper`/`OrgMapper` 依赖**：这两个 Mapper 此前只在 `toVOListWithNames` 里使用，改用 XML JOIN 后不再需要，随 `toVOListWithNames` 方法一起删除，`@RequiredArgsConstructor` 生成的构造器自动收窄为只依赖 `UserPositionMapper`。

## Risks / Trade-offs

- **[风险] XML 里的 JOIN 条件（`up.user_id = u.id`、`up.org_id = o.id`）需要跟实体里的关联字段手动保持一致，不像 Java 端那样有编译期类型检查** → 可接受的既有权衡：这是所有手写 SQL Mapper 的通用代价，项目里迟早要引入第一个 XML 文件；后续如果 `tab_user`/`tab_org` 的主键类型或表名变化，需要同步改 XML，这一点在 XML 头部注释里已注明。
- **[权衡] 只改造"任职管理"这一个入口，`user-management` 内嵌任职子表单的类似拼装逻辑暂不改造，项目里会短暂存在"同一份数据、两种查询实现方式"并存的局面** → 与用户本次明确点名的范围一致（"任职管理"），避免未经确认扩大改动面；后续如果用户要求把 `user-management` 那一处也改为 XML，应作为独立的 change 处理，到时可以直接复用本次新增的 `UserPositionMapper.xml` 里的 JOIN 语句结构。

## Migration Plan

无数据库迁移变更，仅新增一个 MyBatis XML Mapper 文件和对应的 Java 方法声明，不改表结构。
