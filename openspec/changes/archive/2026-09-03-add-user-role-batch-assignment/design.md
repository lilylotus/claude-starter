## Context

已确认的现状（调研得出，决定了本次设计的落点）：

- 系统里"角色"目前只关联"管理员"（`tab_admin_role`），没有任何"普通用户直接持有角色"的
  数据结构；本次需要新增。
- 系统里已经存在一套成熟的"组织范围 + 用户属性条件匹配用户"实现，来自
  `app-access-authorization` 能力（`PolicyExecutionServiceImpl#matchByOrgScope`/
  `matchByUserAttrs`），但属性条件只支持 `tab_user`，不支持 `tab_user_position`；这部分
  结论与取舍见下面 Decision 2，与首次设计时一致，未变。
- **本次是对同一个未归档、未提交的 change 的二次设计**：初版把"批量添加用户角色"实现成了
  一次性批量操作（配置条件 → 预览 → 执行写入 `tab_user_role`，写完即止），代码已经写完并
  通过全部测试，但用户实际验证后发现"组织范围配置好之后，这个组织后续新增的人不会自动
  获得角色"——不符合预期。用户明确要求改为**持久规则 + 组织/用户/任职变更后自动重新计算**，
  并且确认"不再匹配时收回角色"用整体重建语义（与 `app-access-authorization` 策略规则一致），
  以及"因持有某角色被批量转为管理员的人，角色被规则收回时联动停用其管理员身份"。本 design.md
  据此整体替换 Decision 1/3，新增 Decision 3a/7，Decision 2/4/5/6 的核心结论保留但按新模型
  调整措辞。
- `app-access-authorization` 已有成熟的"组织/用户/任职变更后自动重新执行"机制可直接复用：
  - 领域变更统一通过 `DomainChangeEvent`（`cn.nihility.rbac.sync.event`）发布，
    `DomainChangeEventProcessor#process`（`cn.nihility.rbac.sync.event.support`，运行在
    Disruptor 消费者线程上）是唯一的消费入口，已经在处理完"变更流水 + 下游应用通知"之后，
    对 `dataType` 属于 `ORG`/`USER`/`POSITION` 的事件调用
    `reExecutePoliciesIfNeeded(event)`：查出全部启用状态的策略，逐条调用
    `PolicyExecutionService#execute(policyId, event.getOperator())`，单条失败仅记日志、
    不影响其余策略、不影响触发变更的原始写请求。
  - 归档变更 `2026-08-21-close-sso-log-and-policy-gaps` 的 design.md 记录过一个关键教训：
    自动重新执行**必须显式传入 `event.getOperator()`**，不能依赖
    `CurrentOperatorService#resolveUserId()`——后者从 HTTP 请求上下文解析当前登录用户，
    而 Disruptor 消费者线程不处于任何请求上下文中，调用会抛异常并被"单条失败不影响其余"
    的 try/catch 悄悄吞掉，表现为"接入了但从来没真正执行成功"。本次新增的执行方法必须同样
    以显式参数接收操作人，不能在内部调用 `CurrentOperatorService`。
  - `app-access-authorization` 的授权计算结果落在独立的 `tab_app_access_policy_grant`
    表（`policy_id` + `user_id` + `app_id`），按 `policy_id` 整体重建（先算出本次命中集合，
    与该策略此前的授权记录做整体替换），而不是维护一张"当前生效授权"主表再靠状态位过滤——
    这个"计算结果表 + 整体重建"模式本次直接套用。

## Decisions

### Decision 1：用"用户角色规则"持久实体替代一次性批量操作，取消 `tab_user_role` 主表

不再有独立的 `tab_user_role` 表；改为四张表：

```sql
-- 规则主表：一个角色可以有多条规则，每条规则独立维护自己的条件与执行状态
CREATE TABLE IF NOT EXISTS `tab_user_role_rule` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `role_id`        BIGINT       NOT NULL COMMENT '目标角色 id，关联 tab_role.id，不建物理外键',
    `name`           VARCHAR(128) NOT NULL COMMENT '规则名称，便于同一角色下管理多条规则',
    `remark`         VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    `last_exec_time` DATETIME     NULL COMMENT '最近一次执行时间，从未执行过为空',
    `last_exec_by`   VARCHAR(64)  NULL COMMENT '最近一次执行人（人工保存触发时为操作人，事件自动触发时为原始事件操作人）',
    `create_by`      VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`      VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_user_role_rule_role_id` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则表：组织范围/用户属性条件持久化，事件驱动自动重算';

-- 规则组织范围条件，字段形状对齐 tab_app_access_policy_org_scope
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_org_scope` (
    `id`               BIGINT     NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`          BIGINT     NOT NULL COMMENT '所属规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `org_id`           BIGINT     NOT NULL COMMENT '组织 id，关联 tab_org.id，不建物理外键',
    `include_children` TINYINT    NOT NULL DEFAULT 0 COMMENT '是否包含递归子组织：0=否，1=是',
    `create_by`        VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`        VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_org_scope` (`rule_id`, `org_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则组织范围条件表';

-- 规则用户属性条件，字段形状对齐 tab_app_access_policy_user_attr，metadata_field_id
-- 允许关联 biz_type=USER 或 biz_type=POSITION（比现成的应用访问授权多一个域）
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_user_attr` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`           BIGINT       NOT NULL COMMENT '所属规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '关联的元数据字段 id，biz_type 为 USER 或 POSITION，不建物理外键',
    `operator`          VARCHAR(8)   NOT NULL COMMENT '运算符：EQ=等于，NE=不等于，IN=属于多值',
    `attr_value`        VARCHAR(255) NOT NULL COMMENT '比较值，EQ/NE 为单个值，IN 为逗号分隔的多个值',
    `create_by`         VARCHAR(64)          DEFAULT NULL COMMENT '创建人',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`         VARCHAR(64)          DEFAULT NULL COMMENT '更新人',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_user_attr` (`rule_id`, `metadata_field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则用户属性条件表';

-- 规则执行结果表：按 rule_id 整体重建，是"用户是否持有某角色"的唯一数据来源，
-- 直接取代最初设计里独立的 tab_user_role 主表
CREATE TABLE IF NOT EXISTS `tab_user_role_rule_grant` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `rule_id`     BIGINT   NOT NULL COMMENT '产生该关联的规则 id，关联 tab_user_role_rule.id，不建物理外键',
    `user_id`     BIGINT   NOT NULL COMMENT '用户 id，关联 tab_user.id，不建物理外键',
    `role_id`     BIGINT   NOT NULL COMMENT '角色 id，冗余存储自 tab_user_role_rule.role_id，避免查询时反查规则表',
    `create_by`   VARCHAR(64)        DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   VARCHAR(64)        DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tab_user_role_rule_grant` (`rule_id`, `user_id`),
    KEY `idx_tab_user_role_rule_grant_role_user` (`role_id`, `user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色规则计算结果表，按 rule_id 整体重建';
```

"用户 U 当前是否持有角色 R"统一定义为
`EXISTS (SELECT 1 FROM tab_user_role_rule_grant WHERE user_id = U AND role_id = R)`——
一个用户的同一个角色可能被多条规则同时命中（各自一行，`rule_id` 不同），任一规则仍然命中
即视为持有；只有当**全部**曾经命中过的规则都不再命中时，才算真正失去这个角色。

本次不引入"规则启用/停用"这类暂停状态（对比 `tab_app_access_policy` 有 `status` 字段）：
规则要么存在要么删除，存在就参与自动重算，删除就级联收回（Decision 3a）。不做暂停状态是
为了避免"暂停期间到底要不要保留已发出的关联"这类额外的状态语义分叉，本次场景下删除后
重新创建的成本很低（重新配置条件、立即重新执行），不需要专门的暂停态。

### Decision 2：条件匹配组件维持不变

`UserMatchConditionResolver`（首次设计 Decision 2 的产物，已实现并测试通过）不需要跟随
本次调整改动：它只负责"给定组织范围条件 + 用户属性条件，算出当前命中的用户 id 集合"这一
纯函数式计算，与"结果保存在哪张表、要不要持久化为规则"无关，规则执行引擎直接复用它。

### Decision 3：规则执行——整体重建 + 保存即执行 + 事件驱动自动重算

新增 `UserRoleRuleExecutionService#execute(ruleId, operator)`（`operator` 必须显式传入，
不依赖 `CurrentOperatorService`，理由见 Context 里引用的历史教训），单次执行流程：

1. 加载规则（含其组织范围条件、用户属性条件），不存在则抛异常。
2. 调用 `UserMatchConditionResolver` 算出当前命中用户 id 集合 `matched`。
3. 查询 `tab_user_role_rule_grant` 中 `rule_id` 等于本规则的既有记录，得到
   `previouslyGranted`。
4. `toAdd = matched - previouslyGranted`：批量插入新的 `tab_user_role_rule_grant` 记录
   （一次批量 SQL）。
5. `toRemove = previouslyGranted - matched`：删除这些 `(rule_id, user_id)` 记录；对
   `toRemove` 中的每个用户，再查一次"该用户该角色是否还有其他规则命中的记录"——若没有，
   触发 Decision 7 的"角色收回联动停用管理员"检查。
6. 更新规则的 `last_exec_time`/`last_exec_by`。
7. 整个过程在一个数据库事务内完成（含级联的管理员停用），单条规则执行失败不影响其他规则
   （由调用方 Decision 3a 的 try/catch 保证）。

触发时机：
- **保存即执行**：规则新增、编辑（条件变化）保存成功后，同步调用一次 `execute`，让操作人
  立刻看到本次配置产生的效果，不需要等下一次组织/用户/任职变更事件。
- **事件驱动自动重算**：在 `DomainChangeEventProcessor#process` 里，紧挨着现有的
  `reExecutePoliciesIfNeeded(event)` 调用之后，新增一个并列的
  `reExecuteUserRoleRulesIfNeeded(event)`：同样只对 `dataType` 属于
  `ORG`/`USER`/`POSITION` 的事件生效，查出**全部**用户角色规则（不做状态过滤，因为本能力
  没有暂停态），逐条调用 `execute(rule.getId(), event.getOperator())`，单条规则执行异常
  仅记录日志、不影响其余规则、不影响触发变更的原始写请求——完全比照
  `reExecutePoliciesIfNeeded` 的既有实现风格。

### Decision 3a：规则删除——级联收回

删除规则时，SHALL 先按"命中集合为空"执行一次 Decision 3 的第 3-5 步（即把该规则名下全部
`tab_user_role_rule_grant` 记录当作 `toRemove` 处理，触发对应的联动停用检查），再物理删除
规则本身及其组织范围/用户属性条件子表记录，全部在同一个事务内完成。

### Decision 4：角色管理页面——规则列表 + 新增/编辑表单

角色管理页面（角色列表操作列）"批量规则"入口打开后：

- 规则列表：展示该角色当前的全部规则（名称、备注、最近执行时间、当前命中人数——通过
  `COUNT(DISTINCT user_id) FROM tab_user_role_rule_grant WHERE rule_id = ?` 现查，规则
  条数通常不多，不需要为此额外维护冗余计数字段），每行提供"编辑""删除"操作。
- 新增/编辑规则表单：组织范围条件、用户属性条件两组动态多行子表单（与首次设计 Decision 4
  描述的表单交互完全一致：组织树单选 + 含子组织复选框；属性字段下拉合并展示
  `bizType=USER`/`POSITION` 两类元数据字段，带域前缀区分），表单内提供"预览"按钮（调用
  独立的预览接口，不依赖已保存的规则，用给定条件现算命中用户分页列表），两类条件均未配置
  时"预览"与"保存"均禁用。保存成功后立即按 Decision 3 执行一次，提示"规则已保存，当前
  命中 N 名用户"。
- 删除规则前提示"删除后将收回该规则已经产生的角色关联"，二次确认。

### Decision 5：管理员管理按角色批量设置管理员——匹配来源改为规则计算结果表

与首次设计 Decision 5 的整体流程一致，仅把"持有该角色"的判定来源从 `tab_user_role` 改为
`tab_user_role_rule_grant`（见 Decision 1 的判定定义）：

- 预览：查出 `EXISTS (... tab_user_role_rule_grant WHERE role_id = 选中角色)` 且状态启用
  的全部用户，按是否已关联未删除管理员分"将新建管理员"/"将补充角色"两组。
- 执行："将新建管理员"分组批量创建 `tab_admin` 记录（`name`/`code`/`userId`/`roleIds`/
  `orgScopes` 同首次设计），并把新记录的 `auto_created_role_id` 置为本次选择的角色 id
  （Decision 7 的联动停用判断依据）；"将补充角色"分组仅追加一条 `tab_admin_role`，
  **不**设置 `auto_created_role_id`（该管理员身份不是因这次操作才存在）；管理员编码冲突
  时跳过并报告，逻辑不变。

### Decision 6：权限点与操作留痕

沿用首次设计确定的两个权限点编码（`RoleManagement:role:batchAssignUser`、
`AdminManagement:admin:batchPromoteByRole`），语义随入口形态调整（前者现在控制"批量规则"
入口的查看/新增/编辑/删除，后者不变）；操作留痕的范围结论不变（不写入角色/管理员详情页的
"操作历史"列表，理由同首次设计 Decision 6）——规则的新增/编辑/删除、自动重算产生的角色
增减，本次均不接入操作历史，如后续需要审计留待独立 change。

### Decision 7（新增）：角色收回联动停用自动创建的管理员

`tab_admin` 新增可空列 `auto_created_role_id BIGINT NULL`："若非空，表示该管理员记录是
通过'按角色批量设置管理员'为这个角色 id 自动创建的；人工新增管理员、或通过'补充角色'方式
获得角色的管理员，本列为 `NULL`。"

在 Decision 3 第 5 步"确认某用户某角色已不再被任何规则命中"时，追加检查：

```
SELECT id FROM tab_admin WHERE user_id = ? AND auto_created_role_id = ? AND status != -1000
```

若查到记录，SHALL 将其 `status` 置为停用（`3000`），`update_by` 记为触发本次重算的操作人
（人工保存规则触发时是操作人自己；事件自动触发时是 `event.getOperator()`）。**不**清空
`auto_created_role_id`、**不**物理删除、**不**级联处理该管理员的 `tab_admin_role`/
`tab_admin_org_scope`——停用已经足以阻止其登录管理台（复用现有的"管理员状态"语义），
保留其余数据便于后续人工复核或重新启用。人工创建的管理员、或仅"补充角色"获得该角色的
管理员（`auto_created_role_id` 为空）不受本规则影响，即使他们也持有被收回的角色。

## Risks / Trade-offs

- **自动停用管理员是有一定破坏性的联动行为**：已通过"只对 `auto_created_role_id` 精确匹配
  的管理员生效"把影响面收窄到"这个管理员身份的存在理由就是这一个角色"的场景，人工创建/
  补充角色的管理员不受影响；停用而非删除，保留人工复核与快速恢复（重新启用）的余地。
- **事件驱动自动重算的性能/规模假设**：与 `app-access-authorization` 的既有风险结论一致——
  "任意 ORG/USER/POSITION 事件都全量重跑全部规则"，假设规则数量与用户规模在可接受范围内，
  异步处理不阻塞原始写请求；未来规模增长需要节流/去重优化时，两处（策略、用户角色规则）
  可以一并处理，本次不做过度设计。
- **规则条数不设上限、不做暂停态**：见 Decision 1 说明，如后续暴露出管理多条规则的可用性
  问题，再补充暂停态或规则数量提示，本次不预先设计。
- **首次设计已实现的代码需要较大幅度重写/删除**：`tab_user_role` 表、相关 entity/mapper/
  service 及其单元测试，以及角色管理页面原来的"预览+确认添加"一次性弹窗都需要替换；
  `AdminService` 的按角色批量创建逻辑改动较小（只是数据来源表切换 + 新增
  `auto_created_role_id` 赋值）。tasks.md 会明确标注哪些是"删除重做"、哪些是"在已有基础上
  修改"。

## Migration Plan

- 新的 Flyway 脚本需要：① 删除首次设计里创建 `tab_user_role` 的迁移内容，替换为本次四张
  新表（`tab_user_role_rule`/`_org_scope`/`_user_attr`/`_grant`）；② `tab_admin` 新增
  `auto_created_role_id` 列。因为首次实现尚未提交/发布，直接修改对应迁移脚本内容即可，
  不需要"先建后删"的兼容性迁移。
- 无存量数据需要回填（`auto_created_role_id` 新增列对存量管理员记录默认为 `NULL`，语义
  正确——存量管理员当然不是本次新功能创建的）。
