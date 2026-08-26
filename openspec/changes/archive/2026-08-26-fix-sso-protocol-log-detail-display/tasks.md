## 1. 后端：DTO 与数据访问层

- [x] 1.1 `SsoProtocolLogVO` 新增 `userName`（用户姓名）、`deniedPolicyName`（拒绝策略名称）字段，并补充 `@Schema` 描述
- [x] 1.2 `SsoProtocolLogMapper` 新增 `IPage<SsoProtocolLogVO> selectSsoProtocolLogPage(IPage<?> page, @Param("query") SsoProtocolLogQueryRequest query)` 方法签名
- [x] 1.3 新增 `backend/src/main/resources/mybatis/mapper/SsoProtocolLogMapper.xml`：`LEFT JOIN tab_user`、`LEFT JOIN tab_app_access_policy` 查出 `userName`/`deniedPolicyName`，动态 `<where>` 搬迁现有 6 个可选筛选条件（appRefId/protocol/eventType/result/sessionId/startTime~endTime），按调用时间降序排序；`resultType` 为 `SsoProtocolLogVO`
- [x] 1.4 编译通过（`./gradlew compileJava`），确认 XML 与 Mapper 接口方法签名一致、无 MyBatis 绑定异常

## 2. 后端：服务层改造与死代码清理

- [x] 2.1 `SsoProtocolLogQueryServiceImpl.getPage` 改为调用 `ssoProtocolLogMapper.selectSsoProtocolLogPage`，移除 `LambdaQueryWrapper` 拼接逻辑，保留 `resultLabel` 赋值逻辑
- [x] 2.2 删除不再被引用的 `SsoProtocolLogConvert`（`backend/src/main/java/cn/nihility/rbac/ssoprotocollog/mapstruct/SsoProtocolLogConvert.java`），确认删除后项目仍可编译（无其他引用方）

## 3. 后端：测试

- [x] 3.1 更新/补充 `SsoProtocolLogQueryServiceImplTest`：筛选条件的拼接与关联查询已下沉到 XML，改为验证服务层职责（筛选参数原样透传给 mapper、分页参数转换、`resultLabel` 填充、mapper 关联查出的 `userName`/`deniedPolicyName` 原样透传）
- [x] 3.2 `./gradlew test --tests "cn.nihility.rbac.ssoprotocollog.*"` 全部通过

## 4. 前端：类型与接口展示

- [x] 4.1 `frontend/src/types/ssoProtocolLog.ts` 的 `SsoProtocolLogRow` 新增 `userName: string | null`、`deniedPolicyName: string | null` 字段
- [x] 4.2 `frontend/src/components/SsoProtocolLogDialog.vue`："用户ID"列改为"用户"列展示 `userName`（复用现有 `displayValue` 兜底为 `-`），新增"拒绝策略"列展示 `deniedPolicyName`（同样兜底为 `-`）
- [x] 4.3 `npm run build`（`vue-tsc` 类型检查 + vite build）通过

## 5. 验证

- [x] 5.1 本地启动后端 + 前端，在登录日志页面触发一次因应用访问授权策略拒绝导致的 SSO 登录失败，打开"协议详情"弹窗，确认"用户ID"列显示为用户姓名、"拒绝策略"列显示具体策略名称
- [x] 5.2 触发一次与应用访问授权无关的失败（如票据已失效），确认"拒绝策略"列显示为 `-`
