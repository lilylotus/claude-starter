-- ----------------------------------------------------------------------------
-- add-file-upload change：新增通用文件上传/下载能力（design.md Decision 2）。
-- 记录原始文件名、保存在磁盘目录中的文件名、文件大小、状态，供后续业务模块
-- （审批流程附件、Excel 导入导出等）按 id 存取文件。
-- MySQL 5.7 兼容语法：不使用窗口函数/CTE/JSON_TABLE 等 8.0+ 特性。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_file_upload`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `original_file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（含后缀），仅用于展示和下载时的 Content-Disposition 文件名',
    `stored_file_name`   VARCHAR(128) NOT NULL COMMENT '保存在磁盘目录中的文件名（UUID 去横线 + 原始后缀）',
    `file_size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `status`             INT          NOT NULL DEFAULT 2000 COMMENT '文件状态：2000=正常，3000=失效',
    `create_by`          VARCHAR(64)  NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)  NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_file_upload_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '文件上传记录表';
