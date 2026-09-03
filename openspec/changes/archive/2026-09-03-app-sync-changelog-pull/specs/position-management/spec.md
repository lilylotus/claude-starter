## ADDED Requirements

### Requirement: 任职记录版本号维护
系统 SHALL 为每条任职记录维护整型版本号，创建为 1，更新、启停、删除时递增。用户更新触发物理删除时，业务行无需继续保存版本，但删除前必须生成 `oldVersion + 1` 的最终 tombstone 版本并写入事件和流水。

#### Scenario: 新建任职记录的初始版本号
- **WHEN** 客户端调用 `POST /api/positions` 创建一条任职记录，审批通过后真正执行创建
- **THEN** 新任职记录的 `version` 为 1

#### Scenario: 用户更新接口新增的任职记录版本号
- **WHEN** 审批通过后，一条更新用户申请的 `positions` 中包含一项未携带 `id` 的新记录被执行
- **THEN** 该新增任职记录的 `version` 为 1

#### Scenario: 用户更新接口同步更新既有任职记录时版本号递增
- **WHEN** 审批通过后，一条更新用户申请的 `positions` 中某一项携带了该用户既有任职记录的 `id` 并修改了其字段
- **THEN** 该任职记录的 `version` 比更新前多 1

#### Scenario: 独立更新接口写操作版本号递增
- **WHEN** 一条已存在的任职记录先后被独立的更新接口编辑一次、停用一次（每次均在审批通过后真正执行）
- **THEN** 编辑后的 `version` 比编辑前多 1，停用后的 `version` 又比编辑后多 1
