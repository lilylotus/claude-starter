package cn.nihility.rbac.identity.upstream.mapper;

import cn.nihility.rbac.identity.upstream.entity.UpstreamSyncRecordDetailEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 上游数据同步执行记录明细 MyBatis-Plus 数据访问接口，单表 CRUD 直接复用
 * {@link BaseMapper}，不在此处编写 SQL。
 */
@Mapper
public interface UpstreamSyncRecordDetailMapper extends BaseMapper<UpstreamSyncRecordDetailEntity> {
}
