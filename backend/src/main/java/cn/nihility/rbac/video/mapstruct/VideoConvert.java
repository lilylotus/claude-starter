package cn.nihility.rbac.video.mapstruct;

import cn.nihility.rbac.video.dto.VideoVO;
import cn.nihility.rbac.video.entity.VideoEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 视频记录实体与返回视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface VideoConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    VideoConvert INSTANCE = Mappers.getMapper(VideoConvert.class);

    /**
     * 实体转返回视图对象。
     *
     * @param entity 视频记录实体
     * @return 返回视图对象
     */
    VideoVO toVO(VideoEntity entity);

    /**
     * 实体列表转返回视图对象列表。
     *
     * @param entities 视频记录实体列表
     * @return 返回视图对象列表
     */
    List<VideoVO> toVOList(List<VideoEntity> entities);
}
