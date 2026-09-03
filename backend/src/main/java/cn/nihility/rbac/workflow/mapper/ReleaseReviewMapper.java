package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ReleaseReviewEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 发布审核记录数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface ReleaseReviewMapper extends BaseMapper<ReleaseReviewEntity> {
}
