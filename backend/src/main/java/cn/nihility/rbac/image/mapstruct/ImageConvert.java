package cn.nihility.rbac.image.mapstruct;

import cn.nihility.rbac.image.dto.ImageVO;
import cn.nihility.rbac.image.entity.ImageEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 图片记录实体与返回视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态单例调用（add-image-gallery change tasks.md 4.4）。
 */
@Mapper
public interface ImageConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    ImageConvert INSTANCE = Mappers.getMapper(ImageConvert.class);

    /**
     * 实体转返回视图对象。
     *
     * @param entity 图片记录实体
     * @return 返回视图对象
     */
    ImageVO toVO(ImageEntity entity);

    /**
     * 实体列表转返回视图对象列表。
     *
     * @param entities 图片记录实体列表
     * @return 返回视图对象列表
     */
    List<ImageVO> toVOList(List<ImageEntity> entities);
}
