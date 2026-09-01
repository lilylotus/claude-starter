## MODIFIED Requirements

### Requirement: 管理页面的审批入口
系统 SHALL 提供"我的申请"、"待我审批"、"审批设置"三个前端页面；"待我审批"页面的访问与操作 SHALL 受 `ApprovalManagement:request:approve` 权限点门控，"审批设置"页面的访问 SHALL 受 `ApprovalManagement:switch:view` 权限点门控、修改开关操作 SHALL 受 `ApprovalManagement:switch:edit` 权限点门控，无对应权限的用户看不到相应菜单入口。组织、用户、任职、应用四个管理页面的新增/编辑/启用/停用/删除操作，调用对应接口成功后 SHALL 按响应的 `approvalEnabled` 字段分别展示提示：为 `true` 时展示"已提交审批，等待审批通过后生效"，不假定接口返回的是最终生效的业务数据；为 `false` 时展示与本 change 之前一致的直接生效提示（如"创建成功"），并使用响应的 `data` 更新页面展示。"我的申请""待我审批"两个页面共用的申请详情展示（含 `UPDATE` 类型申请的新旧字段对照）依赖字段渲染元数据接口（`GET /api/form-fields/render-schema`，见 `password-login-auth` 能力"表单字段渲染元数据接口豁免操作资源编码校验"）查询业务对象类型的字段展示名与控件配置，该查询 SHALL NOT 因当前查看者不持有被审批业务对象（组织/用户/任职/应用）对应的管理权限点而失败——审批详情的查看权限完全由用户能否看到"我的申请"（自助，任何登录用户）或"待我审批"（`ApprovalManagement:request:approve`）决定，不叠加被审批对象自身的管理权限点要求。

#### Scenario: 无审批权限的用户看不到待我审批菜单
- **WHEN** 当前登录用户的权限编码集合不包含 `ApprovalManagement:request:approve`
- **THEN** 侧边导航不展示"待我审批"菜单项

#### Scenario: 查看更新类申请详情不要求被审批对象的管理权限点
- **WHEN** 用户在"我的申请"或"待我审批"页面打开一条 `operationType=UPDATE` 的申请详情，该用户当前权限编码集合不包含该申请 `bizType` 对应的管理权限点（如 `OrgManagement:org:view`）
- **THEN** 详情弹窗仍能正常拉取到该 `bizType` 的字段渲染元数据并展示新旧字段对照，不因缺少该业务管理权限点而报错或留空
