package cn.nihility.rbac.common.mapper;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/** 为同步实体提供数据库原子版本递增能力的通用 Mapper。 */
public interface VersionedBaseMapper<T> extends BaseMapper<T> {

    /**
     * 使用单条 SQL 原子递增版本。调用方应处于业务事务中，并在成功后重新读取实体获得新版本。
     *
     * @param id 实体 id
     * @return 受影响行数
     */
    default int incrementVersion(Long id) {
        return update(null, new UpdateWrapper<T>().eq("id", id).setSql("version = version + 1"));
    }
}
