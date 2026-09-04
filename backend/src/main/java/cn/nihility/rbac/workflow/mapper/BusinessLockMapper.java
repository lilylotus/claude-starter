package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.BusinessLockEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务活动申请锁数据访问接口。表复合主键 {@code (biz_type, target_key)}，单表 CRUD 直接复用
 * {@link BaseMapper}，但调用方一律通过 {@code LambdaQueryWrapper}/{@code LambdaUpdateWrapper}
 * 显式带条件读写，不使用假设单列主键的 {@code selectById}/{@code updateById}。
 */
@Mapper
public interface BusinessLockMapper extends BaseMapper<BusinessLockEntity> {
}
