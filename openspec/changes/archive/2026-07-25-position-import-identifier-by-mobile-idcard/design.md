## Context

`ImportRowExecutor.processPosition()`（`backend/src/main/java/cn/nihility/rbac/excelimport/service/support/ImportRowExecutor.java:333-354`）
目前用 `LambdaQueryWrapper<UserEntity>().eq(UserEntity::getCode, userCode)` 精确匹配"人员标识"列
（固定标识列 `__userCode`，Excel 表头默认"人员编号"，可由管理员在"导入模板配置"里自行改名）对应
的用户，匹配零条或多条时分别判失败。`tab_user.code`、`tab_user.idCard` 在未删除用户范围内保证
唯一（`user-management` spec 已有约束），但 `tab_user.mobile` 没有唯一性约束。

## Goals / Non-Goals

**Goals:**
- 同一个"人员标识"列的取值可以是编号、手机号或身份证号中的任意一种，系统按这三个字段任一
  精确匹配即可定位到用户。

**Non-Goals:**
- 不新增列、不新增"标识类型"选择配置——继续沿用现有的单一固定标识列（`__userCode`），只扩展
  它背后的匹配逻辑。
- 不改动 APP 导入"负责人编号"（`__ownerCode`）的匹配逻辑。
- 不给 `tab_user.mobile` 增加唯一性约束——手机号重复时按下面"决策"里描述的既有降级路径处理，
  不在本次改动范围内。

## Decisions

**决策：把单字段精确匹配改为三字段 OR 匹配，复用既有的零/一/多判定逻辑**

```java
List<UserEntity> matches = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
        .and(w -> w.eq(UserEntity::getCode, userCode)
                .or().eq(UserEntity::getMobile, userCode)
                .or().eq(UserEntity::getIdCard, userCode))
        .ne(UserEntity::getStatus, UserStatus.DELETED));
UserEntity user = findSingleActive(matches);
```
（变量名 `userCode` 沿用现有命名，语义上现在代表"人员标识"而不仅是编号；如果一并重命名成
`userIdentifier` 有助于可读性，可在实现时顺手改，不影响接口契约。）

匹配到多条记录时复用已有的 `findSingleActive()` 判定逻辑，抛出与其他"多条匹配"场景一致的
"匹配到多条已存在记录，无法确定更新目标"错误，不需要新写异常类型或错误码。

备选方案：新增"标识类型"下拉配置（编号/手机号/身份证号三选一），让管理员显式声明当前 Excel
用哪种标识——放弃，因为：(1) 需要扩展 `tab_import_field_config` 表结构和管理界面，改动面明显
大于问题本身；(2) 三个字段格式差异明显（编号、11 位手机号、18 位身份证号一般不会互相冲突），
OR 匹配已经能覆盖"数据整理阶段只知道其中一种标识"的实际场景，不需要额外配置成本。

## Risks / Trade-offs

- [风险] `mobile` 无唯一性约束，多个用户手机号相同时用手机号作为标识会匹配到多条记录，该行
  导入失败（"匹配到多条已存在记录，无法确定更新目标"）→ 这是可接受的安全退化：宁可该行失败
  要求人工核实，也不应该猜测绑定到错误的人员。不在本次改动中给 `mobile` 加唯一约束（会影响
  现有用户管理功能，超出本次范围）。
- [风险] 如果某个用户的编号恰好等于另一个用户的手机号或身份证号（数据巧合），OR 匹配可能会
  匹配到非预期的用户 → 概率极低（编号、手机号、身份证号三者格式通常不同），且这种巧合在
  "仅按编号匹配"的旧逻辑下也不会被这次改动放大新的错误绑定风险类别，只是新增了触发面；不做
  额外处理。

## Migration Plan

纯 Java 逻辑改动，无数据库变更，无需迁移脚本，随正常发布流程上线即可。
