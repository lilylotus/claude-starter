## 1. 用户"性别"字段

- [x] 1.1 `UserServiceImpl` 注入 `DictItemService`
- [x] 1.2 新增私有方法 `genderLabel(String genderCode)`，按字典类型 `gender` 解析编码为标签，
      查不到时回退原始编码
- [x] 1.3 `toLogSnapshot()` 中 `性别` 快照值由 `entity.getGender()` 改为
      `genderLabel(entity.getGender())`

## 2. 任职记录"任职类型"字段

- [x] 2.1 `PositionLogSnapshotSupport` 注入 `DictItemService`
- [x] 2.2 新增私有方法 `positionTypeLabel(String positionTypeCode)`，按字典类型
      `position_type` 解析编码为标签，查不到时回退原始编码
- [x] 2.3 `snapshot()` 中 `任职类型` 快照值由 `entity.getPositionType()` 改为
      `positionTypeLabel(entity.getPositionType())`（同时覆盖独立任职管理入口
      `PositionServiceImpl` 与用户管理内嵌任职子表单 `UserServiceImpl.syncPositions` 两个
      调用方）

## 3. 验证

- [x] 3.1 `./gradlew compileJava` 通过
