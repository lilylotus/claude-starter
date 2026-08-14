## 1. 后端：`UpstreamRowUpserter` 改造

- [x] 1.1 新增私有方法 `isUnchanged(Object request, Object entity)`：用 `BeanWrapper` 遍历 `request` 自身声明的全部属性（跳过 `class` 伪属性），逐个与 `entity` 同名属性比较，任一不相等返回 `false`，全部相等返回 `true`（design.md Decision 1）
- [x] 1.2 `upsertOrg` 更新分支：构造好 `OrgUpdateRequest` 并设置 `parentId` 后，`isUnchanged` 为真时直接 `return`，不调用 `orgService.update(...)`
- [x] 1.3 `upsertUser` 更新分支：同样在构造好 `UserUpdateRequest` 后判断，`isUnchanged` 为真时直接 `return`，不调用 `userService.update(...)`
- [x] 1.4 `upsertPosition` 更新分支：构造好 `PositionUpdateRequest` 并设置 `orgId` 后判断，`isUnchanged` 为真时直接 `return`，不调用 `positionService.update(...)`
- [x] 1.5 补充/更新相关方法的 Javadoc，说明"匹配到记录后先比较是否有实际差异，无差异时跳过更新"的新增行为与原因

## 2. 测试

- [x] 2.1 `UpstreamRowUpserterTest` 新增用例：组织/用户/任职各一个"匹配到的记录与本次数据完全一致时跳过更新，不调用 `xxxService.update()`"场景
- [x] 2.2 确认既有"匹配到记录且数据有差异时正常更新"的用例仍然通过（无需修改，回归验证通过）
- [x] 2.3 `backend/` 目录执行 `./gradlew test --tests "cn.nihility.rbac.identity.upstream.*"` 全部通过；另跑 `./gradlew test` 全量回归确认无其余模块受影响

## 3. 文档同步

- [x] 3.1 实现完成后核对 `proposal.md`/`design.md`/`tasks.md` 与实际改动一致，未发现需要回写的调整
