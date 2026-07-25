## 1. 后端：扩展人员标识匹配逻辑

- [x] 1.1 修改 `backend/src/main/java/cn/nihility/rbac/excelimport/service/support/ImportRowExecutor.java`
      的 `processPosition()`：把按 `code` 精确匹配用户的查询，改为按 `code`/`mobile`/`idCard`
      三者任一精确相等（OR 条件）匹配，命中零条/多条时复用既有的 `findSingleActive()` 判定与
      失败提示逻辑，不新增异常类型。
- [x] 1.2 同步更新该方法及 `PositionPseudoFieldCode.USER_CODE` 常量上的 Javadoc/注释，说明
      "人员标识"列现在可以是编号、手机号或身份证号中的任意一种，不再仅限编号。
- [x] 1.3 在 `ImportRowExecutorTest` 补充了三个用例：按手机号匹配成功
      （`processRow_shouldMatchUserByMobile_whenCodeNotMatched`）、按身份证号匹配成功
      （`processRow_shouldMatchUserByIdCard_whenCodeAndMobileNotMatched`）、手机号重复导致
      多条匹配判失败（`processRow_shouldFailPosition_whenUserIdentifierMatchesMultiple`）；
      原有按编号匹配的用例未改动，继续通过。

## 2. 验证

- [x] 2.1 `./gradlew test` 全量通过（226 个测试）。
- [x] 2.2 用本地开发库真实数据做了端到端验证：重启 `bootRun` 加载新代码后，临时给一条已有用户
      记录设置手机号/身份证号，写了一个临时 `@SpringBootTest` 直接调用改动后的
      `LambdaQueryWrapper` 查询逻辑，确认按手机号、身份证号、编号三种取值都能唯一命中该用户、
      不存在的取值查询结果为空；验证完成后已删除该临时测试类，并把测试用户的手机号/身份证号
      还原为改动前的空值。未做完整的 Excel 上传端到端（构造真实 .xlsx 走
      `POST /api/excel-import/batch`），因为核心风险点（查询条件的 OR 组合是否正确）已经通过
      真实数据库验证覆盖，`BatchImportServiceImpl`/`ImportRowExecutor` 之间的编排逻辑本次未
      改动、有既有测试覆盖。
