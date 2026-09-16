## 1. 后端逻辑调整

- [x] 1.1 `WorkflowProcessBindingService.switchDefinition` 删除"目标定义必须与当前绑定同一流程模型"的校验分支，保留"目标定义必须是 PUBLISHED 状态"的校验（`requirePublishedDefinition`）
- [x] 1.2 按 design.md Decision 2 调整 `isRollback` 判定：只在 `definition.getProcessModelId().equals(previousDefinition.getProcessModelId())` 为真时才比较版本号；跨模型切换在日志里单独标注为"切换到其它流程模型"

## 2. 测试

- [x] 2.1 新增 `WorkflowProcessBindingServiceTest`（参照同目录 `ProcessBindingResolutionServiceTest` 的 `@SpringBootTest` + `@Transactional` 真实数据库集成测试风格），覆盖：
  - [x] 2.1.1 同模型内切换版本成功，且是"升级"场景时日志/行为符合预期（不需要断言日志内容，断言 `definitionId` 更新即可）
  - [x] 2.1.2 跨流程模型切换版本成功（目标定义属于另一个流程模型），断言 `definitionId` 正确更新为跨模型目标定义的 id
  - [x] 2.1.3 切换到未发布（非 PUBLISHED）状态的定义仍然被拒绝，校验行为未被本次改动破坏
  - [x] 2.1.4 `expectedRevision` 冲突时仍然拒绝（既有乐观锁校验未受影响）

## 3. 验证

- [x] 3.1 `./gradlew build`（`backend/` 目录下）确认编译 + 全部测试通过
- [ ] 3.2 手工验证（需要真实登录态 + 数据库环境时执行；如无法执行需如实说明并保留未勾选）：本轮未执行浏览器端手工验证，如实保留未勾选
