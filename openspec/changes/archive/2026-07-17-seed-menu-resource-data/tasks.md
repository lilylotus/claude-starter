## 1. 迁移脚本

- [x] 1.1 新建 `backend/src/main/resources/db/migration/V11__seed_menu_resource_data.sql`
- [x] 1.2 插入 4 个一级分组（`identity`/`application`/`permission`/`system`），
  `parentId = 0`，`resourceType = 1`
- [x] 1.3 用会话变量回填分组 id，插入 8 个页面菜单节点（编码取自 `权限资源.txt` 的
  `:view` 条目），`resourceType = 1`
- [x] 1.4 用会话变量回填页面 id，插入 52 个按钮节点（编码取自 `权限资源.txt` 除
  `:view` 外的全部条目），`resourceType = 2`

## 2. 验证

- [x] 2.1 `./gradlew test --tests "cn.nihility.rbac.RbacApplicationTests"` 通过
  （连接本地 MySQL `rbac` 库，Flyway 在 Spring 启动阶段自动执行迁移；`V11` 脚本
  无 SQL 语法错误，`flyway_schema_history` 记录 `version=11, success=1`）
- [x] 2.2 直接查询本地 `rbac.tab_menu` 核对结果：本次迁移新插入的 64 行（id 7~70）
  层级、`parentId`、`showOrder` 均与 `权限资源.txt`／设计文档一致（4 个一级分组 ->
  8 个页面菜单 -> 52 个按钮，字典管理下字典类型/字典项两组按钮均正确挂在同一页面
  节点下）；本地库里额外存在的 6 行（id 1~6，`SYS`/`SYS_MENU` 等编码）是此前手工
  测试遗留数据，与本次迁移无关，不属于本次改动范围。未额外发起
  `GET /api/menus/tree` 接口调用验证——该接口本身在 `add-menu-management` change
  里已经验证过，本次只需确认种子数据的父子关系正确，直接查表已经足够
