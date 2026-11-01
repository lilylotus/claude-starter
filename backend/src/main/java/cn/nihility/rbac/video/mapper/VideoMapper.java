package cn.nihility.rbac.video.mapper;

import cn.nihility.rbac.video.entity.VideoEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 视频记录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，不编写 SQL。
 */
@Mapper
public interface VideoMapper extends BaseMapper<VideoEntity> {
}
