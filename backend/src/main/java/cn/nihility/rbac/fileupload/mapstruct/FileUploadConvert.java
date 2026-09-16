package cn.nihility.rbac.fileupload.mapstruct;

import cn.nihility.rbac.fileupload.dto.FileUploadVO;
import cn.nihility.rbac.fileupload.entity.FileUploadEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 文件上传记录实体与返回视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态单例调用。
 */
@Mapper
public interface FileUploadConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    FileUploadConvert INSTANCE = Mappers.getMapper(FileUploadConvert.class);

    /**
     * 实体转返回视图对象。
     *
     * @param entity 文件上传记录实体
     * @return 返回视图对象
     */
    FileUploadVO toVO(FileUploadEntity entity);
}
