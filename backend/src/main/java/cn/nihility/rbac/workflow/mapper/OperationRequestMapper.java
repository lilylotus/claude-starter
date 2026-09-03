package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.OperationRequestEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作幂等记录数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface OperationRequestMapper extends BaseMapper<OperationRequestEntity> {
}
