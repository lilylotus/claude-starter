package cn.nihility.rbac.fileupload.service.support;

import cn.nihility.rbac.fileupload.entity.FileUploadEntity;
import cn.nihility.rbac.fileupload.mapper.FileUploadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文件上传记录落库组件，单独成一个 Spring bean 只为了让 {@link #insert} 上的
 * {@link Transactional} 生效于独立的方法调用（Spring AOP 代理对同类内部方法调用不生效）。
 * 写盘操作（不是事务性资源）在事务方法之外先完成，只对数据库插入这一步开启事务，避免
 * "数据库插入回滚了但磁盘文件已经落地"与"事务长时间持有数据库连接等待磁盘 I/O"两个问题
 * （add-file-upload change design.md 事务边界）。
 */
@Component
@RequiredArgsConstructor
public class FileUploadRecordWriter {

    /** 文件上传记录数据访问接口。 */
    private final FileUploadMapper fileUploadMapper;

    /**
     * 插入一条文件上传记录。
     *
     * @param entity 待插入的文件上传记录实体（已完成写盘，各字段已赋值完毕）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void insert(FileUploadEntity entity) {
        fileUploadMapper.insert(entity);
    }
}
