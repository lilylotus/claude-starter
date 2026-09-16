package cn.nihility.rbac.fileupload.mapper;

import cn.nihility.rbac.fileupload.entity.FileUploadEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件上传记录 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}，
 * 不在此处编写 SQL。
 */
@Mapper
public interface FileUploadMapper extends BaseMapper<FileUploadEntity> {
}
