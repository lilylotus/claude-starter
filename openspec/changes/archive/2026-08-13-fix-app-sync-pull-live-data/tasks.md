## 1. `GlobalExceptionHandler` 参数错误处理

- [x] 1.1 新增 `MissingServletRequestParameterException` 处理器，返回 400 +
      "缺少必填参数：{参数名}"
- [x] 1.2 新增 `MethodArgumentTypeMismatchException` 处理器，返回 400 +
      "参数 {参数名} 格式不正确"
- [x] 1.3 单元测试：分别构造这两种异常，断言返回的 `code`/`message` 符合预期

## 2. `BizSnapshotResolver`：按 dataType 现查业务表

- [x] 2.1 新增 `cn.nihility.rbac.sync.transform.BizSnapshotResolver`，注入
      `OrgMapper`/`UserMapper`/`UserPositionMapper`/`AppMapper`/`RoleMapper`，
      `resolve(dataType, bizId)` 按 `dataType` 分发 `selectById` 并转 `DomainSnapshotSupport.snapshot`
- [x] 2.2 查不到对应行时返回 `null`（防御性分支，四张业务表均逻辑删除不会物理消失）
- [x] 2.3 单元测试：五个数据域各自命中正确的 Mapper；查不到行时返回 `null`

## 3. `SyncPullServiceImpl` 改用现查数据

- [x] 3.1 `toVO` 的 `data` 来源从 `JacksonUtils.toObj(log.getDataSnapshot(), ...)` 改为
      `bizSnapshotResolver.resolve(log.getDataType(), log.getBizId())`
- [x] 3.2 `resolve` 返回 `null` 时跳过该条记录（`pullByBizIds`/`pullBySequence` 均过滤
      掉），记一条 `log.warn`
- [x] 3.3 现有单测（`SyncPullServiceImplTest`）改为 mock `BizSnapshotResolver` 而不是
      依赖 `data_snapshot` JSON 字符串，新增用例覆盖"业务表查不到对应行时该记录被跳过"

## 4. `selectLatestByBizIds` 去窗口函数化

- [x] 4.1 `AppDataChangeLogMapper.xml#selectLatestByBizIds` 改写为
      `INNER JOIN (SELECT biz_id, MAX(id) ... GROUP BY biz_id)` 写法
- [x] 4.2 本地针对真实 MySQL 5.7.44 库跑一次该查询：后端重启后调用
      `GET /open/api/sync/pull/by-id?dataType=ORG&bizIds=7,8`，返回 `{code:0}` 正常结果，
      不再抛 SQL 语法错误

## 5. 联调验证与收尾

- [x] 5.1 本地针对运行中的后端服务，用真实 `X-App-Key` 分别验证：
      - 缺 `fromSequence`/`dataType`/`bizIds` 均返回 400 + 具体参数名（`"缺少必填参数：xxx"`），
        `fromSequence=abc` 返回 400 + `"参数 fromSequence 格式不正确"`，均不再是 500
      - 直接对 `tab_org` 执行 `UPDATE`（绕过应用层，不产生新的变更记录）后，重新调用
        `pull/by-id`，返回的 `data.name` 反映了刚才的 `UPDATE` 结果而不是变更记录里创建时
        的旧快照，验证完成后已把该行数据改回原值
- [x] 5.2 `./gradlew test` 全量跑通（431 tests, 0 failures, 0 errors）
- [x] 5.3 实现完成后按 `openspec-doc-sync` 约定核对 `proposal.md`/`design.md`/`tasks.md`
      与实际 diff/测试结果是否一致
