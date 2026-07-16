## Why

`PositionServiceImpl.getPage`/`getById`（任职管理，`cn.nihility.rbac.user` 包）目前用"应用层拼接"的方式解析任职记录关联的用户姓名、组织名称：先查任职记录，再分别对 `tab_user`、`tab_org` 发起按 id 批量 `IN` 查询，最后在 Java 里用 `Map` 手动拼装成 `PositionVO`。这是一次任职记录列表查询触发最多 3 条 SQL（1 次任职分页 + 1 次用户批量查询 + 1 次组织批量查询），且用户/角色相关的多表关联本应交给数据库一次 JOIN 完成。用户明确要求：任职管理的多表查询改为使用 MyBatis XML 编写（单条 SQL JOIN），XML 脚本统一放在 `backend/src/main/resources/mybatis/mapper/` 下，这也是项目既定但此前从未真正落地的约定（`CLAUDE.md`/`application.yml` 均已预留 `mybatis-plus.mapper-locations: classpath*:mybatis/mapper/*.xml`，此前项目里一直没有任何 XML 文件）。

## What Changes

- 新增 `backend/src/main/resources/mybatis/mapper/UserPositionMapper.xml`，用一条 SQL（`tab_user_position LEFT JOIN tab_user LEFT JOIN tab_org`）分别实现：
  - 按 `orgId` 分页查询任职记录（含 `userName`、`orgName`），替代原 `PositionServiceImpl.getPage` 里"任职分页 + 用户批量查询 + 组织批量查询"的三次查询拼装
  - 按 `id` 查询单条任职记录详情（含 `userName`、`orgName`），替代原 `getById` 里同样的拼装逻辑
- `UserPositionMapper` 接口新增 `selectPositionPage`/`selectPositionDetail` 两个自定义方法声明（对应上面的 XML SQL），其余单表 CRUD 方法继续复用 `BaseMapper`，不受影响。
- `PositionServiceImpl` 改为直接调用这两个新方法，删除 `toVOListWithNames` 及其依赖的 `UserMapper`/`OrgMapper` 字段（任职管理不再需要单独持有这两个 Mapper）。
- 纯内部实现调整：接口路径、请求/响应字段、排序规则、分页语义、状态语义、前端页面均不受影响，`position-management` 现有 spec 描述的行为保持不变，因此本次不修改任何 spec 需求文本。

## Capabilities

本次不新增/修改任何 capability 的行为需求（`position-management` 对外行为不变），仅调整持久层实现方式，符合项目"多表查询统一用 MyBatis XML 编写"的既定约定。

## Impact

- 后端：新增 `backend/src/main/resources/mybatis/mapper/UserPositionMapper.xml`；修改 `cn.nihility.rbac.user.mapper.UserPositionMapper`（新增两个方法声明）、`cn.nihility.rbac.user.service.impl.PositionServiceImpl`（改用新方法，删除批量拼装逻辑及不再需要的 `UserMapper`/`OrgMapper` 依赖）。
- 前端：无变化。
- 数据库：无迁移变更（不涉及表结构）。
- 不涉及 `user-management` 内嵌任职子表单（`UserServiceImpl` 里为用户详情回填任职列表的多表查询）——那是另一处独立的多表查询，范围收敛到本次明确点名的"任职管理"（`PositionController`/`PositionServiceImpl`）入口，是否一并改造留待用户后续单独确认。
