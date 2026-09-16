## Context

ProcessModelListView.fetchModels 调用 listProcessModels 获取数组，模板无分页控件。后端 listModels 使用 selectList 全量查询，按 updateTime、id 倒序。ProcessBindingView 也调用该 API 加载模型选择项，因此直接改变旧接口返回类型会影响绑定功能。项目已有 PageResult 和 MyBatis-Plus 分页实现可复用。

## Goals / Non-Goals

**Goals:** 模型管理列表按数据库分页查询，默认 10 条，支持总数、翻页及页容量切换，保留现有模型操作。

**Non-Goals:** 不新增筛选条件、不调整业务绑定选择器、不修改流程引擎和模型生命周期。ADD_USER 修复方案独立待确认。

## Decisions

1. 新增 GET /api/workflow/process-models/page，参数 page 默认 1、pageSize 默认 10；page 必须大于等于 1，pageSize 范围 1–100，非法参数返回既有统一错误。返回统一包装下的 PageResult<ProcessModelVO>。使用数据库 selectPage，保持 updateTime DESC、id DESC 排序；仅转换当前页数据，不先读取全量再切片。
2. 保留原全量接口和 listProcessModels 函数，新增类型明确的分页 API 函数供列表使用。相比直接替换旧接口，这样不会截断业务绑定选择项。新增路径按 IdentityAuthFilter 实际映射复用 WorkflowDesign:model:view，验证静态 page 路径不被详情 id 路由误处理。
3. 前端维护 page、pageSize、total、models，使用 el-pagination，选项 10/20/50/100。翻页发起对应请求，切换容量回到第一页；发布、启用、下线后刷新当前页。通过请求序号等轻量保护避免快速翻页时旧响应覆盖新页；空数据保持正常空态。创建后继续进入设计器。
4. 分页控件使用既有查看权限，不引入业务动作编码。核对权限资源.txt 与实际页面；如仅新增通用分页控件则无需虚构新的权限点，若实现中出现业务按钮变动必须同步清单。

## Risks / Trade-offs

- [更新模型会改变排序位置] → 保持现有排序，操作后重新请求当前页，允许记录按更新时间移动。
- [新路径被权限规则遗漏] → 实现时检查并验证路由权限，沿用模型查看权限。
- [选择器复用接口] → 保留旧接口，确认绑定页面选项不丢失。
- [并发请求乱序] → 只有最新请求可以更新列表和 loading 状态。

## Migration Plan

先部署兼容性后端接口，再部署分页前端，无数据库迁移；回退前端即可恢复旧列表，后端新接口可保留。验证超过 10 条的多页数据、空列表、容量变化及模型操作。

## Open Questions

无阻塞问题，默认页容量参照已有用户列表取 10 条。
