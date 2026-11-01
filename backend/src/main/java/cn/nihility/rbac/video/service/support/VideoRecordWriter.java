package cn.nihility.rbac.video.service.support;

import cn.nihility.rbac.video.entity.VideoEntity;
import cn.nihility.rbac.video.mapper.VideoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 视频记录落库组件，单独成 bean 使 {@link #insert} 上的事务注解生效；写盘在事务之外先完成，
 * 只对数据库插入开启事务，避免事务长时间持有连接等待大文件磁盘 I/O（与
 * {@code ImageRecordWriter} 的拆分方式一致）。
 */
@Component
@RequiredArgsConstructor
public class VideoRecordWriter {

    /** 视频记录数据访问接口。 */
    private final VideoMapper videoMapper;

    /**
     * 插入一条视频记录。
     *
     * @param entity 待插入的视频记录实体（已完成写盘）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void insert(VideoEntity entity) {
        videoMapper.insert(entity);
    }
}
