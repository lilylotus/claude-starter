## 1. 整理合并后的基线迁移文件

- [x] 1.1 按版本号顺序读取旧 `V1__init_tab_org.sql` 到
      `V34__convert_form_field_dict_type_id_to_code.sql` 共 34 个文件
- [x] 1.2 逐表整理最终建表语句（列、类型、默认值、注释、索引、引擎/字符集），体现全部
      `ALTER` 累加后的结果
- [x] 1.3 逐条整理最终种子数据（字典数据、菜单资源数据、表单字段定义、导入字段配置等），
      体现后续 `UPDATE`/字典编码转换迁移之后的最终值
- [x] 1.4 写入 `backend/src/main/resources/db/migration/V1__init_schema.sql`，文件头部注明
      "本文件由原 V1~V34 合并而来，本地库需清空后重新执行"

## 2. 校验一致性

- [x] 2.1 逐表核对新文件与旧文件按顺序应用后的最终 schema 一致（16 张表全部覆盖）
- [x] 2.2 逐条核对种子数据一致，含 `tab_menu` 86 行菜单资源数据的逐条 diff
- [x] 2.3 核对 Java 实体类（如 `UserEntity.gender`、
      `FormFieldDefinitionEntity.dictTypeCode`）字段类型与合并后的列定义一致

## 3. 清理与验证

- [x] 3.1 删除旧 34 个迁移文件（`git rm`，保留 git 历史可追溯）
- [x] 3.2 确认迁移目录最终只剩 `V1__init_schema.sql`
- [x] 3.3 `./gradlew compileJava` 通过，确认代码中无引用旧迁移文件名的地方
