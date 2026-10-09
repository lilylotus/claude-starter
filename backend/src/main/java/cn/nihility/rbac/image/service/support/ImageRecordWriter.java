package cn.nihility.rbac.image.service.support;

import cn.nihility.rbac.image.entity.ImageEntity;
import cn.nihility.rbac.image.mapper.ImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 图片记录落库组件，单独成一个 Spring bean 只为了让 {@link #insert} 上的
 * {@link Transactional} 生效于独立的方法调用（Spring AOP 代理对同类内部方法调用不生效）。
 * 写盘操作（不是事务性资源）在事务方法之外先完成，只对数据库插入这一步开启事务，避免
 * "数据库插入回滚了但磁盘文件已经落地"与"事务长时间持有数据库连接等待磁盘 I/O"两个问题
 * （add-image-gallery change tasks.md 6.2.2，照抄 {@code FileUploadRecordWriter} 的拆分
 * 方式）。
 */
@Component
@RequiredArgsConstructor
public class ImageRecordWriter {

    /** 图片记录数据访问接口。 */
    private final ImageMapper imageMapper;

    /**
     * 插入一条图片记录。
     *
     * @param entity 待插入的图片记录实体（已完成写盘，各字段已赋值完毕）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void insert(ImageEntity entity) {
        imageMapper.insert(entity);
    }
}
