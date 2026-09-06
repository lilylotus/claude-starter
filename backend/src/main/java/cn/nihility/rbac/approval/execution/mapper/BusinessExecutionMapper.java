package cn.nihility.rbac.approval.execution.mapper;

import cn.nihility.rbac.approval.execution.entity.BusinessExecutionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业务执行尝试记录 MyBatis-Plus 数据访问接口，对应 {@code tab_wf_business_execution}
 * （production-approval-lifecycle change tasks.md 7.3）。单表 CRUD 直接复用 {@link BaseMapper}，
 * 无需自定义查询 SQL。
 */
@Mapper
public interface BusinessExecutionMapper extends BaseMapper<BusinessExecutionEntity> {
}
