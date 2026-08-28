package cn.nihility.rbac.sync.cursor.mapper;

import cn.nihility.rbac.sync.cursor.entity.AppSyncCursorEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用同步服务端投递水位 Mapper，单表 CRUD 直接复用 {@link BaseMapper}；
 * {@link #upsertLastDeliveredSeq} 是唯一的例外，SQL 写在
 * {@code resources/mybatis/mapper/AppSyncCursorMapper.xml} 里。
 */
@Mapper
public interface AppSyncCursorMapper extends BaseMapper<AppSyncCursorEntity> {

    /**
     * 原子插入或更新 {@code (appRefId, entityType)} 的投递水位：不存在则插入，存在则取
     * {@code GREATEST(现值, 本次 nextSeq)}，不先查后写，避免并发/乱序请求导致水位回退
     * （app-sync-changelog-pull change design.md Decision 9）。
     *
     * @param appRefId   应用 id
     * @param entityType 同步实体类型
     * @param nextSeq    本次响应的 {@code nextSeq}
     * @param updateTime 更新时间
     * @return 受影响行数
     */
    int upsertLastDeliveredSeq(@Param("appRefId") Long appRefId, @Param("entityType") String entityType,
            @Param("nextSeq") Long nextSeq, @Param("updateTime") LocalDateTime updateTime);
}
