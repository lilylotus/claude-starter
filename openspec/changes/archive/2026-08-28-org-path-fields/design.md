## Context

- `tab_org` 当前字段：`id`/`name`/`code`/`parent_id`/`parent_code`/`status`/`show_order`/`remark`/`ext1`~`ext10`/审计字段（`V1__init_schema.sql`）。`parent_code` 已经是一个成熟的"冗余派生字段"先例（`org-add-parent-code` change）：创建时按 `parentId` 解析父组织当前 `code` 写入；更新时仅当 `parentId` 变化才重新解析；组织自身 `code` 变化时用 `OrgMapper.updateChildrenParentCode` 级联更新**直属**子组织（只下沉一层，因为 `parentCode` 只存"上一级"的编码，不含更深祖先信息）。本次三个路径字段与 `parentCode` 的关键区别：路径字段是**完整祖先链**，因此上级变化/改名的级联范围是**全部子孙**，不能照搬"只下沉一层"的实现。
- `OrgServiceImpl.create`/`update` 已经是路径字段维护的正确落点：这两个方法是组织新增/更新的**真正执行点**（无论审批开关开启后由 `ApprovalRequestServiceImpl` 在审批通过时调用，还是开关关闭时由 `OrgController` 直接调用，最终都汇聚到这两个方法），不需要在审批模块或 Controller 层额外处理路径字段维护时机。
- `OrgDescendantExpander.expandWithDescendants`：当前实现是"整表加载未删除组织到内存 + 按 `parentId` 建邻接表 + BFS"，被 `OrgScopeServiceImpl`（管理员管辖范围）与 `AppSyncOrgScopeResolver`（应用同步组织范围）两处复用，均为每次请求实时调用、不缓存。运行环境 MySQL 5.7，没有 `WITH RECURSIVE`，这是当初选择整表 BFS 而非 SQL 递归的原因（见该类注释）。
- 项目现有手写 SQL 约定：自定义 Mapper XML 放在 `backend/src/main/resources/mybatis/mapper/`；不使用窗口函数/CTE/`WITH`/JSON_TABLE 等 MySQL 8.0+ 专属语法。字段命名需检查跨数据库保留字（MySQL/PostgreSQL/Oracle/SQL Server）。

## Goals / Non-Goals

**Goals:**
- 新增 `org_path`/`org_name_path`/`org_parent_path` 三个字段，维护规则清晰、级联正确、事务安全。
- 用一条基于路径前缀的 SQL 查询替代 `OrgDescendantExpander` 的整表 BFS，输入输出契约完全不变。
- 提供存量数据回填迁移脚本，MySQL 5.7 兼容。

**Non-Goals:**
- 不改变 `parentCode` 现有的维护逻辑（继续只下沉一层，两套机制并存，互不影响）。
- 不在本次强制把三个新字段加入任何现有 API 响应体（`OrgVO`/`OrgTreeNodeVO`）——是否暴露、暴露到哪些接口留给后续按需决定，本 change 只保证字段在数据库里正确维护；design 里给出可选建议，tasks.md 会把它标为可选任务。
- 不涉及 `app-sync-notify-pull`、"4A 主数据推拉同步"等任何依赖本次字段的下游能力，那些是独立的后续 change。
- 不改变组织树查询接口（`GET /api/orgs/tree` 等）现有的管辖范围收紧算法（"虚拟根节点"过滤逻辑），只替换其依赖的 `OrgDescendantExpander` 内部实现。

## Decisions

### 1. 路径分隔符与内容：id 路径 + 名称路径分开维护，都不做转义
`orgPath` 用组织 `id`（数字）拼接，`orgNamePath` 用组织 `name`（可能含任意字符，含 `/`）拼接。**不对 `orgNamePath` 里出现的 `/` 做转义**：`orgNamePath` 定位是"给人看的完整路径展示"（如日志、详情页面包屑），不是用于程序化前缀匹配的字段——前缀匹配一律走 `orgPath`（纯数字 id，`/` 只会出现在分隔位置，无歧义）。如果组织名称本身包含 `/`，`orgNamePath` 展示上可能有一点视觉歧义，但不影响任何业务逻辑判断，接受这个权衡而不引入转义规则增加复杂度。

### 2. 级联更新用 SQL 端"前缀替换"一次搞定，不在 Java 侧递归
`parentId` 变化后，组织 C 自身及其全部子孙的 `orgPath` 都需要把"旧的 C 及其祖先前缀"换成"新的 C 及其祖先前缀"。用一条 `UPDATE ... WHERE org_path = :oldCPath OR org_path LIKE CONCAT(:oldCPath, '/%')` 配合 `SET org_path = CONCAT(:newCPrefix, SUBSTRING(org_path, LENGTH(:oldCPath) + 1))` 的写法，一次 SQL 覆盖 C 自身与全部子孙——`SUBSTRING(org_path, LENGTH(:oldCPath) + 1)` 取出"旧路径里 C 自身 id 及其后的部分"（对 C 自身是空串，对子孙是从 C 的 id 开始往后的完整子路径），拼接新前缀即为新路径。

**不用 `REPLACE(org_path, oldPrefix, newPrefix)`**：`REPLACE` 是"替换字符串中所有出现的子串"，如果旧前缀恰好在路径的非开头位置也构成一次字面匹配（`orgNamePath` 场景下，某个名称片段在祖先链里重复出现时更容易发生），会产生错误替换。`SUBSTRING` 按固定长度切分，只锚定在字符串开头，没有这个歧义，`orgPath`（纯数字+分隔符）和 `orgNamePath`（任意文本）都适用同一个安全写法。

改名级联同理：组织 B 改名，用同样的 `SUBSTRING` 前缀替换法更新 B 自身及全部子孙的 `orgNamePath`（`orgPath`/`orgParentPath` 不受名称变化影响，不需要一起改）。

**备选方案**：Java 侧查出全部受影响子孙、逐条重新拼接后批量更新。未采用：多一次全表查询 + 应用内存拼接 + 批量更新（MyBatis-Plus 批量更新在没有专门配置的情况下会退化成循环单条更新），性能明显劣于一条覆盖全部受影响行的 `UPDATE`，且逻辑更复杂。

### 3. `OrgMapper` 新增两个级联更新方法，写在 XML 里（不是 `LambdaUpdateWrapper` 能表达的）
`updateChildrenParentCode` 用 `LambdaUpdateWrapper` 是因为它是"精确匹配 `parentId` 后设置一个字面量"，条件和赋值都简单。本次的 `SUBSTRING`/`LIKE` 前缀替换涉及把列值本身作为函数参数做字符串运算，`LambdaUpdateWrapper` 表达不了，改用手写 SQL：

```xml
<update id="cascadeUpdateOrgPath">
  UPDATE tab_org
  SET org_path = CONCAT(#{newPrefix}, SUBSTRING(org_path, #{oldPrefixLength} + 1)),
      org_parent_path = CASE
          WHEN LOCATE('/', CONCAT(#{newPrefix}, SUBSTRING(org_path, #{oldPrefixLength} + 1))) = 0 THEN NULL
          ELSE SUBSTRING(CONCAT(#{newPrefix}, SUBSTRING(org_path, #{oldPrefixLength} + 1)),
                          1,
                          LENGTH(CONCAT(#{newPrefix}, SUBSTRING(org_path, #{oldPrefixLength} + 1)))
                          - LOCATE('/', REVERSE(CONCAT(#{newPrefix}, SUBSTRING(org_path, #{oldPrefixLength} + 1)))))
      END
  WHERE org_path = #{oldPrefix} OR org_path LIKE CONCAT(#{oldPrefix}, '/%')
</update>
```
（`org_parent_path` = 新 `org_path` 去掉最后一段：用 `REVERSE` + `LOCATE('/')` 找到最后一个分隔符位置，MySQL 5.7 兼容写法，不依赖任何 8.0+ 函数如 `REGEXP_REPLACE`。）`cascadeUpdateOrgNamePath` 结构相同，替换成 `org_name_path` 列、不涉及 `org_parent_path`。两个方法都只接收"旧前缀""旧前缀长度""新前缀"三个参数，SQL 本身不拼接任意调用方字符串到语句结构里（值全部走 `#{}` 占位符），不存在注入风险。

### 4. `OrgDescendantExpander` 改造为路径前缀查询，签名不变
```java
public Set<Long> expandWithDescendants(Set<Long> rootOrgIds) {
    // 按 rootOrgIds 查出这些组织当前的 org_path（可能有的 id 已不存在/已删除，跳过）
    // 对每个 org_path，查询 WHERE (org_path = :path OR org_path LIKE CONCAT(:path, '/%'))
    //   AND status != DELETED，把结果 id 全部收集起来，再并上 rootOrgIds 自身
}
```
一次方法调用内，`rootOrgIds` 可能有多个（多条管辖范围配置），每个根各自一次 `LIKE` 查询（或用 `OR` 拼成一条 SQL，视 `rootOrgIds` 数量决定，通常很小，几次查询即可，不必刻意合并）。相比原来"无论 `rootOrgIds` 多大，都整表加载"，新实现的开销只取决于命中的行数，不取决于组织总数。

**备选方案**：给 `org_path` 建立支持中缀匹配的全文索引或额外的"闭包表"（`org_closure(ancestor_id, descendant_id, depth)`）。未采用：闭包表能获得更灵活的查询能力（如"查询某组织的直接父级"），但维护成本远高于路径字段的 `SUBSTRING` 级联更新，当前需求（前缀匹配子孙）用路径字段 + `LIKE` 前缀索引已经足够，符合"不为不需要的灵活性支付复杂度"的原则。

### 5. 索引选型：普通 `KEY` 索引即可，不需要特殊前缀索引类型
MySQL 的 B-Tree 索引天然支持 `LIKE 'prefix%'`（不以通配符开头）走最左前缀扫描，`org_path`/`org_name_path` 均建普通 `KEY` 索引（`VARCHAR` 类型 `org_path` 长度上限按树深度预留，如 `VARCHAR(255)`，足够容纳几十层深度的 id 路径）。不需要引入 MySQL 8.0+ 才有的函数索引或倒排索引方案。

### 6. 存量数据回填：按层级多轮 `UPDATE`，不递归 SQL
一条 Flyway 迁移脚本内，按"深度"从根到叶多轮执行：
1. 第 1 轮：`UPDATE tab_org SET org_path = CAST(id AS CHAR), org_name_path = name, org_parent_path = NULL WHERE parent_id = 0`（顶级组织）。
2. 第 2 轮起：`UPDATE tab_org c JOIN tab_org p ON c.parent_id = p.id SET c.org_path = CONCAT(p.org_path, '/', c.id), c.org_name_path = CONCAT(p.org_name_path, '/', c.name), c.org_parent_path = p.org_path WHERE p.org_path IS NOT NULL AND c.org_path IS NULL`（只更新"父级路径已算好、自身还没算"的行）。
3. 重复第 2 步固定次数（如 20 轮，覆盖树深度上限；某一轮如果 `ROW_COUNT()` 为 0 说明已经全部算完，但 Flyway 迁移脚本是纯 SQL、拿不到上一条语句的受影响行数做条件循环，所以采用"固定轮数、多做几轮不影响正确性"的保守写法——已经算好的行 `c.org_path IS NULL` 条件不再满足，不会被重复处理）。

已被逻辑删除（`status = -1000`）的组织同样参与回填（不排除 `status` 条件）：它们的路径字段值不影响任何查询语义（已删除组织不会出现在 `OrgDescendantExpander` 的候选查询里，因为该查询本身会过滤 `status != DELETED`），但保持字段有值而不是 `NULL`，避免后续该组织被误判为"祖先链断裂"。

### 7. 是否暴露到 `OrgVO`/`OrgTreeNodeVO`：建议加，不强制
建议把 `orgPath`/`orgNamePath` 加入 `OrgVO`（组织详情），供前端组织详情页面展示完整路径面包屑，或供审计/日志场景直接使用而不必逐级查父级名称；`orgParentPath` 展示意义不大（前端已有 `parentName` 展示直接上级），可以不加。`OrgTreeNodeVO`（树节点）建议不加——树形结构本身已经隐含了路径信息，加了反而是冗余负担。这部分作为 tasks.md 里的可选任务，不阻塞本 change 的核心目标（字段落库 + 维护正确 + 查询优化）。

## Risks / Trade-offs

- [级联 `UPDATE` 影响行数在组织树很深/很宽时可能较大，单条 SQL 执行时间变长] → 组织树规模在企业级 RBAC 场景通常是几百到几千个节点，单表 `UPDATE` 配合 `org_path` 前缀索引足以应对；即使达到万级节点也远小于典型 OLTP 表的批量更新阈值，暂不需要分批处理，后续如遇到实际性能问题再考虑分批。
- [`org_name_path` 因为不转义 `/`，理论上无法从纯文本反解析出"这一段是哪个组织"] → 符合预期：`org_name_path` 的定位就是展示用途，不是程序化解析的数据源，需要程序化路径信息一律用 `org_path`（纯数字 id，无歧义）。
- [存量数据回填的"固定轮数"如果树深度超过预设轮数（如 20 层），会有一部分深层组织路径回填不完整] → 20 层的组织层级在实际业务里极其罕见（多数企业组织架构 5-8 层封顶），迁移脚本会在注释里写明这个假设上限，并给出"如果实际树深度超过预设轮数，重新执行等价的补充 `UPDATE` 语句"的排查提示。
- [两套"级联字段"并存（`parentCode` 只下沉一层 vs. 路径字段级联全部子孙）容易让后来的开发者混淆维护规则] → 在 `OrgServiceImpl.update` 里两处级联更新紧邻写在一起，并各自用注释清楚标注级联范围的差异原因（`parentCode` 只存一层信息 vs. 路径字段是完整链条），降低误用风险。

## Migration Plan

1. 新增 Flyway 迁移脚本：`tab_org` 增加 `org_path`/`org_name_path`/`org_parent_path` 三列 + 各自的普通索引；同一脚本内按 Decision 6 的多轮 `UPDATE` 回填存量数据。
2. `OrgEntity`/`OrgMapper`（新增两个级联更新方法 + XML）/`OrgConvert`（如决定加入 `OrgVO`）。
3. `OrgServiceImpl.create`：写入路径字段。`update`：`parentId` 变化时级联更新 `org_path`/`org_parent_path`；`name` 变化时级联更新 `org_name_path`。
4. `OrgDescendantExpander`：替换为路径前缀查询实现，`OrgScopeServiceImpl`/`AppSyncOrgScopeResolver` 不需要改动。
5. 单元/集成测试覆盖：创建、变更上级组织级联、改名级联、`OrgDescendantExpander` 新实现与旧实现结果一致性。
