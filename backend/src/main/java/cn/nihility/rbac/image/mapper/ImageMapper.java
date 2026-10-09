package cn.nihility.rbac.image.mapper;

import cn.nihility.rbac.image.entity.ImageEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 图片记录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL（add-image-gallery change design.md Decision 1：自包含单表，
 * 不需要 JOIN 查询）。
 */
@Mapper
public interface ImageMapper extends BaseMapper<ImageEntity> {
}
