package cn.nihility.rbac.sync.changelog.mapper;

import cn.nihility.rbac.sync.changelog.entity.AppSyncMetadataEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 应用同步全局元数据 Mapper，单表 CRUD 直接复用 {@link BaseMapper}；
 * {@link #advanceRetentionFloorSeqIfGreater} 是唯一的例外，SQL 写在
 * {@code resources/mybatis/mapper/AppSyncMetadataMapper.xml} 里。
 */
@Mapper
public interface AppSyncMetadataMapper extends BaseMapper<AppSyncMetadataEntity> {

    /**
     * 原子推进指定键的元数据值：仅当 {@code newValue}（按数字比较）大于当前值时才更新
     * （{@code GREATEST}，不先查后写），保证并发/乱序调用不会把值往回推（app-sync-changelog-
     * pull change design.md Decision 8）。
     *
     * @param key        元数据键
     * @param newValue   本次待推进的新值（十进制字符串）
     * @param updateTime 更新时间
     * @return 受影响行数，键不存在时为 0
     */
    int advanceRetentionFloorSeqIfGreater(@Param("key") String key, @Param("newValue") String newValue,
            @Param("updateTime") LocalDateTime updateTime);
}
