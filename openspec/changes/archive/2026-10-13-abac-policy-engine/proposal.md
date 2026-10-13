# Proposal

## Why

当前系统的运行时鉴权只有 RBAC 一层：`IdentityAuthFilter` 基于 `menu` 请求头调用
`AuthorizationService.hasPermission` 判断用户是否拥有某个权限点，是"身份(角色)→权限"的
静态判定，不能表达"只允许本部门用户编辑本部门数据""只允许工作日 9-18 点访问""只允许
本人查看自己的记录"这类依赖用户属性、资源属性、环境属性动态组合的授权规则。现有的
`appaccess/policy` 已经有一套属性条件判定能力，但它的语义被限定在"用户能否启动某个
应用"（SSO 登录授权场景），表结构与执行流程（组织范围/批量身份命中/应用级人工例外）
都是为那个场景定制的，不适合直接挪用到系统内部接口/菜单/按钮级别的鉴权上。需要新增
一个独立的 ABAC（基于属性的访问控制）顶级模块，在现有 RBAC 判定通过之后叠加一层基于
属性的策略判定，覆盖系统内部的接口/菜单/按钮鉴权场景，且与 `appaccess/policy` 完全
解耦、不共享表结构与执行逻辑。

## What Changes

- 新增顶级模块 `abac`（与 `permission`、`role` 同级），独立建表、独立的策略 CRUD 与
  策略判定执行逻辑，不依赖、不复用 `appaccess/policy` 的任何表或服务。
- 策略规则可配置的条件维度：
  - 用户属性条件：复用"元数据字段管理"（`biz_type=USER`）暴露的用户属性字段，比较
    运算符至少支持 `EQ`/`NE`/`IN`（与 `appaccess/policy` 现有运算符集合保持一致的
    使用体验，但判定执行代码独立实现，不跨模块调用）。
  - 资源属性条件：策略关联的"资源类型 + 操作"（即被保护的权限编码/菜单维度，区别于
    `appaccess` 的"目标应用"概念），用于表达"这条策略只对某个具体业务资源的某个操作
    生效"。
  - 环境属性条件：至少支持时间窗口（允许访问的星期/时间段）。
- 策略效果最终确定为仅限制（`DENY`-only，不支持 `ALLOW`，见 design.md Decision 1）：
  命中即拒绝；多条策略命中时取"任意一条命中即拒绝"的 OR 语义合并，显示序号仅用于
  列表展示排序，不参与判定优先级。
- 在 `IdentityAuthFilter` 现有 RBAC 判定（`AuthorizationService.hasPermission`）通过
  之后，新增一次 ABAC 判定调用；RBAC 判定本身的逻辑与现有请求头/豁免规则不变，ABAC
  判定只在 RBAC 放行之后对存在匹配策略的资源维度生效，没有任何策略命中该资源维度时
  视为不受 ABAC 约束、直接放行（保证新增模块对现有未配置 ABAC 策略的接口零影响）。
- 新增策略规则的管理端维护页面（CRUD + 启用/停用 + 查询），挂在权限管理相关菜单下，
  受新增权限点控制显示。
- 同步更新仓库根目录 `权限资源.txt`，登记新增的菜单/按钮权限编码。

## Capabilities

### New Capabilities
- `abac-policy-engine`: 新增的 ABAC 策略引擎能力——策略规则的定义与维护（用户属性/
  资源属性/环境属性条件、`ALLOW`/`DENY` 效果、显示序号与合并优先级）、运行时判定接口
  （供 `IdentityAuthFilter` 在 RBAC 判定通过后调用）、管理端维护页面与菜单权限点。

### Modified Capabilities

(无——本次改动在 `IdentityAuthFilter` 现有 RBAC 判定之后新增一次独立的判定调用，不
修改 `password-login-auth`/`permission-driven-visibility` 等既有能力已登记的任何
requirement 文本；对未配置 ABAC 策略的资源维度行为保持不变。)

## Impact

- 新增后端包 `cn.nihility.rbac.abac`（`controller`/`service`/`dto`/`entity`/
  `mapper`/`mapstruct`/`constant`/`support`/`config`，遵循项目既有分层约定；复用
  `common` 下既有的 `BusinessException`/全局异常处理器，未单独建 `exception` 子包），
  新增 Flyway 迁移脚本建表（`tab_abac_` 前缀，最终落地为 `V9__add_abac_policy.sql`），
  不改动 `appaccess` 模块任何现有表/代码。未新增任何第三方依赖，未修改
  `build.gradle`。
- 修改 `auth/filter/IdentityAuthFilter.java`：在现有 `hasPermission` 判定通过后插入
  一次对 `abac` 模块判定服务的调用；修改 `auth` 模块对 `abac` 模块服务的依赖关系
  （新增跨模块注入，延续项目里"直接注入其他模块 Service/Mapper"的既有先例）。
- 新增前端路由/页面（策略规则管理），复用 `metadata`（元数据字段）与权限点勾选等
  既有前端工具函数；更新 `权限资源.txt` 与 `permissionTree.ts` 的模块中文名映射。
- 不影响 `appaccess/policy`、SSO 登录授权、`sso-protocol-access-log` 等既有能力的
  现有行为。
