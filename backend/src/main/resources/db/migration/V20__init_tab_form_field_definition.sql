-- ----------------------------------------------------------------------------
-- 表单字段定义模块 - 建表脚本（Flyway 迁移版本 V20）
-- 在元数据字段配置目录的基础上，选一条元数据字段记录，配上业务侧关心的
-- 展示/校验属性，驱动组织/用户/任职/应用四个管理页面的动态渲染。
-- 承重字段（name/code）的锁定保护不落库，由 Java 常量白名单
-- （cn.nihility.rbac.formfield.constant.LockedFormFields）在读取时计算得出。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_form_field_definition`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `biz_type`          VARCHAR(20)  NOT NULL COMMENT '业务对象类型，创建时取自所绑定元数据字段，之后不可变',
    `metadata_field_id` BIGINT       NOT NULL COMMENT '绑定的元数据字段 id，关联 tab_metadata_field.id，创建后不可改绑',
    `field_name`        VARCHAR(64)  NOT NULL COMMENT '展示名称，创建时默认取自元数据字段的 field_name，此后可独立编辑',
    `field_code`        VARCHAR(64)  NOT NULL COMMENT '前端/DTO 使用的字段标识，如 idCardNo，同一 biz_type 下唯一',
    `control_type`      INT          NOT NULL COMMENT '控件类型：1=文本框，2=数字框，3=字典下拉，4=日期，5=多选字典下拉',
    `dict_type_id`      BIGINT       NULL COMMENT '关联的字典类型 id，仅 control_type=3 时必填',
    `is_unique`         TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否要求同 biz_type 下有效数据唯一',
    `is_required`       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否必填',
    `show_in_list`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在列表中展示',
    `show_in_create`    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在新增表单中展示',
    `show_in_edit`      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否在编辑表单中展示',
    `editable`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '表单中展示时是否可编辑，为否则只读展示',
    `validate_regex`    VARCHAR(255) NULL COMMENT '正则校验规则，前后端共用同一个字符串',
    `placeholder`       VARCHAR(128) NULL COMMENT '输入提示文字',
    `show_order`        INT          NOT NULL DEFAULT 0 COMMENT '显示序号，值越大越靠前',
    `status`            INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=启用，3000=停用，-1000=已删除（逻辑删除）',
    `create_by`         VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`       DATETIME     NULL COMMENT '创建时间',
    `update_by`         VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`       DATETIME     NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_form_field_definition_biz_type` (`biz_type`),
    KEY `idx_tab_form_field_definition_metadata_field_id` (`metadata_field_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '表单字段定义表';
