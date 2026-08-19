package cn.nihility.rbac.dict.mapper;

import cn.nihility.rbac.dict.entity.DictItemEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 字典项 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}；
 * {@link #selectSyncPullPage} 是唯一的例外，SQL 写在
 * {@code resources/mybatis/mapper/DictItemMapper.xml} 里。
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItemEntity> {

    /**
     * 分页拉取字典项当前数据，不过滤 {@code status}（停用/已删除记录原样返回），按
     * {@code update_time ASC, id ASC} 排序，不做组织范围过滤（字典无组织范围概念，与
     * APP/ROLE 现状一致）（app-sync-drop-changelog change design.md Decision 7，三次实现
     * 后修正）。
     *
     * @param offset         偏移量
     * @param limit          每页大小
     * @param updateTimeFrom 更新时间范围起点（含），可为空
     * @param updateTimeTo   更新时间范围终点（含），可为空
     * @param ids            主键 id 列表精确过滤，{@code null} 表示不过滤
     * @param codes          字典项自身编码列表精确过滤，{@code null} 表示不过滤（同一编码在
     *                       不同字典类型下可能命中多条记录，均会返回）
     * @return 查询结果列表
     */
    List<DictItemEntity> selectSyncPullPage(@Param("offset") int offset, @Param("limit") int limit,
            @Param("updateTimeFrom") LocalDateTime updateTimeFrom, @Param("updateTimeTo") LocalDateTime updateTimeTo,
            @Param("ids") List<Long> ids, @Param("codes") List<String> codes);
}
