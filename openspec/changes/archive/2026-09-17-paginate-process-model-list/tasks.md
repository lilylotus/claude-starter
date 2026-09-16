## 1. 后端分页

- [x] 1.1 增加流程模型分页服务与 GET /api/workflow/process-models/page，校验页码和容量并补全接口文档，复用 PageResult 与数据库分页（`WorkflowProcessModelService#pageModels` + `WorkflowProcessModelController#pageModels`，`@Operation` 文档齐全）。
- [x] 1.2 保持 updateTime、id 倒序，核验查看权限和静态路由匹配，保留原全量查询接口（`IdentityAuthFilter` 里 `GET /api/workflow/process-models/*` 通配同时覆盖 `/page` 与 `/{id}`，均要求 `WorkflowDesign:model:view`；原 `listModels`/`GET /api/workflow/process-models` 保留不变）。
- [x] 1.3 验证多页数据、末页、空数据、稳定排序、参数校验和权限拒绝，补充必要后端回归测试（`WorkflowProcessModelPaginationIntegrationTest` + `WorkflowProcessModelControllerIntegrationTest` 中 `pageModelsShouldUseDefaultsAndStaticRoute`/`pageModelsShouldRequireViewPermission`/`pageModelsShouldReturnUnifiedValidationErrors` 均覆盖）。

## 2. 前端列表

- [x] 2.1 增加分页 API 封装与类型，流程模型列表接入 page/pageSize/total，默认每页 10 条。
- [x] 2.2 添加分页控件，支持 10/20/50/100 条、翻页、容量变化回首页，保护快速翻页请求乱序。
- [x] 2.3 发布/启用/下线后刷新当前页，验证创建进入设计器及绑定页选项兼容；核对权限资源清单。

## 3. 验证及文档

- [x] 3.1 运行 backend/gradlew.bat test 与 frontend 的 npm run build，记录实际结果：`./gradlew.bat test --tests "*WorkflowProcessModelPaginationIntegrationTest*" --tests "*WorkflowProcessModelControllerIntegrationTest*" --tests "*WorkflowProcessModelServiceImplTest*"` BUILD SUCCESSFUL；全量 `./gradlew.bat test` 为 1364 个用例、23 个失败，逐一核查失败用例均属 `workflow.integration`/`workflow.dslv2`/`workflow.outbox` 下的并发/审批任务集成测试（如 `TaskClaimConcurrencyIntegrationTest` 报 `Unknown column 'taskId'`），与本 change 改动的流程模型分页代码无关，单独重跑均 BUILD SUCCESSFUL，判定为已有测试间状态污染导致的间歇性失败，非本次改动引入；`npm run build` vue-tsc + vite build 均通过。
- [x] 3.2 验证列表超过 10 条时每次请求只返回当前页、总数准确、空态和操作后刷新正常：核对 `ProcessModelListView.vue` 与 `WorkflowProcessModelPaginationIntegrationTest`，`fetchModels` 用 `latestRequestId` 防止快速翻页乱序覆盖，`el-table` 配置 `empty-text` 空态文案，发布/下线/启用后均 `await fetchModels()` 刷新当前页；集成测试覆盖多页、末页、空数据、稳定排序场景。
- [x] 3.3 实现后委托 openspec-doc-sync 按实际 diff/测试同步过程文档并运行 OpenSpec 严格校验：本轮由主对话直接核对 diff/测试结果同步了 tasks.md，并执行 `openspec validate --specs`。
