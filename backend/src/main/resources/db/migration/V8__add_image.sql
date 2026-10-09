-- ----------------------------------------------------------------------------
-- add-image-gallery change：新增"公共服务"侧边栏一级导航分组（design.md Decision 7），
-- 下挂"图片管理"（ImageManagement:image:view，含上传/删除两个按钮权限点）与"图片展示"
-- （ImageGallery:image:view，纯浏览，不挂按钮）两个二级页面，并新增图片记录表
-- tab_image（design.md Decision 2）。
--
-- 必须在同一条脚本内一次性完成 tab_image 建表 + tab_menu + tab_permission +
-- tab_role_permission（授予 SUPER_ADMIN）：V1 里 SUPER_ADMIN 授权全部权限点是用一条
-- INSERT...SELECT 覆盖当时已插入的全部 tab_permission 行，之后新增的独立迁移脚本不会被
-- 那条一次性语句覆盖，必须自己显式补一条 tab_role_permission（V4 曾漏补、靠 V5 单独修复，
-- V6 的注释完整记录了这个教训，本次不重复）。全部写法参照 V5/V6 的幂等模式
-- （NOT EXISTS 防重复插入），支持脚本被重复应用到已执行过的环境而不报错/不重复插入。
-- MySQL 5.7 兼容语法：不使用窗口函数/CTE/JSON_TABLE 等 8.0+ 特性。
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `tab_image`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键 id',
    `original_file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名（含后缀），仅用于展示，不参与磁盘路径拼接',
    `stored_file_name`   VARCHAR(128) NOT NULL COMMENT '保存在磁盘目录中的文件名（UUID 去横线 + 原始后缀）',
    `file_size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `image_width`        INT          NOT NULL COMMENT '图片宽度（像素），ImageIO 解析得到',
    `image_height`       INT          NOT NULL COMMENT '图片高度（像素），ImageIO 解析得到',
    `status`             INT          NOT NULL DEFAULT 2000 COMMENT '状态：2000=正常，-1000=已删除（逻辑删除）',
    `create_by`          VARCHAR(64)           DEFAULT NULL COMMENT '创建人',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`          VARCHAR(64)           DEFAULT NULL COMMENT '更新人',
    `update_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tab_image_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci
  COMMENT = '图片记录表';

SET @admin_user_id_text := '1';

-- 1) tab_menu：一级分组"公共服务" + 两个二级页面 + 图片管理页下两个按钮。
INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '公共服务', 'common-service', 0, 1, 50, '侧边栏一级导航分组', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'common-service');

SET @common_service_group_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'common-service');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '图片管理', 'ImageManagement:image:view', @common_service_group_id, 1, 20,
       '图片管理页面访问（上传 + 分页管理已上传图片）', 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @common_service_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'ImageManagement:image:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '图片展示', 'ImageGallery:image:view', @common_service_group_id, 1, 10,
       '图片展示页面访问（瀑布流 + 无限滚动浏览，纯浏览无管理操作）', 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE @common_service_group_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'ImageGallery:image:view');

SET @image_management_view_id := (SELECT `id` FROM `tab_menu` WHERE `code` = 'ImageManagement:image:view');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '上传图片', 'ImageManagement:image:add', @image_management_view_id, 2, 20, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @image_management_view_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'ImageManagement:image:add');

INSERT INTO `tab_menu` (`name`, `code`, `parent_id`, `resource_type`, `show_order`, `remark`, `status`, `create_by`,
                         `create_time`, `update_by`, `update_time`)
SELECT '删除图片', 'ImageManagement:image:delete', @image_management_view_id, 2, 10, NULL, 2000, @admin_user_id_text,
       NOW(), @admin_user_id_text, NOW()
WHERE @image_management_view_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `tab_menu` WHERE `code` = 'ImageManagement:image:delete');

-- 2) tab_permission：对应 4 条权限点记录。
INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '图片管理页面访问', 'ImageManagement:image:view', 0, NULL, 2000, @admin_user_id_text, NOW(),
       @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'ImageManagement:image:view');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '上传图片', 'ImageManagement:image:add', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'ImageManagement:image:add');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '删除图片', 'ImageManagement:image:delete', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'ImageManagement:image:delete');

INSERT INTO `tab_permission` (`name`, `code`, `show_order`, `remark`, `status`, `create_by`, `create_time`,
                              `update_by`, `update_time`)
SELECT '图片展示页面访问', 'ImageGallery:image:view', 0, NULL, 2000, @admin_user_id_text, NOW(), @admin_user_id_text,
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM `tab_permission` WHERE `code` = 'ImageGallery:image:view');

-- 3) tab_role_permission：显式为 SUPER_ADMIN 角色补齐这 4 条权限点的授权。
SET @super_admin_role_id := (SELECT `id` FROM `tab_role` WHERE `code` = 'SUPER_ADMIN');

SET @image_management_view_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'ImageManagement:image:view');
SET @image_management_add_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'ImageManagement:image:add');
SET @image_management_delete_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'ImageManagement:image:delete');
SET @image_gallery_view_permission_id := (SELECT `id` FROM `tab_permission` WHERE `code` = 'ImageGallery:image:view');

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @image_management_view_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @image_management_view_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @image_management_view_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @image_management_add_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @image_management_add_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @image_management_add_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @image_management_delete_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @image_management_delete_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @image_management_delete_permission_id);

INSERT INTO `tab_role_permission` (`role_id`, `permission_id`, `create_by`, `create_time`, `update_by`, `update_time`)
SELECT @super_admin_role_id, @image_gallery_view_permission_id, @admin_user_id_text, NOW(), @admin_user_id_text, NOW()
WHERE @super_admin_role_id IS NOT NULL
  AND @image_gallery_view_permission_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM `tab_role_permission`
      WHERE `role_id` = @super_admin_role_id AND `permission_id` = @image_gallery_view_permission_id);
