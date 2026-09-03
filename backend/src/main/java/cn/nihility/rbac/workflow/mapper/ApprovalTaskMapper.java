package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审批任务数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTaskEntity> {
}
