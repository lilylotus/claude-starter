## 1. 接口封装

- [x] 1.1 新增 `frontend/src/api/processBinding.ts`：封装 `GET /api/workflow/process-bindings`、
      `GET /api/workflow/process-bindings/{bindingId}`、`POST /api/workflow/process-bindings`、
      `PUT /api/workflow/process-bindings/{bindingId}`、`POST .../{bindingId}/enable`、
      `POST .../{bindingId}/disable`，类型定义放 `frontend/src/types/`，字段与
      `ProcessBindingVO`/`ProcessBindingRequest` 对齐。
- [x] 1.2 复用现有 `frontend/src/api/workflow.ts`（或等价文件）里的
      `listProcessModels`/`获取版本历史` 接口封装，若字段类型缺失按 `ProcessDefinitionVersionVO`
      补齐类型定义，不重复造一套 workflow api 封装。

## 2. 页面骨架与路由

- [x] 2.1 新增 `frontend/src/views/workflow/binding/ProcessBindingView.vue`，路由
      `/workflow/bindings` 注册到 `frontend/src/router/index.ts`。
- [x] 2.2 `frontend/src/router/menu.ts` 的"流程设计"分组下新增"业务绑定"子菜单项，
      `permissionKey: 'WorkflowDesign:binding:view'`。
- [x] 2.3 页面按 bizType（组织/用户/任职/应用）分 Tab，每个 Tab 内提供 operationType
      （新增/更新/启用/停用/删除）下拉选择器（默认选中"新增"），选中某个操作类型后只展示
      该类型对应的一张表：全局绑定 + 组织范围覆盖绑定列表（改自初版"五种操作类型同时平铺"
      的布局，用户反馈信息量过大，见 design.md 决策2）。

## 3. 数据加载与 definitionId 展示解析

- [x] 3.1 加载 `listBindings()` 全量绑定后按 bizType/operationType/scopeType 分组。
- [x] 3.2 对绑定引用到的每个 `definitionId`，通过所属流程模型的 `GET
      /api/workflow/process-models/{id}/versions` 解析出 `{processName, version, status}`，
      构建 `definitionId -> 展示信息` 的映射表（前端内存 join，见 design.md 决策5）；
      模型列表与版本历史接口按需调用、避免对同一模型重复请求。实现上按加载到的全部流程
      模型各请求一次版本历史（而非仅按绑定实际引用到的 definitionId 精选请求），换取
      "切换绑定"弹窗第二级下拉可直接复用同一份缓存、无需二次请求；模型数量级小，
      符合 design.md 决策5"几次额外请求可接受"的判断。
- [x] 3.3 绑定指向的版本 `status !== 'PUBLISHED'` 时，在列表行标注"绑定指向的版本已下线"。

## 4. 新建/切换绑定弹窗

- [x] 4.1 新建绑定表单：bizType/operationType 由当前 Tab/分组预填不可改，scopeType
      单选（全局/组织），scopeType=ORG 时展示组织选择器（复用 `el-tree-select` 既有写法）。
- [x] 4.2 流程版本级联下拉：第一级选流程模型（`GET /api/workflow/process-models`），
      第二级按选中模型调用 versions 接口并过滤 `status==='PUBLISHED'`，选中项 `id` 作为
      提交的 `definitionId`；第二级为空时提示"该模型尚无已发布版本"。
- [x] 4.3 executionMode 字段固定传 `LEGACY_SYNC`，不在表单中暴露选择项。
- [x] 4.4 新建提交 `POST /api/workflow/process-bindings`；切换版本复用同一弹窗，提交
      `PUT /api/workflow/process-bindings/{bindingId}`，携带当前 `revision` 作为
      `expectedRevision`，冲突时提示重新加载。冲突提示复用 `api/request.ts` 响应拦截器
      统一的错误提示（后端非 0 code 已被拦截器 `ElMessage.error` 展示），未额外定制
      乐观锁冲突的专属文案。

## 5. 启停与权限门控

- [x] 5.1 列表行提供启用/禁用按钮，分别调用对应接口，操作后刷新该条绑定状态。
- [x] 5.2 新建/切换/启停按钮受 `WorkflowDesign:binding:edit` 权限点门控，查看整个页面
      受 `WorkflowDesign:binding:view` 门控（路由 `permissionKey` 已覆盖页面级访问）。

## 6. 验收

- [x] 6.1 `npm run build`（vue-tsc 类型检查 + vite build）通过。
- [x] 6.2（走查由用户在真实浏览器中完成，非本次编码环境的自动化工具——当次会话浏览器
      自动化工具持续不可用）：用户实际走查发现两处真实问题并已修复：1) 切换到"用户"Tab
      后"新建组织覆盖绑定"按钮文案仍写着"组织"，容易被误读为还在配置组织业务类型（实际
      业务类型字段本身是对的），改为"新建组织范围覆盖绑定"并加 tooltip 说明"组织覆盖"指
      按组织范围覆盖、与业务类型无关；2) 页面初版把新增/更新/启用/停用/删除五种操作类型
      的表格同时平铺展示，用户反馈信息量过大，改为下拉选择操作类型、一次只看一种，并补充
      切换业务类型 Tab 时操作类型下拉重置为"新增"。四个 Tab 的绑定矩阵展示、组织覆盖绑定
      新建、版本切换、启停功能经用户走查确认可用，未发现其他功能性缺陷。
- [x] 6.3 核对 `权限资源.txt` 中 `WorkflowDesign:binding:view/edit` 的描述与实际页面/按钮
      行为一致（权限点编码本身无需新增，仅确认描述未过期）：第285-292行描述的接口路径
      （列表/详情/新建/切换/启停）与本次页面实际调用的接口一一对应，未发现描述过期，
      未修改该文件。
